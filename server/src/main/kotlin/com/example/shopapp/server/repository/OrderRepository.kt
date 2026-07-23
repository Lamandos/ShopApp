package com.example.shopapp.server.repository

import com.example.shopapp.server.db.Database
import com.example.shopapp.server.dto.CreateOrderRequest
import com.example.shopapp.server.dto.CreateOrderResponse
import com.example.shopapp.server.dto.OrderDetailsDto
import com.example.shopapp.server.dto.OrderItemDto
import com.example.shopapp.server.service.PromoCode
import com.example.shopapp.server.service.PromoResult
import com.example.shopapp.server.service.PromoService
import java.sql.Connection
import java.sql.Statement
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class OrderRepository(
    private val database: Database,
    private val promoService: PromoService = PromoService(),
) {
    fun create(request: CreateOrderRequest): CreateOrderResponse {
        require(request.customerName.isNotBlank()) { "Customer name is required" }
        require(request.items.isNotEmpty()) { "Order must contain at least one item" }
        require(request.items.all { it.quantity > 0 }) { "Item quantity must be positive" }
        require(request.items.map { it.productId }.distinct().size == request.items.size) {
            "Duplicate products are not allowed"
        }

        return database.transaction { connection ->
            val products = loadProducts(connection, request.items.map { it.productId })
            require(products.size == request.items.size) { "Product not found or inactive" }
            request.items.forEach { item ->
                require(products.getValue(item.productId).stock >= item.quantity) {
                    "Not enough stock for product ${item.productId}"
                }
            }

            val subtotal = request.items.sumOf { item ->
                Math.multiplyExact(products.getValue(item.productId).priceKopecks, item.quantity.toLong())
            }
            val now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            val requestedCode = request.promocode?.trim()?.takeIf(String::isNotEmpty)
            val promocode = requestedCode?.let { findPromocode(connection, it) }
            val promoResult = if (requestedCode == null) {
                PromoResult(0)
            } else {
                promoService.calculate(promocode, subtotal, LocalDateTime.parse(now))
            }
            val appliedPromocode = promocode?.takeIf { promoResult.reason == null }
            val orderId = insertOrder(connection, request, appliedPromocode?.code, now)

            insertItems(connection, orderId, request, products)
            insertInitialStatus(connection, orderId, now)
            decrementStock(connection, request)
            appliedPromocode?.let { incrementPromocodeUse(connection, it.code) }

            CreateOrderResponse(
                orderId = orderId,
                subtotalKopecks = subtotal,
                discountKopecks = promoResult.discountKopecks,
                totalKopecks = subtotal - promoResult.discountKopecks,
                promocodeReason = promoResult.reason,
            )
        }
    }

    fun findById(id: Long): OrderDetailsDto? = database.query { connection ->
        connection.prepareStatement(
            """
            SELECT o.id, o.customer_name, o.phone, o.address, o.created_at, o.promocode,
                   COALESCE((
                       SELECT h.status FROM order_status_history h
                       WHERE h.order_id = o.id
                       ORDER BY h.created_at DESC, h.id DESC LIMIT 1
                   ), 'new') AS current_status
            FROM orders o WHERE o.id = ?
            """.trimIndent()
        ).use { statement ->
            statement.setLong(1, id)
            statement.executeQuery().use { result ->
                if (!result.next()) return@query null
                val items = loadOrderItems(connection, id)
                val subtotal = items.sumOf { Math.multiplyExact(it.priceKopecks, it.quantity.toLong()) }
                val promocode = result.getString("promocode")
                val discount = promocode?.let {
                    findPromocode(connection, it)?.let { found -> promoService.discount(found, subtotal) }
                } ?: 0
                OrderDetailsDto(
                    id = result.getLong("id"),
                    customerName = result.getString("customer_name"),
                    phone = result.getString("phone"),
                    address = result.getString("address"),
                    createdAt = result.getString("created_at"),
                    promocode = promocode,
                    status = result.getString("current_status"),
                    subtotalKopecks = subtotal,
                    discountKopecks = discount,
                    totalKopecks = subtotal - discount,
                    items = items,
                )
            }
        }
    }

    private data class ProductSnapshot(val name: String, val priceKopecks: Long, val stock: Int)

    private fun loadProducts(connection: Connection, ids: List<Long>): Map<Long, ProductSnapshot> {
        val placeholders = ids.joinToString(",") { "?" }
        return connection.prepareStatement(
            "SELECT id, name, price, stock FROM products WHERE is_active = 1 AND id IN ($placeholders)"
        ).use { statement ->
            ids.forEachIndexed { index, id -> statement.setLong(index + 1, id) }
            statement.executeQuery().use { result ->
                buildMap {
                    while (result.next()) {
                        put(
                            result.getLong("id"),
                            ProductSnapshot(result.getString("name"), result.getLong("price"), result.getInt("stock"))
                        )
                    }
                }
            }
        }
    }

    private fun insertOrder(
        connection: Connection,
        request: CreateOrderRequest,
        promocode: String?,
        now: String,
    ): Long = connection.prepareStatement(
        """
        INSERT INTO orders(customer_name, phone, address, created_at, promocode, status)
        VALUES (?, ?, ?, ?, ?, 'new')
        """.trimIndent(),
        Statement.RETURN_GENERATED_KEYS,
    ).use { statement ->
        statement.setString(1, request.customerName.trim())
        statement.setString(2, request.phone)
        statement.setString(3, request.address)
        statement.setString(4, now)
        statement.setString(5, promocode)
        statement.executeUpdate()
        statement.generatedKeys.use { keys ->
            check(keys.next()) { "Order id was not generated" }
            keys.getLong(1)
        }
    }

    private fun insertItems(
        connection: Connection,
        orderId: Long,
        request: CreateOrderRequest,
        products: Map<Long, ProductSnapshot>,
    ) {
        connection.prepareStatement(
            """
            INSERT INTO order_items(order_id, product_id, product_name, price, quantity)
            VALUES (?, ?, ?, ?, ?)
            """.trimIndent()
        ).use { statement ->
            request.items.forEach { item ->
                val product = products.getValue(item.productId)
                statement.setLong(1, orderId)
                statement.setLong(2, item.productId)
                statement.setString(3, product.name)
                statement.setLong(4, product.priceKopecks)
                statement.setInt(5, item.quantity)
                statement.addBatch()
            }
            statement.executeBatch()
        }
    }

    private fun insertInitialStatus(connection: Connection, orderId: Long, now: String) {
        connection.prepareStatement(
            "INSERT INTO order_status_history(order_id, status, created_at) VALUES (?, 'new', ?)"
        ).use { statement ->
            statement.setLong(1, orderId)
            statement.setString(2, now)
            statement.executeUpdate()
        }
    }

    private fun decrementStock(connection: Connection, request: CreateOrderRequest) {
        connection.prepareStatement("UPDATE products SET stock = stock - ? WHERE id = ? AND stock >= ?").use {
            request.items.forEach { item ->
                it.setInt(1, item.quantity)
                it.setLong(2, item.productId)
                it.setInt(3, item.quantity)
                check(it.executeUpdate() == 1) { "Could not update product stock" }
            }
        }
    }

    private fun incrementPromocodeUse(connection: Connection, code: String) {
        connection.prepareStatement("UPDATE promocodes SET used_count = used_count + 1 WHERE code = ?").use {
            it.setString(1, code)
            it.executeUpdate()
        }
    }

    private fun loadOrderItems(connection: Connection, orderId: Long): List<OrderItemDto> =
        connection.prepareStatement(
            """
            SELECT product_id, product_name, price, quantity
            FROM order_items WHERE order_id = ? ORDER BY id
            """.trimIndent()
        ).use { statement ->
            statement.setLong(1, orderId)
            statement.executeQuery().use { result ->
                buildList {
                    while (result.next()) {
                        add(
                            OrderItemDto(
                                productId = result.getLong("product_id"),
                                productName = result.getString("product_name"),
                                priceKopecks = result.getLong("price"),
                                quantity = result.getInt("quantity"),
                            )
                        )
                    }
                }
            }
        }

    private fun findPromocode(connection: Connection, code: String): PromoCode? = connection.prepareStatement(
        """
        SELECT code, type, value, is_active, valid_until, min_order, max_uses, used_count
        FROM promocodes WHERE code = ?
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
}
