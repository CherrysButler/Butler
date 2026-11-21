package com.cherry.butler.feature.lorebook

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cherry.butler.core.lorebook.LorebookStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LorebooksSheetViewModel @Inject constructor(
    private val list: LorebookListDelegate,
    private val store: LorebookStore,
) : ViewModel() {
    fun booksFor(scope: String?, personaScope: String?) = list.books(scope, personaScope)

    fun toggleFavorite(id: String) = list.toggleFavorite(id, viewModelScope)

    fun setEnabled(scope: String, id: String, enabled: Boolean) {
        viewModelScope.launch { store.setEnabled(scope, id, enabled) }
    }
}
