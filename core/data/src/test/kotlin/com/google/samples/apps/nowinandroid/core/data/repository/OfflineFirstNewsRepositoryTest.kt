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

package com.google.samples.apps.nowinandroid.core.data.repository

import com.google.samples.apps.nowinandroid.core.data.Synchronizer
import com.google.samples.apps.nowinandroid.core.data.model.asEntity
import com.google.samples.apps.nowinandroid.core.data.model.topicCrossReferences
import com.google.samples.apps.nowinandroid.core.data.model.topicEntityShells
import com.google.samples.apps.nowinandroid.core.data.testdoubles.CollectionType
import com.google.samples.apps.nowinandroid.core.data.testdoubles.TestNewsResourceDao
import com.google.samples.apps.nowinandroid.core.data.testdoubles.TestNiaNetworkDataSource
import com.google.samples.apps.nowinandroid.core.data.testdoubles.TestTopicDao
import com.google.samples.apps.nowinandroid.core.data.testdoubles.filteredInterestsIds
import com.google.samples.apps.nowinandroid.core.data.testdoubles.nonPresentInterestsIds
import com.google.samples.apps.nowinandroid.core.database.model.NewsResourceEntity
import com.google.samples.apps.nowinandroid.core.database.model.NewsResourceTopicCrossRef
import com.google.samples.apps.nowinandroid.core.database.model.PopulatedNewsResource
import com.google.samples.apps.nowinandroid.core.database.model.TopicEntity
import com.google.samples.apps.nowinandroid.core.database.model.asExternalModel
import com.google.samples.apps.nowinandroid.core.datastore.NiaPreferencesDataSource
import com.google.samples.apps.nowinandroid.core.datastore.UserPreferences
import com.google.samples.apps.nowinandroid.core.datastore.test.InMemoryDataStore
import com.google.samples.apps.nowinandroid.core.domain.repository.NewsResourceQuery
import com.google.samples.apps.nowinandroid.core.model.data.NewsResource
import com.google.samples.apps.nowinandroid.core.model.data.NewsResourceId
import com.google.samples.apps.nowinandroid.core.model.data.Topic
import com.google.samples.apps.nowinandroid.core.model.data.TopicId
import com.google.samples.apps.nowinandroid.core.network.model.NetworkChangeList
import com.google.samples.apps.nowinandroid.core.network.model.NetworkNewsResource
import com.google.samples.apps.nowinandroid.core.testing.notifications.TestNotifier
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OfflineFirstNewsRepositoryTest {

    private val testScope = TestScope(UnconfinedTestDispatcher())

    private lateinit var subject: OfflineFirstNewsRepository

    private lateinit var niaPreferencesDataSource: NiaPreferencesDataSource

    private lateinit var newsResourceDao: TestNewsResourceDao

    private lateinit var topicDao: TestTopicDao

    private lateinit var network: TestNiaNetworkDataSource

    private lateinit var notifier: TestNotifier

    private lateinit var synchronizer: Synchronizer

    @Before
    fun setup() {
        niaPreferencesDataSource = NiaPreferencesDataSource(InMemoryDataStore(UserPreferences.getDefaultInstance()))
        newsResourceDao = TestNewsResourceDao()
        topicDao = TestTopicDao()
        network = TestNiaNetworkDataSource()
        notifier = TestNotifier()
        synchronizer = TestSynchronizer(
            niaPreferencesDataSource,
        )

        subject = OfflineFirstNewsRepository(
            niaPreferencesDataSource = niaPreferencesDataSource,
            newsResourceDao = newsResourceDao,
            topicDao = topicDao,
            network = network,
            notifier = notifier,
        )
    }

    @Test
    fun offlineFirstNewsRepository_news_resources_stream_is_backed_by_news_resource_dao() =
        testScope.runTest {
            subject.syncWith(synchronizer)
            assertEquals(
                newsResourceDao.getNewsResources()
                    .first()
                    .map(PopulatedNewsResource::asExternalModel),
                subject.getNewsResources()
                    .first(),
            )
        }

    @Test
    fun offlineFirstNewsRepository_news_resources_for_topic_is_backed_by_news_resource_dao() =
        testScope.runTest {
            assertEquals(
                expected = newsResourceDao.getNewsResources(
                    filterTopicIds = filteredInterestsIds,
                    useFilterTopicIds = true,
                )
                    .first()
                    .map(PopulatedNewsResource::asExternalModel),
                actual = subject.getNewsResources(
                    query = NewsResourceQuery(
                        filterTopicIds = filteredInterestsIds.mapTo(mutableSetOf(), ::TopicId),
                    ),
                )
                    .first(),
            )

            assertEquals(
                expected = emptyList(),
                actual = subject.getNewsResources(
                    query = NewsResourceQuery(
                        filterTopicIds = nonPresentInterestsIds.mapTo(mutableSetOf(), ::TopicId),
                    ),
                )
                    .first(),
            )
        }

    @Test
    fun offlineFirstNewsRepository_sync_pulls_from_network() =
        testScope.runTest {
            // User has not onboarded
            niaPreferencesDataSource.setShouldHideOnboarding(false)
            subject.syncWith(synchronizer)

            val newsResourcesFromNetwork = network.getNewsResources()
                .map(NetworkNewsResource::asEntity)
                .map(NewsResourceEntity::asExternalModel)

            val newsResourcesFromDb = newsResourceDao.getNewsResources()
                .first()
                .map(PopulatedNewsResource::asExternalModel)

            assertEquals(
                newsResourcesFromNetwork.map { it.id.value }.sorted(),
                newsResourcesFromDb.map { it.id.value }.sorted(),
            )

            // After sync version should be updated
            assertEquals(
                expected = network.latestChangeListVersion(CollectionType.NewsResources),
                actual = synchronizer.getChangeListVersions().newsResourceVersion,
            )

            // Notifier should not have been called
            assertTrue(notifier.addedNewsResources.isEmpty())
        }

    @Test
    fun offlineFirstNewsRepository_sync_deletes_items_marked_deleted_on_network() =
        testScope.runTest {
            // User has not onboarded
            niaPreferencesDataSource.setShouldHideOnboarding(false)

            val newsResourcesFromNetwork = network.getNewsResources()
                .map(NetworkNewsResource::asEntity)
                .map(NewsResourceEntity::asExternalModel)

            // Delete half of the items on the network
            val deletedItems = newsResourcesFromNetwork
                .map(NewsResource::id)
                .partition { it.value.chars().sum() % 2 == 0 }
                .first
                .toSet()

            deletedItems.forEach {
                network.editCollection(
                    collectionType = CollectionType.NewsResources,
                    id = it.value,
                    isDelete = true,
                )
            }

            subject.syncWith(synchronizer)

            val newsResourcesFromDb = newsResourceDao.getNewsResources()
                .first()
                .map(PopulatedNewsResource::asExternalModel)

            // Assert that items marked deleted on the network have been deleted locally
            assertEquals(
                expected = (newsResourcesFromNetwork.map(NewsResource::id) - deletedItems)
                    .map(NewsResourceId::value).sorted(),
                actual = newsResourcesFromDb.map { it.id.value }.sorted(),
            )

            // After sync version should be updated
            assertEquals(
                expected = network.latestChangeListVersion(CollectionType.NewsResources),
                actual = synchronizer.getChangeListVersions().newsResourceVersion,
            )

            // Notifier should not have been called
            assertTrue(notifier.addedNewsResources.isEmpty())
        }

    @Test
    fun offlineFirstNewsRepository_incremental_sync_pulls_from_network() =
        testScope.runTest {
            // User has not onboarded
            niaPreferencesDataSource.setShouldHideOnboarding(false)

            // Set news version to 7
            synchronizer.updateChangeListVersions {
                copy(newsResourceVersion = 7)
            }

            subject.syncWith(synchronizer)

            val changeList = network.changeListsAfter(
                CollectionType.NewsResources,
                version = 7,
            )
            val changeListIds = changeList
                .map(NetworkChangeList::id)
                .toSet()

            val newsResourcesFromNetwork = network.getNewsResources()
                .map(NetworkNewsResource::asEntity)
                .map(NewsResourceEntity::asExternalModel)
                .filter { it.id.value in changeListIds }

            val newsResourcesFromDb = newsResourceDao.getNewsResources()
                .first()
                .map(PopulatedNewsResource::asExternalModel)

            assertEquals(
                expected = newsResourcesFromNetwork.map { it.id.value }.sorted(),
                actual = newsResourcesFromDb.map { it.id.value }.sorted(),
            )

            // After sync version should be updated
            assertEquals(
                expected = changeList.last().changeListVersion,
                actual = synchronizer.getChangeListVersions().newsResourceVersion,
            )

            // Notifier should not have been called
            assertTrue(notifier.addedNewsResources.isEmpty())
        }

    @Test
    fun offlineFirstNewsRepository_sync_saves_shell_topic_entities() =
        testScope.runTest {
            subject.syncWith(synchronizer)

            assertEquals(
                expected = network.getNewsResources()
                    .map(NetworkNewsResource::topicEntityShells)
                    .flatten()
                    .distinctBy(TopicEntity::id)
                    .sortedBy(TopicEntity::toString),
                actual = topicDao.getTopicEntities()
                    .first()
                    .sortedBy(TopicEntity::toString),
            )
        }

    @Test
    fun offlineFirstNewsRepository_sync_saves_topic_cross_references() =
        testScope.runTest {
            subject.syncWith(synchronizer)

            assertEquals(
                expected = network.getNewsResources()
                    .map(NetworkNewsResource::topicCrossReferences)
                    .flatten()
                    .distinct()
                    .sortedBy(NewsResourceTopicCrossRef::toString),
                actual = newsResourceDao.topicCrossReferences
                    .sortedBy(NewsResourceTopicCrossRef::toString),
            )
        }

    @Test
    fun offlineFirstNewsRepository_sync_marks_as_read_on_first_run() =
        testScope.runTest {
            subject.syncWith(synchronizer)

            assertEquals(
                network.getNewsResources().map { NewsResourceId(it.id) }.toSet(),
                niaPreferencesDataSource.userData.first().viewedNewsResources,
            )
        }

    @Test
    fun offlineFirstNewsRepository_sync_does_not_mark_as_read_on_subsequent_run() =
        testScope.runTest {
            // Pretend that we already have up to change list 7
            synchronizer.updateChangeListVersions {
                copy(newsResourceVersion = 7)
            }

            subject.syncWith(synchronizer)

            assertEquals(
                emptySet(),
                niaPreferencesDataSource.userData.first().viewedNewsResources,
            )
        }

    @Test
    fun offlineFirstNewsRepository_sends_notifications_for_newly_synced_news_that_is_followed() =
        testScope.runTest {
            // User has onboarded
            niaPreferencesDataSource.setShouldHideOnboarding(true)

            val networkNewsResources = network.getNewsResources()

            // Follow roughly half the topics
            val followedTopicIds = networkNewsResources
                .flatMap(NetworkNewsResource::topicEntityShells)
                .mapNotNull { topic ->
                    when (topic.id.chars().sum() % 2) {
                        0 -> TopicId(topic.id)
                        else -> null
                    }
                }
                .toSet()

            // Set followed topics
            niaPreferencesDataSource.setFollowedTopicIds(followedTopicIds)

            subject.syncWith(synchronizer)

            val followedTopicIdValues = followedTopicIds.mapTo(mutableSetOf(), TopicId::value)
            val followedNewsResourceIdsFromNetwork = networkNewsResources
                .filter { (it.topics intersect followedTopicIdValues).isNotEmpty() }
                .map(NetworkNewsResource::id)
                .sorted()

            // Notifier should have been called with only news resources that have topics
            // that the user follows
            assertEquals(
                expected = followedNewsResourceIdsFromNetwork,
                actual = notifier.addedNewsResources.first().map { it.id.value }.sorted(),
            )
        }

    @Test
    fun offlineFirstNewsRepository_does_not_send_notifications_for_existing_news_resources() =
        testScope.runTest {
            // User has onboarded
            niaPreferencesDataSource.setShouldHideOnboarding(true)

            val networkNewsResources = network.getNewsResources()
                .map(NetworkNewsResource::asEntity)

            val newsResources = networkNewsResources
                .map(NewsResourceEntity::asExternalModel)

            // Prepopulate dao with news resources
            newsResourceDao.upsertNewsResources(networkNewsResources)

            val followedTopicIds = newsResources
                .flatMap(NewsResource::topics)
                .map(Topic::id)
                .toSet()

            // Follow all topics
            niaPreferencesDataSource.setFollowedTopicIds(followedTopicIds)

            subject.syncWith(synchronizer)

            // Notifier should not have been called bc all news resources existed previously
            assertTrue(notifier.addedNewsResources.isEmpty())
        }
}
