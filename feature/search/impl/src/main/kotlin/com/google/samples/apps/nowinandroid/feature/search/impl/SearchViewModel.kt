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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.samples.apps.nowinandroid.core.analytics.AnalyticsEvent
import com.google.samples.apps.nowinandroid.core.analytics.AnalyticsEvent.Param
import com.google.samples.apps.nowinandroid.core.analytics.AnalyticsHelper
import com.google.samples.apps.nowinandroid.core.model.data.RecentSearchQuery
import com.google.samples.apps.nowinandroid.core.model.data.UserSearchResult
import com.google.samples.apps.nowinandroid.core.usecase.BookmarkNewsResourceUseCase
import com.google.samples.apps.nowinandroid.core.usecase.ClearRecentSearchesUseCase
import com.google.samples.apps.nowinandroid.core.usecase.FollowTopicUseCase
import com.google.samples.apps.nowinandroid.core.usecase.MarkNewsResourceViewedUseCase
import com.google.samples.apps.nowinandroid.core.usecase.ObserveRecentSearchQueriesUseCase
import com.google.samples.apps.nowinandroid.core.usecase.ObserveSearchContentsCountUseCase
import com.google.samples.apps.nowinandroid.core.usecase.ObserveSearchResultsUseCase
import com.google.samples.apps.nowinandroid.core.usecase.SaveRecentSearchUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    observeSearchResults: ObserveSearchResultsUseCase,
    observeRecentSearchQueries: ObserveRecentSearchQueriesUseCase,
    observeSearchContentsCount: ObserveSearchContentsCountUseCase,
    private val saveRecentSearch: SaveRecentSearchUseCase,
    private val clearRecentSearches: ClearRecentSearchesUseCase,
    private val followTopic: FollowTopicUseCase,
    private val bookmarkNewsResource: BookmarkNewsResourceUseCase,
    private val markNewsResourceViewed: MarkNewsResourceViewedUseCase,
    private val savedStateHandle: SavedStateHandle,
    private val analyticsHelper: AnalyticsHelper,
) : ViewModel() {

    private val searchQuery = savedStateHandle.getStateFlow(key = SEARCH_QUERY, initialValue = "")

    private val resultState: Flow<SearchResultState> =
        observeSearchContentsCount()
            .flatMapLatest { totalCount ->
                if (totalCount < SEARCH_MIN_FTS_ENTITY_COUNT) {
                    flowOf(SearchResultState.NotReady)
                } else {
                    searchQuery.flatMapLatest { query ->
                        if (query.trim().length < SEARCH_QUERY_MIN_LENGTH) {
                            flowOf(SearchResultState.EmptyQuery)
                        } else {
                            // Not using .asResult() here, because it emits Loading state every
                            // time the user types a letter in the search box, which flickers the screen.
                            observeSearchResults(query)
                                .map<UserSearchResult, SearchResultState> { data ->
                                    SearchResultState.Results(
                                        topics = data.topics,
                                        newsResources = data.newsResources,
                                    )
                                }
                        }
                    }
                }
            }

    val uiState: StateFlow<SearchUiState> = combine<String, SearchResultState, List<String>, SearchUiState>(
        searchQuery,
        resultState,
        observeRecentSearchQueries().map { queries -> queries.map(RecentSearchQuery::query) },
    ) { query, result, recentQueries ->
        SearchUiState.Success(
            searchQuery = query,
            result = result,
            recentQueries = recentQueries,
        )
    }
        .catch { emit(SearchUiState.Error) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SearchUiState.Loading,
        )

    fun onEvent(event: SearchEvent) {
        when (event) {
            is SearchEvent.QueryChanged ->
                savedStateHandle[SEARCH_QUERY] = event.query

            is SearchEvent.SearchTriggered ->
                onSearchTriggered(event.query)

            SearchEvent.ClearRecentSearches ->
                viewModelScope.launch { clearRecentSearches() }

            is SearchEvent.FollowTopic ->
                viewModelScope.launch { followTopic(event.topicId, event.followed) }

            is SearchEvent.BookmarkNews ->
                viewModelScope.launch { bookmarkNewsResource(event.id, event.bookmarked) }

            is SearchEvent.MarkNewsViewed ->
                viewModelScope.launch { markNewsResourceViewed(event.id, viewed = true) }
        }
    }

    /**
     * 検索が明示的に実行されたとき（IME の検索キーや Enter）に呼ぶ。入力中も結果は逐次表示されるが、
     * このメソッドで明示的に検索クエリを最近の検索として保存する。
     */
    private fun onSearchTriggered(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch { saveRecentSearch(query) }
        analyticsHelper.logEventSearchTriggered(query = query)
    }
}

private fun AnalyticsHelper.logEventSearchTriggered(query: String) =
    logEvent(
        event = AnalyticsEvent(
            type = SEARCH_QUERY,
            extras = listOf(element = Param(key = SEARCH_QUERY, value = query)),
        ),
    )

/** Minimum length where search query is considered as [SearchResultState.EmptyQuery] */
private const val SEARCH_QUERY_MIN_LENGTH = 2

/** Minimum number of the fts table's entity count where it's considered as search is not ready */
private const val SEARCH_MIN_FTS_ENTITY_COUNT = 1
private const val SEARCH_QUERY = "searchQuery"
