/*
 * This file is part of Event Implementation Generator, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2015-2015 SpongePowered <http://spongepowered.org/>
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
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskAction;
import spoon.Launcher;
import spoon.SpoonAPI;
import spoon.compiler.Environment;
import spoon.compiler.SpoonCompiler;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtFieldAccess;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtReturn;
import spoon.reflect.code.CtVariableAccess;
import spoon.reflect.declaration.CtInterface;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.CtType;
import spoon.reflect.declaration.ModifierKind;
import spoon.reflect.factory.Factory;
import spoon.reflect.factory.MethodFactory;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.DefaultJavaPrettyPrinter;
import spoon.support.JavaOutputProcessor;

import java.io.File;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class EventImplGenTask extends DefaultTask {

    private static final String EVENT_CLASS_PROCESSOR = EventInterfaceProcessor.class.getCanonicalName();
    private final SpoonAPI spoon = new Launcher();

    public EventImplGenTask() {
        spoon.addProcessor(EVENT_CLASS_PROCESSOR);
        final Environment environment = spoon.getEnvironment();
        environment.setComplianceLevel(6);
        environment.setGenerateJavadoc(true);
    }

    @SuppressWarnings("unchecked")
    @TaskAction
    public void task() {
        // Configure AST generator
        final EventImplGenExtension extension = getProject().getExtensions().getByType(EventImplGenExtension.class);
        Preconditions.checkState(!extension.eventImplCreateMethod.isEmpty(), "Gradle property eventImplCreateMethod isn't defined");
        spoon.getEnvironment().setNoClasspath(!extension.validateCode);
        final SourceSet sourceSet =
            getProject().getConvention().getPlugin(JavaPluginConvention.class).getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME);
        final SpoonCompiler compiler = spoon.createCompiler();
        compiler.setSourceClasspath(toStringArray(sourceSet.getCompileClasspath()));
        for (File sourceFile : sourceSet.getAllJava().getSrcDirs()) {
            compiler.addInputSource(sourceFile);
        }
        final Factory factory = compiler.getFactory();
        final ObjectProcessorProperties properties = new ObjectProcessorProperties(EVENT_CLASS_PROCESSOR);
        properties.put("extension", extension);
        properties.put("logger", getLogger());
        spoon.getEnvironment().setProcessorProperties(EVENT_CLASS_PROCESSOR, properties);
        // Generate AST
        compiler.build();
        // Analyse AST
        compiler.process(Collections.singletonList(EVENT_CLASS_PROCESSOR));
        // Modify factory class AST
        final CtType<?> factoryClass = factory.Type().get(extension.outputFactory);
        final Map<CtInterface<?>, Map<String, CtTypeReference<?>>> eventFields = properties.get(Map.class, "eventFields");
        for (CtInterface<?> event : eventFields.keySet()) {
            final CtMethod<?> method = factory.Core().createMethod();
            method.setParent(factoryClass);
            method.addModifier(ModifierKind.PUBLIC);
            method.addModifier(ModifierKind.STATIC);
            method.setType((CtTypeReference) event.getReference());
            final String name = generateMethodName(event);
            method.setSimpleName(name);
            final List<CtParameter<?>> parameters = generateMethodParameters(factory.Method(), eventFields, event);
            method.setParameters(parameters);
            method.setBody((CtBlock) generateMethodBody(factory, factoryClass, extension.eventImplCreateMethod, event, parameters));
            method.setDocComment(generateDocComment(event, parameters));
            removeMethodsByName(factoryClass, name);
            factoryClass.addMethod(method);
        }
        // Output source code from AST
        final JavaOutputProcessor outputProcessor =
            new JavaOutputProcessor(new File(extension.outputDir), new DefaultJavaPrettyPrinter(factory.getEnvironment()));
        outputProcessor.setFactory(factory);
        outputProcessor.createJavaFile(factoryClass);
    }

    private String generateMethodName(CtType<?> event) {
        final StringBuilder name = new StringBuilder();
        do {
            name.insert(0, event.getSimpleName());
            event = event.getDeclaringType();
        } while (event != null);
        name.insert(0, "create");
        return name.toString();
    }

    private static List<CtParameter<?>> generateMethodParameters(MethodFactory factory, Map<CtInterface<?>, Map<String, CtTypeReference<?>>>
        eventFields, CtInterface<?> event) {
        final Set<CtParameter<?>> parameters = Sets.newLinkedHashSet();
        addMethodParameters(factory, parameters, eventFields, event);
        return Lists.newArrayList(parameters);
    }

    private static void addMethodParameters(MethodFactory factory, Set<CtParameter<?>> parameters,
        Map<CtInterface<?>, Map<String, CtTypeReference<?>>> eventFields, CtInterface<?> event) {
        final Map<String, CtTypeReference<?>> fields = eventFields.get(event);
        if (fields == null) {
            return;
        }
        for (CtTypeReference<?> superEventReference : event.getSuperInterfaces()) {
            final CtInterface<?> superEvent = (CtInterface<?>) superEventReference.getDeclaration();
            addMethodParameters(factory, parameters, eventFields, superEvent);
        }
        for (Map.Entry<String, CtTypeReference<?>> parameter : fields.entrySet()) {
            parameters.add(factory.createParameter(null, parameter.getValue(), parameter.getKey()));
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static CtBlock<?> generateMethodBody(Factory factory, CtType<?> factoryClass, String eventImplCreationMethod, CtInterface<?> event,
        List<CtParameter<?>> parameters) {
        final CtBlock<?> body = factory.Core().createBlock();
        // Map<String, Object> values = Maps.newHashMap();
        final CtTypeReference<Maps> maps = factory.Type().createReference(Maps.class);
        final CtTypeReference<Map> map = factory.Type().createReference(Map.class);
        final CtExecutableReference<Map> newHashMap = factory.Method().createReference(maps, true, map, "newHashMap");
        final CtLiteral<Object> _null = factory.Code().createLiteral(null);
        final CtInvocation<Map> mapsNewHashMap = factory.Code().createInvocation(_null, newHashMap);
        final CtTypeReference<Map> mapStringObject = factory.Type().createReference(Map.class);
        final CtTypeReference<Object> object = factory.Type().createReference(Object.class);
        mapStringObject.setActualTypeArguments(Lists.newArrayList(factory.Type().createReference(String.class), object));
        final CtLocalVariable<Map> mapValues = factory.Code().createLocalVariable(mapStringObject, "values", mapsNewHashMap);
        body.addStatement(mapValues);
        // values.put("param1", param1); values.put("param2", param2); ...
        final CtVariableAccess<Map> values = factory.Code().createVariableRead(mapValues.getReference(), false);
        for (CtParameter<?> parameter : parameters) {
            final CtLiteral<String> key = factory.Code().createLiteral(parameter.getSimpleName());
            final CtVariableAccess<?> value = factory.Code().createVariableRead(parameter.getReference(), false);
            final CtExecutableReference<Object> put = factory.Method().createReference(map, false, object, "put");
            final CtInvocation<Object> valuesPut = factory.Code().createInvocation(values, put, key, value);
            body.addStatement(valuesPut);
        }
        // return createEventImpl(Event.class, values);
        final CtExecutableReference<?> createEventImpl = factory.Method().createReference(factoryClass.getReference(), true, event.getReference(),
            eventImplCreationMethod, factory.Type().createReference(Class.class), map);
        final CtFieldAccess<? extends Class<?>> eventClass = factory.Code().createClassAccess(event.getReference());
        final CtInvocation<?> createEventImplValues = factory.Code().createInvocation(_null, createEventImpl, eventClass, values);
        final CtReturn<?> _return = factory.Core().createReturn();
        _return.setReturnedExpression((CtInvocation) createEventImplValues);
        body.addStatement(_return);
        return body;
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

    private static void removeMethodsByName(CtType<?> type, String name) {
        for (Iterator<CtMethod<?>> iterator = type.getMethods().iterator(); iterator.hasNext(); ) {
            if (iterator.next().getSimpleName().equals(name)) {
                iterator.remove();
            }
        }
    }

    private static String[] toStringArray(FileCollection fileCollection) {
        final Set<File> files = fileCollection.getFiles();
        final String[] strings = new String[files.size()];
        int i = 0;
        for (File file : files) {
            strings[i++] = file.getAbsolutePath();
        }
        return strings;
    }

}
