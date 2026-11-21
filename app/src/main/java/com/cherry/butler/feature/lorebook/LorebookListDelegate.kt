package com.cherry.butler.feature.lorebook

import com.cherry.butler.core.lorebook.LorebookStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

class LorebookListDelegate @Inject constructor(
    private val store: LorebookStore,
) {
    fun books(scope: String? = null, personaScope: String? = null) = store.books(scope, personaScope)

    fun toggleFavorite(id: String, scope: CoroutineScope) {
        scope.launch { store.toggleFavorite(id) }
    }
}
