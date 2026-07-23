package com.example.shopapp.data

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

object ShopAppContainer {
    private val api: ShopApi by lazy {
        val json = Json { ignoreUnknownKeys = true }
        Retrofit.Builder()
            .baseUrl("http://10.0.2.2:8080/")
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ShopApi::class.java)
    }

    val repository: ShopRepository by lazy { ShopRepository(api) }
}
