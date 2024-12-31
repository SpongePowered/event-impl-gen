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

import com.palantir.javapoet.AnnotationSpec;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.JavaFile;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.TypeSpec;
import com.palantir.javapoet.TypeVariableName;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.eventgen.annotations.PropertySettings;
import org.spongepowered.eventgen.annotations.UseField;
import org.spongepowered.eventgen.annotations.internal.GeneratedEvent;
import org.spongepowered.eventimplgen.eventgencore.Property;
import org.spongepowered.eventimplgen.eventgencore.PropertySorter;
import org.spongepowered.eventimplgen.factory.plugin.EventFactoryPlugin;
import org.spongepowered.eventimplgen.processor.EventImplGenProcessor;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.annotation.processing.Generated;
import javax.annotation.processing.Messager;
import javax.inject.Inject;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
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
    private final ClassNameProvider classNameProvider;

    private NullPolicy nullPolicy = NullPolicy.DISABLE_PRECONDITIONS;

    @Inject
    ClassGenerator(
        final ClassNameProvider classNameProvider,
        final Types types,
        final Elements elements,
        final Messager messager,
        final ClassContext.Factory classContextFactory
    ) {
        this.classNameProvider = classNameProvider;
        this.types = types;
        this.elements = elements;
        this.messager = messager;
        this.classContextFactory = classContextFactory;
    }

    static PropertySettings getPropertySettings(final Property property) {
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

    private void alwaysQualifiedImports(TypeSpec.Builder classBuilder, final TypeElement element) {
        // always qualify the return types of properties in properties
        final Set<String> alwaysQualified = new HashSet<>();
        this.elements.getAllMembers(element)
            .stream()
            .filter(el -> el.getKind().isClass() || el.getKind().isInterface())
            .filter(el -> ((TypeElement) el).getNestingKind().isNested())
            .map(el -> el.getSimpleName().toString())
            .forEach(alwaysQualified::add);

        classBuilder.alwaysQualify(alwaysQualified.toArray(new String[0]));
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
        } else if (!this.types.isSubtype(this.types.erasure(field.asType()),this.types.erasure(property.getType()))) {
            // We need to handle generics and if the field is a subtype of the property, then it fits.
            if (this.types.isAssignable(this.types.erasure(property.getType()), this.types.erasure(field.asType()))) {
                return true;
            }
            this.messager.printMessage(Diagnostic.Kind.ERROR,
                String.format(
                    "In event %s with parent %s - you've specified field '%s' of type %s" + " but the property has the expected type of %s",
                    this.elements.getBinaryName((TypeElement) property.getAccessor().getEnclosingElement()),
                    parentType,
                    field.getSimpleName(),
                    field.asType(),
                    property.getType()),
                field);
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

    private void deriveParentTypeName(TypeSpec.Builder builder, DeclaredType parentType, TypeElement iface) {
        final List<TypeVariableName> classTypeParameters = new ArrayList<>();
        TypeName superClassName = TypeName.get(parentType);

        if (iface != null) {
            iface.getTypeParameters()
                .stream()
                .map(TypeVariableName::get)
                .forEach(classTypeParameters::add);
        }

        // Inspect both the parent type and the interface we're implementing
        // - If the interface we implement has NO type parameters, we can introspect the
        //   extension for generic types declared on the parent's extension
        // - If the parentType HAS type parameters, we must inject them
        //   - If the interface has the matching parameters, do NOT duplicate
        //   - If the interface does not, populate
        if (parentType.asElement() instanceof TypeElement te) {
            final var params = te.getTypeParameters()
                .stream()
                .map(tpe -> TypeName.get(tpe.asType()))
                .toArray(TypeName[]::new);
            final ClassName newClassName = ClassName.get(te);
            if (params.length == 0) {
                // We won't add type parameters because the interface's type parameters would be added later
                builder.superclass(superClassName);
                return;
            }

            superClassName = ParameterizedTypeName.get(newClassName, params);
            if (classTypeParameters.isEmpty()) {
                final var innerParams = new ArrayList<TypeName>();
                for (TypeMirror extended : iface.getInterfaces()) {
                    if (extended instanceof DeclaredType de) {
                        de.getTypeArguments()
                            .stream()
                            .map(ite -> {
                                if (ite instanceof DeclaredType dte) {
                                    final var nte = (TypeElement) dte.asElement();
                                    return ClassName.get(nte);
                                }
                                return TypeName.get(ite);
                            })
                            .forEach(innerParams::add);
                    }
                }
                final var modifiedParams = innerParams.toArray(new TypeName[0]);
                superClassName = ParameterizedTypeName.get(newClassName, modifiedParams);
            }
            builder.superclass(superClassName);
        }
    }

    /**
     * Create the event class.
     *
     * @param type       The type
     * @param name       The canonical of the generated class
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

        TypeName implementedInterface = this.classNameProvider.getImplementingInterfaceName(type);
        List<TypeVariableName> classTypeParameters = new ArrayList<>();

        final TypeSpec.Builder classBuilder = TypeSpec.classBuilder(name)
            .addModifiers(Modifier.FINAL)
            .addTypeVariables(classTypeParameters)
            .addSuperinterface(implementedInterface)
            .addOriginatingElement(type)
            .addAnnotation(this.generatedAnnotation())
            .addAnnotation(
                AnnotationSpec.builder(GeneratedEvent.class)
                    .addMember("source", "$T.class",
                        implementedInterface instanceof ParameterizedTypeName
                            ? ((ParameterizedTypeName) implementedInterface).rawType()
                            : implementedInterface
                    )
                    .addMember("version", "$S", ClassGenerator.class.getPackage().getImplementationVersion())
                    .build()
            );
        this.deriveParentTypeName(classBuilder, parentType, type);
        this.alwaysQualifiedImports(classBuilder, type);
        classBuilder.avoidClashesWithNestedClasses(type);
        data.extraOrigins().forEach(classBuilder::addOriginatingElement);

        for (final TypeParameterElement param : type.getTypeParameters()) {
            classBuilder.addTypeVariable(TypeVariableName.get(param));
        }

        // Create the constructor
        classBuilder.addMethod(this.generateConstructor(parentType, sorter.sortProperties(data.properties())));

        final ClassContext ctx = this.classContextFactory.create(classBuilder);

        ctx.initializeToString(type);

        // Create the fields
        // this.contributeFields(cw, parentType, properties, plugins);

        // The return value of toString takes the form of
        // "ClassName{param1=value1, param2=value2, ...}"

        // Create the accessors and mutators, and fill out the toString method
        if (!this.generateWithPlugins(ctx, type, parentType, data.properties(), plugins)) {
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
        return AnnotationSpec.builder(Generated.class)
            .addMember("value", "$S", EventImplGenProcessor.class.getName())
            .build();
    }

    public ClassName qualifiedName(final TypeElement event) {
        return this.classNameProvider.getClassName(event, "Impl");
    }
}
