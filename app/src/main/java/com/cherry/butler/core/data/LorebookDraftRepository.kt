package com.cherry.butler.core.data

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** Holds imported drafts until their editors claim them. */
@Singleton
class LorebookDraftRepository @Inject constructor() {
    private val drafts = ConcurrentHashMap<String, LorebookDraft>()

    fun put(draft: LorebookDraft) {
        drafts[draft.id] = draft
    }

    fun take(id: String): LorebookDraft? = drafts.remove(id)
}
