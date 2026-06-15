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

package com.google.samples.apps.nowinandroid.feature.settings.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.samples.apps.nowinandroid.core.domain.usecase.ObserveUserDataUseCase
import com.google.samples.apps.nowinandroid.core.domain.usecase.SetDarkThemeConfigUseCase
import com.google.samples.apps.nowinandroid.core.domain.usecase.SetDynamicColorPreferenceUseCase
import com.google.samples.apps.nowinandroid.core.domain.usecase.SetThemeBrandUseCase
import com.google.samples.apps.nowinandroid.core.model.data.UserData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

@HiltViewModel
class SettingsViewModel @Inject constructor(
    observeUserData: ObserveUserDataUseCase,
    private val setThemeBrand: SetThemeBrandUseCase,
    private val setDarkThemeConfig: SetDarkThemeConfigUseCase,
    private val setDynamicColorPreference: SetDynamicColorPreferenceUseCase,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> =
        observeUserData()
            .map<UserData, SettingsUiState> { userData ->
                SettingsUiState.Success(
                    settings = UserEditableSettings(
                        brand = userData.themeBrand,
                        useDynamicColor = userData.useDynamicColor,
                        darkThemeConfig = userData.darkThemeConfig,
                    ),
                )
            }
            .catch { emit(SettingsUiState.Error) }
            .stateIn(
                scope = viewModelScope,
                started = WhileSubscribed(5.seconds.inWholeMilliseconds),
                initialValue = SettingsUiState.Loading,
            )

    fun onEvent(event: SettingsEvent) {
        when (event) {
            is SettingsEvent.ChangeThemeBrand ->
                viewModelScope.launch { setThemeBrand(event.themeBrand) }

            is SettingsEvent.ChangeDarkThemeConfig ->
                viewModelScope.launch { setDarkThemeConfig(event.darkThemeConfig) }

            is SettingsEvent.ChangeDynamicColorPreference ->
                viewModelScope.launch { setDynamicColorPreference(event.useDynamicColor) }
        }
    }
}
