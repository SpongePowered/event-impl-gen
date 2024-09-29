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

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ErrorType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.IntersectionType;
import javax.lang.model.type.NoType;
import javax.lang.model.type.NullType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.UnionType;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.AbstractTypeVisitor14;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

/**
 * A writer to convert a {@link javax.lang.model.type.TypeMirror} into its descriptor string.
 */
@Singleton
public class TypeToDescriptorWriter extends AbstractTypeVisitor14<StringBuilder, StringBuilder> {

    private final Types types;
    private final Elements elements;

    @Inject
    TypeToDescriptorWriter(final Types types, final Elements elements) {
        this.types = types;
        this.elements = elements;
    }

    static char descriptor(final PrimitiveType primitive) {
        return switch (primitive.getKind()) {
            case BOOLEAN -> 'Z';
            case BYTE -> 'B';
            case SHORT -> 'S';
            case INT -> 'I';
            case LONG -> 'J';
            case CHAR -> 'C';
            case FLOAT -> 'F';
            case DOUBLE -> 'D';
            default -> throw new IllegalArgumentException("Unknown primitive type kind " + primitive.getKind());
        };
    }

    @Override
    public StringBuilder visitIntersection(final IntersectionType t, final StringBuilder builder) {
        return this.types.erasure(t).accept(this, builder); // todo: is this right?
    }

    @Override
    public StringBuilder visitPrimitive(final PrimitiveType t, final StringBuilder builder) {
        return builder.append(TypeToDescriptorWriter.descriptor(t));
    }

    @Override
    public StringBuilder visitArray(final ArrayType t, final StringBuilder builder) {
        builder.append('[');
        return t.getComponentType().accept(this, builder);
    }

    @Override
    public StringBuilder visitDeclared(final DeclaredType t, final StringBuilder builder) {
        return builder.append('L')
            .append(this.elements.getBinaryName((TypeElement) t.asElement()).toString().replace('.', '/'))
            .append(';');
    }

    @Override
    public StringBuilder visitTypeVariable(final TypeVariable t, final StringBuilder builder) {
        return t.getUpperBound().accept(this, builder);
    }

    @Override
    public StringBuilder visitWildcard(final WildcardType t, final StringBuilder builder) {
        if (t.getExtendsBound() != null) { // upper bound declared
            t.getExtendsBound().accept(this, builder);
        } else { // no (or only lower) bound, j/l/Object
            this.elements.getTypeElement("java.lang.Object").asType().accept(this, builder);
        }
        return builder;
    }

    @Override
    public StringBuilder visitExecutable(final ExecutableType t, final StringBuilder builder) {
        builder.append('(');
        for (final TypeMirror param : t.getParameterTypes()) {
            param.accept(this, builder);
        }
        builder.append(')');
        return t.getReturnType().accept(this, builder);
    }

    @Override
    public StringBuilder visitNoType(final NoType t, final StringBuilder builder) {
        if (t.getKind() == TypeKind.VOID) {
            return builder.append('V');
        } else {
            return builder;
        }
    }

    // Skipped types

    @Override
    public StringBuilder visitError(final ErrorType t, final StringBuilder builder) {
        // ignore
        builder.append("L!ERROR!;");
        return builder;
    }

    @Override
    public StringBuilder visitNull(final NullType t, final StringBuilder builder) {
        // todo: not representable in a descriptor?
        return builder;
    }

    @Override
    public StringBuilder visitUnion(final UnionType t, final StringBuilder builder) {
        // not representable in descriptors (only usable in catch statements))
        return null;
    }
}
