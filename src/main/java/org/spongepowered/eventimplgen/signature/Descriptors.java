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
package org.spongepowered.eventimplgen.signature;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.spongepowered.eventimplgen.eventgencore.Property;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

public class Descriptors {
    public static final String TYPE_UNKNOWN = "eventImplGen/UNKNOWN";
    public static final String TYPE_ERROR = "eventImplGen/ERROR";

    private final Elements elements;
    private final Types types;
    private final TypeToDescriptorWriter descWriter;

    @Inject
    Descriptors(final Elements elements, final Types types, final TypeToDescriptorWriter descWriter) {
        this.elements = elements;
        this.types = types;
        this.descWriter = descWriter;
    }

    public String getDescriptor(final List<Property> properties, final @Nullable TypeMirror returnType) {
        final StringBuilder builder = new StringBuilder();
        builder.append("(");
        for (final Property property : properties) {
            property.getType().accept(this.descWriter, builder);
        }
        builder.append(")");
        if (returnType != null) {
            returnType.accept(this.descWriter, builder);
        } else {
            builder.append("V");
        }
        return builder.toString();
    }

    public String getDescriptor(final ExecutableElement method) {
        return this.getDescriptor(method, true);
    }

    public String getDescriptor(final ExecutableElement method, final boolean includeReturnType) {
        if (includeReturnType) {
            return method.asType().accept(this.descWriter, new StringBuilder()).toString();
        } else {
            final StringBuilder builder = new StringBuilder();
            builder.append('(');
            for (final VariableElement type : method.getParameters()) {
                type.asType().accept(this.descWriter, builder);
            }
            builder.append(")V");
            return builder.toString();
        }
    }

    public static TypeMirror[] getParameterTypes(final ExecutableElement method) {
        final List<TypeMirror> types = new ArrayList<>();
        for (final VariableElement parameter: method.getParameters()) {
            types.add(parameter.asType());
        }
        return types.toArray(new TypeMirror[0]);
    }

    public String getTypeDescriptor(TypeMirror type) {
        // For descriptors, we want the actual type
        /*if (type.getKind() == TypeKind.TYPEVAR) {
            type = ((TypeVariable) type).getUpperBound();
        }*/
        type = this.types.erasure(type);

        if (type.getKind().isPrimitive()) {
            return String.valueOf(TypeToDescriptorWriter.descriptor((PrimitiveType) type));
        } else {
            return type.accept(this.descWriter, new StringBuilder()).toString();
        }
    }

    public String getInternalName(TypeMirror name) {
        name = this.types.erasure(name);
        switch (name.getKind()) {
            case DECLARED:
                return this.getInternalName(this.elements.getBinaryName((TypeElement) ((DeclaredType) name).asElement()).toString());
            case ERROR:
                return Descriptors.TYPE_ERROR;
            default:
                return Descriptors.TYPE_UNKNOWN;
        }
    }

    public String getInternalName(final String name) {
        return name.replace('.', '/');
    }

}