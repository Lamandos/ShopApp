package com.example.shopapp.server

import com.example.shopapp.server.db.Database
import com.example.shopapp.server.dto.CreateOrderRequest
import com.example.shopapp.server.repository.CatalogRepository
import com.example.shopapp.server.repository.InvalidOrderException
import com.example.shopapp.server.repository.OrderRepository
import com.example.shopapp.server.repository.ProductNotFoundException
import com.example.shopapp.server.repository.StockConflictException
import com.example.shopapp.server.repository.StatsRepository
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import java.nio.file.Files
import java.nio.file.Path

@Serializable
data class HealthResponse(val status: String)

@Serializable
data class ErrorResponse(val error: String)

fun Application.module(databasePath: Path = defaultDatabasePath()) {
    install(ContentNegotiation) {
        json()
    }

    val database = Database(databasePath)
    val catalogRepository = CatalogRepository(database)
    val orderRepository = OrderRepository(database)
    val statsRepository = StatsRepository(database)

    routing {
        get("/health") {
            call.respond(HealthResponse(status = "ok"))
        }

        route("/api") {
            get("/products") {
                val categoryParameter = call.request.queryParameters["category"]
                val category = categoryParameter?.toIntOrNull()
                if (categoryParameter != null && (category == null || category <= 0)) {
                    return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid category"))
                }
                val search = call.request.queryParameters["search"]
                call.respond(catalogRepository.findAll(category, search))
            }

            get("/categories") {
                call.respond(catalogRepository.findCategories())
            }

            get("/products/{id}") {
                val id = call.parameters["id"]?.toLongOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid product id"))
                val product = catalogRepository.findById(id)
                    ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("Product not found"))
                call.respond(product)
            }

            post("/orders") {
                val request = runCatching { call.receive<CreateOrderRequest>() }.getOrElse {
                    return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body"))
                }
                try {
                    call.respond(HttpStatusCode.Created, orderRepository.create(request))
                } catch (exception: InvalidOrderException) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(exception.message ?: "Invalid order"))
                } catch (exception: ProductNotFoundException) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse(exception.message ?: "Product not found"))
                } catch (exception: StockConflictException) {
                    call.respond(HttpStatusCode.Conflict, ErrorResponse(exception.message ?: "Stock conflict"))
                }
            }

            get("/orders/{id}") {
                val id = call.parameters["id"]?.toLongOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid order id"))
                val order = orderRepository.findById(id)
                    ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("Order not found"))
                call.respond(order)
            }

            get("/admin/stats") {
                val from = call.request.queryParameters["from"]
                val to = call.request.queryParameters["to"]
                call.respond(statsRepository.get(from, to))
            }
        }
    }
}

private fun defaultDatabasePath(): Path {
    System.getenv("SHOPAPP_DB_PATH")?.let { return Path.of(it) }
    val fromRoot = Path.of("server/data/test_task.db")
    return if (Files.isRegularFile(fromRoot)) fromRoot else Path.of("data/test_task.db")
}
