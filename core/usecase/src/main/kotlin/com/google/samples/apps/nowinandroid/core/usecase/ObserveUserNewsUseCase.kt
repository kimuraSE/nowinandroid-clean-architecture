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

import com.google.samples.apps.nowinandroid.core.model.data.UserNewsResource
import com.google.samples.apps.nowinandroid.core.usecase.repository.NewsResourceQuery
import com.google.samples.apps.nowinandroid.core.usecase.repository.UserNewsResourceRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * 任意のクエリ条件でニュースを、ユーザー状態付きで観察する
 * （オンボーディング中の全件表示やディープリンクの単一件表示などに使う）。
 */
class ObserveUserNewsUseCase @Inject constructor(
    private val userNewsResourceRepository: UserNewsResourceRepository,
) {
    operator fun invoke(query: NewsResourceQuery): Flow<List<UserNewsResource>> =
        userNewsResourceRepository.observeAll(query)
}
