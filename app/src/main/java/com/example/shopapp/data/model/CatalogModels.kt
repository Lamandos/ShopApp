package com.example.shopapp.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Category(
    val id: Int,
    val name: String,
    val slug: String,
)

@Serializable
data class Product(
    val id: Long,
    val name: String,
    val description: String? = null,
    val category: Category,
    val priceKopecks: Long,
    val stock: Int,
)

@Serializable
data class CreateOrderRequest(
    val customerName: String,
    val phone: String,
    val address: String,
    val promocode: String? = null,
    val items: List<CreateOrderItem>,
)

@Serializable
data class CreateOrderItem(
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
