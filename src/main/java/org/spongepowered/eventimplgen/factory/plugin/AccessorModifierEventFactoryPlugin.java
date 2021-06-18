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
package org.spongepowered.eventimplgen.factory.plugin;

import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.GETFIELD;
import static org.objectweb.asm.Opcodes.INVOKEINTERFACE;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.objectweb.asm.Opcodes.IRETURN;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.spongepowered.eventimplgen.EventImplGenTask;
import org.spongepowered.eventimplgen.eventgencore.Property;
import org.spongepowered.eventimplgen.factory.ClassGenerator;
import spoon.reflect.declaration.CtAnnotation;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;

/**
 * An event factory plugin to modify the return type of an accessor
 * by calling one of its methods.
 */
public class AccessorModifierEventFactoryPlugin implements EventFactoryPlugin {

    private MethodPair getLinkedField(final Property property) {

        final ExecutableElement leastSpecificMethod = property.getLeastSpecificMethod();
        final AnnotationMirror transformResult;
        ExecutableElement transformWith = null;
        String name = null;

        if ((transformResult = EventImplGenTask.getAnnotation(leastSpecificMethod, "org.spongepowered.api.util.annotation.eventgen.TransformResult")) != null) {
            name = EventImplGenTask.getValue(transformResult, "value");
            // Since we that the modifier method (the one annotated with TransformWith) doesn't
            // use covariant types, we can call getMethods on the more specific version,
            // allowing the annotation to be present on a method defined there, as well as in
            // the least specific type.
            for (final ExecutableElement method: property.getAccessor().getType().getDeclaration().getMethods()) {
                final AnnotationMirror annotation = EventImplGenTask.getAnnotation(method, "org.spongepowered.api.util.annotation.eventgen"
                        + ".TransformWith");
                if (annotation != null &&  EventImplGenTask.getValue(annotation, "value").equals(name)) {
                    if (transformWith != null) {
                        throw new RuntimeException("Multiple @TransformResult annotations were found with the name "
                                + name + ". One of them needs to be changed!");
                    }
                    transformWith = method;
                }
            }
            if (transformWith == null) {
                throw new RuntimeException("Unable to locate a matching @TransformWith annotation with the name "
                        + name + " for the method" + property.getAccessor());
            }
        }

        if (transformWith != null) {
            return new MethodPair(name, leastSpecificMethod, transformWith, property);
        }
        return null;
    }

    private void generateTransformingAccessor(final ClassWriter cw, final String internalName, final MethodPair pair, final Property property) {

        final ExecutableElement accessor = property.getAccessor();

        final MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, accessor.getSimpleName().toString(), ClassGenerator.getDescriptor(accessor), null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitFieldInsn(GETFIELD, internalName, property.getName(), ClassGenerator.getTypeDescriptor(property.getLeastSpecificType()));

        final ExecutableElement transformerMethod = pair.getTransformerMethod();

        int opcode = INVOKEVIRTUAL;
        if (transformerMethod.getEnclosingElement().getKind() == ElementKind.INTERFACE) {
            opcode = INVOKEINTERFACE;
        }

        mv.visitMethodInsn(opcode, transformerMethod.getDeclaringType().getQualifiedName().replace(".", "/"), transformerMethod.getSimpleName(),
                ClassGenerator.getDescriptor(transformerMethod), opcode != INVOKEVIRTUAL);

        mv.visitInsn(Type.getType(ClassGenerator.getTypeDescriptor(property.getType())).getOpcode(IRETURN));
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    @Override
    public boolean contributeProperty(final CtType<?> eventClass, final String internalName, final ClassWriter classWriter, final Property property) {
        final MethodPair methodPair = this.getLinkedField(property);
        if (methodPair == null) {
            return false;
        }

        ClassGenerator.generateField(classWriter, eventClass, property);
        if (property.getMutator().isPresent()) {
            ClassGenerator.generateMutator(classWriter, eventClass, internalName, property.getName(), property.getType().getDeclaration(), property);
        }

        this.generateTransformingAccessor(classWriter, internalName, methodPair, property);

        return true;
    }



    private static final class MethodPair {

        private final String name;

        private ExecutableElement callerMethod;
        private ExecutableElement transformerMethod;

        private Property property;

        /**
         * Creates a new {@link MethodPair}.
         *
         * @param name The name
         * @param callerMethod The caller method
         * @param transformerMethod The transformer method
         * @param property The property
         */
        public MethodPair(final String name, final ExecutableElement callerMethod, final ExecutableElement transformerMethod, final Property property) {
            this.name = name;
            this.callerMethod = callerMethod;
            this.transformerMethod = transformerMethod;
            this.property = property;
        }

        public String getName() {
            return this.name;
        }

        public ExecutableElement getTransformerMethod() {
            return this.transformerMethod;
        }

        public Property getProperty() {
            return this.property;
        }
    }
}
