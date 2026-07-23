package com.example.shopapp.server

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable

@Serializable
data class HealthResponse(val status: String)

fun Application.module() {
    install(ContentNegotiation) {
        json()
    }

    routing {
        get("/health") {
            call.respond(HttpStatusCode.OK, HealthResponse(status = "ok"))
        }
    }
}
