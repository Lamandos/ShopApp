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

            val response = client.get("/api/products?category=audio&search=JBL")

            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(response.bodyAsText().contains("JBL Tune 520BT"))
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
            assertTrue(createBody.contains(""""totalKopecks":549000"""))
            val orderId = Regex(""""orderId":(\d+)""").find(createBody)!!.groupValues[1]

            val detailsResponse = client.get("/api/orders/$orderId")
            assertEquals(HttpStatusCode.OK, detailsResponse.status)
            val detailsBody = detailsResponse.bodyAsText()
            assertTrue(detailsBody.contains(""""status":"new""""))
            assertTrue(detailsBody.contains(""""discountKopecks":50000"""))
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
}
