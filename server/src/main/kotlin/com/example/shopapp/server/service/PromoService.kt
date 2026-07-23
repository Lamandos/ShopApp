package com.example.shopapp.server.service

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

data class PromoCode(
    val code: String,
    val type: String,
    val value: BigDecimal,
    val isActive: Boolean,
    val validUntil: String?,
    val minOrderKopecks: Long?,
    val maxUses: Int?,
    val usedCount: Int,
)

data class PromoResult(
    val discountKopecks: Long,
    val reason: String? = null,
)

class PromoService {
    fun calculate(
        promo: PromoCode?,
        subtotalKopecks: Long,
        now: LocalDateTime = LocalDateTime.now(),
    ): PromoResult {
        require(subtotalKopecks >= 0) { "Subtotal must not be negative" }
        if (promo == null) return PromoResult(0, "NOT_FOUND")
        if (!promo.isActive) return PromoResult(0, "INACTIVE")
        if (promo.validUntil != null && parseDateTime(promo.validUntil).isBefore(now)) {
            return PromoResult(0, "EXPIRED")
        }
        if (promo.minOrderKopecks != null && subtotalKopecks < promo.minOrderKopecks) {
            return PromoResult(0, "MIN_ORDER")
        }
        if (promo.maxUses != null && promo.usedCount >= promo.maxUses) {
            return PromoResult(0, "MAX_USES")
        }

        return PromoResult(discount(promo, subtotalKopecks))
    }

    internal fun discount(promo: PromoCode, subtotalKopecks: Long): Long {
        val calculated = when (promo.type) {
            "percent" -> BigDecimal.valueOf(subtotalKopecks)
                .multiply(promo.value)
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
                .longValueExact()
            "fixed" -> promo.value
                .multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact()
            else -> 0
        }
        return calculated.coerceIn(0, subtotalKopecks)
    }

    private fun parseDateTime(value: String): LocalDateTime =
        runCatching { LocalDateTime.parse(value) }
            .getOrElse { LocalDate.parse(value).atTime(LocalTime.MAX) }
}
