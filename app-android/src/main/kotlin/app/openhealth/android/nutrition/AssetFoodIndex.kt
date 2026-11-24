@file:Suppress("MagicNumber", "ReturnCount")

package app.openhealth.android.nutrition

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import app.openhealth.core.nutrition.BundledFoodIndex
import app.openhealth.core.nutrition.FoodIndexMeta
import app.openhealth.core.nutrition.FoodQuery
import app.openhealth.core.nutrition.IndexedFood
import app.openhealth.data.repo.NutritionJson
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicReference

/**
 * The bundled USDA FoodData Central index (`pr.md` §6.3, `tools/foodindex`).
 *
 * The database ships as an asset and is copied once into app-private storage, because a
 * read-only SQLite connection needs a real file. It is plaintext by design: it holds no
 * personal data, only public-domain food records. The encrypted database holds the diary.
 */
class AssetFoodIndex(private val context: Context) : BundledFoodIndex {
    private val lock = Any()
    private val database = AtomicReference<SQLiteDatabase?>(null)
    private val metaHolder = AtomicReference<FoodIndexMeta?>(null)

    override val version: String get() = meta.version

    override val foodCount: Int get() = meta.foodCount

    override val meta: FoodIndexMeta
        get() = metaHolder.get() ?: loadMeta().also(metaHolder::set)

    override fun search(query: String, limit: Int): List<IndexedFood> {
        val tokens = FoodQuery.tokens(query)
        val db = open()
        if (limit <= 0) return emptyList()
        if (tokens.isEmpty()) {
            val normalized = FoodQuery.normalize(query)
            if (normalized.isBlank()) return emptyList()
            return queryByLike(db, normalized, limit)
        }
        val exact = queryByTokens(db, tokens, limit)
        if (exact.isNotEmpty() || tokens.size > 1) return exact
        // A single token can be the start of a word the tokeniser indexed whole. Prefix
        // match the token table before falling back to a full scan of the local index.
        val prefixed = queryByTokenPrefix(db, tokens.first(), limit)
        return prefixed.ifEmpty { queryByLike(db, FoodQuery.normalize(query), limit) }
    }

    override fun byId(id: String): IndexedFood? {
        open().rawQuery(
            "SELECT fdc_id, name, category, nutrients, portions FROM food WHERE fdc_id = ?",
            arrayOf(id),
        ).use { cursor ->
            return if (cursor.moveToFirst()) cursor.toFood() else null
        }
    }

    private fun queryByTokens(
        db: SQLiteDatabase,
        tokens: List<String>,
        limit: Int,
    ): List<IndexedFood> {
        val placeholders = tokens.joinToString(",") { "?" }
        val sql = """
            SELECT f.fdc_id, f.name, f.category, f.nutrients, f.portions
            FROM food f
            JOIN token t ON t.fdc_id = f.fdc_id
            WHERE t.token IN ($placeholders)
            GROUP BY f.fdc_id
            HAVING count(DISTINCT t.token) = ?
            ORDER BY length(f.name), f.sort_name
            LIMIT ?
        """.trimIndent()
        val args = (tokens + tokens.size.toString() + limit.toString()).toTypedArray()
        return read(db.rawQuery(sql, args))
    }

    private fun queryByTokenPrefix(
        db: SQLiteDatabase,
        token: String,
        limit: Int,
    ): List<IndexedFood> {
        val sql = """
            SELECT f.fdc_id, f.name, f.category, f.nutrients, f.portions
            FROM food f
            JOIN token t ON t.fdc_id = f.fdc_id
            WHERE t.token LIKE ? || '%'
            GROUP BY f.fdc_id
            ORDER BY length(f.name), f.sort_name
            LIMIT ?
        """.trimIndent()
        return read(db.rawQuery(sql, arrayOf(token, limit.toString())))
    }

    private fun queryByLike(db: SQLiteDatabase, normalized: String, limit: Int): List<IndexedFood> {
        val sql = """
            SELECT fdc_id, name, category, nutrients, portions
            FROM food
            WHERE sort_name LIKE '%' || ? || '%'
            ORDER BY length(name), sort_name
            LIMIT ?
        """.trimIndent()
        return read(db.rawQuery(sql, arrayOf(normalized, limit.toString())))
    }

    private fun read(cursor: Cursor): List<IndexedFood> = cursor.use {
        val rows = mutableListOf<IndexedFood>()
        while (it.moveToNext()) rows += it.toFood()
        rows
    }

    private fun Cursor.toFood(): IndexedFood = IndexedFood(
        id = getString(0),
        name = getString(1),
        category = getString(2),
        nutrition = app.openhealth.core.nutrition.FoodNutrition(
            per100g = NutritionJson.decodeNutrients(getString(3)),
            portions = NutritionJson.decodePortions(getString(4)),
        ),
    )

    private fun open(): SQLiteDatabase {
        database.get()?.let { return it }
        synchronized(lock) {
            database.get()?.let { return it }
            val file = ensureCopied()
            val opened = SQLiteDatabase.openDatabase(
                file.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS,
            )
            database.set(opened)
            return opened
        }
    }

    private fun ensureCopied(): File {
        val target = File(context.noBackupFilesDir, CACHE_NAME)
        if (target.exists() && target.length() > 0L) return target
        val temporary = File(target.parentFile, "$ASSET_NAME.tmp")
        context.assets.open(ASSET_NAME).use { input ->
            FileOutputStream(temporary).use { output -> input.copyTo(output) }
        }
        if (!temporary.renameTo(target)) {
            temporary.copyTo(target, overwrite = true)
            temporary.delete()
        }
        return target
    }

    private fun loadMeta(): FoodIndexMeta {
        val db = open()
        val values = mutableMapOf<String, String>()
        db.rawQuery("SELECT key, value FROM meta", null).use { cursor ->
            while (cursor.moveToNext()) values[cursor.getString(0)] = cursor.getString(1)
        }
        return FoodIndexMeta(
            version = values["version"] ?: "USDA FoodData Central",
            licence = values["licence"] ?: "CC0-1.0",
            licenceUrl = values["licence_url"]
                ?: "https://creativecommons.org/publicdomain/zero/1.0/",
            foodCount = values["food_count"]?.toIntOrNull() ?: 0,
            foundationCount = values["foundation_count"]?.toIntOrNull() ?: 0,
            srLegacyCount = values["sr_legacy_count"]?.toIntOrNull() ?: 0,
            sourceUrls = listOfNotNull(
                values["foundation_url"],
                values["sr_legacy_url"],
            ),
            generatedAt = values["generated_at"].orEmpty(),
        )
    }

    private companion object {
        const val ASSET_NAME = "foodindex.db"

        /**
         * The copied file's name carries the dataset generation. A later phase that
         * replaces the asset must bump this, otherwise an upgraded app would keep the
         * old public-domain index in app storage forever.
         */
        const val CACHE_NAME = "foodindex-v1-2026-04-30.db"
    }
}
