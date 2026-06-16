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

package com.google.samples.apps.nowinandroid.core.usecase

import com.google.samples.apps.nowinandroid.core.model.data.Topic
import com.google.samples.apps.nowinandroid.core.model.data.TopicId
import com.google.samples.apps.nowinandroid.core.testing.repository.TestTopicsRepository
import com.google.samples.apps.nowinandroid.core.testing.repository.TestUserDataRepository
import com.google.samples.apps.nowinandroid.core.testing.util.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class ObserveFollowableTopicUseCaseTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val topicsRepository = TestTopicsRepository()
    private val userDataRepository = TestUserDataRepository()

    private val useCase = ObserveFollowableTopicUseCase(
        topicsRepository,
        userDataRepository,
    )

    @Test
    fun whenTopicIsFollowed_followableTopicReflectsFollowedState() = runTest {
        val followableTopic = useCase(testTopic.id)

        topicsRepository.sendTopics(listOf(testTopic))
        userDataRepository.setFollowedTopicIds(setOf(testTopic.id))

        assertEquals(testTopic, followableTopic.first().topic)
        assertEquals(true, followableTopic.first().isFollowed)
    }

    @Test
    fun whenTopicIsNotFollowed_followableTopicIsNotFollowed() = runTest {
        val followableTopic = useCase(testTopic.id)

        topicsRepository.sendTopics(listOf(testTopic))
        userDataRepository.setFollowedTopicIds(emptySet())

        assertEquals(false, followableTopic.first().isFollowed)
    }
}

private val testTopic = Topic(
    id = TopicId("1"),
    name = "Headlines",
    shortDescription = "",
    longDescription = "",
    url = "",
    imageUrl = "",
)
