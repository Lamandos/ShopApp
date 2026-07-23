package com.example.shopapp.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.shopapp.data.model.Stats
import kotlin.math.absoluteValue

@Composable
fun StatsScreen(viewModel: StatsViewModel) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Text("Статистика", style = MaterialTheme.typography.headlineMedium)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 16.dp),
        ) {
            OutlinedTextField(
                value = state.from,
                onValueChange = viewModel::onFromChange,
                label = { Text("from") },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag("stats_from"),
            )
            OutlinedTextField(
                value = state.to,
                onValueChange = viewModel::onToChange,
                label = { Text("to") },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag("stats_to"),
            )
        }
        Button(
            onClick = viewModel::loadStats,
            enabled = !state.isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
        ) {
            Text("Показать")
        }

        when {
            state.isLoading -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            ) {
                CircularProgressIndicator()
            }
            state.error != null -> Text(
                text = state.error ?: "Ошибка",
                color = MaterialTheme.colorScheme.error,
            )
            state.stats != null -> StatsContent(requireNotNull(state.stats))
        }
    }
}

@Composable
private fun StatsContent(stats: Stats) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxSize()
            .testTag("stats_content"),
    ) {
        item {
            StatCard("Выручка", formatRubles(stats.revenue))
        }
        item {
            StatCard("Заказы", stats.ordersCount.toString())
        }
        item {
            StatCard("Средний чек", formatRubles(stats.averageCheck))
        }
        item {
            Text(
                text = "Топ товаров",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        if (stats.topProducts.isEmpty()) {
            item { Text("Нет продаж за выбранный период") }
        } else {
            items(stats.topProducts, key = { "${it.productId}:${it.productName}" }) { product ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(product.productName, style = MaterialTheme.typography.titleMedium)
                        Text("Количество: ${product.quantity}")
                        Text("Выручка: ${formatRubles(product.revenue)}")
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(title: String, value: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Text(value, style = MaterialTheme.typography.headlineSmall)
        }
    }
}

private fun formatRubles(kopecks: Long): String {
    val rubles = kopecks / 100
    val remainder = (kopecks % 100).absoluteValue
    return "$rubles,${remainder.toString().padStart(2, '0')} ₽"
}
