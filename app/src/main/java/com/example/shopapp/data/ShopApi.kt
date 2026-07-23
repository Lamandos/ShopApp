package com.example.shopapp.data

import com.example.shopapp.data.model.Category
import com.example.shopapp.data.model.Product
import retrofit2.http.GET
import retrofit2.http.Query

interface ShopApi {
    @GET("api/categories")
    suspend fun getCategories(): List<Category>

    @GET("api/products")
    suspend fun getProducts(
        @Query("category") category: Int? = null,
        @Query("search") search: String? = null,
    ): List<Product>
}
