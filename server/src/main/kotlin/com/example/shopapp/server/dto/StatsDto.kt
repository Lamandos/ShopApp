package com.example.shopapp.server.dto

import kotlinx.serialization.Serializable

@Serializable
data class StatsDto(
    val revenue: Long,
    val ordersCount: Long,
    val averageCheck: Long,
    val topProducts: List<TopProductDto>,
)

@Serializable
data class TopProductDto(
    val productId: Long,
    val productName: String,
    val quantity: Long,
    val revenue: Long,
)
