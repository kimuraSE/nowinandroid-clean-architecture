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

package com.google.samples.apps.nowinandroid.feature.bookmarks.impl

import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.samples.apps.nowinandroid.core.designsystem.component.NiaLoadingWheel
import com.google.samples.apps.nowinandroid.core.designsystem.component.scrollbar.DraggableScrollbar
import com.google.samples.apps.nowinandroid.core.designsystem.component.scrollbar.rememberDraggableScroller
import com.google.samples.apps.nowinandroid.core.designsystem.component.scrollbar.scrollbarState
import com.google.samples.apps.nowinandroid.core.designsystem.theme.LocalTintTheme
import com.google.samples.apps.nowinandroid.core.designsystem.theme.NiaTheme
import com.google.samples.apps.nowinandroid.core.model.data.NewsResourceId
import com.google.samples.apps.nowinandroid.core.model.data.UserNewsResource
import com.google.samples.apps.nowinandroid.core.ui.NewsFeedUiState
import com.google.samples.apps.nowinandroid.core.ui.TrackScreenViewEvent
import com.google.samples.apps.nowinandroid.core.ui.TrackScrollJank
import com.google.samples.apps.nowinandroid.core.ui.UserNewsResourcePreviewParameterProvider
import com.google.samples.apps.nowinandroid.core.ui.newsFeed
import com.google.samples.apps.nowinandroid.feature.bookmarks.api.R

@Composable
internal fun BookmarksScreen(
    onTopicClick: (String) -> Unit,
    onShowSnackbar: suspend (String, String?) -> Boolean,
    modifier: Modifier = Modifier,
    viewModel: BookmarksViewModel = hiltViewModel(),
) {
    // ViewModel の uiState（StateFlow）を購読し、最新値を Compose の State として受け取る（前面表示中のみ購読）
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    BookmarksScreen(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        onShowSnackbar = onShowSnackbar,
        onTopicClick = onTopicClick,
        modifier = modifier,
    )
}

/**
 * Displays the user's bookmarked articles. Includes support for loading and empty states.
 */
@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
@Composable
internal fun BookmarksScreen(
    uiState: BookmarksUiState,
    onEvent: (BookmarksEvent) -> Unit,
    onShowSnackbar: suspend (String, String?) -> Boolean,
    onTopicClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val bookmarkRemovedMessage = stringResource(id = R.string.feature_bookmarks_api_removed)
    val undoText = stringResource(id = R.string.feature_bookmarks_api_undo)

    //取り消しSnackBarを出すべきか(Successかつフラグがtrueのときのみ)
    val shouldDisplayUndoBookmark =
        (uiState as? BookmarksUiState.Success)?.shouldShowUndoBookmark == true

    LaunchedEffect(shouldDisplayUndoBookmark) {
        if (shouldDisplayUndoBookmark) {
            // 削除Snackbarを表示し、「取り消し」押下なら復活イベント、そうでなければフラグ解除イベントを送る
            val snackBarResult = onShowSnackbar(bookmarkRemovedMessage, undoText)
            if (snackBarResult) {
                onEvent(BookmarksEvent.UndoBookmarkRemoval)
            } else {
                onEvent(BookmarksEvent.ClearUndoState)
            }
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        onEvent(BookmarksEvent.ClearUndoState)
    }

    when (uiState) {
        BookmarksUiState.Loading -> LoadingState(modifier)
        BookmarksUiState.Error -> EmptyState(modifier)
        is BookmarksUiState.Success -> if (uiState.feed.isNotEmpty()) {
            BookmarksGrid(
                feed = uiState.feed,
                onEvent = onEvent,
                onTopicClick = onTopicClick,
                modifier = modifier,
            )
        } else {
            EmptyState(modifier)
        }
    }

    TrackScreenViewEvent(screenName = "Saved")
}

@Composable
private fun LoadingState(modifier: Modifier = Modifier) {
    NiaLoadingWheel(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentSize()
            .testTag("bookmarks:loading"),
        contentDesc = stringResource(id = R.string.feature_bookmarks_api_loading),
    )
}

@Composable
private fun BookmarksGrid(
    feed: List<UserNewsResource>,
    onEvent: (BookmarksEvent) -> Unit,
    onTopicClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollableState = rememberLazyStaggeredGridState()
    TrackScrollJank(scrollableState = scrollableState, stateName = "bookmarks:grid")
    Box(
        modifier = modifier
            .fillMaxSize(),
    ) {
        // 画面外＝未コンポーズ。入ってくる直前（少し先読み）でコンポーズして描画、出ていったら破棄。再入場時はまた構築。
        // ただし表示中のものは毎フレーム作り直すわけではない（state 変化時のみ再コンポーズ）。
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Adaptive(300.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalItemSpacing = 24.dp,
            state = scrollableState,
            modifier = Modifier
                .fillMaxSize()
                .testTag("bookmarks:feed"),
        ) {
            newsFeed(
                feedState = NewsFeedUiState.Success(feed),
                // この id は「このラムダが決める値」ではなく、「ラムダを“呼ぶ側”が渡してくる引数」つまりnewsFeedが鍵
                onNewsResourcesCheckedChanged = { id, _ ->
                    onEvent(BookmarksEvent.RemoveBookmark(NewsResourceId(id)))
                },
                onNewsResourceViewed = { onEvent(BookmarksEvent.MarkAsViewed(NewsResourceId(it))) },
                onTopicClick = onTopicClick,
            )
            item(span = StaggeredGridItemSpan.FullLine) {
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.safeDrawing))
            }
        }
        val itemsAvailable = feed.size
        val scrollbarState = scrollableState.scrollbarState(
            itemsAvailable = itemsAvailable,
        )
        scrollableState.DraggableScrollbar(
            modifier = Modifier
                .fillMaxHeight()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 2.dp)
                .align(Alignment.CenterEnd),
            state = scrollbarState,
            orientation = Orientation.Vertical,
            onThumbMoved = scrollableState.rememberDraggableScroller(
                itemsAvailable = itemsAvailable,
            ),
        )
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .padding(16.dp)
            .fillMaxSize()
            .testTag("bookmarks:empty"),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val iconTint = LocalTintTheme.current.iconTint
        Image(
            modifier = Modifier.fillMaxWidth(),
            painter = painterResource(id = R.drawable.feature_bookmarks_api_mg_empty_bookmarks),
            colorFilter = if (iconTint != Color.Unspecified) ColorFilter.tint(iconTint) else null,
            contentDescription = null,
        )

        Spacer(modifier = Modifier.height(48.dp))

        Text(
            text = stringResource(id = R.string.feature_bookmarks_api_empty_error),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(id = R.string.feature_bookmarks_api_empty_description),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Preview
@Composable
private fun LoadingStatePreview() {
    NiaTheme {
        LoadingState()
    }
}

@Preview
@Composable
private fun BookmarksGridPreview(
    @PreviewParameter(UserNewsResourcePreviewParameterProvider::class)
    userNewsResources: List<UserNewsResource>,
) {
    NiaTheme {
        BookmarksGrid(
            feed = userNewsResources,
            onEvent = {},
            onTopicClick = {},
        )
    }
}

@Preview
@Composable
private fun EmptyStatePreview() {
    NiaTheme {
        EmptyState()
    }
}
