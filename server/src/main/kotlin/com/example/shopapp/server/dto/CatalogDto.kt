package com.example.shopapp.server.dto

import kotlinx.serialization.Serializable

@Serializable
data class ProductDto(
    val id: Long,
    val name: String,
    val description: String?,
    val category: CategoryDto,
    val priceKopecks: Long,
    val stock: Int,
)

@Serializable
data class CategoryDto(
    val id: Long,
    val name: String,
    val slug: String,
)
