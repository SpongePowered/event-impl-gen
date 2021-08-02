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
package org.spongepowered.eventimplgen.factory.plugin;

import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import org.spongepowered.api.util.annotation.eventgen.TransformResult;
import org.spongepowered.api.util.annotation.eventgen.TransformWith;
import org.spongepowered.eventimplgen.eventgencore.Property;
import org.spongepowered.eventimplgen.factory.ClassContext;

import java.util.Objects;

import javax.annotation.processing.Messager;
import javax.inject.Inject;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;

/**
 * An event factory plugin to modify the return type of an accessor
 * by calling one of its methods.
 */
public class AccessorModifierEventFactoryPlugin implements EventFactoryPlugin {

    private final Types types;
    private final Messager messager;

    @Inject
    AccessorModifierEventFactoryPlugin(final Types types, final Messager messager) {
        this.types = types;
        this.messager = messager;
    }

    private MethodPair getLinkedField(final Property property) {
        final ExecutableElement leastSpecificMethod = property.getLeastSpecificMethod();
        final TransformResult transformResult;
        ExecutableElement transformWith = null;
        String name = null;

        if ((transformResult = leastSpecificMethod.getAnnotation(TransformResult.class)) != null) {
            name = transformResult.value();
            // Since we that the modifier method (the one annotated with TransformWith) doesn't
            // use covariant types, we can call getMethods on the more specific version,
            // allowing the annotation to be present on a method defined there, as well as in
            // the least specific type.
            for (final ExecutableElement method : ElementFilter.methodsIn(this.types.asElement(property.getAccessor().getReturnType()).getEnclosedElements())) {
                final TransformWith annotation = method.getAnnotation(TransformWith.class);
                if (annotation != null && Objects.equals(annotation.value(), name)) {
                    if (transformWith != null) {
                        this.messager.printMessage(Diagnostic.Kind.ERROR, "Multiple @TransformResult annotations were found with the name "
                                + name + ". One of them needs to be changed!", method);
                        return MethodPair.FAILED;
                    }
                    transformWith = method;
                }
            }
            if (transformWith == null) {
                this.messager.printMessage(Diagnostic.Kind.ERROR, "Unable to locate a matching @TransformWith annotation with the name "
                        + name + " for this method", property.getAccessor());
                return MethodPair.FAILED;
            }
        }

        if (transformWith != null) {
            return new MethodPair(name, transformWith, property);
        }
        return null;
    }

    private void generateTransformingAccessor(final ClassContext cw, final MethodPair pair, final Property property) {
        final ExecutableElement accessor = property.getAccessor();
        final ExecutableElement transformerMethod = pair.getTransformerMethod();
        cw.addMethod(MethodSpec.methodBuilder(accessor.getSimpleName().toString())
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(Override.class)
            .returns(TypeName.get(property.getType()))
            .addStatement("return this.$L(this.$L)", transformerMethod.getSimpleName().toString(), property.getName()));
    }

    @Override
    public Result contributeProperty(final TypeElement eventClass, final ClassContext classWriter, final Property property) {
        final MethodPair methodPair = this.getLinkedField(property);
        if (methodPair == null) {
            return Result.IGNORE;
        } else if (methodPair == MethodPair.FAILED) {
            return Result.FAILURE;
        }

        classWriter.addField(property);
        if (property.getMutator().isPresent()) {
            classWriter.addMutator(eventClass, property.getName(), property);
        }

        this.generateTransformingAccessor(classWriter, methodPair, property);

        return Result.SUCCESSS;
    }

    static final class MethodPair {

        static final MethodPair FAILED = new MethodPair("error", null, null);

        private final String name;
        private final ExecutableElement transformerMethod;
        private final Property property;

        /**
         * Creates a new {@link MethodPair}.
         *
         * @param name The name
         * @param transformerMethod The transformer method
         * @param property The property
         */
        public MethodPair(final String name, final ExecutableElement transformerMethod, final Property property) {
            this.name = name;
            this.transformerMethod = transformerMethod;
            this.property = property;
        }

        public String getName() {
            return this.name;
        }

        public ExecutableElement getTransformerMethod() {
            return this.transformerMethod;
        }

        public Property getProperty() {
            return this.property;
        }

    }

}
