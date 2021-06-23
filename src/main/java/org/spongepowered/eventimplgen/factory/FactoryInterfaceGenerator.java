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

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;
import org.spongepowered.eventimplgen.eventgencore.Property;
import org.spongepowered.eventimplgen.eventgencore.PropertySorter;

import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeVariable;

@Singleton
public class FactoryInterfaceGenerator {

    private final ClassGenerator generator;

    @Inject
    FactoryInterfaceGenerator(final ClassGenerator generator) {
        this.generator = generator;
    }

    public JavaFile createClass(
            final String name,
            final Map<TypeElement, List<Property>> foundProperties,
            final PropertySorter sorter,
            final List<ExecutableElement> forwardedMethods) {
        final ClassName clazz = ClassName.bestGuess(name);
        final TypeSpec.Builder factoryClass = TypeSpec.classBuilder(clazz.topLevelClassName())
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addMethod(MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PRIVATE)
                .build());

        for (final Map.Entry<TypeElement, List<Property>> event : foundProperties.entrySet()) {
            factoryClass.addOriginatingElement(event.getKey());
            factoryClass.addMethod(this.generateRealImpl(
                 event.getKey(),
                 this.generator.qualifiedName(event.getKey()),
                 this.generator.getRequiredProperties(sorter.sortProperties(event.getValue()))
            ));
        }

        for (final ExecutableElement forwardedMethod : forwardedMethods) {
            factoryClass.addOriginatingElement(forwardedMethod);
            factoryClass.addMethod(this.generateForwardingMethod(forwardedMethod));
        }

        return JavaFile.builder(clazz.packageName(), factoryClass.build()).build();
    }

    private MethodSpec generateForwardingMethod(final ExecutableElement targetMethod) {
        final MethodSpec.Builder spec = MethodSpec.methodBuilder(targetMethod.getSimpleName().toString())
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .returns(TypeName.get(targetMethod.getReturnType()));

        for (final TypeParameterElement param : targetMethod.getTypeParameters()) {
            spec.addTypeVariable(TypeVariableName.get((TypeVariable) param.asType()));
        }

        final StringBuilder params = new StringBuilder();
        for (final VariableElement parameter : targetMethod.getParameters()) {
            spec.addParameter(TypeName.get(parameter.asType()), parameter.getSimpleName().toString(), Modifier.FINAL);
            if (params.length() > 0) {
                params.append(", ");
            }
            params.append(parameter.getSimpleName().toString());
        }

        spec.addCode("return $T.$L($L);", TypeName.get(targetMethod.getEnclosingElement().asType()), targetMethod.getSimpleName().toString(), params.toString());

        return spec.build();
    }

    private MethodSpec generateRealImpl(final TypeElement event, final String eventName, final List<Property> params) {
        final MethodSpec.Builder spec = MethodSpec.methodBuilder(FactoryInterfaceGenerator.generateMethodName(event))
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .returns(TypeName.get(event.asType()));

        for (final TypeParameterElement param : event.getTypeParameters()) {
            spec.addTypeVariable(TypeVariableName.get((TypeVariable) param.asType()));
        }

        final StringBuilder paramNames = new StringBuilder();
        for (final Property property : params) {
            spec.addParameter(TypeName.get(property.getType()), property.getName(), Modifier.FINAL);
            if (paramNames.length() > 0) {
                paramNames.append(", ");
            }
            paramNames.append(property.getName());
        }

        final String pkgName;
        final String simpleName;
        final int lastDot = eventName.lastIndexOf('.');
        if (lastDot > -1) {
            pkgName = eventName.substring(0, lastDot);
            simpleName = eventName.substring(lastDot + 1);
        } else {
            pkgName = "";
            simpleName = eventName;
        }
        final TypeName implType = ClassName.get(pkgName, simpleName);
        spec.addCode("return new $T($L);", implType, paramNames.toString());

        return spec.build();
    }

    public static String generateMethodName(TypeElement event) {
        final StringBuilder name = new StringBuilder();
        do {
            name.insert(0, event.getSimpleName());
            final ElementKind kind = event.getEnclosingElement().getKind();
            event = kind.isClass() || kind.isInterface() ? (TypeElement) event.getEnclosingElement() : null;
        } while (event != null);
        name.insert(0, "create");
        return name.toString();
    }

}
