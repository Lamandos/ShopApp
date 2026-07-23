package com.example.shopapp.server.dto

import kotlinx.serialization.Serializable

@Serializable
data class CreateOrderRequest(
    val customerName: String,
    val phone: String? = null,
    val address: String? = null,
    val promocode: String? = null,
    val items: List<CreateOrderItemRequest>,
)

@Serializable
data class CreateOrderItemRequest(
    val productId: Long,
    val quantity: Int,
)

@Serializable
data class CreateOrderResponse(
    val orderId: Long,
    val subtotal: Long,
    val discount: Long,
    val total: Long,
    val promoApplied: Boolean,
    val promoMessage: String? = null,
)

@Serializable
data class OrderDetailsDto(
    val id: Long,
    val customerName: String,
    val phone: String?,
    val address: String?,
    val createdAt: String,
    val promocode: String?,
    val status: String,
    val subtotalKopecks: Long,
    val discountKopecks: Long,
    val totalKopecks: Long,
    val items: List<OrderItemDto>,
)

@Serializable
data class OrderItemDto(
    val productId: Long,
    val productName: String,
    val priceKopecks: Long,
    val quantity: Int,
)
