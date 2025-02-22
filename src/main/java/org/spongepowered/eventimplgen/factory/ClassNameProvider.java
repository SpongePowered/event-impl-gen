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

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeName;
import org.spongepowered.eventimplgen.processor.EventGenOptions;

import javax.inject.Inject;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import java.util.ArrayList;
import java.util.List;

/**
 * Provide class names for event interface generation.
 */
public class ClassNameProvider {

    private final String targetPackage;

    @Inject
    public ClassNameProvider(final EventGenOptions options) {
        final String outputFactory = options.generatedEventFactory();
        this.targetPackage = outputFactory.substring(0, outputFactory.lastIndexOf('.'));
    }

    /**
     * Get the canonical name used for a generated event class.
     *
     * @param clazz The class
     * @param classifier The classifier
     * @return Canonical name
     */
    public ClassName getClassName(final TypeElement clazz, final String classifier) {
        final StringBuilder name = new StringBuilder();
        final int startIdx = name.length();
        for (Element target = clazz; target != null && (target.getKind().isClass() || target.getKind().isInterface()); target = target.getEnclosingElement()) {
            if (name.length() > startIdx) {
                name.insert(startIdx, '_');
            }
            name.insert(startIdx, target.getSimpleName());
        }
        return ClassName.get(this.targetPackage, name
            .append('_')
            .append(classifier)
            .toString());
    }

    public TypeName getImplementingInterfaceName(final TypeElement clazz) {
        var original = TypeName.get(clazz.asType());
        // In the off chance
        if (!clazz.getNestingKind().isNested()) {
            return original;
        }
        if (!clazz.getTypeParameters().isEmpty()) {
            final List<TypeName> typeArguments = new ArrayList<>();
            for (var typeParameter : clazz.getTypeParameters()) {
                typeArguments.add(TypeName.get(typeParameter.asType()));
            }
            final var ifaceName = ClassName.get(clazz);

            return ParameterizedTypeName.get(ifaceName, typeArguments.toArray(new TypeName[0]));
        }
        return ClassName.get(clazz);
    }

}
