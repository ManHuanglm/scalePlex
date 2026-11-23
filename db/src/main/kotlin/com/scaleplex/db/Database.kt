package com.scaleplex.db

import org.slf4j.LoggerFactory
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.ResultSet

/**
 * Manages the SQLite connection, applies schema migrations and provides backup/restore.
 * A single serialized connection is used (SQLite is file-level locked); DAOs run their
 * work through [withDb].
 */
class Database(private val dbFile: File) : AutoCloseable {

    private val log = LoggerFactory.getLogger(Database::class.java)
    private var conn: Connection? = null

    init {
        Class.forName("org.sqlite.JDBC")
        conn = DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}")
        migrate()
    }

    fun <T> withDb(block: (Connection) -> T): T {
        val c = conn ?: error("database closed")
        synchronized(c) { return block(c) }
    }

    private fun migrate() = withDb { c ->
        val userVersion = c.createStatement().use { st ->
            st.executeQuery("PRAGMA user_version").use { rs -> if (rs.next()) rs.getInt(1) else 0 }
        }
        log.info("SQLite user_version=$userVersion, applying migrations...")
        c.autoCommit = false
        try {
            when {
                userVersion < 1 -> SchemaV1.apply(c)
            }
            if (userVersion < 2) SchemaV2.apply(c)
            SchemaV1.seedDefaults(c)
            c.createStatement().use { it.executeUpdate("PRAGMA user_version = 2") }
            c.commit()
        } catch (t: Throwable) {
            c.rollback()
            throw t
        } finally {
            c.autoCommit = true
        }
        log.info("SQLite schema ready")
    }

    /** Copy the live database to [target] after checkpointing WAL for a consistent snapshot. */
    fun backupTo(target: File) {
        withDb { c ->
            c.createStatement().use { it.executeUpdate("PRAGMA wal_checkpoint(TRUNCATE)") }
        }
        dbFile.copyTo(target, overwrite = true)
    }

    /**
     * Restore the database from a backup file: close the live connection, overwrite
     * the db file (including WAL/SHM companions), reopen and re-run migrations.
     * Cached DAO statements are per-call so no further invalidation is needed;
     * the UI should prompt the user to restart for in-memory caches to refresh.
     */
    fun restoreFrom(backup: File) {
        if (!backup.exists()) error("backup file not found: $backup")
        close()
        dbFile.copyTo(File(dbFile.parentFile, "${dbFile.name}.bak.${System.currentTimeMillis()}"), overwrite = true)
        backup.copyTo(dbFile, overwrite = true)
        runCatching { File("$dbFile-wal").delete() }
        runCatching { File("$dbFile-shm").delete() }
        conn = DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}")
        migrate()
        log.info("Database restored from $backup")
    }

    override fun close() {
        runCatching { conn?.close() }
        conn = null
    }
}

// ---- JDBC helpers used by DAOs (top-level extensions on Connection) ----
fun PreparedStatement.bind(vararg args: Any?): PreparedStatement {
    args.forEachIndexed { i, a ->
        when (a) {
            null -> setObject(i + 1, null)
            is Int -> setInt(i + 1, a)
            is Long -> setLong(i + 1, a)
            is Boolean -> setInt(i + 1, if (a) 1 else 0)
            is String -> setString(i + 1, a)
            is Double -> setDouble(i + 1, a)
            else -> setObject(i + 1, a)
        }
    }
    return this
}

fun <T> Connection.query(sql: String, vararg args: Any?, mapper: (ResultSet) -> T): List<T> =
    prepareStatement(sql).bind(*args).use { ps ->
        ps.executeQuery().use { rs ->
            val out = mutableListOf<T>()
            while (rs.next()) out.add(mapper(rs))
            out
        }
    }

fun Connection.exec(sql: String, vararg args: Any?): Int =
    prepareStatement(sql).bind(*args).use { it.executeUpdate() }

fun Connection.insert(sql: String, vararg args: Any?): Long =
    prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS).bind(*args).use { ps ->
        ps.executeUpdate()
        ps.generatedKeys.use { rs -> if (rs.next()) rs.getLong(1) else -1L }
    }
