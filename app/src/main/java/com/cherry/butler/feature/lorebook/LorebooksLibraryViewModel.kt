package com.cherry.butler.feature.lorebook

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cherry.butler.core.data.LorebookDraft
import com.cherry.butler.core.data.LorebookDraftRepository
import com.cherry.butler.core.lorebook.LorebookJson
import com.cherry.butler.core.lorebook.LorebookStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class LorebooksLibraryViewModel @Inject constructor(
    private val list: LorebookListDelegate,
    private val store: LorebookStore,
    private val drafts: LorebookDraftRepository,
) : ViewModel() {
    val books = list.books()
    val contextCharLimit = store.contextCharLimit

    fun toggleFavorite(id: String) = list.toggleFavorite(id, viewModelScope)

    fun setContextCharLimit(limit: Int) {
        viewModelScope.launch { store.setContextCharLimit(limit) }
    }

    suspend fun prepareImport(text: String, fallbackName: String): String {
        val draft = withContext(Dispatchers.Default) {
            LorebookDraft.fromImport(LorebookJson.parse(text, fallbackName = fallbackName))
        }
        drafts.put(draft)
        return draft.id
    }

    fun discardImport(id: String) {
        drafts.take(id)
    }
}
