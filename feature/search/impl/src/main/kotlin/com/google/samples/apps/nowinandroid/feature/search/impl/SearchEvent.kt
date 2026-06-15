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

package com.google.samples.apps.nowinandroid.feature.search.impl

import com.google.samples.apps.nowinandroid.core.model.data.NewsResourceId
import com.google.samples.apps.nowinandroid.core.model.data.TopicId

/**
 * 検索画面のユーザー操作を表す単一のイベント型（UDF）。
 */
sealed interface SearchEvent {
    /** 検索ボックスの入力が変わった。 */
    data class QueryChanged(val query: String) : SearchEvent

    /** 検索を明示的に実行した（IME 検索キー等）。最近の検索として保存する。 */
    data class SearchTriggered(val query: String) : SearchEvent

    /** 最近の検索履歴を全消去する。 */
    data object ClearRecentSearches : SearchEvent

    /** トピックのフォロー状態を変更する。 */
    data class FollowTopic(val topicId: TopicId, val followed: Boolean) : SearchEvent

    /** ニュースのブックマーク状態を変更する。 */
    data class BookmarkNews(val id: NewsResourceId, val bookmarked: Boolean) : SearchEvent

    /** ニュースを既読にする。 */
    data class MarkNewsViewed(val id: NewsResourceId) : SearchEvent
}
