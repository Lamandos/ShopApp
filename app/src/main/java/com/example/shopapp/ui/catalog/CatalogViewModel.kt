package com.example.shopapp.ui.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.shopapp.data.ShopRepository
import com.example.shopapp.data.model.Category
import com.example.shopapp.data.model.CreateOrderItem
import com.example.shopapp.data.model.CreateOrderRequest
import com.example.shopapp.data.model.CreateOrderResponse
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
    val isOrderFormVisible: Boolean = false,
    val customerName: String = "",
    val phone: String = "",
    val address: String = "",
    val promocode: String = "",
    val isSubmittingOrder: Boolean = false,
    val orderValidationError: String? = null,
    val orderError: String? = null,
    val orderResult: CreateOrderResponse? = null,
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
        val updated = (current + delta).coerceIn(0, product.stock)
        val quantities = if (updated == 0) {
            _uiState.value.quantities - product.id
        } else {
            _uiState.value.quantities + (product.id to updated)
        }
        _uiState.value = _uiState.value.copy(
            quantities = quantities
        )
    }

    fun quantity(product: Product): Int =
        _uiState.value.quantities[product.id] ?: 0

    fun showOrderForm() {
        _uiState.value = _uiState.value.copy(
            isOrderFormVisible = true,
            orderValidationError = null,
            orderError = null,
            orderResult = null,
        )
    }

    fun hideOrderForm() {
        if (_uiState.value.isSubmittingOrder) return
        _uiState.value = _uiState.value.copy(isOrderFormVisible = false)
    }

    fun onCustomerNameChange(value: String) = updateOrderFields(customerName = value)
    fun onPhoneChange(value: String) = updateOrderFields(phone = value)
    fun onAddressChange(value: String) = updateOrderFields(address = value)
    fun onPromocodeChange(value: String) = updateOrderFields(promocode = value)

    fun submitOrder() {
        val state = _uiState.value
        if (state.isSubmittingOrder) return
        val validationError = when {
            state.customerName.isBlank() -> "Введите имя"
            state.phone.isBlank() -> "Введите телефон"
            state.address.isBlank() -> "Введите адрес"
            state.quantities.isEmpty() -> "Добавьте хотя бы один товар"
            else -> null
        }
        if (validationError != null) {
            _uiState.value = state.copy(orderValidationError = validationError)
            return
        }

        val request = CreateOrderRequest(
            customerName = state.customerName.trim(),
            phone = state.phone.trim(),
            address = state.address.trim(),
            promocode = state.promocode.trim().takeIf(String::isNotEmpty),
            items = state.quantities.map { (productId, quantity) ->
                CreateOrderItem(productId, quantity)
            },
        )
        _uiState.value = state.copy(
            isSubmittingOrder = true,
            orderValidationError = null,
            orderError = null,
        )
        viewModelScope.launch {
            runCatching { repository.createOrder(request) }
                .onSuccess { result ->
                    _uiState.value = _uiState.value.copy(
                        isSubmittingOrder = false,
                        orderResult = result,
                        quantities = emptyMap(),
                    )
                    loadProducts(filters.value)
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isSubmittingOrder = false,
                        orderError = error.message ?: "Не удалось оформить заказ",
                    )
                }
        }
    }

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
                )
            }
            .onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = error.message ?: "Не удалось загрузить каталог",
                )
            }
    }

    private fun updateOrderFields(
        customerName: String = _uiState.value.customerName,
        phone: String = _uiState.value.phone,
        address: String = _uiState.value.address,
        promocode: String = _uiState.value.promocode,
    ) {
        _uiState.value = _uiState.value.copy(
            customerName = customerName,
            phone = phone,
            address = address,
            promocode = promocode,
            orderValidationError = null,
        )
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
