package com.projectember.mobile.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.projectember.mobile.data.local.entities.SupplementEntry
import com.projectember.mobile.data.repository.SupplementRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class SupplementViewModel(
    private val supplementRepository: SupplementRepository
) : ViewModel() {

    val entries: StateFlow<List<SupplementEntry>> = supplementRepository.getAll()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )
}

class SupplementViewModelFactory(
    private val supplementRepository: SupplementRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        SupplementViewModel(supplementRepository) as T
}
