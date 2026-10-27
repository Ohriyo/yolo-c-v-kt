package com.yoloc.app.db

import androidx.room3.executeSQL
import androidx.room3.useReaderConnection
import androidx.room3.useWriterConnection
import androidx.test.platform.app.InstrumentationRegistry
import com.yoloc.app.features.detection.ModelConfig
import com.yoloc.app.features.scans.ScanFileStore
import java.io.File
import java.io.IOException

/** RN: __tests__/helpers/testDb.ts, on the phone with the real Room database (in memory). */
object TestDb {
    val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    val config: ModelConfig by lazy { ModelConfig.load(context) }

    /** Opened, created and seeded like the app's database, but in memory. */
    fun holder() = AppDatabaseHolder(context, config, name = null)

    /** Created (schema + defaults) but not seeded, for seed tests. */
    fun unseeded() = buildAppDatabase(context, name = null)
}

suspend fun AppDatabase.exec(sql: String) = useWriterConnection { it.executeSQL(sql) }

/** Every row of `sql`, each as a list of its columns' text values (null as null). */
suspend fun AppDatabase.rows(sql: String): List<List<String?>> = useReaderConnection { connection ->
    connection.usePrepared(sql) { stmt ->
        buildList {
            while (stmt.step()) {
                add((0 until stmt.getColumnCount()).map { if (stmt.isNull(it)) null else stmt.getText(it) })
            }
        }
    }
}

suspend fun AppDatabase.column(sql: String): List<String?> = rows(sql).map { it[0] }

suspend fun AppDatabase.rowCount(table: String): Int = column("SELECT COUNT(*) FROM $table").single()!!.toInt()

/** In-memory ScanFileStore that can be told to fail, to test cleanup paths (RN: createMemoryFileStore). */
class MemoryFileStore : ScanFileStore {
    val files = mutableMapOf<String, String>()
    val removed = mutableListOf<String>()
    var failNextCopy = false
    var failNextThumbnail = false

    override suspend fun copyIn(source: File, relativePath: String) {
        if (failNextCopy) {
            failNextCopy = false
            // Simulate a copy that died halfway and left a partial file behind.
            files[relativePath] = "partial"
            throw IOException("disk full")
        }
        files[relativePath] = source.path
    }

    override suspend fun makeThumbnail(relativeSource: String, relativeDest: String, width: Int, height: Int) {
        if (failNextThumbnail) {
            failNextThumbnail = false
            throw IOException("decode failed")
        }
        val source = files[relativeSource] ?: throw IOException("No file at $relativeSource")
        files[relativeDest] = "thumb:$source"
    }

    override suspend fun remove(relativePath: String) {
        removed += relativePath
        files.remove(relativePath)
    }

    override suspend fun removeAll() {
        removed += files.keys
        files.clear()
    }

    /** Pretend each stored file is 100 KB. */
    override suspend fun usedBytes() = files.size * 100_000L

    override suspend fun readBytes(relativePath: String): ByteArray =
        "jpeg:${files[relativePath] ?: throw IOException("No file at $relativePath")}".encodeToByteArray()

    override fun sizeOf(relativePath: String): Long =
        files[relativePath]?.let { "jpeg:$it".encodeToByteArray().size.toLong() } ?: 0L

    override fun fileFor(relativePath: String) = File("/doc/$relativePath")
}
