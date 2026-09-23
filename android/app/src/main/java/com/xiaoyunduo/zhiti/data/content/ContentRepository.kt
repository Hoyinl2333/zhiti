package com.xiaoyunduo.zhiti.data.content

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.xiaoyunduo.zhiti.data.ContentBlock
import com.xiaoyunduo.zhiti.data.Question
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

class ContentRepository(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(ContentBlock.serializer())

    fun packDirectory(packId: String): File = File(context.filesDir, "content/$packId")
    fun isInstalled(packId: String): Boolean = File(packDirectory(packId), "content.sqlite").isFile
    fun asset(packId: String, relative: String): File = File(packDirectory(packId), relative)

    suspend fun questionCount(packId: String): Int = withDatabase(packId) { db ->
        db.rawQuery("SELECT COUNT(*) FROM questions", null).use { it.moveToFirst(); it.getInt(0) }
    }

    suspend fun categories(packId: String): Map<String, Int> = withDatabase(packId) { db ->
        buildMap {
            db.rawQuery("SELECT category, COUNT(*) FROM questions GROUP BY category ORDER BY category", null).use { cursor ->
                while (cursor.moveToNext()) put(cursor.getString(0), cursor.getInt(1))
            }
        }
    }

    suspend fun answeredByCategory(attemptedQids: Set<String>): Map<String, Int> = withDatabase("judgment") { db ->
        buildMap {
            db.rawQuery("SELECT category, qid FROM questions", null).use { cursor ->
                while (cursor.moveToNext()) {
                    val category = cursor.getString(0)
                    if (cursor.getString(1) in attemptedQids) put(category, (get(category) ?: 0) + 1)
                }
            }
        }
    }

    suspend fun completedMaterialCount(attemptedQids: Set<String>): Int = withDatabase("data-analysis") { db ->
        var completed = 0
        db.rawQuery("SELECT group_concat(qid) FROM questions GROUP BY COALESCE(material_id, 'q:' || qid)", null).use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.getString(0).split(',').all { it in attemptedQids }) completed += 1
            }
        }
        completed
    }

    suspend fun chooseJudgment(category: String, excluded: Set<String>, limit: Int = 5): List<String> =
        queryIds("judgment", "category = ?", arrayOf(category)).filterNot(excluded::contains).shuffled().take(limit)

    suspend fun chooseMaterial(excludedQids: Set<String>): List<String> = withDatabase("data-analysis") { db ->
        val groups = mutableListOf<List<String>>()
        db.rawQuery("SELECT COALESCE(material_id, 'q:' || qid), group_concat(qid) FROM questions GROUP BY COALESCE(material_id, 'q:' || qid) ORDER BY 1", null).use { cursor ->
            while (cursor.moveToNext()) {
                val qids = cursor.getString(1).split(',')
                if (qids.any { it !in excludedQids }) groups += qids
            }
        }
        groups.randomOrNull().orEmpty()
    }

    suspend fun question(qid: String): Question? = withContext(Dispatchers.IO) {
        for (packId in listOf("judgment", "data-analysis")) {
            if (!isInstalled(packId)) continue
            open(packId).use { db ->
                db.rawQuery("SELECT * FROM questions WHERE qid = ?", arrayOf(qid)).use { cursor ->
                    if (cursor.moveToFirst()) return@withContext readQuestion(db, cursor)
                }
            }
        }
        null
    }

    suspend fun questions(qids: List<String>): List<Question> = qids.mapNotNull { question(it) }

    private suspend fun queryIds(packId: String, where: String, args: Array<String>): List<String> = withDatabase(packId) { db ->
        buildList {
            db.rawQuery("SELECT qid FROM questions WHERE $where ORDER BY qid", args).use { cursor ->
                while (cursor.moveToNext()) add(cursor.getString(0))
            }
        }
    }

    private fun readQuestion(db: SQLiteDatabase, cursor: Cursor): Question {
        fun value(name: String): String = cursor.getString(cursor.getColumnIndexOrThrow(name))
        fun blocks(name: String): List<ContentBlock> = json.decodeFromString(serializer, value(name))
        val qid = value("qid")
        val options = buildMap {
            db.rawQuery("SELECT option_key, content_json FROM options WHERE qid = ? ORDER BY option_key", arrayOf(qid)).use { optionCursor ->
                while (optionCursor.moveToNext()) put(optionCursor.getString(0), json.decodeFromString(serializer, optionCursor.getString(1)))
            }
        }
        val materialId = cursor.getString(cursor.getColumnIndexOrThrow("material_id"))
        val material = if (materialId == null) blocks("inline_material_json") else db.rawQuery(
            "SELECT content_json FROM materials WHERE mid = ?", arrayOf(materialId)
        ).use { materialCursor ->
            if (materialCursor.moveToFirst()) json.decodeFromString(serializer, materialCursor.getString(0)) else emptyList()
        }
        return Question(
            qid = qid,
            module = value("module"),
            category = value("category"),
            year = if (cursor.isNull(cursor.getColumnIndexOrThrow("year"))) null else cursor.getInt(cursor.getColumnIndexOrThrow("year")),
            region = value("region"), paper = value("paper"), title = value("title"), stem = blocks("stem_json"),
            options = options, answer = value("answer"), explanation = blocks("explanation_json"),
            fastSolution = blocks("fast_solution_json"), reasoning = blocks("reasoning_json"), pitfalls = blocks("pitfalls_json"),
            materialId = materialId, material = material,
        )
    }

    private fun open(packId: String): SQLiteDatabase = SQLiteDatabase.openDatabase(
        File(packDirectory(packId), "content.sqlite").path, null, SQLiteDatabase.OPEN_READONLY
    )

    private suspend fun <T> withDatabase(packId: String, block: (SQLiteDatabase) -> T): T = withContext(Dispatchers.IO) {
        open(packId).use(block)
    }
}
