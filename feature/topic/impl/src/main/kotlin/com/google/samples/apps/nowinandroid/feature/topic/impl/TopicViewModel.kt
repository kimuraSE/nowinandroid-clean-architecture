/*
 * Copyright 2021 The Android Open Source Project
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

package com.google.samples.apps.nowinandroid.feature.topic.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.samples.apps.nowinandroid.core.model.data.FollowableTopic
import com.google.samples.apps.nowinandroid.core.model.data.TopicId
import com.google.samples.apps.nowinandroid.core.model.data.UserNewsResource
import com.google.samples.apps.nowinandroid.core.usecase.BookmarkNewsResourceUseCase
import com.google.samples.apps.nowinandroid.core.usecase.FollowTopicUseCase
import com.google.samples.apps.nowinandroid.core.usecase.MarkNewsResourceViewedUseCase
import com.google.samples.apps.nowinandroid.core.usecase.ObserveFollowableTopicUseCase
import com.google.samples.apps.nowinandroid.core.usecase.ObserveTopicNewsUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel(assistedFactory = TopicViewModel.Factory::class)
class TopicViewModel @AssistedInject constructor(
    observeFollowableTopic: ObserveFollowableTopicUseCase,
    observeTopicNews: ObserveTopicNewsUseCase,
    private val followTopic: FollowTopicUseCase,
    private val bookmarkNewsResource: BookmarkNewsResourceUseCase,
    private val markNewsResourceViewed: MarkNewsResourceViewedUseCase,
    @Assisted val topicId: String,
) : ViewModel() {

    private val typedTopicId = TopicId(topicId)

    val uiState: StateFlow<TopicUiState> = combine(
        observeFollowableTopic(typedTopicId),
        observeTopicNews(typedTopicId),
        ::toUiState,
    )
        .catch { emit(TopicUiState.Error) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = TopicUiState.Loading,
        )

    fun onEvent(event: TopicEvent) {
        when (event) {
            is TopicEvent.FollowTopicToggle ->
                viewModelScope.launch { followTopic(typedTopicId, event.followed) }

            is TopicEvent.BookmarkNews ->
                viewModelScope.launch { bookmarkNewsResource(event.id, event.bookmarked) }

            is TopicEvent.MarkNewsViewed ->
                viewModelScope.launch { markNewsResourceViewed(event.id, viewed = true) }
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(
            topicId: String,
        ): TopicViewModel
    }
}

private fun toUiState(
    followableTopic: FollowableTopic,
    news: List<UserNewsResource>,
): TopicUiState = TopicUiState.Success(
    followableTopic = followableTopic,
    news = news,
)
