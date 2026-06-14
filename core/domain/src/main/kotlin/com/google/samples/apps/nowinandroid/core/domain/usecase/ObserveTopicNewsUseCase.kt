/*
 * Copyright 2024 The Android Open Source Project
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

import com.google.samples.apps.nowinandroid.core.domain.repository.NewsResourceQuery
import com.google.samples.apps.nowinandroid.core.domain.repository.UserNewsResourceRepository
import com.google.samples.apps.nowinandroid.core.model.data.TopicId
import com.google.samples.apps.nowinandroid.core.model.data.UserNewsResource
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * 指定トピックに紐づくニュースを、ユーザー状態付きで観察する。
 */
class ObserveTopicNewsUseCase @Inject constructor(
    private val userNewsResourceRepository: UserNewsResourceRepository,
) {
    operator fun invoke(topicId: TopicId): Flow<List<UserNewsResource>> =
        userNewsResourceRepository.observeAll(
            NewsResourceQuery(filterTopicIds = setOf(topicId)),
        )
}
