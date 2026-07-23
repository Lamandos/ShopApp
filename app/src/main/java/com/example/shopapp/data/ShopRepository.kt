package com.example.shopapp.data

import com.example.shopapp.data.model.Category
import com.example.shopapp.data.model.CreateOrderRequest
import com.example.shopapp.data.model.CreateOrderResponse
import com.example.shopapp.data.model.Product
import com.example.shopapp.data.model.Stats

class ShopRepository(private val api: ShopApi) {
    suspend fun getCategories(): List<Category> = api.getCategories()

    suspend fun getProducts(category: Int?, search: String): List<Product> =
        api.getProducts(
            category = category,
            search = search.trim().takeIf(String::isNotEmpty),
        )

    suspend fun createOrder(request: CreateOrderRequest): CreateOrderResponse =
        api.createOrder(request)

    suspend fun getStats(from: String, to: String): Stats = api.getStats(from, to)
}
