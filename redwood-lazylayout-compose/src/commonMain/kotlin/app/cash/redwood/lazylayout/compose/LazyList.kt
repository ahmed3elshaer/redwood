/*
 * Copyright (C) 2022 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
@file:JvmName("LazyList") // Conflicts with generated LazyList compose widget

package app.cash.redwood.lazylayout.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import app.cash.redwood.Modifier
import app.cash.redwood.layout.api.Constraint
import app.cash.redwood.layout.api.CrossAxisAlignment
import app.cash.redwood.ui.Margin
import kotlin.jvm.JvmName

@Composable
internal fun LazyList(
  isVertical: Boolean,
  state: LazyListState,
  width: Constraint,
  height: Constraint,
  margin: Margin,
  crossAxisAlignment: CrossAxisAlignment,
  placeholder: @Composable () -> Unit,
  modifier: Modifier = Modifier,
  content: LazyListScope.() -> Unit,
) {
  val itemProvider = rememberLazyListItemProvider(content)
  val itemCount = itemProvider.itemCount
  val loadRange = state.loadRange(itemCount)
  val placeholderPoolSize = 20
  LazyList(
    isVertical = isVertical,
    onViewportChanged = { localFirstVisibleItemIndex, localLastVisibleItemIndex ->
      state.onUserScroll(localFirstVisibleItemIndex, localLastVisibleItemIndex)
    },
    itemsBefore = loadRange.first,
    itemsAfter = itemCount - loadRange.count() - loadRange.first,
    width = width,
    height = height,
    margin = margin,
    crossAxisAlignment = crossAxisAlignment,
    modifier = modifier,
    scrollItemIndex = state.programmaticScrollIndex,
    placeholder = { repeat(placeholderPoolSize) { placeholder() } },
    items = {
      for (index in loadRange) {
        key(index) {
          itemProvider.Item(index)
        }
      }
    },
  )
}

@Composable
internal fun RefreshableLazyList(
  isVertical: Boolean,
  refreshing: Boolean,
  onRefresh: (() -> Unit)?,
  state: LazyListState,
  width: Constraint,
  height: Constraint,
  margin: Margin,
  crossAxisAlignment: CrossAxisAlignment,
  pullRefreshContentColor: UInt,
  placeholder: @Composable () -> Unit,
  modifier: Modifier = Modifier,
  content: LazyListScope.() -> Unit,
) {
  val itemProvider = rememberLazyListItemProvider(content)
  val itemCount = itemProvider.itemCount
  val loadRange = state.loadRange(itemCount)
  val placeholderPoolSize = 20
  RefreshableLazyList(
    isVertical,
    itemsBefore = loadRange.first,
    itemsAfter = itemCount - loadRange.count() - loadRange.first,
    onViewportChanged = { localFirstVisibleItemIndex, localLastVisibleItemIndex ->
      state.onUserScroll(localFirstVisibleItemIndex, localLastVisibleItemIndex)
    },
    refreshing = refreshing,
    onRefresh = onRefresh,
    width = width,
    height = height,
    margin = margin,
    crossAxisAlignment = crossAxisAlignment,
    modifier = modifier,
    scrollItemIndex = state.programmaticScrollIndex,
    placeholder = { repeat(placeholderPoolSize) { placeholder() } },
    pullRefreshContentColor = pullRefreshContentColor,
    items = {
      for (index in loadRange) {
        key(index) {
          itemProvider.Item(index)
        }
      }
    },
  )
}
