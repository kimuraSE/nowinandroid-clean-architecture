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

package com.google.samples.apps.nowinandroid.feature.interests.impl

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.samples.apps.nowinandroid.core.domain.usecase.FollowTopicUseCase
import com.google.samples.apps.nowinandroid.core.domain.usecase.ObserveFollowableTopicsUseCase
import com.google.samples.apps.nowinandroid.core.domain.usecase.TopicSortField
import com.google.samples.apps.nowinandroid.feature.interests.api.navigation.InterestsNavKey
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel(assistedFactory = InterestsViewModel.Factory::class)
class InterestsViewModel @AssistedInject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val followTopic: FollowTopicUseCase,
    observeFollowableTopics: ObserveFollowableTopicsUseCase,
    // TODO: see comment below
    @Assisted val key: InterestsNavKey,
) : ViewModel() {

    // TODO: this should no longer be necessary, the currently selected topic should be
    //  available through the navigation state
    // Key used to save and retrieve the currently selected topic id from saved state.
    private val selectedTopicIdKey = "selectedTopicIdKey"

    private val selectedTopicId = savedStateHandle.getStateFlow(
        key = selectedTopicIdKey,
        initialValue = key.initialTopicId,
    )

    val uiState: StateFlow<InterestsUiState> = combine(
        selectedTopicId,
        observeFollowableTopics(sortBy = TopicSortField.NAME),
        InterestsUiState::Interests,
    ).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = InterestsUiState.Loading,
    )

    fun onEvent(event: InterestsEvent) {
        when (event) {
            is InterestsEvent.FollowTopic -> viewModelScope.launch {
                followTopic(event.topicId, event.followed)
            }

            is InterestsEvent.SelectTopic ->
                // TODO: This should modify the navigation state directly rather than just updating
                //  the savedStateHandle
                savedStateHandle[selectedTopicIdKey] = event.topicId
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(key: InterestsNavKey): InterestsViewModel
    }
}
