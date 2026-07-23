package com.example.shopapp.ui.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.shopapp.data.ShopRepository
import com.example.shopapp.data.model.Category
import com.example.shopapp.data.model.Product
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

data class CatalogUiState(
    val categories: List<Category> = emptyList(),
    val products: List<Product> = emptyList(),
    val search: String = "",
    val selectedCategoryId: Int? = null,
    val quantities: Map<Long, Int> = emptyMap(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

private data class CatalogFilter(val categoryId: Int?, val search: String)

@OptIn(FlowPreview::class)
class CatalogViewModel(private val repository: ShopRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(CatalogUiState())
    val uiState: StateFlow<CatalogUiState> = _uiState.asStateFlow()

    private val filters = MutableStateFlow(CatalogFilter(null, ""))

    init {
        loadCategories()
        viewModelScope.launch {
            filters.debounce(300).collectLatest(::loadProducts)
        }
    }

    fun onSearchChange(search: String) {
        _uiState.value = _uiState.value.copy(search = search)
        filters.value = filters.value.copy(search = search)
    }

    fun onCategorySelect(categoryId: Int?) {
        _uiState.value = _uiState.value.copy(selectedCategoryId = categoryId)
        filters.value = filters.value.copy(categoryId = categoryId)
    }

    fun changeQuantity(product: Product, delta: Int) {
        if (product.stock <= 0) return
        val current = quantity(product)
        val updated = (current + delta).coerceIn(1, product.stock)
        _uiState.value = _uiState.value.copy(
            quantities = _uiState.value.quantities + (product.id to updated)
        )
    }

    fun quantity(product: Product): Int =
        _uiState.value.quantities[product.id] ?: if (product.stock > 0) 1 else 0

    fun retry() {
        viewModelScope.launch { loadProducts(filters.value) }
        if (_uiState.value.categories.isEmpty()) loadCategories()
    }

    private fun loadCategories() {
        viewModelScope.launch {
            runCatching { repository.getCategories() }
                .onSuccess { categories ->
                    _uiState.value = _uiState.value.copy(categories = categories)
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message ?: "Не удалось загрузить категории",
                    )
                }
        }
    }

    private suspend fun loadProducts(filter: CatalogFilter) {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        runCatching { repository.getProducts(filter.categoryId, filter.search) }
            .onSuccess { products ->
                _uiState.value = _uiState.value.copy(
                    products = products,
                    isLoading = false,
                    quantities = _uiState.value.quantities.filterKeys { id ->
                        products.any { it.id == id }
                    },
                )
            }
            .onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = error.message ?: "Не удалось загрузить каталог",
                )
            }
    }

    companion object {
        fun factory(repository: ShopRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    CatalogViewModel(repository) as T
            }
    }
}
