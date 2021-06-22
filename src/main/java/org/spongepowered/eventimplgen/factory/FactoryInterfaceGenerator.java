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

import org.checkerframework.checker.nullness.qual.Nullable;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.spongepowered.eventimplgen.eventgencore.Property;
import org.spongepowered.eventimplgen.eventgencore.PropertySorter;
import org.spongepowered.eventimplgen.signature.Descriptors;
import org.spongepowered.eventimplgen.signature.Signatures;

import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;

@Singleton
public class FactoryInterfaceGenerator {

    private final Signatures signatures;
    private final ClassGenerator generator;
    private final Descriptors descriptors;

    @Inject
    FactoryInterfaceGenerator(final Signatures signatures, final ClassGenerator generator, final Descriptors descriptors) {
        this.signatures = signatures;
        this.generator = generator;
        this.descriptors = descriptors;
    }

    public byte[] createClass(
            final String name,
            final Map<TypeElement, List<Property>> foundProperties,
            final PropertySorter sorter,
            final List<ExecutableElement> forwardedMethods) {
        final String internalName = this.descriptors.getInternalName(name);

        final ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cw.visit(V1_8, ACC_PUBLIC | ACC_SUPER | ACC_FINAL, internalName, null, "java/lang/Object", new String[] {});

        for (final Map.Entry<TypeElement, List<Property>> event : foundProperties.entrySet()) {
            this.generateRealImpl(cw,
                             event.getKey(),
                             this.descriptors.getInternalName(this.generator.qualifiedName(event.getKey())),
                             this.generator.getRequiredProperties(sorter.sortProperties(event.getValue())));
        }

        for (final ExecutableElement forwardedMethod : forwardedMethods) {
            this.generateForwardingMethod(cw, forwardedMethod);
        }

        cw.visitEnd();
        return cw.toByteArray();
    }

    private void generateForwardingMethod(final ClassWriter cw, final ExecutableElement targetMethod) {
        final String desc = this.descriptors.getDescriptor(targetMethod);
        final MethodVisitor mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, targetMethod.getSimpleName().toString(), desc, null, null);


        final Label start = new Label();
        final Label end = new Label();

        mv.visitCode();
        mv.visitLabel(start);

        final List<? extends VariableElement> parameters = targetMethod.getParameters();
        for (int i = 0, slot = 0; i < parameters.size(); i++, slot++) {
            final VariableElement param = parameters.get(i);
            final Type type = Type.getType(this.descriptors.getTypeDescriptor(param.asType()));
            mv.visitVarInsn(type.getOpcode(Opcodes.ILOAD), i);

            mv.visitLocalVariable(param.getSimpleName().toString(), type.getDescriptor(), null, start, end, slot);
            mv.visitParameter(param.getSimpleName().toString(), 0);

            if (type.getSize() > 1) {
                slot++; // Skip over unusable following slot
            }
        }
        mv.visitMethodInsn(INVOKESTATIC, this.descriptors.getInternalName(targetMethod.getEnclosingElement().asType()), targetMethod.getSimpleName().toString(), desc, targetMethod.getEnclosingElement().getKind().isInterface());
        mv.visitInsn(ARETURN);
        mv.visitLabel(end);

        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private void generateRealImpl(final ClassWriter cw, final TypeElement event, final String eventName, final List<Property> params) {
        final MethodVisitor mv = cw.visitMethod(
            ACC_PUBLIC | ACC_STATIC,
            FactoryInterfaceGenerator.generateMethodName(event),
            this.getDescriptor(event, params),
            this.signatures.ofFactoryMethod(event, params),
            null
        );

        final Label start = new Label();
        final Label end = new Label();

        mv.visitCode();

        mv.visitLabel(start);

        mv.visitTypeInsn(NEW, eventName);
        mv.visitInsn(DUP);

        final int[] slots = new int[params.size()];

        for (int i = 0, slot = 0; i < params.size(); i++, slot++) {
            final Property param = params.get(i);
            slots[i] = slot;
            final Type type = Type.getType(this.descriptors.getTypeDescriptor(param.getType()));
            mv.visitVarInsn(type.getOpcode(Opcodes.ILOAD), slot); // Parameters start at slot 0 for static methods

            if (type.getSize() > 1) {
                slot++; // Skip over unusable following slot
            }
        }
        mv.visitMethodInsn(INVOKESPECIAL, eventName, "<init>", this.getDescriptor(null, params), false);

        mv.visitInsn(ARETURN);
        mv.visitLabel(end);

        for (int i = 0; i < params.size(); i++) {
            final Property property = params.get(i);
            mv.visitLocalVariable(property.getName(), this.descriptors.getTypeDescriptor(property.getType()), null, start, end, slots[i]);
            mv.visitParameter(property.getName(), ACC_FINAL);
        }

        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private String getDescriptor(final @Nullable TypeElement event, final List<Property> params) {
        return this.descriptors.getDescriptor(params, event == null ? null : event.asType());
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
