package com.example.shopapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.shopapp.data.ShopAppContainer
import com.example.shopapp.ui.catalog.CatalogScreen
import com.example.shopapp.ui.catalog.CatalogViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val catalogViewModel: CatalogViewModel = viewModel(
                factory = CatalogViewModel.factory(ShopAppContainer.repository)
            )
            CatalogScreen(viewModel = catalogViewModel)
        }
    }
}
