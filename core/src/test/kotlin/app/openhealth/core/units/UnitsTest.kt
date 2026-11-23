package app.openhealth.core.units

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class UnitsTest {
    @Test
    fun massConvertsToCanonicalKilograms() {
        val pounds = Measure(220.462262, UcumUnit.POUND).toCanonical()
        assertEquals(100.0, pounds.value, 0.0001)
        assertEquals(UcumUnit.KILOGRAM, pounds.unit)
    }

    @Test
    fun temperatureIsAffine() {
        assertEquals(100.0, Measure(212.0, UcumUnit.FAHRENHEIT).toCanonical().value, 0.0001)
        assertEquals(
            32.0,
            Measure(0.0, UcumUnit.CELSIUS).convertTo(UcumUnit.FAHRENHEIT).value,
            0.0001,
        )
        assertEquals(
            -40.0,
            Measure(-40.0, UcumUnit.CELSIUS).convertTo(UcumUnit.FAHRENHEIT).value,
            0.0001,
        )
    }

    @Test
    fun glucoseUsesMillimolesPerLitre() {
        val value = Measure(90.0, UcumUnit.MILLIGRAM_PER_DECILITRE).toCanonical()
        assertEquals(4.9949, value.value, 0.01)
        assertEquals(UcumUnit.MILLIMOL_PER_LITRE, value.unit)
    }

    @Test
    fun bodyLengthIsCentimetresAndDistanceIsMetres() {
        assertEquals(180.0, Measure(70.866, UcumUnit.INCH).toCanonical().value, 0.001)
        assertEquals(
            UcumUnit.CENTIMETRE,
            UcumUnit.canonicalFor(Dimension.BODY_LENGTH),
        )
        assertEquals(UcumUnit.METRE, UcumUnit.canonicalFor(Dimension.DISTANCE))
    }

    @Test
    fun everyDimensionHasExactlyOneCanonicalUnit() {
        Dimension.entries.forEach { dimension ->
            val canonical = UcumUnit.entries.filter { it.dimension == dimension && it.canonical }
            assertEquals(1, canonical.size, "dimension $dimension")
            assertEquals(1.0, canonical.single().scale)
            assertEquals(0.0, canonical.single().offset)
        }
    }

    @Test
    fun conversionAcrossDimensionsIsRefused() {
        assertFailsWith<IllegalArgumentException> {
            Measure(1.0, UcumUnit.KILOGRAM).convertTo(UcumUnit.CENTIMETRE)
        }
    }

    @Test
    fun aUcumCodeResolvesWithinItsDimension() {
        assertEquals(UcumUnit.METRE, UcumUnit.byUcum("m"))
        assertEquals(UcumUnit.METRE, UcumUnit.byUcum("m", Dimension.DISTANCE))
        assertFailsWith<IllegalArgumentException> { UcumUnit.byUcum("m", Dimension.MASS) }
    }

    @Test
    fun roundingIsDisplayOnly() {
        val rounded = Measure(70.4449, UcumUnit.KILOGRAM).rounded(1)
        assertEquals(70.4, rounded.value, 1e-9)
        assertTrue(rounded.unit.canonical)
    }
}
