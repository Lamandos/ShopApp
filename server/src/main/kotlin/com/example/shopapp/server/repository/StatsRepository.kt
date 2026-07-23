package com.example.shopapp.server.repository

import com.example.shopapp.server.db.Database
import com.example.shopapp.server.dto.StatsDto
import com.example.shopapp.server.dto.TopProductDto
import java.sql.Connection

class StatsRepository(private val database: Database) {
    fun get(from: String?, to: String?): StatsDto = database.query { connection ->
        val paidOrders = loadPaidOrders(connection, from, to)
        val revenue = paidOrders.sumOf { it.totalKopecks }
        val orderCount = countNotCancelled(connection, from, to)
        val paidOrderCount = paidOrders.size.toLong()

        StatsDto(
            revenueKopecks = revenue,
            orderCount = orderCount,
            paidOrderCount = paidOrderCount,
            averageOrderKopecks = if (paidOrderCount == 0L) 0 else revenue / paidOrderCount,
            topProducts = loadTopProducts(connection, from, to),
        )
    }

    private data class PaidOrder(val totalKopecks: Long)

    private fun loadPaidOrders(connection: Connection, from: String?, to: String?): List<PaidOrder> {
        val (dateSql, parameters) = dateFilter(from, to)
        val sql =
            """
            SELECT o.created_at, o.promocode, SUM(oi.price * oi.quantity) AS subtotal
            FROM orders o
            JOIN order_items oi ON oi.order_id = o.id
            WHERE (
                SELECT h.status FROM order_status_history h
                WHERE h.order_id = o.id
                ORDER BY h.created_at DESC, h.id DESC LIMIT 1
            ) IN ('paid', 'shipped', 'delivered')
            $dateSql
            GROUP BY o.id
            """.trimIndent()

        return connection.prepareStatement(sql).use { statement ->
            parameters.forEachIndexed { index, value -> statement.setString(index + 1, value) }
            statement.executeQuery().use { result ->
                buildList {
                    while (result.next()) {
                        val subtotal = result.getLong("subtotal")
                        val promocode = result.getString("promocode")?.let {
                            findPromocode(connection, it, subtotal, result.getString("created_at"))
                        }
                        add(PaidOrder(subtotal - (promocode?.discount(subtotal) ?: 0)))
                    }
                }
            }
        }
    }

    private fun countNotCancelled(connection: Connection, from: String?, to: String?): Long {
        val (dateSql, parameters) = dateFilter(from, to)
        return connection.prepareStatement(
            """
            SELECT COUNT(*) FROM orders o
            WHERE COALESCE((
                SELECT h.status FROM order_status_history h
                WHERE h.order_id = o.id
                ORDER BY h.created_at DESC, h.id DESC LIMIT 1
            ), 'new') != 'cancelled'
            $dateSql
            """.trimIndent()
        ).use { statement ->
            parameters.forEachIndexed { index, value -> statement.setString(index + 1, value) }
            statement.executeQuery().use { result -> result.next(); result.getLong(1) }
        }
    }

    private fun loadTopProducts(connection: Connection, from: String?, to: String?): List<TopProductDto> {
        val (dateSql, parameters) = dateFilter(from, to)
        return connection.prepareStatement(
            """
            SELECT oi.product_id, oi.product_name, SUM(oi.quantity) AS quantity,
                   SUM(oi.price * oi.quantity) AS revenue
            FROM order_items oi
            JOIN orders o ON o.id = oi.order_id
            WHERE (
                SELECT h.status FROM order_status_history h
                WHERE h.order_id = o.id
                ORDER BY h.created_at DESC, h.id DESC LIMIT 1
            ) IN ('paid', 'shipped', 'delivered')
            $dateSql
            GROUP BY oi.product_id, oi.product_name
            ORDER BY revenue DESC
            LIMIT 10
            """.trimIndent()
        ).use { statement ->
            parameters.forEachIndexed { index, value -> statement.setString(index + 1, value) }
            statement.executeQuery().use { result ->
                buildList {
                    while (result.next()) {
                        add(
                            TopProductDto(
                                productId = result.getLong("product_id"),
                                productName = result.getString("product_name"),
                                quantity = result.getLong("quantity"),
                                revenueKopecks = result.getLong("revenue"),
                            )
                        )
                    }
                }
            }
        }
    }

    private fun findPromocode(connection: Connection, code: String, subtotal: Long, at: String): Promocode? =
        connection.prepareStatement(
            """
            SELECT code, type, value FROM promocodes
            WHERE code = ? AND (valid_until IS NULL OR valid_until >= ?)
              AND (min_order IS NULL OR min_order <= ?)
            """.trimIndent()
        ).use { statement ->
            statement.setString(1, code)
            statement.setString(2, at)
            statement.setLong(3, subtotal)
            statement.executeQuery().use { result ->
                if (result.next()) Promocode(result.getString("code"), result.getString("type"), result.getBigDecimal("value"))
                else null
            }
        }

    private fun dateFilter(from: String?, to: String?): Pair<String, List<String>> {
        val parts = mutableListOf<String>()
        val parameters = mutableListOf<String>()
        if (!from.isNullOrBlank()) {
            parts += "o.created_at >= ?"
            parameters += from
        }
        if (!to.isNullOrBlank()) {
            parts += "o.created_at <= ?"
            parameters += to
        }
        return (if (parts.isEmpty()) "" else "AND ${parts.joinToString(" AND ")}") to parameters
    }
}
