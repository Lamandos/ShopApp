package com.example.shopapp.server

import com.example.shopapp.server.db.Database
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApplicationTest {
    @Test
    fun healthEndpointReturnsOk() = withTestDatabase { databasePath ->
        testApplication {
            application { module(databasePath) }

            val response = client.get("/health")

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("""{"status":"ok"}""", response.bodyAsText())
        }
    }

    @Test
    fun catalogCanBeFilteredAndSearched() = withTestDatabase { databasePath ->
        testApplication {
            application { module(databasePath) }

            val response = client.get("/api/products?category=3&search=jBl")

            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(response.bodyAsText().contains("JBL Tune 520BT"))
            assertTrue(!response.bodyAsText().contains("AirPods Pro 2"))
        }
    }

    @Test
    fun catalogRejectsInvalidCategory() = withTestDatabase { databasePath ->
        testApplication {
            application { module(databasePath) }

            assertEquals(
                HttpStatusCode.BadRequest,
                client.get("/api/products?category=audio").status,
            )
            assertEquals(
                HttpStatusCode.BadRequest,
                client.get("/api/products?category=0").status,
            )
        }
    }

    @Test
    fun catalogContainsOnlyActiveProducts() = withTestDatabase { databasePath ->
        testApplication {
            application { module(databasePath) }

            val response = client.get("/api/products")

            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(!response.bodyAsText().contains("Старый товара нет на складе"))
            assertEquals(HttpStatusCode.NotFound, client.get("/api/products/16").status)
        }
    }

    @Test
    fun categoriesAreReturned() = withTestDatabase { databasePath ->
        testApplication {
            application { module(databasePath) }

            val response = client.get("/api/categories")

            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(response.bodyAsText().contains(""""slug":"smartfony""""))
            assertTrue(response.bodyAsText().contains(""""slug":"igrovye""""))
        }
    }

    @Test
    fun missingProductReturnsNotFound() = withTestDatabase { databasePath ->
        testApplication {
            application { module(databasePath) }

            assertEquals(HttpStatusCode.NotFound, client.get("/api/products/999999").status)
        }
    }

    @Test
    fun orderIsCreatedWithFixedDiscountAndCanBeRead() = withTestDatabase { databasePath ->
        testApplication {
            application { module(databasePath) }

            val createResponse = client.post("/api/orders") {
                header(HttpHeaders.ContentType, ContentType.Application.Json)
                setBody(
                    """
                    {
                      "customerName": "Тест",
                      "promocode": "FIX500",
                      "items": [{"productId": 9, "quantity": 1}]
                    }
                    """.trimIndent()
                )
            }

            assertEquals(HttpStatusCode.Created, createResponse.status)
            val createBody = createResponse.bodyAsText()
            assertTrue(createBody.contains(""""subtotal":599000"""))
            assertTrue(createBody.contains(""""discount":50000"""))
            assertTrue(createBody.contains(""""total":549000"""))
            assertTrue(createBody.contains(""""promoApplied":true"""))
            val orderId = Regex(""""orderId":(\d+)""").find(createBody)!!.groupValues[1]

            val detailsResponse = client.get("/api/orders/$orderId")
            assertEquals(HttpStatusCode.OK, detailsResponse.status)
            val detailsBody = detailsResponse.bodyAsText()
            assertTrue(detailsBody.contains(""""status":"new""""))
            assertTrue(detailsBody.contains(""""discountKopecks":50000"""))
            assertEquals(89, databaseValue(databasePath, "SELECT used_count FROM promocodes WHERE code = 'FIX500'"))
        }
    }

    @Test
    fun invalidPromoDoesNotPreventOrderCreation() = withTestDatabase { databasePath ->
        testApplication {
            application { module(databasePath) }

            val response = client.post("/api/orders") {
                header(HttpHeaders.ContentType, ContentType.Application.Json)
                setBody(
                    """
                    {
                      "customerName": "Тест",
                      "promocode": "EXPIRED",
                      "items": [{"productId": 9, "quantity": 1}]
                    }
                    """.trimIndent()
                )
            }

            assertEquals(HttpStatusCode.Created, response.status)
            val body = response.bodyAsText()
            assertTrue(body.contains(""""discount":0"""))
            assertTrue(body.contains(""""promoApplied":false"""))
            assertTrue(body.contains(""""promoMessage":"EXPIRED""""))
            assertEquals(12, databaseValue(databasePath, "SELECT used_count FROM promocodes WHERE code = 'EXPIRED'"))
        }
    }

    @Test
    fun duplicateProductsAreMerged() = withTestDatabase { databasePath ->
        testApplication {
            application { module(databasePath) }

            val createResponse = client.post("/api/orders") {
                header(HttpHeaders.ContentType, ContentType.Application.Json)
                setBody(
                    """
                    {
                      "customerName": "Тест",
                      "phone": "+79990000000",
                      "address": "Москва",
                      "items": [
                        {"productId": 9, "quantity": 1},
                        {"productId": 9, "quantity": 2}
                      ]
                    }
                    """.trimIndent()
                )
            }

            assertEquals(HttpStatusCode.Created, createResponse.status)
            val createBody = createResponse.bodyAsText()
            assertTrue(createBody.contains(""""subtotal":1797000"""))
            assertTrue(createBody.contains(""""promoApplied":false"""))
            val orderId = Regex(""""orderId":(\d+)""").find(createBody)!!.groupValues[1]

            val detailsBody = client.get("/api/orders/$orderId").bodyAsText()
            assertEquals(1, Regex(""""productId":9""").findAll(detailsBody).count())
            assertTrue(detailsBody.contains(""""quantity":3"""))
            assertEquals(147, databaseValue(databasePath, "SELECT stock FROM products WHERE id = 9"))
        }
    }

    @Test
    fun orderValidationUsesClearStatuses() = withTestDatabase { databasePath ->
        testApplication {
            application { module(databasePath) }

            assertEquals(
                HttpStatusCode.BadRequest,
                client.postOrder("""{"customerName":" ","items":[{"productId":9,"quantity":1}]}""").status,
            )
            assertEquals(
                HttpStatusCode.NotFound,
                client.postOrder("""{"customerName":"Тест","items":[{"productId":16,"quantity":1}]}""").status,
            )
            assertEquals(
                HttpStatusCode.Conflict,
                client.postOrder("""{"customerName":"Тест","items":[{"productId":6,"quantity":16}]}""").status,
            )
        }
    }

    @Test
    fun statsAreReturnedInKopecks() = withTestDatabase { databasePath ->
        testApplication {
            application { module(databasePath) }

            val response = client.get("/api/admin/stats?from=2025-05-01&to=2025-07-31")

            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(response.bodyAsText().contains("revenueKopecks"))
            assertTrue(response.bodyAsText().contains("averageOrderKopecks"))
        }
    }

    @Test
    fun sqliteForeignKeysAreEnabled() = withTestDatabase { databasePath ->
        Database(databasePath).connection().use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("PRAGMA foreign_keys").use { result ->
                    assertTrue(result.next())
                    assertEquals(1, result.getInt(1))
                }
            }
        }
    }

    private fun withTestDatabase(test: (Path) -> Unit) {
        val source = Path.of("data/test_task.db")
        val copy = Files.createTempFile("shopapp-test-", ".db")
        try {
            Files.copy(source, copy, StandardCopyOption.REPLACE_EXISTING)
            test(copy)
        } finally {
            Files.deleteIfExists(copy)
        }
    }

    private suspend fun io.ktor.client.HttpClient.postOrder(body: String) =
        post("/api/orders") {
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(body)
        }

    private fun databaseValue(databasePath: Path, sql: String): Int =
        Database(databasePath).connection().use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.executeQuery().use { result ->
                    assertTrue(result.next())
                    result.getInt(1)
                }
            }
        }
}
