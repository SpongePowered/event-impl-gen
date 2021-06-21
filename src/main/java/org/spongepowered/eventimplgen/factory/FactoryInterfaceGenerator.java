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

import javax.inject.Inject;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.spongepowered.eventimplgen.EventImplGenTask;
import org.spongepowered.eventimplgen.eventgencore.Property;
import org.spongepowered.eventimplgen.eventgencore.PropertySorter;
import org.spongepowered.eventimplgen.factory.plugin.EventFactoryPlugin;
import org.spongepowered.eventimplgen.signature.Signatures;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class FactoryInterfaceGenerator {

    private final Signatures signatures;
    private final ClassGenerator generator;
    private final Set<EventFactoryPlugin> plugins;

    @Inject
    FactoryInterfaceGenerator(final Signatures signatures, final Set<EventFactoryPlugin> plugins, final ClassGenerator generator) {
        this.signatures = signatures;
        this.generator = generator;
        this.plugins = plugins;
    }

    public byte[] createClass(
            final String name,
            final Map<TypeElement, List<Property>> foundProperties,
            final ClassGeneratorProvider provider,
            final PropertySorter sorter,
            final List<ExecutableElement> forwardedMethods) {
        final String internalName = this.generator.getInternalName(name);

        final ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cw.visit(V1_8, ACC_PUBLIC | ACC_SUPER | ACC_FINAL, internalName, null, "java/lang/Object", new String[] {});

        for (final Map.Entry<TypeElement, List<Property>> event : foundProperties.entrySet()) {
            this.generateRealImpl(cw,
                             event.getKey(),
                             this.generator.getInternalName(ClassGenerator.getEventName(event.getKey(), provider)),
                             this.generator.getRequiredProperties(sorter.sortProperties(event.getValue())));
        }

        for (final ExecutableElement forwardedMethod : forwardedMethods) {
            this.generateForwardingMethod(cw, forwardedMethod);
        }

        cw.visitEnd();
        return cw.toByteArray();
    }

    private void generateForwardingMethod(final ClassWriter cw, final ExecutableElement targetMethod) {
        final String desc = ClassGenerator.getDescriptor(targetMethod);
        final MethodVisitor mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, targetMethod.getSimpleName().toString(), desc, null, null);


        final Label start = new Label();
        final Label end = new Label();

        mv.visitCode();
        mv.visitLabel(start);

        final List<? extends VariableElement> parameters = targetMethod.getParameters();
        for (int i = 0, slot = 0; i < parameters.size(); i++, slot++) {
            final VariableElement param = parameters.get(i);
            final Type type = Type.getType(ClassGenerator.getTypeDescriptor(param.asType()));
            mv.visitVarInsn(type.getOpcode(Opcodes.ILOAD), i);

            mv.visitLocalVariable(param.getSimpleName().toString(), type.getDescriptor(), null, start, end, slot);
            mv.visitParameter(param.getSimpleName().toString(), 0);

            if (type.getSize() > 1) {
                slot++; // Skip over unusable following slot
            }
        }
        mv.visitMethodInsn(INVOKESTATIC, this.generator.getInternalName(targetMethod.getEnclosingElement().asType()), targetMethod.getSimpleName().toString(), desc, targetMethod.getEnclosingElement().getKind().isInterface());
        mv.visitInsn(ARETURN);
        mv.visitLabel(end);

        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private void generateRealImpl(final ClassWriter cw, final TypeElement event, final String eventName, final List<Property> params) {
        final MethodVisitor mv = cw.visitMethod(
            ACC_PUBLIC | ACC_STATIC,
            EventImplGenTask.generateMethodName(event),
            FactoryInterfaceGenerator.getDescriptor(event, params),
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
            final Type type = Type.getType(ClassGenerator.getTypeDescriptor(param.getType()));
            mv.visitVarInsn(type.getOpcode(Opcodes.ILOAD), slot); // Parameters start at slot 0 for static methods

            if (type.getSize() > 1) {
                slot++; // Skip over unusable following slot
            }
        }
        mv.visitMethodInsn(INVOKESPECIAL, eventName, "<init>", FactoryInterfaceGenerator.getDescriptor(null, params), false);

        mv.visitInsn(ARETURN);
        mv.visitLabel(end);

        for (int i = 0; i < params.size(); i++) {
            final Property property = params.get(i);
            mv.visitLocalVariable(property.getName(), ClassGenerator.getTypeDescriptor(property.getType()), null, start, end, slots[i]);
            mv.visitParameter(property.getName(), ACC_FINAL);
        }

        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private static String getDescriptor(final TypeElement event, final List<? extends Property> params) {
        final StringBuilder builder = new StringBuilder();
        builder.append("(");
        for (final Property property : params) {
            builder.append(ClassGenerator.getTypeDescriptor(property.getType()));
        }
        builder.append(")");
        if (event != null) {
            builder.append(ClassGenerator.getTypeDescriptor(event.asType()));
        } else {
            builder.append("V");
        }
        return builder.toString();
    }

}
