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

import com.google.samples.apps.nowinandroid.core.model.data.FollowableTopic
import com.google.samples.apps.nowinandroid.core.model.data.UserNewsResource

/**
 * 検索画面の単一 UiState（UDF）。入力中クエリ・検索結果・最近の検索をまとめて保持する。
 */
sealed interface SearchUiState {
    data object Loading : SearchUiState

    /** 検索コンテンツの取得に失敗した状態（旧 LoadFailed）。 */
    data object Error : SearchUiState

    data class Success(
        val searchQuery: String,
        val result: SearchResultState,
        val recentQueries: List<String>,
    ) : SearchUiState
}

/**
 * 検索結果セクションの内部状態。
 */
sealed interface SearchResultState {
    /** 検索用 FTS テーブルがまだ準備できていない状態。 */
    data object NotReady : SearchResultState

    /** クエリが未入力・短すぎる状態（最近の検索のみ表示）。 */
    data object EmptyQuery : SearchResultState

    /** 検索結果。topics と newsResources がともに空なら「該当なし」を表す。 */
    data class Results(
        val topics: List<FollowableTopic>,
        val newsResources: List<UserNewsResource>,
    ) : SearchResultState {
        fun isEmpty(): Boolean = topics.isEmpty() && newsResources.isEmpty()
    }
}
