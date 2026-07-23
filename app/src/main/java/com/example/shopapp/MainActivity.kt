package com.example.shopapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.shopapp.data.ShopAppContainer
import com.example.shopapp.ui.catalog.CatalogScreen
import com.example.shopapp.ui.catalog.CatalogViewModel
import com.example.shopapp.ui.stats.StatsScreen
import com.example.shopapp.ui.stats.StatsViewModel

private enum class ShopDestination { CATALOG, STATS }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val catalogViewModel: CatalogViewModel = viewModel(
                factory = CatalogViewModel.factory(ShopAppContainer.repository)
            )
            val statsViewModel: StatsViewModel = viewModel(
                factory = StatsViewModel.factory(ShopAppContainer.repository)
            )
            var destination by rememberSaveable { mutableStateOf(ShopDestination.CATALOG) }

            MaterialTheme {
                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                selected = destination == ShopDestination.CATALOG,
                                onClick = { destination = ShopDestination.CATALOG },
                                icon = { Text("К") },
                                label = { Text("Каталог") },
                                modifier = Modifier.testTag("nav_catalog"),
                            )
                            NavigationBarItem(
                                selected = destination == ShopDestination.STATS,
                                onClick = { destination = ShopDestination.STATS },
                                icon = { Text("С") },
                                label = { Text("Статистика") },
                                modifier = Modifier.testTag("nav_stats"),
                            )
                        }
                    }
                ) { padding ->
                    Box(modifier = Modifier.padding(padding)) {
                        when (destination) {
                            ShopDestination.CATALOG -> CatalogScreen(catalogViewModel)
                            ShopDestination.STATS -> StatsScreen(statsViewModel)
                        }
                    }
                }
            }
        }
    }
}
