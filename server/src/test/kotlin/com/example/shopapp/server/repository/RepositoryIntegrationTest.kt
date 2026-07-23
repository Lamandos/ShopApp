package com.example.shopapp.server.repository

import com.example.shopapp.server.db.Database
import com.example.shopapp.server.dto.CreateOrderItemRequest
import com.example.shopapp.server.dto.CreateOrderRequest
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class RepositoryIntegrationTest {
    @Test
    fun createOrderWritesAllChangesInTemporaryDatabase() = withDatabaseCopy { path ->
        val database = Database(path)
        val stockBefore = database.longValue("SELECT stock FROM products WHERE id = 9")

        val result = OrderRepository(database).create(
            CreateOrderRequest(
                customerName = "Integration test",
                phone = "+79990000000",
                address = "Test address",
                items = listOf(CreateOrderItemRequest(productId = 9, quantity = 2)),
            )
        )

        assertEquals(
            listOf("Integration test", "+79990000000", "Test address"),
            database.row(
                "SELECT customer_name, phone, address FROM orders WHERE id = ?",
                result.orderId,
            ),
        )
        assertEquals(
            listOf("9", "JBL Tune 520BT", "599000", "2"),
            database.row(
                "SELECT product_id, product_name, price, quantity FROM order_items WHERE order_id = ?",
                result.orderId,
            ),
        )
        assertEquals(stockBefore - 2, database.longValue("SELECT stock FROM products WHERE id = 9"))
        assertEquals(
            listOf("new"),
            database.row(
                "SELECT status FROM order_status_history WHERE order_id = ?",
                result.orderId,
            ),
        )
    }

    @Test
    fun statsUseStatusHistoryAndExcludeNewAndCancelledFromRevenue() = withDatabaseCopy { path ->
        val database = Database(path)
        database.transaction { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate("DELETE FROM order_status_history")
                statement.executeUpdate("DELETE FROM order_items")
                statement.executeUpdate("DELETE FROM orders")
                statement.executeUpdate(
                    """
                    INSERT INTO orders(id, customer_name, created_at, status) VALUES
                        (1001, 'Paid', '2025-01-10T10:00:00', 'cancelled'),
                        (1002, 'New', '2025-01-10T11:00:00', 'paid'),
                        (1003, 'Cancelled', '2025-01-10T12:00:00', 'paid')
                    """.trimIndent()
                )
                statement.executeUpdate(
                    """
                    INSERT INTO order_items(order_id, product_id, product_name, price, quantity) VALUES
                        (1001, 1, 'Paid snapshot', 1000, 2),
                        (1002, 2, 'New snapshot', 9000, 1),
                        (1003, 3, 'Cancelled snapshot', 8000, 1)
                    """.trimIndent()
                )
                statement.executeUpdate(
                    """
                    INSERT INTO order_status_history(order_id, status, created_at) VALUES
                        (1001, 'paid', '2025-01-10T10:01:00'),
                        (1002, 'new', '2025-01-10T11:01:00'),
                        (1003, 'cancelled', '2025-01-10T12:01:00')
                    """.trimIndent()
                )
            }
        }

        val stats = StatsRepository(database).get(
            from = LocalDate.parse("2025-01-10"),
            to = LocalDate.parse("2025-01-10"),
        )

        assertEquals(2_000L, stats.revenue)
        assertEquals(2L, stats.ordersCount)
        assertEquals(2_000L, stats.averageCheck)
        assertEquals(listOf("Paid snapshot"), stats.topProducts.map { it.productName })
    }

    private fun withDatabaseCopy(test: (Path) -> Unit) {
        val copy = Files.createTempFile("shopapp-repository-test-", ".db")
        try {
            Files.copy(Path.of("data/test_task.db"), copy, StandardCopyOption.REPLACE_EXISTING)
            test(copy)
        } finally {
            Files.deleteIfExists(copy)
        }
    }

    private fun Database.longValue(sql: String): Long = query { connection ->
        connection.prepareStatement(sql).use { statement ->
            statement.executeQuery().use { result ->
                check(result.next())
                result.getLong(1)
            }
        }
    }

    private fun Database.row(sql: String, id: Long): List<String> = query { connection ->
        connection.prepareStatement(sql).use { statement ->
            statement.setLong(1, id)
            statement.executeQuery().use { result ->
                check(result.next())
                (1..result.metaData.columnCount).map(result::getString)
            }
        }
    }
}
