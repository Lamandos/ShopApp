package com.example.shopapp.server.db

import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager

class Database(private val path: Path) {
    init {
        require(Files.isRegularFile(path)) { "SQLite database not found: ${path.toAbsolutePath()}" }
        Class.forName("org.sqlite.JDBC")
    }

    fun connection(): Connection =
        DriverManager.getConnection("jdbc:sqlite:${path.toAbsolutePath()}").also { connection ->
            connection.createStatement().use { statement ->
                statement.execute("PRAGMA foreign_keys = ON")
            }
        }

    fun <T> query(block: (Connection) -> T): T =
        connection().use(block)

    fun <T> transaction(block: (Connection) -> T): T =
        connection().use { connection ->
            connection.autoCommit = false
            try {
                block(connection).also { connection.commit() }
            } catch (exception: Exception) {
                connection.rollback()
                throw exception
            }
        }
}
