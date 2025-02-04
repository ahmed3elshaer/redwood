/*
 * Copyright (C) 2021 Square, Inc.
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

import app.cash.redwood.tooling.schema.ProtocolWidget
import app.cash.redwood.tooling.schema.ProtocolWidget.ProtocolTrait
import app.cash.redwood.tooling.schema.Schema
import app.cash.redwood.tooling.schema.SchemaSet
import app.cash.redwood.tooling.schema.Widget
import app.cash.redwood.tooling.schema.Widget.Children
import app.cash.redwood.tooling.schema.Widget.Event
import app.cash.redwood.tooling.schema.Widget.Property
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier.ABSTRACT
import com.squareup.kotlinpoet.KModifier.OVERRIDE
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec

/*
@OptIn(RedwoodCodegenApi::class)
@ObjCName("ExampleWidgetSystem", exact = true)
class ExampleWidgetSystem<W : Any>(
  override val Example: ExampleWidgetFactory<W>,
  override val RedwoodLayout: RedwoodLayoutWidgetFactory<W>,
) : WidgetSystem<W>,
    ExampleWidgetFactoryOwner<W>,
    RedwoodLayoutWidgetFactoryOwner<W> {
  override fun apply(value: W, element: Modifier.UnscopedElement) {
    Example.apply(value, element)
    RedwoodLayout.apply(value, element)
  }
}

@RedwoodCodegenApi
interface ExampleWidgetFactoryOwner<W : Any> {
  val Example: ExampleWidgetFactory<W>

  companion object {
    fun <W : Any> apply(
      factory: ExampleWidgetFactory<W>,
      value: W,
      element: Modifier.UnscopedElement,
    ): W? {
      when (element) {
        is BackgroundColor -> factory.BackgroundColor(value, element)
      }
    }
  }
}
 */
internal fun generateWidgetSystem(schemaSet: SchemaSet): FileSpec {
  val schema = schemaSet.schema
  val widgetSystemType = schema.getWidgetSystemType()
  return FileSpec.builder(widgetSystemType)
    .addAnnotation(suppressDeprecations)
    .addType(
      TypeSpec.classBuilder(widgetSystemType)
        .addTypeVariable(typeVariableW)
        .addSuperinterface(RedwoodWidget.WidgetSystem.parameterizedBy(typeVariableW))
        .optIn(Stdlib.ExperimentalObjCName, Redwood.RedwoodCodegenApi)
        .addAnnotation(
          AnnotationSpec.builder(Stdlib.ObjCName)
            .addMember("%S", widgetSystemType.simpleName)
            .addMember("exact = true")
            .build(),
        )
        .apply {
          for (dependency in schemaSet.all) {
            addSuperinterface(dependency.getWidgetFactoryOwnerType().parameterizedBy(typeVariableW))
          }

          val constructorBuilder = FunSpec.constructorBuilder()

          for (dependency in schemaSet.all) {
            val dependencyType = dependency.getWidgetFactoryType().parameterizedBy(typeVariableW)
            addProperty(
              PropertySpec.builder(dependency.type.flatName, dependencyType, OVERRIDE)
                .initializer(dependency.type.flatName)
                .build(),
            )
            constructorBuilder.addParameter(dependency.type.flatName, dependencyType)
          }

          primaryConstructor(constructorBuilder.build())
        }
        .addFunction(
          FunSpec.builder("apply")
            .addModifiers(OVERRIDE)
            .addParameter("value", typeVariableW)
            .addParameter("element", Redwood.ModifierUnscopedElement)
            .apply {
              for (dependency in schemaSet.all) {
                addStatement(
                  "%T.apply(%N, value, element)",
                  dependency.getWidgetFactoryOwnerType(),
                  dependency.type.flatName,
                )
              }
            }
            .build(),
        )
        .build(),
    )
    .addType(
      TypeSpec.interfaceBuilder(schema.getWidgetFactoryOwnerType())
        .addKdoc("@suppress For generated code usage only.")
        .addTypeVariable(typeVariableW)
        .addAnnotation(Redwood.RedwoodCodegenApi)
        .addSuperinterface(RedwoodWidget.WidgetFactoryOwner.parameterizedBy(typeVariableW))
        .addProperty(schema.type.flatName, schema.getWidgetFactoryType().parameterizedBy(typeVariableW))
        .addType(
          TypeSpec.companionObjectBuilder()
            .addFunction(
              FunSpec.builder("apply")
                .addTypeVariable(typeVariableW)
                .addParameter("factory", schema.getWidgetFactoryType().parameterizedBy(typeVariableW))
                .addParameter("value", typeVariableW)
                .addParameter("element", Redwood.ModifierUnscopedElement)
                .apply {
                  if (schema.unscopedModifiers.isEmpty()) {
                    // Even with no modifiers, we need to maintain this function signature for
                    // other modules to call into it.
                    addAnnotation(
                      AnnotationSpec.builder(Suppress::class)
                        .addMember("%S", "UNUSED_PARAMETER")
                        .build(),
                    )
                  } else {
                    beginControlFlow("when (element)")
                    for (globalModifier in schema.unscopedModifiers) {
                      addStatement(
                        "is %T -> factory.%N(value, element)",
                        schema.modifierType(globalModifier),
                        globalModifier.type.flatName,
                      )
                    }
                    endControlFlow()
                  }
                }
                .build(),
            )
            .build(),
        )
        .build(),
    )
    .build()
}

/*
@ObjCName("ExampleWidgetFactory", exact = true)
interface ExampleWidgetFactory<W : Any> : Widget.Factory<W> {
  /** {tag=1} */
  fun Text(): Text<W>
  /** {tag=2} */
  fun Button(): Button<W>
}
*/
internal fun generateWidgetFactory(schema: Schema): FileSpec {
  val widgetFactoryType = schema.getWidgetFactoryType()
  return FileSpec.builder(widgetFactoryType)
    .addAnnotation(suppressDeprecations)
    .addType(
      TypeSpec.interfaceBuilder(widgetFactoryType)
        .addTypeVariable(typeVariableW)
        .optIn(Stdlib.ExperimentalObjCName)
        .addAnnotation(
          AnnotationSpec.builder(Stdlib.ObjCName)
            .addMember("%S", widgetFactoryType.simpleName)
            .addMember("exact = true")
            .build(),
        )
        .maybeAddKDoc(schema.documentation)
        .apply {
          for (widget in schema.widgets) {
            addFunction(
              FunSpec.builder(widget.type.flatName)
                .addModifiers(ABSTRACT)
                .returns(schema.widgetType(widget).parameterizedBy(typeVariableW))
                .maybeAddDeprecation(widget.deprecation)
                .maybeAddKDoc(widget.documentation)
                .apply {
                  if (widget is ProtocolWidget) {
                    addKdoc("{tag=${widget.tag}}")
                  }
                }
                .build(),
            )
          }

          for (modifier in schema.unscopedModifiers) {
            addFunction(
              FunSpec.builder(modifier.type.flatName)
                .addModifiers(ABSTRACT)
                .addParameter("value", typeVariableW)
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
/** {tag=2} */
@ObjCName("Button", exact = true)
interface Button<W: Any> : Widget<W> {
  /** {tag=1} */
  fun text(text: String?)
  /** {tag=2} */
  fun enabled(enabled: Boolean)
  /** {tag=3} */
  fun onClick(onClick: (() -> Unit)?)
}
*/
internal fun generateWidget(schema: Schema, widget: Widget): FileSpec {
  val flatName = widget.type.flatName
  return FileSpec.builder(schema.widgetPackage(), flatName)
    .addAnnotation(suppressDeprecations)
    .addType(
      TypeSpec.interfaceBuilder(flatName)
        .addTypeVariable(typeVariableW)
        .addSuperinterface(RedwoodWidget.Widget.parameterizedBy(typeVariableW))
        .optIn(Stdlib.ExperimentalObjCName)
        .addAnnotation(
          AnnotationSpec.builder(Stdlib.ObjCName)
            .addMember("%S", flatName)
            .addMember("exact = true")
            .build(),
        )
        .maybeAddDeprecation(widget.deprecation)
        .maybeAddKDoc(widget.documentation)
        .apply {
          if (widget is ProtocolWidget) {
            addKdoc("{tag=${widget.tag}}")
          }

          for (trait in widget.traits) {
            when (trait) {
              is Property -> {
                addFunction(
                  FunSpec.builder(trait.name)
                    .addModifiers(ABSTRACT)
                    .addParameter(trait.name, trait.type.asTypeName())
                    .maybeAddDeprecation(trait.deprecation)
                    .maybeAddKDoc(trait.documentation)
                    .apply {
                      if (trait is ProtocolTrait) {
                        addKdoc("{tag=${trait.tag}}")
                      }
                    }
                    .build(),
                )
              }

              is Event -> {
                addFunction(
                  FunSpec.builder(trait.name)
                    .addModifiers(ABSTRACT)
                    .addParameter(trait.name, trait.lambdaType)
                    .maybeAddDeprecation(trait.deprecation)
                    .maybeAddKDoc(trait.documentation)
                    .apply {
                      if (trait is ProtocolTrait) {
                        addKdoc("{tag=${trait.tag}}")
                      }
                    }
                    .build(),
                )
              }

              is Children -> {
                addProperty(
                  PropertySpec.builder(trait.name, RedwoodWidget.WidgetChildrenOfW)
                    .addModifiers(ABSTRACT)
                    .maybeAddDeprecation(trait.deprecation)
                    .maybeAddKDoc(trait.documentation)
                    .apply {
                      if (trait is ProtocolTrait) {
                        addKdoc("{tag=${trait.tag}}")
                      }
                    }
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
