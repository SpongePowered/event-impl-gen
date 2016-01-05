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
package org.spongepowered.api.eventimplgen;

import com.google.common.base.Preconditions;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskAction;
import org.spongepowered.api.eventgencore.Property;
import org.spongepowered.api.eventgencore.annotation.PropertySettings;
import org.spongepowered.api.eventgencore.annotation.codecheck.CompareTo;
import org.spongepowered.api.eventgencore.annotation.codecheck.FactoryCodeCheck;
import spoon.Launcher;
import spoon.SpoonAPI;
import spoon.compiler.Environment;
import spoon.compiler.SpoonCompiler;
import spoon.fixed.support.JavaOutputProcessor;
import spoon.reflect.code.BinaryOperatorKind;
import spoon.reflect.code.CtBinaryOperator;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtFieldAccess;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtReturn;
import spoon.reflect.code.CtVariableAccess;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.CtType;
import spoon.reflect.declaration.ModifierKind;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class EventImplGenTask extends DefaultTask {

    private static final String EVENT_CLASS_PROCESSOR = EventInterfaceProcessor.class.getCanonicalName();
    private final SpoonAPI spoon = new Launcher();
    private Factory factory;
    private EventImplGenExtension extension;

    public EventImplGenTask() {
        spoon.addProcessor(EVENT_CLASS_PROCESSOR);
        final Environment environment = spoon.getEnvironment();
        environment.setComplianceLevel(8);
        environment.setGenerateJavadoc(true);
        environment.setAutoImports(true);
    }

    @TaskAction
    public <T> void task() {
        // Configure AST generator
        extension = getProject().getExtensions().getByType(EventImplGenExtension.class);
        Preconditions.checkState(!extension.eventImplCreateMethod.isEmpty(), "genEventImpl extension property eventImplCreateMethod isn't defined");
        spoon.getEnvironment().setNoClasspath(!extension.validateCode);
        final SourceSet sourceSet =
            getProject().getConvention().getPlugin(JavaPluginConvention.class).getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME);
        final SpoonCompiler compiler = spoon.createCompiler();
        compiler.setSourceClasspath(toPathArray(sourceSet.getCompileClasspath()));
        sourceSet.getAllJava().getSrcDirs().forEach(compiler::addInputSource);
        factory = compiler.getFactory();
        // Generate AST
        compiler.build();
        // Analyse AST
        final EventInterfaceProcessor processor = new EventInterfaceProcessor(extension);
        compiler.process(Collections.singletonList(processor));
        final Map<CtType<?>, Collection<? extends Property<CtTypeReference<?>, CtMethod<?>>>> foundProperties = processor.getFoundProperties();
        // Modify factory class AST
        final CtType<T> factoryClass = factory.Type().get(extension.outputFactory);
        factoryClass.getMethods().clear();
        for (Map.Entry<CtType<?>, Collection<? extends Property<CtTypeReference<?>, CtMethod<?>>>> entry : foundProperties.entrySet()) {
            final CtMethod<?> method = generateMethod(factoryClass, entry.getKey(), entry.getValue());
            factoryClass.addMethod(method);
        }
        // Output source code from AST
        final JavaOutputProcessor outputProcessor = new JavaOutputProcessor(factory, new File(extension.outputDir));
        outputProcessor.setWritePackageAnnotationFile(false);
        outputProcessor.createJavaFile(factoryClass);
    }

    private <T> CtMethod<T> generateMethod(CtType<?> factoryClass, CtType<T> event,
        Collection<? extends Property<CtTypeReference<?>, CtMethod<?>>> foundProperties) {

        final CtMethod<T> method = factory.Core().createMethod();
        method.setParent(factoryClass);
        method.addModifier(ModifierKind.PUBLIC);
        method.addModifier(ModifierKind.STATIC);
        method.setType(event.getReference());
        method.setSimpleName(generateMethodName(event));
        final Map<CtMethod<?>, CtParameter<?>> parameterMap = generateMethodParameters(method, foundProperties);
        List<CtParameter<?>> parameters = new ArrayList<>(parameterMap.values());
        method.setParameters(parameters);
        method.setBody(generateMethodBody(event, parameterMap));
        method.setDocComment(generateDocComment(event, parameters));
        return method;
    }

    @SuppressWarnings("rawtypes")
    private Map<CtMethod<?>, CtParameter<?>> generateMethodParameters(CtMethod<?> method,
        Collection<? extends Property<CtTypeReference<?>, CtMethod<?>>> properties) {

        properties = new PropertySorter(extension.sortPriorityPrefix, extension.groupingPrefixes).sortProperties(properties);
        return properties.stream().filter(EventImplGenTask::shouldAdd).collect(Collectors.toMap(
            Property::getMostSpecificMethod,
            property -> factory.Method().createParameter(method, property.getMostSpecificType(), property.getName()),
            (u, v) -> {
                throw new IllegalArgumentException("Found duplicate methods: " + u);
            },
            LinkedHashMap::new
        ));
    }

    @SuppressWarnings("rawtypes")
    private <T> CtBlock<T> generateMethodBody(CtType<T> event, Map<CtMethod<?>, CtParameter<?>> parameters) {

        final CtBlock<T> body = factory.Core().createBlock();
        // Preconditions.checkArgument(param1 == param2, String.format("Error message", targetEntity, goal.getOwner()));
        generatePreconditionsCheck(event, body, parameters);
        // new HashMap<>();
        final CtTypeReference<HashMap> hashMap = factory.Type().createReference(HashMap.class);
        final CtExecutableReference<HashMap> hashMapInit = factory.Executable().createReference(hashMap, hashMap, "<init>");
        final CtConstructorCall<HashMap> newHashMap = factory.Core().createConstructorCall();
        newHashMap.setType(hashMap);
        newHashMap.setExecutable(hashMapInit);
        newHashMap.setActualTypeArguments(Collections.emptyList());
        // HashMap<String, Object> values
        final CtTypeReference<String> string = factory.Type().createReference(String.class);
        final CtTypeReference<Object> object = factory.Type().createReference(Object.class);
        final CtTypeReference<HashMap> mapStringObject = factory.Type().createReference(HashMap.class);
        mapStringObject.setActualTypeArguments(Arrays.asList(string, object));
        // HashMap<String, Object> values = new HashMap<>();
        final CtLocalVariable<HashMap> mapValues = factory.Code().createLocalVariable(mapStringObject, "values", newHashMap);
        body.addStatement(mapValues);
        // values.put("param1", param1); values.put("param2", param2); ...
        final CtVariableAccess<HashMap> values = factory.Code().createVariableRead(mapValues.getReference(), false);
        for (CtParameter<?> parameter : parameters.values()) {
            final CtLiteral<String> key = factory.Code().createLiteral(parameter.getSimpleName());
            final CtVariableAccess<?> value = factory.Code().createVariableRead(parameter.getReference(), false);
            final CtExecutableReference<Object> put = factory.Executable().createReference(hashMap, false, object, "put");
            final CtInvocation<Object> valuesPut = factory.Code().createInvocation(values, put, key, value);
            body.addStatement(valuesPut);
        }
        // Split eventImplCreateMethod string into the declaring class and method name and create references to them
        final String eventImplCreateMethod = extension.eventImplCreateMethod;
        final int classMethodSeparator = eventImplCreateMethod.lastIndexOf('.');
        final CtTypeReference<?> eventImplCreationClass = factory.Type().createReference(eventImplCreateMethod.substring(0, classMethodSeparator));
        final CtExecutableReference<T> createEventImpl = factory.Executable().createReference(eventImplCreationClass, true, event.getReference(),
            eventImplCreateMethod.substring(classMethodSeparator + 1), factory.Type().createReference(Class.class), hashMap);
        // Event.class
        final CtFieldAccess<? extends Class<T>> eventClass = factory.Code().createClassAccess(event.getReference());
        // createEventImpl(Event.class, values)
        final CtLiteral<Object> _null = factory.Code().createLiteral(null);
        final CtInvocation<T> createEventImplValues = factory.Code().createInvocation(_null, createEventImpl, eventClass, values);
        // return createEventImpl(Event.class, values);
        final CtReturn<T> _return = factory.Core().createReturn();
        _return.setReturnedExpression(createEventImplValues);
        body.addStatement(_return);
        return body;
    }

    private void generatePreconditionsCheck(CtType<?> event, CtBlock<?> body, Map<CtMethod<?>, CtParameter<?>> parameters) {
        final FactoryCodeCheck check = event.getAnnotation(FactoryCodeCheck.class);
        if (check == null) {
            return;
        }
        final List<CtMethod<?>> annotations = findCompareAnnotations(check.value(), event);
        if (annotations.size() != 2) {
            throw new IllegalStateException(String.format("@FactoryCodeCheck annotation is present on interface %s, but a @CompareTo annotation"
                + " pair was not found! Instead, the following annotated methods were found: %s", event.getQualifiedName(), annotations));
        }
        annotations.sort((c1, c2) -> c1.getAnnotation(CompareTo.class).position() - c2.getAnnotation(CompareTo.class).position());
        final CtMethod<?> method1 = annotations.get(0);
        final CtMethod<?> method2 = annotations.get(1);
        final CtParameter<?> param1 = parameters.get(method1);
        final CtParameter<?> param2 = parameters.get(method2);
        // Preconditions.checkArgument(...)
        final CtTypeReference<Preconditions> preconditions = factory.Type().createReference(Preconditions.class);
        final CtExecutableReference<Void> checkArgument =
            factory.Executable().createReference(preconditions, true, factory.Type().VOID_PRIMITIVE, "checkArgument");
        // String.format("Error message", param1, param2)
        final CtExecutableReference<String> format = factory.Executable()
            .createReference(factory.Type().STRING, true, factory.Type().STRING, "format");
        final CtInvocation<String> stringFormat = factory.Code()
            .createInvocation(factory.Code().createLiteral(null), format, factory.Code().createLiteral(check.errorMessage()),
                factory.Code().createVariableRead(param1.getReference(), false), factory.Code().createVariableRead(param2.getReference(), false));
        // param1 == param2
        final CtBinaryOperator<Boolean> equalityCheck = factory.Core().createBinaryOperator();
        equalityCheck.setKind(BinaryOperatorKind.EQ);
        equalityCheck.setLeftHandOperand(getInvocation(method1, param1));
        equalityCheck.setRightHandOperand(getInvocation(method2, param2));
        // Preconditions.checkArgument(param1 == param2, String.format("Error message", param1, param2));
        final CtInvocation<Void> preconditionsCheckArgument =
            factory.Code().createInvocation(factory.Code().createLiteral(null), checkArgument, equalityCheck, stringFormat);
        body.addStatement(preconditionsCheckArgument);
    }

    private CtExpression<?> getInvocation(CtMethod<?> method, CtParameter<?> parameter) {
        final CompareTo compareTo = method.getAnnotation(CompareTo.class);
        final CtVariableAccess<?> parameterRead = factory.Code().createVariableRead(parameter.getReference(), false);
        if (compareTo.method().isEmpty()) {
            return parameterRead;
        }
        final CtType<?> methodDeclaringClass = method.getType().getDeclaration();
        final CtMethod<?> compareToMethod = methodDeclaringClass.getMethod(compareTo.method());
        if (compareToMethod == null) {
            throw new IllegalStateException(
                String.format("Unable to find method %s on type %s", compareTo.method(), methodDeclaringClass.getQualifiedName()));
        }
        return factory.Code().createInvocation(parameterRead, compareToMethod.getReference());
    }

    private static boolean shouldAdd(Property<CtTypeReference<?>, CtMethod<?>> property) {
        if (!property.isMostSpecificType()) {
            return false;
        }
        final PropertySettings settings = property.getAccessor().getAnnotation(PropertySettings.class);
        return settings == null || settings.requiredParameter();
    }

    private static List<CtMethod<?>> findCompareAnnotations(String name, CtType<?> event) {
        return event.getAllMethods().stream()
            .filter(method -> method.getAnnotation(CompareTo.class) != null && method.getAnnotation(CompareTo.class).value().equals(name))
            .collect(Collectors.toList());
    }

    private static String generateDocComment(CtType<?> event, List<CtParameter<?>> parameters) {
        final StringBuilder comment = new StringBuilder();
        comment.append("AUTOMATICALLY GENERATED, DO NOT EDIT.\n");
        comment.append("Creates a new instance of\n");
        comment.append("{@link ").append(event.getQualifiedName().replace('$', '.')).append("}.\n");
        comment.append('\n');
        for (CtParameter<?> parameter : parameters) {
            final String name = parameter.getSimpleName();
            comment.append("@param ").append(name).append(" The ").append(camelCaseToWords(name)).append('\n');
        }
        comment.append("@return A new ");
        do {
            comment.append(camelCaseToWords(event.getSimpleName())).append(' ');
            event = event.getDeclaringType();
        } while (event != null);
        return comment.toString();
    }

    private static String generateMethodName(CtType<?> event) {
        final StringBuilder name = new StringBuilder();
        do {
            name.insert(0, event.getSimpleName());
            event = event.getDeclaringType();
        } while (event != null);
        name.insert(0, "create");
        return name.toString();
    }

    private static String camelCaseToWords(String camelCase) {
        final StringBuilder words = new StringBuilder();
        int nextUppercase = camelCase.length();
        for (int i = 1; i < nextUppercase; i++) {
            if (Character.isUpperCase(camelCase.charAt(i))) {
                nextUppercase = i;
                break;
            }
        }
        words.append(Character.toLowerCase(camelCase.charAt(0)));
        words.append(camelCase.substring(1, nextUppercase));
        if (nextUppercase < camelCase.length()) {
            words.append(' ');
            words.append(camelCaseToWords(camelCase.substring(nextUppercase)));
        }
        return words.toString();
    }

    private static String[] toPathArray(FileCollection fileCollection) {
        final Set<File> files = fileCollection.getFiles();
        final String[] strings = new String[files.size()];
        int i = 0;
        for (File file : files) {
            strings[i++] = file.getAbsolutePath();
        }
        return strings;
    }

}
