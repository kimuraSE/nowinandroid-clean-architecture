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

import com.google.samples.apps.nowinandroid.core.data.repository.CompositeUserNewsResourceRepository
import com.google.samples.apps.nowinandroid.core.domain.usecase.BookmarkNewsResourceUseCase
import com.google.samples.apps.nowinandroid.core.domain.usecase.MarkNewsResourceViewedUseCase
import com.google.samples.apps.nowinandroid.core.domain.usecase.ObserveBookmarkedNewsUseCase
import com.google.samples.apps.nowinandroid.core.testing.data.newsResourcesTestData
import com.google.samples.apps.nowinandroid.core.testing.repository.TestNewsRepository
import com.google.samples.apps.nowinandroid.core.testing.repository.TestUserDataRepository
import com.google.samples.apps.nowinandroid.core.testing.util.MainDispatcherRule
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * To learn more about how this test handles Flows created with stateIn, see
 * https://developer.android.com/kotlin/flow/test#statein
 */
class BookmarksViewModelTest {
    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private val userDataRepository = TestUserDataRepository()
    private val newsRepository = TestNewsRepository()
    private val userNewsResourceRepository = CompositeUserNewsResourceRepository(
        newsRepository = newsRepository,
        userDataRepository = userDataRepository,
    )
    private lateinit var viewModel: BookmarksViewModel

    @Before
    fun setup() {
        viewModel = BookmarksViewModel(
            observeBookmarkedNews = ObserveBookmarkedNewsUseCase(userNewsResourceRepository),
            bookmarkNewsResource = BookmarkNewsResourceUseCase(userDataRepository),
            markNewsResourceViewed = MarkNewsResourceViewedUseCase(userDataRepository),
        )
    }

    @Test
    fun stateIsInitiallyLoading() = runTest {
        assertEquals(BookmarksUiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun oneBookmark_showsInFeed() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect() }

        newsRepository.sendNewsResources(newsResourcesTestData)
        userDataRepository.setNewsResourceBookmarked(newsResourcesTestData[0].id, true)
        val item = viewModel.uiState.value
        assertIs<BookmarksUiState.Success>(item)
        assertEquals(item.feed.size, 1)
    }

    @Test
    fun oneBookmark_whenRemoving_removesFromFeed() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect() }
        // Set the news resources to be used by this test
        newsRepository.sendNewsResources(newsResourcesTestData)
        // Start with the resource saved
        userDataRepository.setNewsResourceBookmarked(newsResourcesTestData[0].id, true)
        // Use onEvent to remove the saved resource
        viewModel.onEvent(BookmarksEvent.RemoveBookmark(newsResourcesTestData[0].id))
        // Verify list of saved resources is now empty
        val item = viewModel.uiState.value
        assertIs<BookmarksUiState.Success>(item)
        assertEquals(item.feed.size, 0)
        assertTrue(item.shouldShowUndoBookmark)
    }

    @Test
    fun feedUiState_resourceIsViewed_setResourcesViewed() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect() }

        // Given
        newsRepository.sendNewsResources(newsResourcesTestData)
        userDataRepository.setNewsResourceBookmarked(newsResourcesTestData[0].id, true)
        val itemBeforeViewed = viewModel.uiState.value
        assertIs<BookmarksUiState.Success>(itemBeforeViewed)
        assertFalse(itemBeforeViewed.feed.first().hasBeenViewed)

        // When
        viewModel.onEvent(BookmarksEvent.MarkAsViewed(newsResourcesTestData[0].id))

        // Then
        val item = viewModel.uiState.value
        assertIs<BookmarksUiState.Success>(item)
        assertTrue(item.feed.first().hasBeenViewed)
    }

    @Test
    fun feedUiState_undoneBookmarkRemoval_bookmarkIsRestored() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect() }

        // Given
        newsRepository.sendNewsResources(newsResourcesTestData)
        userDataRepository.setNewsResourceBookmarked(newsResourcesTestData[0].id, true)
        viewModel.onEvent(BookmarksEvent.RemoveBookmark(newsResourcesTestData[0].id))
        val itemBeforeUndo = viewModel.uiState.value
        assertIs<BookmarksUiState.Success>(itemBeforeUndo)
        assertTrue(itemBeforeUndo.shouldShowUndoBookmark)
        assertEquals(0, itemBeforeUndo.feed.size)

        // When
        viewModel.onEvent(BookmarksEvent.UndoBookmarkRemoval)

        // Then
        val item = viewModel.uiState.value
        assertIs<BookmarksUiState.Success>(item)
        assertFalse(item.shouldShowUndoBookmark)
        assertEquals(1, item.feed.size)
    }
}
