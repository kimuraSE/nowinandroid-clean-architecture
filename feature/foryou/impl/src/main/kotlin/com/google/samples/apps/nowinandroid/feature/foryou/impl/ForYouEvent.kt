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

package com.google.samples.apps.nowinandroid.feature.foryou.impl

import com.google.samples.apps.nowinandroid.core.model.data.NewsResourceId
import com.google.samples.apps.nowinandroid.core.model.data.TopicId

/**
 * For You 画面のユーザー操作を表す単一のイベント型（UDF）。
 */
sealed interface ForYouEvent {
    /** オンボーディングでトピックのフォロー状態を変更する。 */
    data class UpdateTopicSelection(val topicId: TopicId, val followed: Boolean) : ForYouEvent

    /** ニュースのブックマーク状態を変更する。 */
    data class UpdateNewsResourceSaved(
        val newsResourceId: NewsResourceId,
        val bookmarked: Boolean,
    ) : ForYouEvent

    /** ニュースを既読にする。 */
    data class MarkNewsViewed(val newsResourceId: NewsResourceId) : ForYouEvent

    /** オンボーディングを閉じる（以後表示しない）。 */
    data object DismissOnboarding : ForYouEvent

    /** ディープリンクで開いたニュースを処理する（既読化・状態クリア・解析ログ）。 */
    data class DeepLinkOpened(val newsResourceId: NewsResourceId) : ForYouEvent
}
