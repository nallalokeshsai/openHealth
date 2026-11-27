package app.openhealth.integrations.food

import app.openhealth.core.nutrition.Nutrient
import app.openhealth.core.seam.HttpRequest
import app.openhealth.core.seam.HttpResponse
import app.openhealth.core.seam.HttpTransport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class OpenFoodFactsParserTest {
    private val sample = """
        {
          "status": 1,
          "product": {
            "code": "3017620422003",
            "product_name": "Nutella",
            "brands": "Ferrero, Nutella",
            "serving_quantity": "15",
            "serving_size": "15 g",
            "nutriments": {
              "energy-kcal_100g": 539,
              "proteins_100g": 6.3,
              "carbohydrates_100g": 57.5,
              "fat_100g": 30.9,
              "fiber_100g": 0,
              "sugars_100g": 56.3,
              "saturated-fat_100g": 10.6,
              "sodium_100g": 0.0428,
              "vitamin-c_100g": 0.0009,
              "vitamin-d_100g": 0.0000021
            },
            "allergens_tags": ["en:milk", "en:nuts", "en:soybeans"],
            "ingredients_analysis_tags": ["en:palm-oil", "en:vegetarian"],
            "ingredients_text": "Sugar, palm oil, hazelnuts, milk"
          }
        }
    """.trimIndent()

    @Test
    fun aProductBecomesOurNutrientsInTheirOwnUnits() {
        val result = OffParser.parse(sample, "3017620422003") as OffParser.Result.Found
        val product = result.product
        assertEquals("Nutella", product.name)
        assertEquals("Ferrero", product.brand)
        assertEquals(15.0, product.servingGrams)
        assertEquals("15 g", product.servingLabel)
        assertEquals(539.0, product.per100g.getValue(Nutrient.ENERGY))
        assertEquals(6.3, product.per100g.getValue(Nutrient.PROTEIN))
        assertEquals(0.0, product.per100g.getValue(Nutrient.FIBRE))
        // 0.0428 g of sodium per 100 g is 42.8 mg.
        assertEquals(42.8, product.per100g.getValue(Nutrient.SODIUM), 1e-9)
        // 0.0009 g of vitamin C is 0.9 mg; 0.0000021 g of vitamin D is 2.1 µg.
        assertEquals(0.9, product.per100g.getValue(Nutrient.VITAMIN_C), 1e-9)
        assertEquals(2.1, product.per100g.getValue(Nutrient.VITAMIN_D), 1e-9)
    }

    @Test
    fun allergensAndIngredientsArriveAsTags() {
        val product = (OffParser.parse(sample, "3017620422003") as OffParser.Result.Found).product
        assertTrue("en:milk" in product.allergens)
        assertTrue("en:nuts" in product.allergens)
        assertTrue("en:vegetarian" in product.analysisTags)
        assertEquals("Sugar, palm oil, hazelnuts, milk", product.ingredientsText)
    }

    @Test
    fun aMissingProductIsNotFound() {
        assertEquals(OffParser.Result.NotFound, OffParser.parse("""{"status":0}""", "1"))
        assertEquals(
            OffParser.Result.NotFound,
            OffParser.parse("""{"status":1,"product":null}""", "1"),
        )
    }

    @Test
    fun aProductWithoutANameOrNutritionIsInvalid() {
        assertTrue(
            OffParser.parse(
                """{"status":1,"product":{"nutriments":{"proteins_100g":1}}}""",
                "1",
            ) is OffParser.Result.Invalid,
        )
        assertTrue(
            OffParser.parse(
                """{"status":1,"product":{"product_name":"Thing"}}""",
                "1",
            ) is OffParser.Result.Invalid,
        )
        assertTrue(OffParser.parse("not json", "1") is OffParser.Result.Invalid)
    }

    @Test
    fun energyMayArriveInKilojoules() {
        val product = (
            OffParser.parse(
                """{"status":1,"product":{"product_name":"Bar","nutriments":""" +
                    """{"energy_100g":418.4,"proteins_100g":1}}}""",
                "1",
            ) as OffParser.Result.Found
            ).product
        assertEquals(100.0, product.per100g.getValue(Nutrient.ENERGY), 1e-9)
    }

    @Test
    fun aBarcodeMustBeDigitsOfAPlausibleLength() {
        assertTrue(OpenFoodFactsClient.isPlausibleBarcode("3017620422003"))
        assertTrue(!OpenFoodFactsClient.isPlausibleBarcode("123"))
        assertTrue(!OpenFoodFactsClient.isPlausibleBarcode("30176A0422003"))
        assertTrue(!OpenFoodFactsClient.isPlausibleBarcode("3017 6204"))
    }
}

class OpenFoodFactsClientTest {
    private class FakeTransport(private val response: () -> HttpResponse) : HttpTransport {
        val requests = mutableListOf<HttpRequest>()

        override suspend fun get(request: HttpRequest): HttpResponse {
            requests += request
            return response()
        }

        override suspend fun post(request: HttpRequest): HttpResponse {
            requests += request
            return response()
        }
    }

    @Test
    fun theRequestCarriesOnlyTheBarcodeAndAnAppIdentifier() = runTest {
        val transport = FakeTransport { HttpResponse(404, "") }
        val client = OpenFoodFactsClient(transport)
        assertEquals(OffLookup.NotFound, client.lookup("3017620422003"))
        val request = transport.requests.single()
        assertTrue(request.url.contains("/api/v2/product/3017620422003.json"))
        assertTrue(request.url.startsWith(OpenFoodFactsClient.DEFAULT_BASE_URL))
        assertEquals(2, request.headers.size)
        assertTrue(request.headers.getValue("User-Agent").startsWith("openHealth/"))
        assertTrue(!request.headers.keys.any { it.equals("Cookie", ignoreCase = true) })
    }

    @Test
    fun anInvalidBarcodeNeverLeavesTheDevice() = runTest {
        val transport = FakeTransport { error("must not be called") }
        assertEquals(OffLookup.NotFound, OpenFoodFactsClient(transport).lookup("abc"))
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun aTransportFailureIsUnavailableNotAFalseProduct() = runTest {
        val transport = FakeTransport { error("offline") }
        val result = OpenFoodFactsClient(transport).lookup("3017620422003")
        assertTrue(result is OffLookup.Unavailable)
        assertNull((result as? OffLookup.Found)?.product)
    }

    @Test
    fun aServerErrorIsUnavailable() = runTest {
        val transport = FakeTransport { HttpResponse(500, "") }
        val result = OpenFoodFactsClient(transport).lookup("3017620422003")
        assertTrue(result is OffLookup.Unavailable)
    }
}

class OpenFoodFactsSearchTest {
    @Test
    fun aSearchPayloadKeepsOnlyProductsWithANameAndNutrition() {
        val body = """
            {"products": [
              {"code": "111", "product_name": "Oat bar",
               "nutriments": {"energy-kcal_100g": 400, "proteins_100g": 8}},
              {"code": "222", "product_name": "No nutrition"},
              {"code": "333", "nutriments": {"energy-kcal_100g": 100}}
            ]}
        """.trimIndent()
        val products = OffParser.parseSearch(body)
        assertEquals(1, products.size)
        assertEquals("Oat bar", products.single().name)
        assertEquals("111", products.single().barcode)
    }

    @Test
    fun aGarbledSearchPayloadIsEmptyNotACrash() {
        assertTrue(OffParser.parseSearch("not json").isEmpty())
    }
}
