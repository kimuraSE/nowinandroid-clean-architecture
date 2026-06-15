/*
 * Copyright 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.samples.apps.nowinandroid.feature.bookmarks.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.samples.apps.nowinandroid.core.domain.usecase.BookmarkNewsResourceUseCase
import com.google.samples.apps.nowinandroid.core.domain.usecase.MarkNewsResourceViewedUseCase
import com.google.samples.apps.nowinandroid.core.domain.usecase.ObserveBookmarkedNewsUseCase
import com.google.samples.apps.nowinandroid.core.model.data.NewsResourceId
import com.google.samples.apps.nowinandroid.core.model.data.UserNewsResource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BookmarksViewModel @Inject constructor(
    observeBookmarkedNews: ObserveBookmarkedNewsUseCase,
    private val bookmarkNewsResource: BookmarkNewsResourceUseCase,
    private val markNewsResourceViewed: MarkNewsResourceViewedUseCase,
) : ViewModel() {

    /** 直前に解除したブックマーク。null でなければ「取り消し」を提示中。 */
    private val lastRemovedBookmarkId = MutableStateFlow<NewsResourceId?>(null)

    val uiState: StateFlow<BookmarksUiState> = combine(
        observeBookmarkedNews(),
        lastRemovedBookmarkId,
        ::toUiState,
    )
        .catch { emit(BookmarksUiState.Error) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = BookmarksUiState.Loading,
        )

    fun onEvent(event: BookmarksEvent) {
        when (event) {
            is BookmarksEvent.RemoveBookmark -> removeFromBookmarks(event.id)
            BookmarksEvent.UndoBookmarkRemoval -> undoBookmarkRemoval()
            BookmarksEvent.ClearUndoState -> clearUndoState()
            is BookmarksEvent.MarkAsViewed -> markViewed(event.id)
        }
    }

    private fun removeFromBookmarks(id: NewsResourceId) {
        viewModelScope.launch {
            lastRemovedBookmarkId.value = id
            bookmarkNewsResource(id, bookmarked = false)
        }
    }

    private fun undoBookmarkRemoval() {
        viewModelScope.launch {
            lastRemovedBookmarkId.value?.let { bookmarkNewsResource(it, bookmarked = true) }
            lastRemovedBookmarkId.value = null
        }
    }

    private fun clearUndoState() {
        lastRemovedBookmarkId.value = null
    }

    private fun markViewed(id: NewsResourceId) {
        viewModelScope.launch {
            markNewsResourceViewed(id, viewed = true)
        }
    }
}

private fun toUiState(
    feed: List<UserNewsResource>,
    lastRemovedBookmarkId: NewsResourceId?,
): BookmarksUiState = BookmarksUiState.Success(
    feed = feed,
    shouldShowUndoBookmark = lastRemovedBookmarkId != null,
)
