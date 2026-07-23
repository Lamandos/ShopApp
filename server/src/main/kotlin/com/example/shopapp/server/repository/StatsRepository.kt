package com.example.shopapp.server.repository

import com.example.shopapp.server.db.Database
import com.example.shopapp.server.dto.StatsDto
import com.example.shopapp.server.dto.TopProductDto
import com.example.shopapp.server.service.PromoCode
import com.example.shopapp.server.service.PromoService
import java.sql.Connection
import java.time.LocalDate
import java.time.LocalDateTime

class StatsRepository(
    private val database: Database,
    private val promoService: PromoService = PromoService(),
) {
    fun get(from: LocalDate, to: LocalDate): StatsDto = database.query { connection ->
        val paidOrders = loadPaidOrders(connection, from, to)
        val revenue = paidOrders.sumOf { order ->
            val promo = order.promocode?.let { findPromocode(connection, it) }
            val discount = if (promo == null) {
                0
            } else {
                promoService.calculate(promo, order.subtotal, parseCreatedAt(order.createdAt)).discountKopecks
            }
            order.subtotal - discount
        }
        val paidOrdersCount = paidOrders.size.toLong()

        StatsDto(
            revenue = revenue,
            ordersCount = countNotCancelled(connection, from, to),
            averageCheck = if (paidOrdersCount == 0L) 0 else revenue / paidOrdersCount,
            topProducts = loadTopProducts(connection, from, to),
        )
    }

    private data class PaidOrder(
        val createdAt: String,
        val promocode: String?,
        val subtotal: Long,
    )

    private fun loadPaidOrders(connection: Connection, from: LocalDate, to: LocalDate): List<PaidOrder> =
        connection.prepareStatement(
            """
            WITH ranked_status AS (
                SELECT order_id, status,
                       ROW_NUMBER() OVER (
                           PARTITION BY order_id
                           ORDER BY datetime(created_at) DESC, id DESC
                       ) AS row_number
                FROM order_status_history
            )
            SELECT o.created_at, o.promocode, SUM(oi.price * oi.quantity) AS subtotal
            FROM orders o
            JOIN ranked_status rs ON rs.order_id = o.id AND rs.row_number = 1
            JOIN order_items oi ON oi.order_id = o.id
            WHERE rs.status IN ('paid', 'shipped', 'delivered')
              AND date(o.created_at) BETWEEN date(?) AND date(?)
            GROUP BY o.id, o.created_at, o.promocode
            """.trimIndent()
        ).use { statement ->
            statement.setString(1, from.toString())
            statement.setString(2, to.toString())
            statement.executeQuery().use { result ->
                buildList {
                    while (result.next()) {
                        add(
                            PaidOrder(
                                createdAt = result.getString("created_at"),
                                promocode = result.getString("promocode"),
                                subtotal = result.getLong("subtotal"),
                            )
                        )
                    }
                }
            }
        }

    private fun countNotCancelled(connection: Connection, from: LocalDate, to: LocalDate): Long =
        connection.prepareStatement(
            """
            WITH ranked_status AS (
                SELECT order_id, status,
                       ROW_NUMBER() OVER (
                           PARTITION BY order_id
                           ORDER BY datetime(created_at) DESC, id DESC
                       ) AS row_number
                FROM order_status_history
            )
            SELECT COUNT(*)
            FROM orders o
            LEFT JOIN ranked_status rs ON rs.order_id = o.id AND rs.row_number = 1
            WHERE COALESCE(rs.status, 'new') != 'cancelled'
              AND date(o.created_at) BETWEEN date(?) AND date(?)
            """.trimIndent()
        ).use { statement ->
            statement.setString(1, from.toString())
            statement.setString(2, to.toString())
            statement.executeQuery().use { result ->
                check(result.next())
                result.getLong(1)
            }
        }

    private fun loadTopProducts(
        connection: Connection,
        from: LocalDate,
        to: LocalDate,
    ): List<TopProductDto> = connection.prepareStatement(
        """
        WITH ranked_status AS (
            SELECT order_id, status,
                   ROW_NUMBER() OVER (
                       PARTITION BY order_id
                       ORDER BY datetime(created_at) DESC, id DESC
                   ) AS row_number
            FROM order_status_history
        )
        SELECT oi.product_id, oi.product_name, SUM(oi.quantity) AS quantity,
               SUM(oi.price * oi.quantity) AS revenue
        FROM orders o
        JOIN ranked_status rs ON rs.order_id = o.id AND rs.row_number = 1
        JOIN order_items oi ON oi.order_id = o.id
        WHERE rs.status IN ('paid', 'shipped', 'delivered')
          AND date(o.created_at) BETWEEN date(?) AND date(?)
        GROUP BY oi.product_id, oi.product_name
        ORDER BY revenue DESC, oi.product_id
        LIMIT 5
        """.trimIndent()
    ).use { statement ->
        statement.setString(1, from.toString())
        statement.setString(2, to.toString())
        statement.executeQuery().use { result ->
            buildList {
                while (result.next()) {
                    add(
                        TopProductDto(
                            productId = result.getLong("product_id"),
                            productName = result.getString("product_name"),
                            quantity = result.getLong("quantity"),
                            revenue = result.getLong("revenue"),
                        )
                    )
                }
            }
        }
    }

    private fun findPromocode(connection: Connection, code: String): PromoCode? =
        connection.prepareStatement(
            """
            SELECT code, type, value, is_active, valid_until, min_order, max_uses, used_count
            FROM promocodes
            WHERE code = ?
            """.trimIndent()
        ).use { statement ->
            statement.setString(1, code)
            statement.executeQuery().use { result ->
                if (!result.next()) return@use null
                PromoCode(
                    code = result.getString("code"),
                    type = result.getString("type"),
                    value = result.getBigDecimal("value"),
                    isActive = result.getBoolean("is_active"),
                    validUntil = result.getString("valid_until"),
                    minOrderKopecks = result.getLong("min_order").takeUnless { result.wasNull() },
                    maxUses = result.getInt("max_uses").takeUnless { result.wasNull() },
                    usedCount = result.getInt("used_count"),
                )
            }
        }

    private fun parseCreatedAt(value: String): LocalDateTime =
        runCatching { LocalDateTime.parse(value) }
            .getOrElse { LocalDate.parse(value).atStartOfDay() }
}
