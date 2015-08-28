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

import com.google.common.collect.Maps;
import org.gradle.api.logging.Logger;
import spoon.processing.AbstractProcessor;
import spoon.reflect.declaration.CtInterface;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.processing.XmlProcessorProperties;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class EventInterfaceProcessor extends AbstractProcessor<CtInterface<?>> {

    private final Map<CtInterface<?>, Map<String, CtTypeReference<?>>> generatedFields = Maps.newTreeMap(new Comparator<CtInterface<?>>() {

        @Override
        public int compare(CtInterface<?> o1, CtInterface<?> o2) {
            return o1.getQualifiedName().compareTo(o2.getQualifiedName());
        }

    });
    private EventImplGenExtension extension;
    private Logger logger;

    @Override
    public void init() {
        try {
            final XmlProcessorProperties properties = (XmlProcessorProperties) getEnvironment().getProcessorProperties(getClass().getCanonicalName());
            properties.addProperty("generatedFields", generatedFields);
            extension = Util.getProperty(properties, "extension");
            logger = Util.getProperty(properties, "logger");
        } catch (Exception exception) {
            exception.printStackTrace();
            throw new RuntimeException(exception);
        }
    }

    @Override
    public boolean isToBeProcessed(CtInterface<?> candidate) {
        return extension.isIncluded(candidate.getPosition().getCompilationUnit().getFile());
    }

    @Override
    public void process(CtInterface<?> element) {
        final Map<String, CtTypeReference<?>> fields = Maps.newLinkedHashMap();
        for (CtMethod<?> method : element.getMethods()) {
            String fieldName = null;
            CtTypeReference<?> fieldType = null;
            for (MethodType methodType : MethodType.values()) {
                if (methodType.matches(method)) {
                    fieldName = methodType.generateFieldName(method);
                    fieldType = methodType.extractFieldType(method);
                    break;
                }
            }
            if (fieldName == null || fieldName.isEmpty()) {
                logger.warn("Unknown method type " + method.getSignature() + " in " + element.getQualifiedName());
            } else {
                final CtTypeReference<?> existingFieldType = fields.get(fieldName);
                if (existingFieldType != null) {
                    if (!fieldType.equals(existingFieldType)) {
                        logger.warn(
                            "Conflicting types " + existingFieldType.getQualifiedName() + " and " + fieldType.getQualifiedName() + " for field name "
                                + fieldName + " in " + element.getQualifiedName());
                    }
                } else {
                    fields.put(fieldName, fieldType);
                }
            }
        }
        generatedFields.put(element, fields);
    }

    private enum MethodType {

        GETTER("get") {
            @Override
            protected boolean matches(CtMethod<?> method) {
                return method.getSimpleName().startsWith(prefix) || method.getParameters().isEmpty();
            }

            @Override
            protected CtTypeReference<?> extractFieldType(CtMethod<?> method) {
                CtTypeReference<?> type = method.getType();
                // Unbox optionals
                if (type.getSimpleName().equals("Optional")) {
                    final List<CtTypeReference<?>> generics = type.getActualTypeArguments();
                    if (generics.size() == 1) {
                        type = generics.get(0);
                    }
                }
                return type;
            }

        },
        SETTER("set") {
            @Override
            protected boolean matches(CtMethod<?> method) {
                return method.getSimpleName().startsWith(prefix) && method.getParameters().size() == 1;
            }

            @Override
            protected CtTypeReference<?> extractFieldType(CtMethod<?> method) {
                return method.getParameters().get(0).getType();
            }

        },
        FILTER("filter") {
            @Override
            protected boolean matches(CtMethod<?> method) {
                return method.getSimpleName().startsWith(prefix) && method.getParameters().size() == 1;
            }

            @Override
            protected CtTypeReference<?> extractFieldType(CtMethod<?> method) {
                return method.getType();
            }

        };

        protected final String prefix;

        MethodType(String prefix) {
            this.prefix = prefix;
        }

        protected abstract boolean matches(CtMethod<?> method);

        protected abstract CtTypeReference<?> extractFieldType(CtMethod<?> method);

        private String generateFieldName(CtMethod<?> method) {
            String name = method.getSimpleName();
            if (name.startsWith(prefix)) {
                name = name.substring(prefix.length());
            }
            return Character.toLowerCase(name.charAt(0)) + name.substring(1);
        }

    }

}
