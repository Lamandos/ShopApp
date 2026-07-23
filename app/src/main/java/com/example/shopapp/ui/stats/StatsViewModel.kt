package com.example.shopapp.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.shopapp.data.ShopRepository
import com.example.shopapp.data.model.Stats
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class StatsUiState(
    val from: String = "2025-05-01",
    val to: String = "2025-07-31",
    val stats: Stats? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
)

class StatsViewModel(private val repository: ShopRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    init {
        loadStats()
    }

    fun onFromChange(value: String) {
        _uiState.value = _uiState.value.copy(from = value, error = null)
    }

    fun onToChange(value: String) {
        _uiState.value = _uiState.value.copy(to = value, error = null)
    }

    fun loadStats() {
        val state = _uiState.value
        val from = runCatching { LocalDate.parse(state.from) }.getOrNull()
        val to = runCatching { LocalDate.parse(state.to) }.getOrNull()
        if (from == null || to == null) {
            _uiState.value = state.copy(error = "Введите даты в формате YYYY-MM-DD")
            return
        }
        if (from > to) {
            _uiState.value = state.copy(error = "Дата from не должна быть позже to")
            return
        }

        _uiState.value = state.copy(isLoading = true, error = null)
        viewModelScope.launch {
            runCatching { repository.getStats(state.from, state.to) }
                .onSuccess { stats ->
                    _uiState.value = _uiState.value.copy(
                        stats = stats,
                        isLoading = false,
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message ?: "Не удалось загрузить статистику",
                    )
                }
        }
    }

    companion object {
        fun factory(repository: ShopRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    StatsViewModel(repository) as T
            }
    }
}
