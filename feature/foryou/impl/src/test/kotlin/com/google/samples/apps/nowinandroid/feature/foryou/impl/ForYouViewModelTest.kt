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
import com.google.samples.apps.nowinandroid.core.analytics.AnalyticsEvent
import com.google.samples.apps.nowinandroid.core.analytics.AnalyticsEvent.Param
import com.google.samples.apps.nowinandroid.core.data.repository.CompositeUserNewsResourceRepository
import com.google.samples.apps.nowinandroid.core.domain.usecase.BookmarkNewsResourceUseCase
import com.google.samples.apps.nowinandroid.core.domain.usecase.DismissOnboardingUseCase
import com.google.samples.apps.nowinandroid.core.domain.usecase.FollowTopicUseCase
import com.google.samples.apps.nowinandroid.core.domain.usecase.MarkNewsResourceViewedUseCase
import com.google.samples.apps.nowinandroid.core.domain.usecase.ObserveFollowableTopicsUseCase
import com.google.samples.apps.nowinandroid.core.domain.usecase.ObserveFollowedNewsUseCase
import com.google.samples.apps.nowinandroid.core.domain.usecase.ObserveUserDataUseCase
import com.google.samples.apps.nowinandroid.core.domain.usecase.ObserveUserNewsUseCase
import com.google.samples.apps.nowinandroid.core.model.data.FollowableTopic
import com.google.samples.apps.nowinandroid.core.model.data.NewsResource
import com.google.samples.apps.nowinandroid.core.model.data.NewsResourceId
import com.google.samples.apps.nowinandroid.core.model.data.Topic
import com.google.samples.apps.nowinandroid.core.model.data.TopicId
import com.google.samples.apps.nowinandroid.core.model.data.UserNewsResource
import com.google.samples.apps.nowinandroid.core.model.data.mapToUserNewsResources
import com.google.samples.apps.nowinandroid.core.notifications.DEEP_LINK_NEWS_RESOURCE_ID_KEY
import com.google.samples.apps.nowinandroid.core.testing.repository.TestNewsRepository
import com.google.samples.apps.nowinandroid.core.testing.repository.TestTopicsRepository
import com.google.samples.apps.nowinandroid.core.testing.repository.TestUserDataRepository
import com.google.samples.apps.nowinandroid.core.testing.repository.emptyUserData
import com.google.samples.apps.nowinandroid.core.testing.util.MainDispatcherRule
import com.google.samples.apps.nowinandroid.core.testing.util.TestAnalyticsHelper
import com.google.samples.apps.nowinandroid.core.testing.util.TestSyncManager
import com.google.samples.apps.nowinandroid.core.ui.NewsFeedUiState
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * To learn more about how this test handles Flows created with stateIn, see
 * https://developer.android.com/kotlin/flow/test#statein
 */
class ForYouViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val syncManager = TestSyncManager()
    private val analyticsHelper = TestAnalyticsHelper()
    private val userDataRepository = TestUserDataRepository()
    private val topicsRepository = TestTopicsRepository()
    private val newsRepository = TestNewsRepository()
    private val userNewsResourceRepository = CompositeUserNewsResourceRepository(
        newsRepository = newsRepository,
        userDataRepository = userDataRepository,
    )

    private val getFollowableTopicsUseCase = ObserveFollowableTopicsUseCase(
        topicsRepository = topicsRepository,
        userDataRepository = userDataRepository,
    )

    private val savedStateHandle = SavedStateHandle()
    private lateinit var viewModel: ForYouViewModel

    @Before
    fun setup() {
        viewModel = ForYouViewModel(
            savedStateHandle = savedStateHandle,
            syncManager = syncManager,
            analyticsHelper = analyticsHelper,
            observeUserData = ObserveUserDataUseCase(userDataRepository),
            observeFollowableTopics = getFollowableTopicsUseCase,
            observeFollowedNews = ObserveFollowedNewsUseCase(userNewsResourceRepository),
            observeUserNews = ObserveUserNewsUseCase(userNewsResourceRepository),
            followTopic = FollowTopicUseCase(userDataRepository),
            bookmarkNewsResource = BookmarkNewsResourceUseCase(userDataRepository),
            markNewsResourceViewed = MarkNewsResourceViewedUseCase(userDataRepository),
            dismissOnboarding = DismissOnboardingUseCase(userDataRepository),
        )
    }

    @Test
    fun stateIsInitiallyLoading() = runTest {
        assertEquals(ForYouUiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun stateIsLoadingWhenFollowedTopicsAreLoading() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect() }

        topicsRepository.sendTopics(sampleTopics)

        val state = assertIs<ForYouUiState.Success>(viewModel.uiState.value)
        assertEquals(OnboardingUiState.Loading, state.onboarding)
        assertEquals(NewsFeedUiState.Loading, state.feed)
    }

    @Test
    fun stateIsLoadingWhenAppIsSyncingWithNoInterests() = runTest {
        syncManager.setSyncing(true)

        backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect() }

        val state = assertIs<ForYouUiState.Success>(viewModel.uiState.value)
        assertEquals(true, state.isSyncing)
    }

    @Test
    fun onboardingStateIsLoadingWhenTopicsAreLoading() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect() }

        userDataRepository.setFollowedTopicIds(emptySet())

        val state = assertIs<ForYouUiState.Success>(viewModel.uiState.value)
        assertEquals(OnboardingUiState.Loading, state.onboarding)
        assertEquals(NewsFeedUiState.Success(emptyList()), state.feed)
    }

    @Test
    fun onboardingIsShownWhenNewsResourcesAreLoading() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect() }

        topicsRepository.sendTopics(sampleTopics)
        userDataRepository.setFollowedTopicIds(emptySet())

        val state = assertIs<ForYouUiState.Success>(viewModel.uiState.value)
        assertEquals(
            OnboardingUiState.Shown(
                topics = listOf(
                    FollowableTopic(
                        topic = Topic(
                            id = TopicId("0"),
                            name = "Headlines",
                            shortDescription = "",
                            longDescription = "long description",
                            url = "URL",
                            imageUrl = "image URL",
                        ),
                        isFollowed = false,
                    ),
                    FollowableTopic(
                        topic = Topic(
                            id = TopicId("1"),
                            name = "UI",
                            shortDescription = "",
                            longDescription = "long description",
                            url = "URL",
                            imageUrl = "image URL",
                        ),
                        isFollowed = false,
                    ),
                    FollowableTopic(
                        topic = Topic(
                            id = TopicId("2"),
                            name = "Tools",
                            shortDescription = "",
                            longDescription = "long description",
                            url = "URL",
                            imageUrl = "image URL",
                        ),
                        isFollowed = false,
                    ),
                ),
            ),
            state.onboarding,
        )
        assertEquals(
            NewsFeedUiState.Success(
                feed = emptyList(),
            ),
            state.feed,
        )
    }

    @Test
    fun onboardingIsShownAfterLoadingEmptyFollowedTopics() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect() }

        topicsRepository.sendTopics(sampleTopics)
        userDataRepository.setFollowedTopicIds(emptySet())
        newsRepository.sendNewsResources(sampleNewsResources)

        val state = assertIs<ForYouUiState.Success>(viewModel.uiState.value)
        assertEquals(
            OnboardingUiState.Shown(
                topics = listOf(
                    FollowableTopic(
                        topic = Topic(
                            id = TopicId("0"),
                            name = "Headlines",
                            shortDescription = "",
                            longDescription = "long description",
                            url = "URL",
                            imageUrl = "image URL",
                        ),
                        isFollowed = false,
                    ),
                    FollowableTopic(
                        topic = Topic(
                            id = TopicId("1"),
                            name = "UI",
                            shortDescription = "",
                            longDescription = "long description",
                            url = "URL",
                            imageUrl = "image URL",
                        ),
                        isFollowed = false,
                    ),
                    FollowableTopic(
                        topic = Topic(
                            id = TopicId("2"),
                            name = "Tools",
                            shortDescription = "",
                            longDescription = "long description",
                            url = "URL",
                            imageUrl = "image URL",
                        ),
                        isFollowed = false,
                    ),
                ),
            ),
            state.onboarding,
        )
        assertEquals(
            NewsFeedUiState.Success(
                feed = emptyList(),
            ),
            state.feed,
        )
    }

    @Test
    fun onboardingIsNotShownAfterUserDismissesOnboarding() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect() }

        topicsRepository.sendTopics(sampleTopics)

        val followedTopicIds = setOf(TopicId("0"), TopicId("1"))
        val userData = emptyUserData.copy(followedTopics = followedTopicIds)
        userDataRepository.setUserData(userData)
        viewModel.onEvent(ForYouEvent.DismissOnboarding)

        var state = assertIs<ForYouUiState.Success>(viewModel.uiState.value)
        assertEquals(OnboardingUiState.NotShown, state.onboarding)
        assertEquals(NewsFeedUiState.Loading, state.feed)

        newsRepository.sendNewsResources(sampleNewsResources)

        state = assertIs<ForYouUiState.Success>(viewModel.uiState.value)
        assertEquals(OnboardingUiState.NotShown, state.onboarding)
        assertEquals(
            NewsFeedUiState.Success(
                feed = sampleNewsResources.mapToUserNewsResources(userData),
            ),
            state.feed,
        )
    }

    @Test
    fun topicSelectionUpdatesAfterSelectingTopic() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect() }

        topicsRepository.sendTopics(sampleTopics)
        userDataRepository.setFollowedTopicIds(emptySet())
        newsRepository.sendNewsResources(sampleNewsResources)

        var state = assertIs<ForYouUiState.Success>(viewModel.uiState.value)
        assertEquals(
            OnboardingUiState.Shown(
                topics = sampleTopics.map {
                    FollowableTopic(it, false)
                },
            ),
            state.onboarding,
        )
        assertEquals(
            NewsFeedUiState.Success(
                feed = emptyList(),
            ),
            state.feed,
        )

        val followedTopicId = sampleTopics[1].id
        viewModel.onEvent(ForYouEvent.UpdateTopicSelection(topicId = followedTopicId, followed = true))

        state = assertIs<ForYouUiState.Success>(viewModel.uiState.value)
        assertEquals(
            OnboardingUiState.Shown(
                topics = sampleTopics.map {
                    FollowableTopic(it, it.id == followedTopicId)
                },
            ),
            state.onboarding,
        )

        val userData = emptyUserData.copy(followedTopics = setOf(followedTopicId))

        assertEquals(
            NewsFeedUiState.Success(
                feed = listOf(
                    UserNewsResource(sampleNewsResources[1], userData),
                    UserNewsResource(sampleNewsResources[2], userData),
                ),
            ),
            state.feed,
        )
    }

    @Test
    fun topicSelectionUpdatesAfterUnselectingTopic() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect() }

        topicsRepository.sendTopics(sampleTopics)
        userDataRepository.setFollowedTopicIds(emptySet())
        newsRepository.sendNewsResources(sampleNewsResources)
        viewModel.onEvent(ForYouEvent.UpdateTopicSelection(topicId = TopicId("1"), followed = true))
        viewModel.onEvent(ForYouEvent.UpdateTopicSelection(topicId = TopicId("1"), followed = false))

        advanceUntilIdle()
        val state = assertIs<ForYouUiState.Success>(viewModel.uiState.value)
        assertEquals(
            OnboardingUiState.Shown(
                topics = listOf(
                    FollowableTopic(
                        topic = Topic(
                            id = TopicId("0"),
                            name = "Headlines",
                            shortDescription = "",
                            longDescription = "long description",
                            url = "URL",
                            imageUrl = "image URL",
                        ),
                        isFollowed = false,
                    ),
                    FollowableTopic(
                        topic = Topic(
                            id = TopicId("1"),
                            name = "UI",
                            shortDescription = "",
                            longDescription = "long description",
                            url = "URL",
                            imageUrl = "image URL",
                        ),
                        isFollowed = false,
                    ),
                    FollowableTopic(
                        topic = Topic(
                            id = TopicId("2"),
                            name = "Tools",
                            shortDescription = "",
                            longDescription = "long description",
                            url = "URL",
                            imageUrl = "image URL",
                        ),
                        isFollowed = false,
                    ),
                ),
            ),
            state.onboarding,
        )
        assertEquals(
            NewsFeedUiState.Success(
                feed = emptyList(),
            ),
            state.feed,
        )
    }

    @Test
    fun newsResourceSelectionUpdatesAfterLoadingFollowedTopics() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect() }

        val followedTopicIds = setOf(TopicId("1"))
        val userData = emptyUserData.copy(
            followedTopics = followedTopicIds,
            shouldHideOnboarding = true,
        )

        topicsRepository.sendTopics(sampleTopics)
        userDataRepository.setUserData(userData)
        newsRepository.sendNewsResources(sampleNewsResources)

        val bookmarkedNewsResourceId = "2"
        viewModel.onEvent(
            ForYouEvent.UpdateNewsResourceSaved(
                newsResourceId = NewsResourceId(bookmarkedNewsResourceId),
                bookmarked = true,
            ),
        )

        val userDataExpected = userData.copy(
            bookmarkedNewsResources = setOf(NewsResourceId(bookmarkedNewsResourceId)),
        )

        val state = assertIs<ForYouUiState.Success>(viewModel.uiState.value)
        assertEquals(OnboardingUiState.NotShown, state.onboarding)
        assertEquals(
            NewsFeedUiState.Success(
                feed = listOf(
                    UserNewsResource(newsResource = sampleNewsResources[1], userDataExpected),
                    UserNewsResource(newsResource = sampleNewsResources[2], userDataExpected),
                ),
            ),
            state.feed,
        )
    }

    @Test
    fun deepLinkedNewsResourceIsFetchedAndResetAfterViewing() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect() }

        newsRepository.sendNewsResources(sampleNewsResources)
        userDataRepository.setUserData(emptyUserData)
        savedStateHandle[DEEP_LINK_NEWS_RESOURCE_ID_KEY] = sampleNewsResources.first().id.value

        assertEquals(
            expected = UserNewsResource(
                newsResource = sampleNewsResources.first(),
                userData = emptyUserData,
            ),
            actual = assertIs<ForYouUiState.Success>(viewModel.uiState.value).deepLinkedUserNewsResource,
        )

        viewModel.onEvent(
            ForYouEvent.DeepLinkOpened(newsResourceId = sampleNewsResources.first().id),
        )

        assertNull(
            assertIs<ForYouUiState.Success>(viewModel.uiState.value).deepLinkedUserNewsResource,
        )

        assertTrue(
            analyticsHelper.hasLogged(
                AnalyticsEvent(
                    type = "news_deep_link_opened",
                    extras = listOf(
                        Param(
                            key = DEEP_LINK_NEWS_RESOURCE_ID_KEY,
                            value = sampleNewsResources.first().id.value,
                        ),
                    ),
                ),
            ),
        )
    }

    @Test
    fun whenUpdateNewsResourceSavedIsCalled_bookmarkStateIsUpdated() = runTest {
        val newsResourceId = NewsResourceId("123")
        viewModel.onEvent(ForYouEvent.UpdateNewsResourceSaved(newsResourceId, true))

        assertEquals(
            expected = setOf(newsResourceId),
            actual = userDataRepository.userData.first().bookmarkedNewsResources,
        )

        viewModel.onEvent(ForYouEvent.UpdateNewsResourceSaved(newsResourceId, false))

        assertEquals(
            expected = emptySet(),
            actual = userDataRepository.userData.first().bookmarkedNewsResources,
        )
    }
}

private val sampleTopics = listOf(
    Topic(
        id = TopicId("0"),
        name = "Headlines",
        shortDescription = "",
        longDescription = "long description",
        url = "URL",
        imageUrl = "image URL",
    ),
    Topic(
        id = TopicId("1"),
        name = "UI",
        shortDescription = "",
        longDescription = "long description",
        url = "URL",
        imageUrl = "image URL",
    ),
    Topic(
        id = TopicId("2"),
        name = "Tools",
        shortDescription = "",
        longDescription = "long description",
        url = "URL",
        imageUrl = "image URL",
    ),
)

private val sampleNewsResources = listOf(
    NewsResource(
        id = NewsResourceId("1"),
        title = "Thanks for helping us reach 1M YouTube Subscribers",
        content = "Thank you everyone for following the Now in Android series and everything the " +
            "Android Developers YouTube channel has to offer. During the Android Developer " +
            "Summit, our YouTube channel reached 1 million subscribers! Here’s a small video to " +
            "thank you all.",
        url = "https://youtu.be/-fJ6poHQrjM",
        headerImageUrl = "https://i.ytimg.com/vi/-fJ6poHQrjM/maxresdefault.jpg",
        publishDate = Instant.parse("2021-11-09T00:00:00.000Z"),
        type = "Video 📺",
        topics = listOf(
            Topic(
                id = TopicId("0"),
                name = "Headlines",
                shortDescription = "",
                longDescription = "long description",
                url = "URL",
                imageUrl = "image URL",
            ),
        ),
    ),
    NewsResource(
        id = NewsResourceId("2"),
        title = "Transformations and customisations in the Paging Library",
        content = "A demonstration of different operations that can be performed with Paging. " +
            "Transformations like inserting separators, when to create a new pager, and " +
            "customisation options for consuming PagingData.",
        url = "https://youtu.be/ZARz0pjm5YM",
        headerImageUrl = "https://i.ytimg.com/vi/ZARz0pjm5YM/maxresdefault.jpg",
        publishDate = Instant.parse("2021-11-01T00:00:00.000Z"),
        type = "Video 📺",
        topics = listOf(
            Topic(
                id = TopicId("1"),
                name = "UI",
                shortDescription = "",
                longDescription = "long description",
                url = "URL",
                imageUrl = "image URL",
            ),
        ),
    ),
    NewsResource(
        id = NewsResourceId("3"),
        title = "Community tip on Paging",
        content = "Tips for using the Paging library from the developer community",
        url = "https://youtu.be/r5JgIyS3t3s",
        headerImageUrl = "https://i.ytimg.com/vi/r5JgIyS3t3s/maxresdefault.jpg",
        publishDate = Instant.parse("2021-11-08T00:00:00.000Z"),
        type = "Video 📺",
        topics = listOf(
            Topic(
                id = TopicId("1"),
                name = "UI",
                shortDescription = "",
                longDescription = "long description",
                url = "URL",
                imageUrl = "image URL",
            ),
        ),
    ),
)
