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

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.gradle.api.DefaultTask;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskAction;
import spoon.Launcher;
import spoon.SpoonAPI;
import spoon.compiler.Environment;
import spoon.compiler.SpoonCompiler;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtReturn;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtInterface;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.CtType;
import spoon.reflect.declaration.ModifierKind;
import spoon.reflect.factory.Factory;
import spoon.reflect.factory.MethodFactory;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.DefaultJavaPrettyPrinter;
import spoon.support.JavaOutputProcessor;
import spoon.support.processing.XmlProcessorProperties;

import java.io.File;
import java.util.Collections;
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
        final SourceSet sourceSet =
            getProject().getConvention().getPlugin(JavaPluginConvention.class).getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME);
        final SpoonCompiler compiler = spoon.createCompiler();
        compiler.setSourceClasspath(Util.toStringArray(sourceSet.getCompileClasspath()));
        for (File sourceFile : sourceSet.getAllJava().getSrcDirs()) {
            compiler.addInputSource(sourceFile);
        }
        final Factory factory = compiler.getFactory();
        final XmlProcessorProperties properties = new XmlProcessorProperties(factory, EVENT_CLASS_PROCESSOR);
        final EventImplGenExtension extension = getProject().getExtensions().getByType(EventImplGenExtension.class);
        properties.addProperty("extension", extension);
        properties.addProperty("logger", getLogger());
        spoon.getEnvironment().setProcessorProperties(EVENT_CLASS_PROCESSOR, properties);
        // Generate AST
        compiler.build();
        // Analyse AST
        compiler.process(Collections.singletonList(EVENT_CLASS_PROCESSOR));
        // Generate factory class AST
        final CtClass<?> factoryClass = factory.Class().create(extension.outputFactory);
        factoryClass.addModifier(ModifierKind.PUBLIC);
        factoryClass.addModifier(ModifierKind.FINAL);
        final Map<CtInterface<?>, Map<String, CtTypeReference<?>>> eventFields = Util.getProperty(properties, "eventFields");
        for (CtInterface<?> event : eventFields.keySet()) {
            final CtMethod<?> method = factory.Core().createMethod();
            method.addModifier(ModifierKind.PUBLIC);
            method.addModifier(ModifierKind.STATIC);
            method.setType((CtTypeReference) event.getReference());
            method.setSimpleName(generateMethodName(event));
            method.setParameters(generateConstructorParameters(factory.Method(), eventFields, event));
            final CtBlock<?> body = factory.Core().createBlock();
            final CtReturn<Object> _return = factory.Core().createReturn();
            _return.setReturnedExpression(factory.Code().createLiteral(null));
            body.addStatement(_return);
            method.setBody((CtBlock) body);
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

    private static List<CtParameter<?>> generateConstructorParameters(MethodFactory factory, Map<CtInterface<?>, Map<String, CtTypeReference<?>>>
        eventFields, CtInterface<?> event) {
        final Set<CtParameter<?>> parameters = Sets.newLinkedHashSet();
        addConstructorParameters(factory, parameters, eventFields, event);
        return Lists.newArrayList(parameters);
    }

    private static void addConstructorParameters(MethodFactory factory, Set<CtParameter<?>> parameters,
        Map<CtInterface<?>, Map<String, CtTypeReference<?>>> eventFields, CtInterface<?> event) {
        final Map<String, CtTypeReference<?>> fields = eventFields.get(event);
        if (fields == null) {
            return;
        }
        for (CtTypeReference<?> superEventReference : event.getSuperInterfaces()) {
            final CtInterface<?> superEvent = (CtInterface<?>) superEventReference.getDeclaration();
            addConstructorParameters(factory, parameters, eventFields, superEvent);
        }
        for (Map.Entry<String, CtTypeReference<?>> parameter : fields.entrySet()) {
            parameters.add(factory.createParameter(null, parameter.getValue(), parameter.getKey()));
        }
    }

}
