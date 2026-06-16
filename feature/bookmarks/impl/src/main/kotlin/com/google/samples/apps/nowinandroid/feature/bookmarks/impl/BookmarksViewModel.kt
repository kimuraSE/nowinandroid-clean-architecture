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
import com.google.samples.apps.nowinandroid.core.model.data.NewsResourceId
import com.google.samples.apps.nowinandroid.core.model.data.UserNewsResource
import com.google.samples.apps.nowinandroid.core.usecase.BookmarkNewsResourceUseCase
import com.google.samples.apps.nowinandroid.core.usecase.MarkNewsResourceViewedUseCase
import com.google.samples.apps.nowinandroid.core.usecase.ObserveBookmarkedNewsUseCase
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

    // 2つの**Flow（値が何度も流れるストリーム）**を監視して、どちらかが新しい値を出すたびに毎回 transform を再実行する
    val uiState: StateFlow<BookmarksUiState> = combine(
        observeBookmarkedNews(),
        lastRemovedBookmarkId,
        ::toUiState, // ← 関数自体を値として渡して、ラムダと同じ役割をさせている。::toUiState は { a, b -> toUiState(a, b) }
    )
        .catch { emit(BookmarksUiState.Error) } // emit = Flow が値を1つ下流に送り出すこと（何度でも起きる。受け手は collect）
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = BookmarksUiState.Loading,
        )

    // UIに渡されるイベント管理用メソッド
    fun onEvent(event: BookmarksEvent) {
        when (event) {
            is BookmarksEvent.RemoveBookmark -> removeFromBookmarks(event.id)
            BookmarksEvent.UndoBookmarkRemoval -> undoBookmarkRemoval()
            BookmarksEvent.ClearUndoState -> clearUndoState()
            is BookmarksEvent.MarkAsViewed -> markViewed(event.id)
        }
    }

    // ブックマーク解除：取り消し用に直前 ID を記録（→Snackbar 表示条件が立つ）してから、UseCase で解除を DB へ反映
    private fun removeFromBookmarks(id: NewsResourceId) {
        viewModelScope.launch {
            lastRemovedBookmarkId.value = id
            bookmarkNewsResource(id, bookmarked = false)
        }
    }

    // 取り消し：記録しておいた直前 ID を再ブックマークし、取り消し状態（記録）をクリア
    private fun undoBookmarkRemoval() {
        viewModelScope.launch {
            lastRemovedBookmarkId.value?.let { bookmarkNewsResource(it, bookmarked = true) }
            lastRemovedBookmarkId.value = null
        }
    }

    // 取り消し可能状態の破棄：記録を消すだけ（再ブックマークはしない）。Snackbar が押されず消えた時など
    private fun clearUndoState() {
        lastRemovedBookmarkId.value = null
    }

    // ニュースを既読にする（UseCase 経由で DB に反映）
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
