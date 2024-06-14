/*
 * This file is part of Event Implementation Generator, licensed under the MIT License (MIT).
 *
 * Copyright (c) SpongePowered <https://www.spongepowered.org>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.spongepowered.eventimplgen.factory;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.api.util.annotation.eventgen.PropertySettings;
import org.spongepowered.api.util.annotation.eventgen.UseField;
import org.spongepowered.api.util.annotation.eventgen.internal.GeneratedEvent;
import org.spongepowered.eventimplgen.eventgencore.Property;
import org.spongepowered.eventimplgen.eventgencore.PropertySorter;
import org.spongepowered.eventimplgen.factory.plugin.EventFactoryPlugin;
import org.spongepowered.eventimplgen.processor.EventImplGenProcessor;
import org.spongepowered.eventimplgen.signature.Descriptors;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.annotation.processing.Messager;
import javax.inject.Inject;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;

/**
 * Generates the bytecode for classes needed by {@link ClassNameProvider}.
 */
public class ClassGenerator {

    private static final ClassName OBJECTS = ClassName.get(Objects.class);

    private final Types types;
    private final Elements elements;
    private final Messager messager;
    private final ClassContext.Factory classContextFactory;
    private final SourceVersion targetVersion;
    private final ClassNameProvider classNameProvider;

    private NullPolicy nullPolicy = NullPolicy.DISABLE_PRECONDITIONS;

    @Inject
    ClassGenerator(
        final ClassNameProvider classNameProvider,
        final Types types,
        final Elements elements,
        final Messager messager,
        final ClassContext.Factory classContextFactory,
        final SourceVersion targetVersion
    ) {
        this.classNameProvider = classNameProvider;
        this.types = types;
        this.elements = elements;
        this.messager = messager;
        this.classContextFactory = classContextFactory;
        this.targetVersion = targetVersion;
    }

    private static PropertySettings getPropertySettings(final Property property) {
        // Check the most specific method first.
        // This ensures that users can add @PropertySettings on an
        // overridden method.
        final PropertySettings anno = property.getMostSpecificMethod().getAnnotation(PropertySettings.class);

        if (anno != null) {
            return anno;
        }
        return property.getAccessor().getAnnotation(PropertySettings.class);
    }

    static boolean isRequired(final Property property) {
        final PropertySettings settings = ClassGenerator.getPropertySettings(property);
        if (settings == null) {
            return !property.getMostSpecificMethod().isDefault();
        } else {
            return settings.requiredParameter();
        }
    }

    static boolean generateMethods(final Property property) {
        final PropertySettings settings = ClassGenerator.getPropertySettings(property);
        if (settings != null) {
            return settings.generateMethods();
        } else {
            return !property.getMostSpecificMethod().isDefault();
        }
    }

    static UseField getUseField(final TypeMirror clazz, final String fieldName) {
        if (clazz.getKind() != TypeKind.DECLARED) {
            return null;
        }
        final Element type = ((DeclaredType) clazz).asElement();
        if (type == null) {
            return null;
        }

        for (final VariableElement element : ElementFilter.fieldsIn(type.getEnclosedElements())) {
            if (element.getSimpleName().contentEquals(fieldName)) {
                return element.getAnnotation(UseField.class);
            }
        }

        return null;
    }

    public boolean hasDeclaredMethod(final DeclaredType clazz, final String name, final TypeMirror... params) {
        for (final ExecutableElement method : ElementFilter.methodsIn(this.elements.getAllMembers((TypeElement) clazz.asElement()))) {
            if (method.getSimpleName().contentEquals(name) && this.parametersEqual(method.getParameters(), params)) {
                return true;
            }
        }

        return false;
    }

    private boolean parametersEqual(final List<? extends VariableElement> a, final TypeMirror... b) {
        if (a.size() != b.length) {
            return false;
        }

        for (int i = 0; i < b.length; ++i) {
            if (!this.types.isSameType(a.get(i).asType(), b[i])) {
                return false;
            }
        }
        return true;
    }

    public VariableElement getField(final DeclaredType clazz, final String fieldName) {
        for (final VariableElement field : ElementFilter.fieldsIn(this.elements.getAllMembers((TypeElement) clazz.asElement()))) {
            if (field.getSimpleName().contentEquals(fieldName)) {
                return field;
            }
        }
        return null;
    }

    /**
     * Get the policy regarding how null parameters are handled.
     *
     * @return The null policy
     */
    public NullPolicy getNullPolicy() {
        return this.nullPolicy;
    }

    /**
     * Set the policy regarding how null parameters are handled.
     *
     * @param nullPolicy The null policy
     */
    public void setNullPolicy(final NullPolicy nullPolicy) {
        this.nullPolicy = Objects.requireNonNull(nullPolicy, "nullPolicy");
    }

    private Set<String> alwaysQualifiedImports(final TypeElement element) {
        // always qualify the return types of properties in properties
        return this.elements.getAllMembers(element)
            .stream()
            .filter(el -> el.getKind().isClass() || el.getKind().isInterface())
            .map(el -> el.getSimpleName().toString())
            .collect(Collectors.toSet());
    }

    private boolean hasNullable(final ExecutableElement method) {
        return ClassGenerator.hasAnnotationOnSelfOrReturnType(method, name -> name.contentEquals("Nullable"));
    }

    private boolean hasNonNull(final ExecutableElement method) {
        return ClassGenerator.hasAnnotationOnSelfOrReturnType(method, name -> name.contentEquals("NotNull") || name.contentEquals("Nonnull"));
    }

    private static boolean hasAnnotationOnSelfOrReturnType(final ExecutableElement method, final Predicate<Name> annotationTest) {
        // On the method itself
        final List<? extends AnnotationMirror> annotations = method.getAnnotationMirrors();
        if (!annotations.isEmpty()) {
            for (final AnnotationMirror annotation : annotations) {
                if (annotationTest.test(annotation.getAnnotationType().asElement().getSimpleName())) {
                    return true;
                }
            }
        }

        // On return type
        final List<? extends AnnotationMirror> typeUseAnnotations = method.getReturnType().getAnnotationMirrors();
        if (!typeUseAnnotations.isEmpty()) {
            for (final AnnotationMirror annotation : annotations) {
                if (annotationTest.test(annotation.getAnnotationType().asElement().getSimpleName())) {
                    return true;
                }
            }
        }

        return false;
    }

    public boolean contributeField(final ClassContext classWriter, final DeclaredType parentType, final Property property) {
        final VariableElement field = this.getField(parentType, property.getName());
        if (field == null || field.getAnnotation(UseField.class) == null) {
            classWriter.addField(property);
        } else if (field.getModifiers().contains(Modifier.PRIVATE)) {
            this.messager.printMessage(Diagnostic.Kind.ERROR, "You've annotated the field " + property.getName() + " with @UseField, "
                + "but it's private. This just won't work.", field);
            return false;
        } else if (!this.types.isSubtype(field.asType(), property.getType())) {
            this.messager.printMessage(Diagnostic.Kind.ERROR, String.format("In event %s with parent %s - you've specified field '%s' of type %s"
                    + " but the property has the expected type of %s", this.elements.getBinaryName((TypeElement) property.getAccessor().getEnclosingElement()),
                parentType, field.getSimpleName(), field.asType(), property.getType()), field);
            return false;
        }
        return true;
    }

    public List<Property> getRequiredProperties(final List<Property> properties) {
        return properties.stream().filter(p -> p.isMostSpecificType(this.types) && ClassGenerator.isRequired(p)).collect(Collectors.toList());
    }

    private MethodSpec generateConstructor(final DeclaredType parentType, final List<Property> properties) {
        final List<Property> requiredProperties = this.getRequiredProperties(properties);
        final MethodSpec.Builder builder = MethodSpec.constructorBuilder();

        final CodeBlock.Builder initializer = CodeBlock.builder();
        for (final Property property : requiredProperties) {
            builder.addParameter(TypeName.get(property.getType()), property.getName(), Modifier.FINAL);
                // Only if we have a null policy:
                // if (value == null) throw new NullPointerException(...)
                if (this.nullPolicy != NullPolicy.DISABLE_PRECONDITIONS) {
                    final boolean useNullTest = !property.getType().getKind().isPrimitive()
                        && (((this.nullPolicy == NullPolicy.NON_NULL_BY_DEFAULT && !this.hasNullable(property.getAccessor()))
                        || (this.nullPolicy == NullPolicy.NULL_BY_DEFAULT && this.hasNonNull(property.getAccessor())))
                        && ClassGenerator.isRequired(property));

                    if (useNullTest) {
                        initializer.addStatement(
                            "this.$1L = $2T.requireNonNull($1L, $3S)",
                            property.getName(),
                            ClassGenerator.OBJECTS,
                            "The property '" + property.getName() + "' was not provided!"
                        );
                        continue;
                    }
                }

            // no null test
            initializer.addStatement("this.$1L = $1L", property.getName());
        }

        // super.init();
        if (this.hasDeclaredMethod(parentType, "init")) {
            initializer.addStatement("super.init()");
        }

        builder.addCode(initializer.build());
        return builder.build();
    }

    private MethodSpec generateAccessor(final Property property) {
        final ExecutableElement accessor = property.getAccessor();
        final TypeName returnType = TypeName.get(property.getType());
        final MethodSpec.Builder builder = MethodSpec.methodBuilder(accessor.getSimpleName().toString())
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(TypeName.get(property.getType()));

        if (property.isLeastSpecificType(this.types)) {
            builder.addStatement("return this.$L", property.getName());
        } else {
            // a cast is needed
            builder.addStatement("return ($T) this.$L", returnType, property.getName());
        }
        return builder.build();
    }


    private void generateAccessorsAndMutator(
        final ClassContext typeBuilder,
        final TypeElement type,
        final Property property
    ) {
        if (ClassGenerator.generateMethods(property)) {
            if (property.isMostSpecificType(this.types)) { // only generate most specific return type -- compiler will generate the others for us
                typeBuilder.addMethod(this.generateAccessor(property));
            }

            final Optional<ExecutableElement> mutatorOptional = property.getMutator();
            if (mutatorOptional.isPresent()) {
                typeBuilder.addMutator(type, property.getName(), property);
            }
        }
    }

    /**
     * Create the event class.
     *
     * @param type The type
     * @param name The canonical of the generated class
     * @param parentType The parent type
     * @return The class' contents, or {@code null} if an error was reported while generating the class
     */
    public @Nullable JavaFile createClass(
        final TypeElement type,
        final ClassName name,
        final DeclaredType parentType,
        final EventData data,
        final PropertySorter sorter,
        final Set<? extends EventFactoryPlugin> plugins
    ) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(parentType, "parentType");

        final TypeName implementedInterface = TypeName.get(type.asType());
        final TypeSpec.Builder classBuilder = TypeSpec.classBuilder(name)
            .addModifiers(Modifier.FINAL)
            .superclass(TypeName.get(parentType))
            .addSuperinterface(implementedInterface)
            .addOriginatingElement(type)
            .addAnnotation(this.generatedAnnotation())
            .addAnnotation(
                AnnotationSpec.builder(GeneratedEvent.class)
                    .addMember("source", "$T.class",
                        implementedInterface instanceof ParameterizedTypeName
                        ? ((ParameterizedTypeName) implementedInterface).rawType
                        : implementedInterface
                    )
                    .addMember("version", "$S", ClassGenerator.class.getPackage().getImplementationVersion())
                    .build()
            );
        classBuilder.alwaysQualifiedNames.addAll(this.alwaysQualifiedImports(type));
        classBuilder.originatingElements.addAll(data.extraOrigins);

        for (final TypeParameterElement param : type.getTypeParameters()) {
            classBuilder.addTypeVariable(TypeVariableName.get(param));
        }

        // Create the constructor
        classBuilder.addMethod(this.generateConstructor(parentType, sorter.sortProperties(data.properties)));

        final ClassContext ctx = this.classContextFactory.create(classBuilder);

        ctx.initializeToString(type);

        // Create the fields
        // this.contributeFields(cw, parentType, properties, plugins);

        // The return value of toString takes the form of
        // "ClassName{param1=value1, param2=value2, ...}"

        // Create the accessors and mutators, and fill out the toString method
        if (!this.generateWithPlugins(ctx, type, parentType, data.properties, plugins)) {
            return null;
        }

        // Now build the toString
        ctx.finalizeToString(type);

        return JavaFile.builder(name.packageName(), classBuilder.build())
            .indent("    ")
            .build();
    }

    private boolean generateWithPlugins(
        final ClassContext classBuilder,
        final TypeElement eventClass,
        final DeclaredType parentType,
        final List<Property> properties,
        final Set<? extends EventFactoryPlugin> plugins
    ) {
        boolean success = true;

        for (final Property property : properties) {
            boolean processed = false;

            for (final EventFactoryPlugin plugin : plugins) {
                final EventFactoryPlugin.Result result = plugin.contributeProperty(eventClass, classBuilder, property);
                processed = result != EventFactoryPlugin.Result.IGNORE;
                success &= result != EventFactoryPlugin.Result.FAILURE;
                if (processed) {
                    break;
                }
            }

            classBuilder.contributeToString(parentType, property);

            if (!processed) {
                success &= this.contributeField(classBuilder, parentType, property);
                this.generateAccessorsAndMutator(classBuilder, eventClass, property);
            }
        }
        return success;
    }

    AnnotationSpec generatedAnnotation() {
        final ClassName clazz;
        if (this.targetVersion.compareTo(SourceVersion.RELEASE_8) > 0) {
            clazz = ClassName.get("javax.annotation.processing", "Generated");
        } else {
            clazz = ClassName.get("javax.annotation", "Generated");
        }

        return AnnotationSpec.builder(clazz)
            .addMember("value", "$S", EventImplGenProcessor.class.getName())
            .build();
    }

    public ClassName qualifiedName(final TypeElement event) {
        return this.classNameProvider.getClassName(event, "Impl");
    }
}
