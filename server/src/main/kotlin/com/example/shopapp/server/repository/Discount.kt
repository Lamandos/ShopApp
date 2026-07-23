package com.example.shopapp.server.repository

import java.math.BigDecimal
import java.math.RoundingMode

internal data class Promocode(
    val code: String,
    val type: String,
    val value: BigDecimal,
)

internal fun Promocode.discount(subtotalKopecks: Long): Long =
    when (type) {
        "percent" -> BigDecimal.valueOf(subtotalKopecks)
            .multiply(value)
            .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
            .longValueExact()
        "fixed" -> value.multiply(BigDecimal.valueOf(100))
            .setScale(0, RoundingMode.HALF_UP)
            .longValueExact()
        else -> 0
    }.coerceIn(0, subtotalKopecks)
