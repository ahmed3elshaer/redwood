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
package app.cash.redwood.layout.composeui

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.cash.redwood.layout.AbstractFlexContainerTest
import app.cash.redwood.layout.TestFlexContainer
import app.cash.redwood.layout.widget.FlexContainer
import app.cash.redwood.widget.Widget
import app.cash.redwood.widget.compose.ComposeWidgetChildren
import app.cash.redwood.yoga.FlexDirection
import com.android.resources.LayoutDirection
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import org.junit.Rule
import org.junit.runner.RunWith

@RunWith(TestParameterInjector::class)
class ComposeUiFlexContainerTest(
  @TestParameter layoutDirection: LayoutDirection,
) : AbstractFlexContainerTest<@Composable () -> Unit>() {

  @get:Rule
  val paparazzi = Paparazzi(
    deviceConfig = DeviceConfig.PIXEL_6.copy(layoutDirection = layoutDirection),
    theme = "android:Theme.Material.Light.NoActionBar",
    supportsRtl = true,
  )

  override fun flexContainer(
    direction: FlexDirection,
    backgroundColor: Int,
  ): ComposeTestFlexContainer {
    return ComposeTestFlexContainer(direction, backgroundColor)
  }

  override fun row() = flexContainer(FlexDirection.Row)

  override fun column() = flexContainer(FlexDirection.Column)

  override fun text() = ComposeUiText()

  override fun verifySnapshot(container: Widget<@Composable () -> Unit>, name: String?) {
    paparazzi.snapshot(name) {
      container.value()
    }
  }

  class ComposeTestFlexContainer private constructor(
    private val delegate: ComposeUiFlexContainer,
  ) : TestFlexContainer<@Composable () -> Unit>,
    FlexContainer<@Composable () -> Unit> by delegate {
    private var childCount = 0
    override val children: ComposeWidgetChildren = delegate.children
    constructor(direction: FlexDirection, backgroundColor: Int) : this(
      ComposeUiFlexContainer(direction).apply {
        testOnlyModifier = Modifier.background(Color(backgroundColor))
      },
    )

    override fun add(widget: Widget<@Composable () -> Unit>) {
      addAt(childCount, widget)
    }

    override fun addAt(index: Int, widget: Widget<@Composable () -> Unit>) {
      delegate.children.insert(index, widget)
      childCount++
    }

    override fun removeAt(index: Int) {
      delegate.children.remove(index = index, count = 1)
      childCount--
    }

    override fun onEndChanges() {
    }
  }
}
