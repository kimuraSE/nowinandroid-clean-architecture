/*
 * Copyright 2023 The Android Open Source Project
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

package com.google.samples.apps.nowinandroid.feature.search.impl

import androidx.lifecycle.SavedStateHandle
import com.google.samples.apps.nowinandroid.core.analytics.NoOpAnalyticsHelper
import com.google.samples.apps.nowinandroid.core.domain.usecase.BookmarkNewsResourceUseCase
import com.google.samples.apps.nowinandroid.core.domain.usecase.ClearRecentSearchesUseCase
import com.google.samples.apps.nowinandroid.core.domain.usecase.FollowTopicUseCase
import com.google.samples.apps.nowinandroid.core.domain.usecase.MarkNewsResourceViewedUseCase
import com.google.samples.apps.nowinandroid.core.domain.usecase.ObserveRecentSearchQueriesUseCase
import com.google.samples.apps.nowinandroid.core.domain.usecase.ObserveSearchContentsCountUseCase
import com.google.samples.apps.nowinandroid.core.domain.usecase.ObserveSearchResultsUseCase
import com.google.samples.apps.nowinandroid.core.domain.usecase.SaveRecentSearchUseCase
import com.google.samples.apps.nowinandroid.core.model.data.NewsResourceId
import com.google.samples.apps.nowinandroid.core.testing.data.newsResourcesTestData
import com.google.samples.apps.nowinandroid.core.testing.data.topicsTestData
import com.google.samples.apps.nowinandroid.core.testing.repository.TestRecentSearchRepository
import com.google.samples.apps.nowinandroid.core.testing.repository.TestSearchContentsRepository
import com.google.samples.apps.nowinandroid.core.testing.repository.TestUserDataRepository
import com.google.samples.apps.nowinandroid.core.testing.repository.emptyUserData
import com.google.samples.apps.nowinandroid.core.testing.util.MainDispatcherRule
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * To learn more about how this test handles Flows created with stateIn, see
 * https://developer.android.com/kotlin/flow/test#statein
 */
class SearchViewModelTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private val userDataRepository = TestUserDataRepository()
    private val searchContentsRepository = TestSearchContentsRepository()
    private val recentSearchRepository = TestRecentSearchRepository()
    private val observeRecentSearchQueries = ObserveRecentSearchQueriesUseCase(recentSearchRepository)

    private lateinit var viewModel: SearchViewModel

    @Before
    fun setup() {
        viewModel = SearchViewModel(
            observeSearchResults = ObserveSearchResultsUseCase(
                searchContentsRepository = searchContentsRepository,
                userDataRepository = userDataRepository,
            ),
            observeRecentSearchQueries = observeRecentSearchQueries,
            observeSearchContentsCount = ObserveSearchContentsCountUseCase(searchContentsRepository),
            saveRecentSearch = SaveRecentSearchUseCase(recentSearchRepository),
            clearRecentSearches = ClearRecentSearchesUseCase(recentSearchRepository),
            followTopic = FollowTopicUseCase(userDataRepository),
            bookmarkNewsResource = BookmarkNewsResourceUseCase(userDataRepository),
            markNewsResourceViewed = MarkNewsResourceViewedUseCase(userDataRepository),
            savedStateHandle = SavedStateHandle(),
            analyticsHelper = NoOpAnalyticsHelper(),
        )
        userDataRepository.setUserData(emptyUserData)
    }

    @Test
    fun stateIsInitiallyLoading() = runTest {
        assertEquals(SearchUiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun resultIsEmptyQuery_withEmptySearchQuery() = runTest {
        searchContentsRepository.addNewsResources(newsResourcesTestData)
        searchContentsRepository.addTopics(topicsTestData)
        backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect() }

        viewModel.onEvent(SearchEvent.QueryChanged(""))

        val state = assertIs<SearchUiState.Success>(viewModel.uiState.value)
        assertEquals(SearchResultState.EmptyQuery, state.result)
    }

    @Test
    fun emptyResultIsReturned_withNotMatchingQuery() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect() }

        viewModel.onEvent(SearchEvent.QueryChanged("XXX"))
        searchContentsRepository.addNewsResources(newsResourcesTestData)
        searchContentsRepository.addTopics(topicsTestData)

        val state = assertIs<SearchUiState.Success>(viewModel.uiState.value)
        assertIs<SearchResultState.Results>(state.result)
    }

    @Test
    fun recentSearches_verifyUiStateIsSuccess() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect() }
        viewModel.onEvent(SearchEvent.SearchTriggered("kotlin"))

        assertIs<SearchUiState.Success>(viewModel.uiState.value)
    }

    @Test
    fun resultIsNotReady_withNoFtsTableEntity() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect() }

        viewModel.onEvent(SearchEvent.QueryChanged(""))

        val state = assertIs<SearchUiState.Success>(viewModel.uiState.value)
        assertEquals(SearchResultState.NotReady, state.result)
    }

    @Test
    fun emptySearchText_isNotAddedToRecentSearches() = runTest {
        viewModel.onEvent(SearchEvent.SearchTriggered(""))

        val recentSearchQuery = observeRecentSearchQueries().first().firstOrNull()

        assertNull(recentSearchQuery)
    }

    @Test
    fun searchTextWithThreeSpaces_isEmptyQuery() = runTest {
        searchContentsRepository.addNewsResources(newsResourcesTestData)
        searchContentsRepository.addTopics(topicsTestData)
        val collectJob = launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect() }

        viewModel.onEvent(SearchEvent.QueryChanged("   "))

        val state = assertIs<SearchUiState.Success>(viewModel.uiState.value)
        assertEquals(SearchResultState.EmptyQuery, state.result)

        collectJob.cancel()
    }

    @Test
    fun searchTextWithThreeSpacesAndOneLetter_isEmptyQuery() = runTest {
        searchContentsRepository.addNewsResources(newsResourcesTestData)
        searchContentsRepository.addTopics(topicsTestData)
        val collectJob = launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect() }

        viewModel.onEvent(SearchEvent.QueryChanged("   a"))

        val state = assertIs<SearchUiState.Success>(viewModel.uiState.value)
        assertEquals(SearchResultState.EmptyQuery, state.result)

        collectJob.cancel()
    }

    @Test
    fun whenBookmarkNewsEventIsSent_bookmarkStateIsUpdated() = runTest {
        val newsResourceId = NewsResourceId("123")
        viewModel.onEvent(SearchEvent.BookmarkNews(newsResourceId, true))

        assertEquals(
            expected = setOf(newsResourceId),
            actual = userDataRepository.userData.first().bookmarkedNewsResources,
        )

        viewModel.onEvent(SearchEvent.BookmarkNews(newsResourceId, false))

        assertEquals(
            expected = emptySet(),
            actual = userDataRepository.userData.first().bookmarkedNewsResources,
        )
    }
}
