/*
 * Copyright (C) 2023 Square, Inc.
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
package app.cash.redwood.tooling.codegen

import app.cash.redwood.tooling.schema.ProtocolWidget.ProtocolTrait
import app.cash.redwood.tooling.schema.Schema
import app.cash.redwood.tooling.schema.SchemaSet
import app.cash.redwood.tooling.schema.Widget
import app.cash.redwood.tooling.schema.Widget.Children
import app.cash.redwood.tooling.schema.Widget.Event
import app.cash.redwood.tooling.schema.Widget.Property
import com.squareup.kotlinpoet.ANY
import com.squareup.kotlinpoet.BOOLEAN
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier.INTERNAL
import com.squareup.kotlinpoet.KModifier.OVERRIDE
import com.squareup.kotlinpoet.KModifier.PRIVATE
import com.squareup.kotlinpoet.KModifier.SUSPEND
import com.squareup.kotlinpoet.LIST
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.joinToCode

/*
suspend fun <R> ExampleTester(
  onBackPressedDispatcher: OnBackPressedDispatcher = NoOpOnBackPressedDispatcher,
  savedState: TestSavedState? = null,
  uiConfiguration: UiConfiguration = UiConfiguration(),
  body: suspend TestRedwoodComposition<List<WidgetValue>>.() -> R,
): R = coroutineScope {
  val widgetSystem = ExampleWidgetSystem(
    TestSchema = TestSchemaTestingWidgetFactory(),
    RedwoodLayout = RedwoodLayoutTestingWidgetFactory(),
  )
  val container = MutableListChildren<WidgetValue>()
  val tester = TestRedwoodComposition(this, widgetSystem, container, savedState, uiConfiguration) {
    container.map { it.value }
  }
  try {
    tester.body()
  } finally {
    tester.cancel()
  }
}
*/
internal fun generateTester(schemaSet: SchemaSet): FileSpec {
  val schema = schemaSet.schema
  val testerFunction = MemberName(schema.testingPackage(), "${schema.type.flatName}Tester")
  val typeVarR = TypeVariableName("R")
  val bodyType = LambdaTypeName.get(
    receiver = RedwoodTesting.TestRedwoodComposition
      .parameterizedBy(LIST.parameterizedBy(RedwoodTesting.WidgetValue)),
    returnType = typeVarR,
  ).copy(suspending = true)
  return FileSpec.builder(testerFunction)
    .addAnnotation(suppressDeprecations)
    .addFunction(
      FunSpec.builder(testerFunction)
        .optIn(Redwood.RedwoodCodegenApi)
        .addModifiers(SUSPEND)
        .addParameter(
          ParameterSpec.builder("onBackPressedDispatcher", Redwood.OnBackPressedDispatcher)
            .defaultValue("%T", RedwoodTesting.NoOpOnBackPressedDispatcher)
            .build(),
        )
        .addParameter(
          ParameterSpec.builder("savedState", RedwoodTesting.TestSavedState.copy(nullable = true))
            .defaultValue("null")
            .build(),
        )
        .addParameter(
          ParameterSpec.builder("uiConfiguration", Redwood.UiConfiguration)
            .defaultValue("%T()", Redwood.UiConfiguration)
            .build(),
        )
        .addParameter("body", bodyType)
        .addTypeVariable(typeVarR)
        .returns(typeVarR)
        .beginControlFlow("return %M", KotlinxCoroutines.coroutineScope)
        .addCode("val widgetSystem = %T(⇥\n", schema.getWidgetSystemType())
        .apply {
          for (dependency in schemaSet.all) {
            addCode("%N = %T(),\n", dependency.type.flatName, dependency.getTestingWidgetFactoryType())
          }
        }
        .addCode("⇤)\n")
        .addStatement("val container = %T<%T>()", RedwoodWidget.MutableListChildren, RedwoodTesting.WidgetValue)
        .beginControlFlow("val tester = %T(this, widgetSystem, container, onBackPressedDispatcher, savedState, uiConfiguration)", RedwoodTesting.TestRedwoodComposition)
        .addStatement("container.map { it.value }")
        .endControlFlow()
        .beginControlFlow("try")
        .addStatement("tester.body()")
        .nextControlFlow("finally")
        .addStatement("tester.cancel()")
        .endControlFlow()
        .endControlFlow()
        .build(),
    )
    .build()
}

/*
@RedwoodCodegenApi
public class EmojiSearchTestingWidgetFactory : EmojiSearchWidgetFactory<WidgetValue> {
  public override fun Text(): Text<WidgetValue> = MutableText()
  public override fun Button(): Button<WidgetValue> = MutableButton()
}
*/
internal fun generateMutableWidgetFactory(schema: Schema): FileSpec {
  val mutableWidgetFactoryType = schema.getTestingWidgetFactoryType()
  return FileSpec.builder(mutableWidgetFactoryType)
    .addAnnotation(suppressDeprecations)
    .addType(
      TypeSpec.classBuilder(mutableWidgetFactoryType)
        .addSuperinterface(schema.getWidgetFactoryType().parameterizedBy(RedwoodTesting.WidgetValue))
        .addAnnotation(Redwood.RedwoodCodegenApi)
        .apply {
          for (widget in schema.widgets) {
            addFunction(
              FunSpec.builder(widget.type.flatName)
                .addModifiers(OVERRIDE)
                .returns(schema.widgetType(widget).parameterizedBy(RedwoodTesting.WidgetValue))
                .addStatement("return %T()", schema.mutableWidgetType(widget))
                .build(),
            )
          }
          for (modifier in schema.unscopedModifiers) {
            addFunction(
              FunSpec.builder(modifier.type.flatName)
                .addModifiers(OVERRIDE)
                .addParameter("value", RedwoodTesting.WidgetValue)
                .addParameter("modifier", schema.modifierType(modifier))
                .build(),
            )
          }
        }
        .build(),
    )
    .build()
}

/*
internal class MutableButton : Button<WidgetValue> {
  public override val value: WidgetValue
    get() = ButtonValue(modifier, text, enabled!!, maxLength!!)

  public override var modifier: Modifier = Modifier

  private var text: String? = null
  private var enabled: Boolean? = null
  private var maxLength: Int? = null

  public override fun text(text: String?) {
    this.text = text
  }

  public override fun enabled(enabled: Boolean) {
    this.enabled = enabled
  }
}
*/
internal fun generateMutableWidget(schema: Schema, widget: Widget): FileSpec {
  val mutableWidgetType = schema.mutableWidgetType(widget)
  val widgetValueType = schema.widgetValueType(widget)
  return FileSpec.builder(mutableWidgetType)
    .addAnnotation(suppressDeprecations)
    .addType(
      TypeSpec.classBuilder(mutableWidgetType)
        .addModifiers(INTERNAL)
        .addSuperinterface(schema.widgetType(widget).parameterizedBy(RedwoodTesting.WidgetValue))
        .addProperty(
          PropertySpec.builder("value", RedwoodTesting.WidgetValue)
            .addModifiers(OVERRIDE)
            .getter(
              FunSpec.getterBuilder()
                .addCode("return %T(⇥\n", widgetValueType)
                .addCode("modifier = modifier,\n")
                .apply {
                  for (trait in widget.traits) {
                    when (trait) {
                      is Event, is Property -> {
                        val nullable = when (trait) {
                          is Property -> trait.type.nullable
                          is Event -> trait.lambdaType.isNullable
                          else -> false
                        }
                        if (nullable) {
                          addCode("%1N = %1N,\n", trait.name)
                        } else {
                          addCode("%1N = %1N!!,\n", trait.name)
                        }
                      }

                      is Children -> addCode("%1N = %1N.map { it.`value` },\n", trait.name)

                      is ProtocolTrait -> throw AssertionError()
                    }
                  }
                }
                .addCode("⇤)\n")
                .build(),
            )
            .build(),
        )
        .addProperty(
          PropertySpec.builder("modifier", Redwood.Modifier)
            .addModifiers(OVERRIDE)
            .mutable(true)
            .initializer("%T", Redwood.Modifier)
            .build(),
        )
        .apply {
          for (trait in widget.traits) {
            when (trait) {
              is Property, is Event -> {
                val type = when (trait) {
                  is Property -> trait.type.asTypeName()
                  is Event -> trait.lambdaType
                  else -> throw AssertionError()
                }
                addProperty(
                  PropertySpec.builder(trait.name, type.copy(nullable = true))
                    .addModifiers(PRIVATE)
                    .initializer("null")
                    .mutable(true)
                    .build(),
                )
                addFunction(
                  FunSpec.builder(trait.name)
                    .addModifiers(OVERRIDE)
                    .addParameter(trait.name, type)
                    .addCode("this.%N = %N", trait.name, trait.name)
                    .build(),
                )
              }

              is Children -> {
                val mutableChildrenOfMutableWidget = RedwoodWidget.MutableListChildren
                  .parameterizedBy(RedwoodTesting.WidgetValue)
                addProperty(
                  PropertySpec.builder(trait.name, mutableChildrenOfMutableWidget)
                    .addModifiers(OVERRIDE)
                    .initializer("%T()", RedwoodWidget.MutableListChildren)
                    .build(),
                )
              }

              is ProtocolTrait -> throw AssertionError()
            }
          }
        }
        .build(),
    )
    .build()
}

/*
public class ButtonValue(
  override val modifier: Modifier = Modifier,
  public val text: String?,
  public val onClick: (() -> Unit)?,
) : WidgetValue {
  override val childrenLists: List<List<WidgetValue>>
    get() = listOf()

  override fun equals(other: Any?): Boolean = other is SunspotButtonValue &&
    other.text == text &&
    other.enabled == enabled

  override fun hashCode(): Int = listOf(
    text,
    enabled,
  ).hashCode()

  override fun toString(): String =
    """ButtonValue(text=$text, enabled=$enabled)"""

  override fun toDebugString(): String = buildString {
    append("Button")
    append("""
        |(
        |  text = $text
        |""".trimMargin())
    append(")")
  }

  override fun <W : Any> toWidget(provider: Widget.Provider<W>): Widget<W> {
    val factory = provider as TestSchemaWidgetFactoryProvider<W>
    val instance = factory.TestSchema.Button()

    instance.modifier = modifier
    instance.text(text)


    return instance
  }
}
*/
internal fun generateWidgetValue(schema: Schema, widget: Widget): FileSpec {
  val widgetValueType = schema.widgetValueType(widget)

  val classBuilder = TypeSpec.classBuilder(widgetValueType)
    .addSuperinterface(RedwoodTesting.WidgetValue)
    .addProperty(
      PropertySpec.builder("modifier", Redwood.Modifier)
        .addModifiers(OVERRIDE)
        .initializer("modifier")
        .build(),
    )

  val constructorBuilder = FunSpec.constructorBuilder()
    .addParameter(
      ParameterSpec.builder("modifier", Redwood.Modifier)
        .defaultValue("%T", Redwood.Modifier)
        .build(),
    )

  val childrenLists = mutableListOf<CodeBlock>()
  val equalsComparisons = mutableListOf(
    CodeBlock.of("other is %T", widgetValueType),
    CodeBlock.of("other.modifier == modifier"),
  )
  val hashCodeProperties = mutableListOf(CodeBlock.of("modifier"))
  val toStringProperties = mutableListOf("modifier=\$modifier")
  val debugStringProperties = mutableListOf<String>()
  val childrenDebugStringProperties = mutableListOf<String>()
  val widgetDebugName = widgetValueType.simpleName.removeSuffix("Value")
  val widgetListToDebugStringMethod = MemberName(
    packageName = "app.cash.redwood.testing",
    simpleName = "toDebugString",
    isExtension = true,
  )

  fun addEqualsHashCodeToString(trait: Widget.Trait) {
    equalsComparisons += CodeBlock.of("other.%1N == %1N", trait.name)
    hashCodeProperties += CodeBlock.of("%N", trait.name)
    toStringProperties += "${trait.name}=\$${trait.name}"
    if (trait !is Children) {
      debugStringProperties += "${trait.name} = \$${trait.name}"
    } else {
      childrenDebugStringProperties += "  ${trait.name} = {"
    }
  }

  val toWidgetChildrenBuilder = CodeBlock.builder()
  val toWidgetPropertiesBuilder = CodeBlock.builder()

  for (trait in widget.traits) {
    val type: TypeName
    val defaultExpression: CodeBlock?
    when (trait) {
      is Property -> {
        type = trait.type.asTypeName()
        defaultExpression = trait.defaultExpression?.let { CodeBlock.of(it) }
        addEqualsHashCodeToString(trait)

        toWidgetPropertiesBuilder.addStatement("instance.%1N(%1N)", trait.name)
      }

      is Children -> {
        type = Stdlib.List.parameterizedBy(RedwoodTesting.WidgetValue)
        defaultExpression = CodeBlock.of("%M()", Stdlib.listOf)
        addEqualsHashCodeToString(trait)

        childrenLists += CodeBlock.of("%N", trait.name)

        toWidgetChildrenBuilder.beginControlFlow("for ((index, child) in %N.withIndex())", trait.name)
          .addStatement("instance.%N.insert(index, child.toWidget(widgetSystem))", trait.name)
          .endControlFlow()
      }

      is Event -> {
        type = trait.lambdaType
        defaultExpression = trait.defaultExpression?.let { CodeBlock.of(it) }
        // Events are omitted from equals/hashCode/toString.
      }

      else -> throw AssertionError()
    }

    constructorBuilder.addParameter(
      ParameterSpec.builder(trait.name, type)
        .defaultValue(defaultExpression)
        .build(),
    )

    classBuilder.addProperty(
      PropertySpec.builder(trait.name, type)
        .initializer("%N", trait.name)
        .build(),
    )
  }

  return FileSpec.builder(widgetValueType)
    .addAnnotation(suppressDeprecations)
    .addType(
      classBuilder
        .primaryConstructor(constructorBuilder.build())
        .addProperty(
          PropertySpec.builder(
            "childrenLists",
            LIST.parameterizedBy(LIST.parameterizedBy(RedwoodTesting.WidgetValue)),
          )
            .addModifiers(OVERRIDE)
            .getter(
              FunSpec.getterBuilder()
                .addStatement("return %M(%L)", Stdlib.listOf, childrenLists.joinToCode())
                .build(),
            )
            .build(),
        )
        .addFunction(
          FunSpec.builder("equals")
            .addModifiers(OVERRIDE)
            .addParameter("other", ANY.copy(nullable = true))
            .returns(BOOLEAN)
            .addStatement("return %L", equalsComparisons.joinToCode(" &&\n"))
            .build(),
        )
        .addFunction(
          FunSpec.builder("hashCode")
            .addModifiers(OVERRIDE)
            .returns(Int::class)
            .addStatement("return %M(%L).hashCode()", Stdlib.listOf, hashCodeProperties.joinToCode())
            .build(),
        )
        .addFunction(
          FunSpec.builder("toString")
            .addModifiers(OVERRIDE)
            .returns(String::class)
            .addStatement(
              "return %P",
              toStringProperties.joinToString(
                prefix = "${widgetValueType.simpleName}(",
                postfix = ")",
              ),
            )
            .build(),
        )
        .addFunction(
          FunSpec.builder("toDebugString")
            .addModifiers(OVERRIDE)
            .returns(String::class)
            .apply {
              beginControlFlow("return buildString")
              addStatement("append(%S)", widgetDebugName)
              if (debugStringProperties.isNotEmpty() || childrenDebugStringProperties.size > 1) {
                addStatement(
                  "append(%P)",
                  debugStringProperties.joinToString(
                    prefix = "(",
                    postfix = "\n",
                    transform = { it.prependIndent("\n  ") },
                  ),
                )
                if (childrenDebugStringProperties.size > 1) {
                  for ((index, childDebugStringProperty) in childrenDebugStringProperties.withIndex()) {
                    addStatement("append(%S)", childDebugStringProperty)
                    beginControlFlow("if (childrenLists[$index].isNotEmpty())")
                    addStatement("appendLine()")
                    addStatement(
                      "append(childrenLists[$index].%M().prependIndent(\"    \"))",
                      widgetListToDebugStringMethod,
                    )
                    addStatement("appendLine(\"\\n  }\")")
                    endControlFlow()
                    beginControlFlow("else")
                    addStatement("appendLine(\" }\")")
                    endControlFlow()
                  }
                }
                addStatement("append(\")\")")
              }
              if (childrenDebugStringProperties.size == 1) {
                addStatement("append(\" {\")")
                beginControlFlow("if (childrenLists[0].isNotEmpty())")
                addStatement("appendLine()")
                addStatement(
                  "append(childrenLists[0].%M().prependIndent(\"  \"))",
                  widgetListToDebugStringMethod,
                )
                addStatement("append(\"\\n}\")")
                endControlFlow()
                beginControlFlow("else")
                addStatement("append(\" }\")")
                endControlFlow()
              }
              endControlFlow()
            }
            .build(),
        )
        .addFunction(
          FunSpec.builder("toWidget")
            .addModifiers(OVERRIDE)
            .optIn(Redwood.RedwoodCodegenApi)
            .addTypeVariable(typeVariableW)
            .addParameter("widgetSystem", RedwoodWidget.WidgetSystem.parameterizedBy(typeVariableW))
            .returns(RedwoodWidget.Widget.parameterizedBy(typeVariableW))
            .addStatement("@%T(%S) // Type parameter shared in generated code.", Suppress::class, "UNCHECKED_CAST")
            .addStatement("val factoryOwner = widgetSystem as %T", schema.getWidgetFactoryOwnerType().parameterizedBy(typeVariableW))
            .addStatement("val instance = factoryOwner.%L.%L()", schema.type.flatName, widget.type.flatName)
            .addStatement("")
            .addCode(toWidgetPropertiesBuilder.build())
            .addStatement("instance.modifier = modifier")
            .addStatement("")
            .addCode(toWidgetChildrenBuilder.build())
            .addStatement("")
            .addStatement("return instance")
            .build(),
        )
        .build(),
    )
    .build()
}

private fun Schema.getTestingWidgetFactoryType(): ClassName {
  return ClassName(testingPackage(), "${type.flatName}TestingWidgetFactory")
}

private fun Schema.mutableWidgetType(widget: Widget): ClassName {
  return ClassName(testingPackage(), "Mutable${widget.type.flatName}")
}

private fun Schema.widgetValueType(widget: Widget): ClassName {
  return ClassName(testingPackage(), "${widget.type.flatName}Value")
}
