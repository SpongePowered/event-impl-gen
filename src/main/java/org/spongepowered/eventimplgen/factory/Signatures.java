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


import org.checkerframework.checker.nullness.qual.Nullable;
import org.objectweb.asm.signature.SignatureVisitor;
import org.objectweb.asm.signature.SignatureWriter;
import org.spongepowered.eventimplgen.eventgencore.Property;
import spoon.reflect.declaration.CtFormalTypeDeclarer;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.CtType;
import spoon.reflect.declaration.CtTypeParameter;
import spoon.reflect.reference.CtArrayTypeReference;
import spoon.reflect.reference.CtTypeParameterReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtWildcardReference;
import spoon.support.visitor.ClassTypingContext;

import java.util.List;

/**
 * Utilities to generate ASM signatures based on Spoon elements.
 */
public class Signatures {

    static String ofFactoryMethod(final CtType<?> event, final List<? extends Property> params) {
        return getSignature(event, params, false);
    }

    static String ofConstructor(final CtType<?> eventInterface, final List<? extends Property> properties) {
        return getSignature(eventInterface, properties, true);
    }

    /**
     * Get a generic signature for the field created for a property.
     *
     * @param field the field to generate a signature for
     * @return the signature
     */
    static @Nullable String ofField(final CtType<?> container, final Property field) {
        final List<CtTypeReference<?>> parameters = field.getType().getActualTypeArguments();
        if (parameters.isEmpty() && field.getType().isPrimitive() && !(field.getType() instanceof CtArrayTypeReference
                || field.getType() instanceof CtTypeParameterReference)) {
            return null;
        }
        final SignatureVisitor visitor = new SignatureWriter();
        writePropertyType(visitor, container, field);
        return visitor.toString();
    }

    static @Nullable String ofMethod(final CtType<?> container, final CtMethod<?> method) {
        final SignatureVisitor visitor = new SignatureWriter();
        final ClassTypingContext typeCalculator = new ClassTypingContext(container);

        // Formal type parameters
        visitTypeParametersFromFormals(visitor, method);

        // Parameters
        for (final CtParameter<?> parameter : method.getParameters()) {
            visitComputedType(visitor.visitParameterType(), typeCalculator.adaptType(parameter.getType()));
        }

        // Return type
        visitComputedType(visitor.visitReturnType(), typeCalculator.adaptType(method.getType()));

        // Exception type
        for (final CtTypeReference<?> exceptionType : method.getThrownTypes()) {
            visitComputedType(visitor.visitExceptionType(), typeCalculator.adaptType(exceptionType));
        }

        visitor.visitEnd();
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
    static String ofImplClass(final CtType<?> supertype, final List<CtType<?>> interfaces) {
        final SignatureVisitor visitor = new SignatureWriter();

        visitFormalTypeParameters(visitor, supertype.getFormalCtTypeParameters());
        for (final CtType<?> superinterface : interfaces) {
            // XX: Deduplicate type parameter names
            // Hopefully this isn't an issue yet because it's annoying to do
            visitFormalTypeParameters(visitor, superinterface.getFormalCtTypeParameters());
        }

        final SignatureVisitor stV = visitor.visitSuperclass();
        boolean doVisitEnd = visitBaseType(stV, supertype.getReference());
        visitTypeParametersFromFormals(stV, supertype);
        if (doVisitEnd) {
            stV.visitEnd();
        }

        for (final CtType<?> itf : interfaces) {
            final SignatureVisitor itfVisitor = visitor.visitInterface();
            doVisitEnd = visitBaseType(itfVisitor, itf.getReference());
            visitTypeParametersFromFormals(itfVisitor, itf);
            if (doVisitEnd) {
                itfVisitor.visitEnd();
            }
        }

        return visitor.toString();
    }

    // isConstructor: constructor or factory method
    private static String getSignature(final CtType<?> event, final List<? extends Property> params, final boolean isConstructor) {
        final SignatureVisitor v = new SignatureWriter();

        if (!isConstructor) {
            visitFormalTypeParameters(v, event.getFormalCtTypeParameters());
        }

        for (final Property property : params) {
            final SignatureVisitor pv = v.visitParameterType();
            writePropertyType(pv, event, property);
        }

        final SignatureVisitor rv = v.visitReturnType();
        if (isConstructor) {
            rv.visitBaseType('V');
        } else {
            rv.visitClassType(event.getQualifiedName().replace('.', '/'));
            visitTypeParametersFromFormals(rv, event);
        }
        v.visitEnd();
        return v.toString();
    }

    private static void visitTypeParametersFromFormals(final SignatureVisitor visitor, final CtFormalTypeDeclarer type) {
        for (final CtTypeParameter param : type.getFormalCtTypeParameters()) {
            final SignatureVisitor argVisitor = visitor.visitTypeArgument(SignatureVisitor.INSTANCEOF);
            argVisitor.visitTypeVariable(param.getSimpleName());
        }
    }

    private static void visitFormalTypeParameters(final SignatureVisitor visitor, final List<CtTypeParameter> parameters) {
        for (final CtTypeParameter param : parameters) {
            visitor.visitFormalTypeParameter(param.getSimpleName());
            final boolean doVisitEnd = visitBaseType(visitor, param.getSuperclass());
            if (!param.getSuperclass().getActualTypeArguments().isEmpty()) {
                visitTypeParameters(visitor, param.getSuperclass().getActualTypeArguments());
            }
            if (doVisitEnd) {
                visitor.visitEnd();
            }
        }

    }

    private static void writePropertyType(final SignatureVisitor visitor, final CtType<?> container, final Property property) {
        final CtTypeReference<?> actualType = new ClassTypingContext(container).adaptType(property.getMostSpecificType());
        visitComputedType(visitor, actualType);
    }

    private static void visitComputedType(final SignatureVisitor visitor, final CtTypeReference<?> actualType) {
        final boolean doVisitEnd = visitBaseType(visitor, actualType);
        final List<CtTypeReference<?>> typeArguments = actualType.getActualTypeArguments();
        if (!typeArguments.isEmpty()) {
            visitTypeParameters(visitor, typeArguments);
        }
        if (doVisitEnd) {
            visitor.visitEnd();
        }
    }

    /**
     * Visit a single type, without its parameters.
     *
     * @param pv the visitor to write to
     * @param type the type to write to the signature
     * @return whether visitEnd should be called after
     */
    private static boolean visitBaseType(final SignatureVisitor pv, final CtTypeReference<?> type) {
        if (type.isPrimitive()) {
            pv.visitBaseType(ClassGenerator.getTypeDescriptor(type).charAt(0));
            return false;
        } else if (type instanceof CtArrayTypeReference) {
            final SignatureVisitor ar = pv.visitArrayType();
            visitBaseType(ar, ((CtArrayTypeReference<?>) type).getComponentType());
            return true;
        } else if (type instanceof CtTypeParameterReference) {
            if (type instanceof CtWildcardReference) {
                CtTypeReference<?> bound = ((CtTypeParameterReference) type).getBoundingType();
                if (bound == null) {
                    bound = type.getFactory().Type().OBJECT;
                }
                visitBaseType(pv, bound);
                return true;
            } else {
                pv.visitTypeVariable(type.getSimpleName());
                return false;
            }
        } else {
            pv.visitClassType(type.getQualifiedName().replace('.', '/'));
            return true;
        }
    }

    private static void visitTypeParameters(final SignatureVisitor baseVisitor, final List<CtTypeReference<?>> types) {
        for (final CtTypeReference<?> type : types) {
            final SignatureVisitor inner;
            if (type instanceof CtWildcardReference) {
                inner = baseVisitor.visitTypeArgument(((CtWildcardReference) type).isUpper() ? SignatureVisitor.EXTENDS : SignatureVisitor.SUPER);
            } else {
                inner = baseVisitor.visitTypeArgument(SignatureVisitor.INSTANCEOF);
            }

            final boolean doVisitEnd = visitBaseType(inner, type);

            if (!type.getActualTypeArguments().isEmpty()) {
                visitTypeParameters(inner, type.getActualTypeArguments());
            }
            if (doVisitEnd) {
                inner.visitEnd();
            }
        }
    }
}
