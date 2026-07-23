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
