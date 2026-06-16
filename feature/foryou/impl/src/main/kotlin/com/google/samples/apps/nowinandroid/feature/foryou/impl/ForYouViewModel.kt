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

package com.google.samples.apps.nowinandroid.feature.foryou.impl

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.samples.apps.nowinandroid.core.analytics.AnalyticsEvent
import com.google.samples.apps.nowinandroid.core.analytics.AnalyticsEvent.Param
import com.google.samples.apps.nowinandroid.core.analytics.AnalyticsHelper
import com.google.samples.apps.nowinandroid.core.model.data.NewsResourceId
import com.google.samples.apps.nowinandroid.core.model.data.UserNewsResource
import com.google.samples.apps.nowinandroid.core.notifications.DEEP_LINK_NEWS_RESOURCE_ID_KEY
import com.google.samples.apps.nowinandroid.core.ui.NewsFeedUiState
import com.google.samples.apps.nowinandroid.core.usecase.BookmarkNewsResourceUseCase
import com.google.samples.apps.nowinandroid.core.usecase.DismissOnboardingUseCase
import com.google.samples.apps.nowinandroid.core.usecase.FollowTopicUseCase
import com.google.samples.apps.nowinandroid.core.usecase.MarkNewsResourceViewedUseCase
import com.google.samples.apps.nowinandroid.core.usecase.ObserveFollowableTopicsUseCase
import com.google.samples.apps.nowinandroid.core.usecase.ObserveFollowedNewsUseCase
import com.google.samples.apps.nowinandroid.core.usecase.ObserveUserDataUseCase
import com.google.samples.apps.nowinandroid.core.usecase.ObserveUserNewsUseCase
import com.google.samples.apps.nowinandroid.core.usecase.repository.NewsResourceQuery
import com.google.samples.apps.nowinandroid.core.usecase.util.SyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ForYouViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    syncManager: SyncManager,
    private val analyticsHelper: AnalyticsHelper,
    observeUserData: ObserveUserDataUseCase,
    observeFollowableTopics: ObserveFollowableTopicsUseCase,
    observeFollowedNews: ObserveFollowedNewsUseCase,
    observeUserNews: ObserveUserNewsUseCase,
    private val followTopic: FollowTopicUseCase,
    private val bookmarkNewsResource: BookmarkNewsResourceUseCase,
    private val markNewsResourceViewed: MarkNewsResourceViewedUseCase,
    private val dismissOnboarding: DismissOnboardingUseCase,
) : ViewModel() {

    private val shouldShowOnboarding: Flow<Boolean> =
        observeUserData().map { it.shouldShowOnboarding() }

    private val deepLinkedNewsResource: StateFlow<UserNewsResource?> =
        savedStateHandle.getStateFlow<String?>(
            key = DEEP_LINK_NEWS_RESOURCE_ID_KEY,
            null,
        )
            .flatMapLatest { newsResourceId ->
                if (newsResourceId == null) {
                    flowOf(emptyList())
                } else {
                    observeUserNews(
                        NewsResourceQuery(
                            filterNewsIds = setOf(NewsResourceId(newsResourceId)),
                        ),
                    )
                }
            }
            .map { it.firstOrNull() }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = null,
            )

    private val isSyncing: StateFlow<Boolean> = syncManager.isSyncing
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false,
        )

    private val feed: StateFlow<NewsFeedUiState> =
        observeFollowedNews()
            .map(NewsFeedUiState::Success)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = NewsFeedUiState.Loading,
            )

    private val onboarding: StateFlow<OnboardingUiState> =
        combine(
            shouldShowOnboarding,
            observeFollowableTopics(),
        ) { shouldShowOnboarding, topics ->
            if (shouldShowOnboarding) {
                OnboardingUiState.Shown(topics = topics)
            } else {
                OnboardingUiState.NotShown
            }
        }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = OnboardingUiState.Loading,
            )

    val uiState: StateFlow<ForYouUiState> =
        combine<Boolean, OnboardingUiState, NewsFeedUiState, UserNewsResource?, ForYouUiState>(
            isSyncing,
            onboarding,
            feed,
            deepLinkedNewsResource,
        ) { isSyncing, onboarding, feed, deepLinkedUserNewsResource ->
            ForYouUiState.Success(
                isSyncing = isSyncing,
                onboarding = onboarding,
                feed = feed,
                deepLinkedUserNewsResource = deepLinkedUserNewsResource,
            )
        }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = ForYouUiState.Loading,
            )

    fun onEvent(event: ForYouEvent) {
        when (event) {
            is ForYouEvent.UpdateTopicSelection ->
                viewModelScope.launch { followTopic(event.topicId, event.followed) }

            is ForYouEvent.UpdateNewsResourceSaved ->
                viewModelScope.launch { bookmarkNewsResource(event.newsResourceId, event.bookmarked) }

            is ForYouEvent.MarkNewsViewed ->
                viewModelScope.launch { markNewsResourceViewed(event.newsResourceId, viewed = true) }

            ForYouEvent.DismissOnboarding ->
                viewModelScope.launch { dismissOnboarding() }

            is ForYouEvent.DeepLinkOpened ->
                onDeepLinkOpened(event.newsResourceId)
        }
    }

    private fun onDeepLinkOpened(newsResourceId: NewsResourceId) {
        if (newsResourceId == deepLinkedNewsResource.value?.id) {
            savedStateHandle[DEEP_LINK_NEWS_RESOURCE_ID_KEY] = null
        }
        analyticsHelper.logNewsDeepLinkOpen(newsResourceId = newsResourceId.value)
        viewModelScope.launch {
            markNewsResourceViewed(newsResourceId, viewed = true)
        }
    }
}

private fun AnalyticsHelper.logNewsDeepLinkOpen(newsResourceId: String) =
    logEvent(
        AnalyticsEvent(
            type = "news_deep_link_opened",
            extras = listOf(
                Param(
                    key = DEEP_LINK_NEWS_RESOURCE_ID_KEY,
                    value = newsResourceId,
                ),
            ),
        ),
    )
