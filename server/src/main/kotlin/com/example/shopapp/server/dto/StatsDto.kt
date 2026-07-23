package com.example.shopapp.server.dto

import kotlinx.serialization.Serializable

@Serializable
data class StatsDto(
    val revenueKopecks: Long,
    val orderCount: Long,
    val paidOrderCount: Long,
    val averageOrderKopecks: Long,
    val topProducts: List<TopProductDto>,
)

@Serializable
data class TopProductDto(
    val productId: Long,
    val productName: String,
    val quantity: Long,
    val revenueKopecks: Long,
)
