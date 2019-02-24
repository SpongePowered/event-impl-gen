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

import static org.objectweb.asm.Opcodes.ACC_FINAL;
import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.ACC_STATIC;
import static org.objectweb.asm.Opcodes.ACC_SUPER;
import static org.objectweb.asm.Opcodes.ARETURN;
import static org.objectweb.asm.Opcodes.DUP;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.NEW;
import static org.objectweb.asm.Opcodes.V1_8;

import com.google.common.collect.Lists;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.signature.SignatureVisitor;
import org.objectweb.asm.signature.SignatureWriter;
import org.spongepowered.eventimplgen.EventImplGenTask;
import org.spongepowered.eventimplgen.eventgencore.Property;
import org.spongepowered.eventimplgen.eventgencore.PropertySorter;
import org.spongepowered.eventimplgen.factory.plugin.AccessorModifierEventFactoryPlugin;
import org.spongepowered.eventimplgen.factory.plugin.EventFactoryPlugin;
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
import java.util.Map;

public class FactoryInterfaceGenerator {

    public static List<? extends EventFactoryPlugin> plugins = Lists.newArrayList(new AccessorModifierEventFactoryPlugin());

    public static byte[] createClass(String name, Map<CtType<?>, List<Property>> foundProperties, ClassGeneratorProvider provider, PropertySorter sorter, List<CtMethod<?>> forwardedMethods) {
        String internalName = ClassGenerator.getInternalName(name);

        final ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cw.visit(V1_8, ACC_PUBLIC | ACC_SUPER | ACC_FINAL, internalName, null, "java/lang/Object", new String[] {});

        for (CtType<?> event: foundProperties.keySet()) {
            generateRealImpl(cw, event, ClassGenerator.getInternalName(ClassGenerator.getEventName(event, provider)), ClassGenerator.getRequiredProperties(sorter.sortProperties(foundProperties.get(event))));
        }

        for (CtMethod<?> forwardedMethod: forwardedMethods) {
            generateForwardingMethod(cw, forwardedMethod);
        }

        cw.visitEnd();
        return cw.toByteArray();
    }

    private static void generateForwardingMethod(ClassWriter cw, CtMethod<?> targetMethod) {
        String desc = ClassGenerator.getDescriptor(targetMethod);
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, targetMethod.getSimpleName(), desc, null, null);


        Label start = new Label();
        Label end = new Label();

        mv.visitCode();
        mv.visitLabel(start);

        for (int i = 0, slot = 0; i < targetMethod.getParameters().size(); i++, slot++) {
            CtParameter<?> param = targetMethod.getParameters().get(i);
            Type type = Type.getType(ClassGenerator.getTypeDescriptor(param.getType()));
            mv.visitVarInsn(type.getOpcode(Opcodes.ILOAD), i);

            mv.visitLocalVariable(param.getSimpleName(), type.getDescriptor(), null, start, end, slot);
            mv.visitParameter(param.getSimpleName(), 0);

            if (type.getSize() > 1) {
                slot++; // Skip over unusable following slot
            }
        }
        mv.visitMethodInsn(INVOKESTATIC, ClassGenerator.getInternalName(targetMethod.getDeclaringType().getQualifiedName()), targetMethod.getSimpleName(), desc, targetMethod.getDeclaringType().isInterface());
        mv.visitInsn(ARETURN);
        mv.visitLabel(end);

        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private static void generateRealImpl(ClassWriter cw, CtType<?> event, String eventName, List<Property> params) {
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, EventImplGenTask.generateMethodName(event), getDescriptor(event, params), getSignature(event, params), null);

        Label start = new Label();
        Label end = new Label();

        mv.visitCode();

        mv.visitLabel(start);

        mv.visitTypeInsn(NEW, eventName);
        mv.visitInsn(DUP);

        int[] slots = new int[params.size()];

        for (int i = 0, slot = 0; i < params.size(); i++, slot++) {
            Property param = params.get(i);
            slots[i] = slot;
            Type type = Type.getType(ClassGenerator.getTypeDescriptor(param.getType()));
            mv.visitVarInsn(type.getOpcode(Opcodes.ILOAD), slot); // Parameters start at slot 0 for static methods

            if (type.getSize() > 1) {
                slot++; // Skip over unusable following slot
            }
        }
        mv.visitMethodInsn(INVOKESPECIAL, eventName, "<init>", getDescriptor(null, params), false);

        mv.visitInsn(ARETURN);
        mv.visitLabel(end);

        for (int i = 0; i < params.size(); i++) {
            Property property = params.get(i);
            mv.visitLocalVariable(property.getName(), ClassGenerator.getTypeDescriptor(property.getType()), null, start, end, slots[i]);
            mv.visitParameter(property.getName(), 0);
        }

        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    // TODO: work this out
    /*private static String getSignature(CtTypeReference<?> type) {
        List<CtTypeReference<?>> typeParams = type.getActualTypeArguments();
        if (typeParams.size() == 0) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        builder.append('L');
        builder.append(ClassGenerator.getInternalName(type.getQualifiedName()));
        builder.append('<');

        for (CtTypeReference<?> typeParam: typeParams) {
            builder.append(ClassGenerator.getTypeDescriptor(typeParam));
        }
        builder.append(">;");
        return builder.toString();
    }*/

    private static String getSignature(CtType<?> event, List<? extends Property> params) {
        SignatureVisitor v = new SignatureWriter();

        for (CtTypeParameter param: event.getFormalCtTypeParameters()) {
            v.visitFormalTypeParameter(param.getSimpleName());
            boolean doVisitEnd = visitTypeForSignature(v, param.getSuperclass());
            if (!param.getSuperclass().getActualTypeArguments().isEmpty()) {
                processTypes(v, param.getSuperclass().getActualTypeArguments());
            }
            if (doVisitEnd) {
                v.visitEnd();
            }
            /*v.visitClassType(param.getSuperclass().getQualifiedName().replace(".", "/"));
            /*if (param.getSuperInterfaces().isEmpty()) {
                v/*.visitInterfaceBound()*///.visitClassType("java/lang/Object;");
            /*} else {
                for (CtTypeReference<?> superType: param.getSuperInterfaces()) {
                    SignatureVisitor inner = v.visitInterfaceBound();
                    visitTypeForSignature(inner, superType);
                }
            }*/
        }

        for (Property property: params) {
            SignatureVisitor pv = v.visitParameterType();
            CtTypeReference<?> actualType = new ClassTypingContext(event).adaptType(property.getMostSpecificType());
            boolean doVisitEnd = visitTypeForSignature(pv, actualType);
            if (!property.getType().getActualTypeArguments().isEmpty()) {
                processTypes(pv, actualType.getActualTypeArguments());
            }
            if (doVisitEnd) {
                pv.visitEnd();
            }
        }

        SignatureVisitor rv = v.visitReturnType();
        rv.visitClassType(event.getQualifiedName().replace('.', '/'));
        processTypes(rv, event.getReference().getActualTypeArguments());

        v.visitEnd();

        return v.toString();
    }

    private static boolean visitTypeForSignature(SignatureVisitor pv, CtTypeReference<?> type) {
        if (type.isPrimitive()) {
            pv.visitBaseType(Type.getDescriptor(type.getActualClass()).charAt(0));
            return false;
        } else if (type instanceof CtArrayTypeReference) {
            SignatureVisitor ar = pv.visitArrayType();
            visitTypeForSignature(ar, ((CtArrayTypeReference<?>) type).getComponentType());
            return true;
        } else if (type instanceof CtTypeParameterReference) {
            CtTypeReference<?> bound = ((CtTypeParameterReference) type).getBoundingType();
            if (bound == null) {
                bound = type.getFactory().Type().OBJECT;
            }
            visitTypeForSignature(pv, bound);
            return true;
        } else {
            pv.visitClassType(type.getQualifiedName().replace('.', '/'));
            return true;
        }
    }

    private static void processTypes(SignatureVisitor baseVisitor, List<CtTypeReference<?>> types) {
        for (CtTypeReference<?> type: types) {
            SignatureVisitor inner;
            if (type instanceof CtWildcardReference) {
                inner = baseVisitor.visitTypeArgument(((CtWildcardReference) type).isUpper() ? '+' : '-');
            } else {
                inner = baseVisitor.visitTypeArgument('=');
            }
            SignatureVisitor pv = inner.visitParameterType();

            boolean doVisitEnd = visitTypeForSignature(pv, type);

            if (!type.getActualTypeArguments().isEmpty()) {
                processTypes(pv, type.getActualTypeArguments());
                //SignatureVisitor nested = pv.visitTypeArgument('=');
                //processTypes(nested, type.getActualTypeArguments());
                //inner.visitEnd();
            }
            if (doVisitEnd) {
                pv.visitEnd();
            }
        }
    }

    private static String getDescriptor(CtType<?> event, List<? extends Property> params) {
        StringBuilder builder = new StringBuilder();
        builder.append("(");
        for (Property property: params) {
            builder.append(ClassGenerator.getTypeDescriptor(property.getType()));
        }
        builder.append(")");
        if (event != null) {
            builder.append(ClassGenerator.getTypeDescriptor(event.getReference()));
        } else {
            builder.append("V");
        }
        return builder.toString();
    }

    private static String getEventImplName(CtTypeReference<?> event, ClassGeneratorProvider provider) {
        return ClassGenerator.getEventName(event.getDeclaration(), provider);
    }

}
