package com.example.shopapp.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Stats(
    val revenue: Long,
    val ordersCount: Long,
    val averageCheck: Long,
    val topProducts: List<TopProduct>,
)

@Serializable
data class TopProduct(
    val productId: Long,
    val productName: String,
    val quantity: Long,
    val revenue: Long,
)
