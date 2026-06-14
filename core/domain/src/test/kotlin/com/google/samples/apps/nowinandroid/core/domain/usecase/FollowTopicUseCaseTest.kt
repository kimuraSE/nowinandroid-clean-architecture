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

import com.google.samples.apps.nowinandroid.core.model.data.TopicId
import com.google.samples.apps.nowinandroid.core.testing.repository.TestUserDataRepository
import com.google.samples.apps.nowinandroid.core.testing.repository.emptyUserData
import com.google.samples.apps.nowinandroid.core.testing.util.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FollowTopicUseCaseTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val userDataRepository = TestUserDataRepository()

    private val useCase = FollowTopicUseCase(userDataRepository)

    @Test
    fun whenFollowingTopic_resultIsSuccessAndStateIsUpdated() = runTest {
        userDataRepository.setUserData(emptyUserData)

        val result = useCase(TopicId("1"), followed = true)

        assertTrue(result.isSuccess)
        assertEquals(
            setOf(TopicId("1")),
            userDataRepository.userData.first().followedTopics,
        )
    }

    @Test
    fun whenUnfollowingTopic_topicIsRemovedFromFollowedSet() = runTest {
        userDataRepository.setUserData(
            emptyUserData.copy(followedTopics = setOf(TopicId("1"))),
        )

        val result = useCase(TopicId("1"), followed = false)

        assertTrue(result.isSuccess)
        assertEquals(emptySet(), userDataRepository.userData.first().followedTopics)
    }
}
