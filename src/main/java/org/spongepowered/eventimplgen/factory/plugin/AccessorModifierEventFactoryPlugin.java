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

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.spongepowered.api.util.annotation.eventgen.TransformResult;
import org.spongepowered.api.util.annotation.eventgen.TransformWith;
import org.spongepowered.eventimplgen.eventgencore.Property;
import org.spongepowered.eventimplgen.factory.EventImplClassWriter;
import org.spongepowered.eventimplgen.signature.Descriptors;

import java.util.Objects;

import javax.annotation.processing.Messager;
import javax.inject.Inject;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;

/**
 * An event factory plugin to modify the return type of an accessor
 * by calling one of its methods.
 */
public class AccessorModifierEventFactoryPlugin implements EventFactoryPlugin {

    private final Types types;
    private final Descriptors descriptors;
    private final Messager messager;

    @Inject
    AccessorModifierEventFactoryPlugin(final Types types, final Descriptors descriptors, final Messager messager) {
        this.types = types;
        this.descriptors = descriptors;
        this.messager = messager;
    }

    private MethodPair getLinkedField(final Property property) {

        final ExecutableElement leastSpecificMethod = property.getLeastSpecificMethod();
        final TransformResult transformResult;
        ExecutableElement transformWith = null;
        String name = null;

        if ((transformResult = leastSpecificMethod.getAnnotation(TransformResult.class)) != null) {
            name = transformResult.value();
            // Since we that the modifier method (the one annotated with TransformWith) doesn't
            // use covariant types, we can call getMethods on the more specific version,
            // allowing the annotation to be present on a method defined there, as well as in
            // the least specific type.
            for (final ExecutableElement method : ElementFilter.methodsIn(this.types.asElement(property.getAccessor().getReturnType()).getEnclosedElements())) {
                final TransformWith annotation = method.getAnnotation(TransformWith.class);
                if (annotation != null && Objects.equals(annotation.value(), name)) {
                    if (transformWith != null) {
                        this.messager.printMessage(Diagnostic.Kind.ERROR, "Multiple @TransformResult annotations were found with the name "
                                + name + ". One of them needs to be changed!", method);
                        return MethodPair.FAILED;
                    }
                    transformWith = method;
                }
            }
            if (transformWith == null) {
                this.messager.printMessage(Diagnostic.Kind.ERROR, "Unable to locate a matching @TransformWith annotation with the name "
                        + name + " for this method", property.getAccessor());
                return MethodPair.FAILED;
            }
        }

        if (transformWith != null) {
            return new MethodPair(name, leastSpecificMethod, transformWith, property);
        }
        return null;
    }

    private void generateTransformingAccessor(final EventImplClassWriter cw, final String internalName, final MethodPair pair, final Property property) {

        final ExecutableElement accessor = property.getAccessor();

        final MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, accessor.getSimpleName().toString(), this.descriptors.getDescriptor(accessor), null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitFieldInsn(GETFIELD, internalName, property.getName(), this.descriptors.getTypeDescriptor(property.getLeastSpecificType()));

        final ExecutableElement transformerMethod = pair.getTransformerMethod();

        int opcode = INVOKEVIRTUAL;
        if (transformerMethod.getEnclosingElement().getKind() == ElementKind.INTERFACE) {
            opcode = INVOKEINTERFACE;
        }

        mv.visitMethodInsn(
            opcode,
            this.descriptors.getInternalName(transformerMethod.getEnclosingElement().asType()),
            transformerMethod.getSimpleName().toString(),
            this.descriptors.getDescriptor(transformerMethod),
            opcode != INVOKEVIRTUAL
        );

        mv.visitInsn(Type.getType(this.descriptors.getTypeDescriptor(property.getType())).getOpcode(IRETURN));
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    @Override
    public Result contributeProperty(final TypeElement eventClass, final String internalName, final EventImplClassWriter classWriter, final Property property) {
        final MethodPair methodPair = this.getLinkedField(property);
        if (methodPair == null) {
            return Result.IGNORE;
        } else if (methodPair == MethodPair.FAILED) {
            return Result.FAILURE;
        }

        classWriter.generateField(eventClass, property);
        if (property.getMutator().isPresent()) {
            classWriter.generateMutator(eventClass, internalName, property.getName(), property);
        }

        this.generateTransformingAccessor(classWriter, internalName, methodPair, property);

        return Result.SUCCESSS;
    }



    static final class MethodPair {

        static final MethodPair FAILED = new MethodPair("error", null, null, null);

        private final String name;

        private ExecutableElement callerMethod;
        private final ExecutableElement transformerMethod;

        private final Property property;

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
