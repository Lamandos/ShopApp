package com.example.shopapp.server.repository

import com.example.shopapp.server.db.Database
import com.example.shopapp.server.dto.CategoryDto
import com.example.shopapp.server.dto.ProductDto
import java.sql.ResultSet

class CatalogRepository(private val database: Database) {
    fun findAll(category: String?, search: String?): List<ProductDto> = database.query { connection ->
        val sql = buildString {
            append(
                """
                SELECT p.id, p.name, p.description, p.price, p.stock,
                       c.id AS category_id, c.name AS category_name, c.slug
                FROM products p
                JOIN categories c ON c.id = p.category_id
                WHERE p.is_active = 1
                """.trimIndent()
            )
            if (!category.isNullOrBlank()) append(" AND c.slug = ?")
            if (!search.isNullOrBlank()) append(" AND LOWER(p.name) LIKE LOWER(?)")
            append(" ORDER BY p.id")
        }

        connection.prepareStatement(sql).use { statement ->
            var index = 1
            if (!category.isNullOrBlank()) statement.setString(index++, category)
            if (!search.isNullOrBlank()) statement.setString(index, "%${search.trim()}%")
            statement.executeQuery().use { result ->
                buildList {
                    while (result.next()) add(result.toProduct())
                }
            }
        }
    }

    fun findById(id: Long): ProductDto? = database.query { connection ->
        connection.prepareStatement(
            """
            SELECT p.id, p.name, p.description, p.price, p.stock,
                   c.id AS category_id, c.name AS category_name, c.slug
            FROM products p
            JOIN categories c ON c.id = p.category_id
            WHERE p.id = ? AND p.is_active = 1
            """.trimIndent()
        ).use { statement ->
            statement.setLong(1, id)
            statement.executeQuery().use { result ->
                if (result.next()) result.toProduct() else null
            }
        }
    }

    private fun ResultSet.toProduct() = ProductDto(
        id = getLong("id"),
        name = getString("name"),
        description = getString("description"),
        category = CategoryDto(
            id = getLong("category_id"),
            name = getString("category_name"),
            slug = getString("slug"),
        ),
        priceKopecks = getLong("price"),
        stock = getInt("stock"),
    )
}
