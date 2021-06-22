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

import org.objectweb.asm.signature.SignatureVisitor;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
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
import javax.lang.model.type.TypeVisitor;
import javax.lang.model.type.UnionType;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.AbstractTypeVisitor8;
import javax.lang.model.util.Elements;

/**
 * A type visitor to convert a {@link javax.lang.model.type.TypeMirror} into a bytecode signature.
 *
 * <p>State management in signatures is a bit odd. The only element that we know
 * can't be included as a root element is the executable type. Everything else
 * should be designed to be as position-independent as possible. This does mean
 * that for some cases like formal parameters, alternative handling must be
 * performed.</p>
 */
@Singleton
class TypeToSignatureWriter extends AbstractTypeVisitor8<SignatureVisitor, SignatureVisitor> {

    private final Elements elements;

    @Inject
    TypeToSignatureWriter(final Elements elements) {
        this.elements = elements;
    }

    @SuppressWarnings("unchecked")
    public <R extends SignatureVisitor> TypeVisitor<R, R> as() {
        return (TypeVisitor<R, R>) this;
    }

    @Override
    public SignatureVisitor visitIntersection(final IntersectionType t, final SignatureVisitor writer) {
        for (final TypeMirror type : t.getBounds()) {
            if (type.getKind() != TypeKind.DECLARED) {
                final SignatureVisitor v = writer.visitInterfaceBound();
                v.visitClassType("!NOT_DECLARED!");
                v.visitEnd();
                continue;
            }

            final ElementKind elementKind = ((DeclaredType) type).asElement().getKind();
            final SignatureVisitor boundVisitor;
            if (elementKind.isClass()) {
                boundVisitor = writer.visitClassBound();
            } else if (elementKind.isInterface()) {
                boundVisitor = writer.visitInterfaceBound();
            } else {
                return writer;
            }
            type.accept(this, boundVisitor);
        }
        return writer;
    }

    @Override
    public SignatureVisitor visitPrimitive(final PrimitiveType t, final SignatureVisitor writer) {
        writer.visitBaseType(TypeToDescriptorWriter.descriptor(t));
        return writer;
    }

    @Override
    public SignatureVisitor visitArray(final ArrayType t, final SignatureVisitor writer) {
        t.getComponentType().accept(this, writer.visitArrayType());
        return writer;
    }

    // declared type as an element in a TypeSignature
    @Override
    public SignatureVisitor visitDeclared(final DeclaredType t, final SignatureVisitor writer) {
        final TypeKind enclosing = t.getEnclosingType().getKind();
        if (enclosing == TypeKind.DECLARED) {
            t.getEnclosingType().accept(this, writer);
            writer.visitInnerClassType(t.asElement().getSimpleName().toString());
        } else {
            writer.visitClassType(this.elements.getBinaryName((TypeElement) t.asElement()).toString().replace('.', '/'));
        }

        for (final TypeMirror arg : t.getTypeArguments()) {
            if (arg.getKind() == TypeKind.WILDCARD) {
                arg.accept(this, writer);
            } else {
                arg.accept(this, writer.visitTypeArgument(SignatureVisitor.INSTANCEOF));
            }
        }

        writer.visitEnd();
        return writer;
    }

    public SignatureVisitor visitDeclaredAsClassSignature(final DeclaredType t, final SignatureVisitor writer) {
        final TypeElement type = (TypeElement) t.asElement();
        for (final TypeParameterElement param : type.getTypeParameters()) {
            this.writeFormalTypeParameter(((TypeVariable) param.asType()), writer);
        }

        type.getSuperclass().accept(this, writer.visitSuperclass());

        for (final TypeMirror itf : type.getInterfaces()) {
            itf.accept(this, writer.visitInterface());
        }

        return writer;
    }

    @Override
    public SignatureVisitor visitTypeVariable(final TypeVariable t, final SignatureVisitor writer) {
        writer.visitTypeVariable(t.asElement().getSimpleName().toString());
        return writer;
    }

    @Override
    public SignatureVisitor visitWildcard(final WildcardType t, final SignatureVisitor writer) {
        if (t.getExtendsBound() != null) {
            t.getExtendsBound().accept(this, writer.visitTypeArgument(SignatureVisitor.EXTENDS));
        } else if (t.getSuperBound() != null) {
            t.getSuperBound().accept(this, writer.visitTypeArgument(SignatureVisitor.SUPER));
        } else {
            writer.visitTypeArgument();
        }
        return writer;
    }

    @Override
    public SignatureVisitor visitExecutable(final ExecutableType t, final SignatureVisitor writer) {
        for (final TypeVariable variable : t.getTypeVariables()) {
            this.writeFormalTypeParameter(variable, writer);
        }

        // Parameters
        for (final TypeMirror parameter : t.getParameterTypes()) {
            parameter.accept(this, writer.visitParameterType());
        }

        // Return type
        t.getReturnType().accept(this, writer.visitReturnType());

        // Exception type
        for (final TypeMirror exceptionType : t.getThrownTypes()) {
            exceptionType.accept(this, writer.visitExceptionType());
        }
        return writer;
    }

    void writeFormalTypeParameter(final TypeVariable variable, final SignatureVisitor writer) {
        writer.visitFormalTypeParameter(variable.asElement().getSimpleName().toString());
        final TypeMirror upperBound = variable.getUpperBound();
        if (upperBound.getKind() == TypeKind.INTERSECTION) { // declared or intersection
            upperBound.accept(this, writer);
        } else if (upperBound.getKind() == TypeKind.DECLARED) {
            final Element boundElement = ((DeclaredType) upperBound).asElement();
            final SignatureVisitor boundVisitor;
            if (boundElement.getKind().isClass()) {
                boundVisitor = writer.visitClassBound();
            } else if (boundElement.getKind().isInterface()) {
                boundVisitor = writer.visitInterfaceBound();
            } else {
                return;
            }

            upperBound.accept(this, boundVisitor);
        }
    }

    @Override
    public SignatureVisitor visitNoType(final NoType t, final SignatureVisitor writer) {
        if (t.getKind() == TypeKind.VOID) {
            writer.visitBaseType('V');
        }
        return writer;
    }

    @Override
    public SignatureVisitor visitUnion(final UnionType t, final SignatureVisitor writer) {
        // todo: warn?
        return writer;
    }

    @Override
    public SignatureVisitor visitNull(final NullType t, final SignatureVisitor writer) {
        // todo: throw exception?
        return writer;
    }

    @Override
    public SignatureVisitor visitError(final ErrorType t, final SignatureVisitor writer) {
        writer.visitClassType("!ERROR!");
        writer.visitEnd();
        return writer;
    }

}
