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

package com.google.samples.apps.nowinandroid.core.domain.usecase

import com.google.samples.apps.nowinandroid.core.domain.repository.TopicsRepository
import com.google.samples.apps.nowinandroid.core.domain.repository.UserDataRepository
import com.google.samples.apps.nowinandroid.core.domain.usecase.TopicSortField.NAME
import com.google.samples.apps.nowinandroid.core.domain.usecase.TopicSortField.NONE
import com.google.samples.apps.nowinandroid.core.model.data.FollowableTopic
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

/**
 * トピック一覧を、各トピックのフォロー状態とともに観察する。
 */
class ObserveFollowableTopicsUseCase @Inject constructor(
    private val topicsRepository: TopicsRepository,
    private val userDataRepository: UserDataRepository,
) {
    /**
     * @param sortBy ソートに使うフィールド。既定の NONE はソートなし。
     */
    operator fun invoke(sortBy: TopicSortField = NONE): Flow<List<FollowableTopic>> = combine(
        userDataRepository.userData,
        topicsRepository.getTopics(),
    ) { userData, topics ->
        val followableTopics = topics.map { topic ->
            FollowableTopic(
                topic = topic,
                isFollowed = userData.isFollowing(topic.id),
            )
        }
        when (sortBy) {
            NAME -> followableTopics.sortedBy { it.topic.name }
            else -> followableTopics
        }
    }
}
