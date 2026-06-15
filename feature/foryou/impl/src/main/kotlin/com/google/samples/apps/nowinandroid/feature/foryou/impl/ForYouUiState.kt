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

import com.google.samples.apps.nowinandroid.core.model.data.UserNewsResource
import com.google.samples.apps.nowinandroid.core.ui.NewsFeedUiState

/**
 * For You 画面の単一 UiState（UDF）。オンボーディング・フィード・同期状態・ディープリンクを
 * まとめて保持する。各セクションは独立してロードされるため、[Success] 内のサブ状態
 * （[OnboardingUiState] / [NewsFeedUiState]）がそれぞれの読み込み状態を表す。
 */
sealed interface ForYouUiState {
    data object Loading : ForYouUiState

    data class Success(
        val isSyncing: Boolean,
        val onboarding: OnboardingUiState,
        val feed: NewsFeedUiState,
        val deepLinkedUserNewsResource: UserNewsResource?,
    ) : ForYouUiState
}
