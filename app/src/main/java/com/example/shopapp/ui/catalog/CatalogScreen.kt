package com.example.shopapp.ui.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.shopapp.data.model.Product
import kotlin.math.absoluteValue

@Composable
fun CatalogScreen(viewModel: CatalogViewModel) {
    val state by viewModel.uiState.collectAsState()

    MaterialTheme {
        Scaffold { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
            ) {
                Text(
                    text = "Каталог",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
                OutlinedTextField(
                    value = state.search,
                    onValueChange = viewModel::onSearchChange,
                    label = { Text("Поиск по названию") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("catalog_search"),
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(vertical = 12.dp),
                ) {
                    item {
                        FilterChip(
                            selected = state.selectedCategoryId == null,
                            onClick = { viewModel.onCategorySelect(null) },
                            label = { Text("Все") },
                        )
                    }
                    items(state.categories, key = { it.id }) { category ->
                        FilterChip(
                            selected = state.selectedCategoryId == category.id,
                            onClick = { viewModel.onCategorySelect(category.id) },
                            label = { Text(category.name) },
                        )
                    }
                }
                Button(
                    onClick = viewModel::showOrderForm,
                    enabled = state.quantities.isNotEmpty(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .testTag("checkout"),
                ) {
                    Text("Оформить (${state.quantities.values.sum()})")
                }

                when {
                    state.isLoading -> CatalogMessage {
                        CircularProgressIndicator()
                    }
                    state.error != null -> CatalogMessage {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(state.error ?: "Ошибка")
                            Button(
                                onClick = viewModel::retry,
                                modifier = Modifier.padding(top = 12.dp),
                            ) {
                                Text("Повторить")
                            }
                        }
                    }
                    state.products.isEmpty() -> CatalogMessage {
                        Text("Товары не найдены")
                    }
                    else -> LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(state.products, key = { it.id }) { product ->
                            ProductCard(
                                product = product,
                                quantity = viewModel.quantity(product),
                                onDecrease = { viewModel.changeQuantity(product, -1) },
                                onIncrease = { viewModel.changeQuantity(product, 1) },
                            )
                        }
                    }
                }
            }
        }

        if (state.isOrderFormVisible) {
            OrderDialog(
                state = state,
                onDismiss = viewModel::hideOrderForm,
                onCustomerNameChange = viewModel::onCustomerNameChange,
                onPhoneChange = viewModel::onPhoneChange,
                onAddressChange = viewModel::onAddressChange,
                onPromocodeChange = viewModel::onPromocodeChange,
                onSubmit = viewModel::submitOrder,
            )
        }
    }
}

@Composable
private fun ProductCard(
    product: Product,
    quantity: Int,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(product.name, style = MaterialTheme.typography.titleMedium)
            Text(
                text = formatRubles(product.priceKopecks),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text("В наличии: ${product.stock}")
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(top = 12.dp),
            ) {
                OutlinedButton(
                    onClick = onDecrease,
                    enabled = quantity > 0,
                    modifier = Modifier.testTag("decrease_product_${product.id}"),
                ) {
                    Text("−")
                }
                Text(quantity.toString(), style = MaterialTheme.typography.titleMedium)
                OutlinedButton(
                    onClick = onIncrease,
                    enabled = product.stock > 0 && quantity < product.stock,
                    modifier = Modifier.testTag("increase_product_${product.id}"),
                ) {
                    Text("+")
                }
            }
        }
    }
}

@Composable
private fun OrderDialog(
    state: CatalogUiState,
    onDismiss: () -> Unit,
    onCustomerNameChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onAddressChange: (String) -> Unit,
    onPromocodeChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    val result = state.orderResult
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (result == null) "Оформление заказа" else "Заказ оформлен") },
        text = {
            if (result == null) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = state.customerName,
                        onValueChange = onCustomerNameChange,
                        label = { Text("Имя *") },
                        singleLine = true,
                        modifier = Modifier.testTag("order_customer_name"),
                    )
                    OutlinedTextField(
                        value = state.phone,
                        onValueChange = onPhoneChange,
                        label = { Text("Телефон *") },
                        singleLine = true,
                        modifier = Modifier.testTag("order_phone"),
                    )
                    OutlinedTextField(
                        value = state.address,
                        onValueChange = onAddressChange,
                        label = { Text("Адрес *") },
                        modifier = Modifier.testTag("order_address"),
                    )
                    OutlinedTextField(
                        value = state.promocode,
                        onValueChange = onPromocodeChange,
                        label = { Text("Промокод") },
                        singleLine = true,
                        modifier = Modifier.testTag("order_promocode"),
                    )
                    state.orderValidationError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                    state.orderError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                }
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.testTag("order_result"),
                ) {
                    Text("Номер заказа: ${result.orderId}")
                    Text("Сумма: ${formatRubles(result.subtotal)}")
                    Text("Скидка: ${formatRubles(result.discount)}")
                    Text("Итого: ${formatRubles(result.total)}")
                    if (!result.promoApplied && result.promoMessage != null) {
                        Text("Промокод: ${result.promoMessage}")
                    }
                }
            }
        },
        confirmButton = {
            if (result == null) {
                Button(
                    onClick = onSubmit,
                    enabled = !state.isSubmittingOrder,
                    modifier = Modifier.testTag("order_submit"),
                ) {
                    if (state.isSubmittingOrder) {
                        CircularProgressIndicator()
                    } else {
                        Text("Отправить")
                    }
                }
            } else {
                Button(onClick = onDismiss) {
                    Text("Закрыть")
                }
            }
        },
        dismissButton = {
            if (result == null) {
                OutlinedButton(
                    onClick = onDismiss,
                    enabled = !state.isSubmittingOrder,
                ) {
                    Text("Отмена")
                }
            }
        },
    )
}

@Composable
private fun CatalogMessage(content: @Composable () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize(),
        ) {
            content()
        }
    }
}

private fun formatRubles(kopecks: Long): String {
    val rubles = kopecks / 100
    val remainder = (kopecks % 100).absoluteValue
    return "$rubles,${remainder.toString().padStart(2, '0')} ₽"
}
