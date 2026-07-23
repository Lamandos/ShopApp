package com.example.shopapp.server.service

import java.math.BigDecimal
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class PromoServiceTest {
    private val service = PromoService()
    private val now = LocalDateTime.parse("2025-06-01T12:00:00")

    @Test
    fun percentDiscountIsRoundedAndCapped() {
        assertEquals(334L, service.calculate(promo(type = "percent", value = "33.35"), 1_000, now).discountKopecks)
        assertEquals(1_000L, service.calculate(promo(type = "percent", value = "150"), 1_000, now).discountKopecks)
    }

    @Test
    fun fixedDiscountConvertsRublesToKopecks() {
        assertEquals(50_000L, service.calculate(promo(type = "fixed", value = "500"), 80_000, now).discountKopecks)
    }

    @Test
    fun expiredPromoIsRejected() {
        assertRejected(promo(validUntil = "2025-05-31T23:59:59"), "EXPIRED")
    }

    @Test
    fun inactivePromoIsRejected() {
        assertRejected(promo(isActive = false), "INACTIVE")
    }

    @Test
    fun minimumOrderIsChecked() {
        assertRejected(promo(minOrderKopecks = 100_001), "MIN_ORDER")
    }

    @Test
    fun maximumUsesIsChecked() {
        assertRejected(promo(maxUses = 5, usedCount = 5), "MAX_USES")
    }

    private fun assertRejected(promo: PromoCode, reason: String) {
        assertEquals(PromoResult(0, reason), service.calculate(promo, 100_000, now))
    }

    private fun promo(
        type: String = "percent",
        value: String = "10",
        isActive: Boolean = true,
        validUntil: String? = "2025-12-31T23:59:59",
        minOrderKopecks: Long? = null,
        maxUses: Int? = null,
        usedCount: Int = 0,
    ) = PromoCode(
        code = "TEST",
        type = type,
        value = BigDecimal(value),
        isActive = isActive,
        validUntil = validUntil,
        minOrderKopecks = minOrderKopecks,
        maxUses = maxUses,
        usedCount = usedCount,
    )
}
