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
import org.objectweb.asm.signature.SignatureVisitor;
import org.objectweb.asm.signature.SignatureWriter;
import org.spongepowered.eventimplgen.eventgencore.Property;

import java.util.Collections;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.util.Types;

/**
 * Utilities to generate ASM signatures based on Spoon elements.
 */
@Singleton
public class Signatures {
    private final Types types;
    private final Descriptors descriptors;
    private final TypeToSignatureWriter sig;

    @Inject
    Signatures(final Types types, final Descriptors descriptors, final TypeToSignatureWriter sig) {
        this.types = types;
        this.descriptors = descriptors;
        this.sig = sig;
    }

    public String ofFactoryMethod(final TypeElement event, final List<? extends Property> params) {
        return this.getSignature(event, params, false);
    }

    public String ofConstructor(final TypeElement eventInterface, final List<? extends Property> properties) {
        return this.getSignature(eventInterface, properties, true);
    }

    /**
     * Get a generic signature for the field created for a property.
     *
     * @param field the field to generate a signature for
     * @return the signature
     */
    public @Nullable String ofField(final Property field) {
        final TypeMirror fieldType = field.getType(); // field.getType().getKind() == TypeKind.DECLARED ? this.types.asMemberOf((DeclaredType) container.asType(), this.types.asElement(field.getType())) : field.getType();
        final List<? extends TypeMirror> parameters = fieldType.getKind() == TypeKind.DECLARED ? ((DeclaredType) fieldType).getTypeArguments() : Collections.emptyList();
        if ((parameters.isEmpty() || fieldType.getKind().isPrimitive()) && !(fieldType.getKind() == TypeKind.ARRAY
                || fieldType.getKind() == TypeKind.TYPEVAR)) {
            return null;
        }
        final SignatureWriter visitor = new SignatureWriter();
        fieldType.accept(this.sig, visitor);
        return visitor.toString();
    }

    @Nullable
    public String ofMethod(final TypeElement container, final ExecutableElement method) {
        final SignatureWriter visitor = new SignatureWriter();
        final TypeMirror computedType = this.types.asMemberOf((DeclaredType) container.asType(), method);
        computedType.accept(this.sig, visitor);
        return visitor.toString();
    }

    /**
     * Create a signature for an implementation class.
     *
     * <p>This takes the combined type parameters of the implementation, its
     * supertypes and superinterfaces. It should disambiguate the reuses of type parameters too. </p>
     *
     * @param supertype implementation supertype
     * @param interfaces interfaces implemented by the implementation
     * @return the class signature for the implementation class
     */
    public String ofImplClass(final TypeMirror supertype, final List<TypeMirror> interfaces) {
        final SignatureWriter visitor = new SignatureWriter();

        // TODO :
        this.writeFormalTypeParameters(visitor, supertype);
        for (final TypeMirror superinterface : interfaces) {
            // XX: Deduplicate type parameter names
            // Hopefully this isn't an issue yet because it's annoying to do
            this.writeFormalTypeParameters(visitor, superinterface);
        }

        final SignatureVisitor stV = visitor.visitSuperclass();
        stV.visitClassType(this.descriptors.getInternalName(this.types.erasure(supertype)));
        this.visitTypeParametersFromFormals(visitor, supertype);
        stV.visitEnd();

        for (final TypeMirror itf : interfaces) {
            final SignatureVisitor itfVisitor = visitor.visitInterface();
            stV.visitClassType(this.descriptors.getInternalName(this.types.erasure(itf)));
            this.visitTypeParametersFromFormals(visitor, itf);
            itfVisitor.visitEnd();
        }

        return visitor.toString();
    }

    private void writeFormalTypeParameters(final SignatureWriter writer, final TypeMirror formalsSource) {
        if (formalsSource.getKind() == TypeKind.DECLARED) {
            this.writeFormalTypeParameters(writer, (TypeElement) ((DeclaredType) formalsSource).asElement());
        }
    }

    private void writeFormalTypeParameters(final SignatureWriter writer, final TypeElement formalsSource) {
            for (final TypeParameterElement param : formalsSource.getTypeParameters()) {
                this.sig.writeFormalTypeParameter(((TypeVariable) param.asType()), writer);
        }
    }

    private void visitTypeParametersFromFormals(final SignatureWriter writer, final TypeMirror type) {
        if (type.getKind() == TypeKind.DECLARED) {
            for (final TypeParameterElement param : ((TypeElement) ((DeclaredType) type).asElement()).getTypeParameters()) {
                writer.visitTypeArgument(SignatureVisitor.INSTANCEOF);
                param.asType().accept(this.sig, writer);
            }
        }
    }

    // isConstructor: constructor or factory method
    private String getSignature(final TypeElement event, final List<? extends Property> params, final boolean isConstructor) {
        final SignatureWriter v = new SignatureWriter();

       if (!isConstructor) {
            this.writeFormalTypeParameters(v, event);
        }

        for (final Property property : params) {
            final SignatureVisitor pv = v.visitParameterType();
            // final TypeMirror actualType = this.types.asMemberOf((DeclaredType) event.asType(), this.types.asElement(property.getMostSpecificType()));
            property.getType().accept(this.sig, pv);
        }

        v.visitReturnType();
        if (isConstructor) {
            v.visitBaseType('V');
        } else {
            event.asType().accept(this.sig, v);
        }

        return v.toString();
    }

}
