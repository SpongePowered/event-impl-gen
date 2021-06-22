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

import static org.objectweb.asm.Opcodes.ACC_PRIVATE;
import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ATHROW;
import static org.objectweb.asm.Opcodes.DUP;
import static org.objectweb.asm.Opcodes.IFNE;
import static org.objectweb.asm.Opcodes.IFNULL;
import static org.objectweb.asm.Opcodes.ILOAD;
import static org.objectweb.asm.Opcodes.INSTANCEOF;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.objectweb.asm.Opcodes.NEW;
import static org.objectweb.asm.Opcodes.PUTFIELD;
import static org.objectweb.asm.Opcodes.RETURN;

import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.spongepowered.eventimplgen.eventgencore.Property;
import org.spongepowered.eventimplgen.signature.Descriptors;
import org.spongepowered.eventimplgen.signature.Signatures;

import java.util.Optional;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

public class EventImplClassWriter extends ClassWriter {

    private final Types types;
    private final Elements elements;
    private final Descriptors descriptors;
    private final Signatures signatures;

    @AssistedInject
    public EventImplClassWriter(
        final Types types,
        final Elements elements,
        final Descriptors descriptors,
        final Signatures signatures,
        @Assisted final int flags
    ) {
        super(flags);
        this.types = types;
        this.elements = elements;
        this.descriptors = descriptors;
        this.signatures = signatures;
    }

    /**
     * Factory for creating class writers
     */
    @AssistedFactory
    public interface Factory {
        EventImplClassWriter create(final int flags);
    }

    public void generateField(final TypeElement container, final Property property) {
        if (!ClassGenerator.isRequired(property) && !ClassGenerator.generateMethods(property)) {
            // If the field will be unused, don't generate it at all
            return;
        }

        final FieldVisitor fv = this.visitField(
            ACC_PRIVATE,
            property.getName(),
            this.descriptors.getTypeDescriptor(property.getType()),
            this.signatures.ofField(container, property),
            null
        );
        fv.visitEnd();
    }

    /**
     * Generates a standard mutator method.
     *
     * <p>This method assumes that a standard field has been generated for the
     * provided {@link Property}</p>
     *
     * @param type The {@link Class} of the event that's having an
     *        implementation generated
     * @param internalName The internal name (slashes instead of periods in the
     *        package) of the new class being generated
     * @param fieldName The name of the field to mutate
     * @param fieldType The type of the field to mutate
     * @param property The {@link Property} containing the mutator method to
     *        generate for
     */
    public void generateMutator(
        final TypeElement type,
        final String internalName,
        final String fieldName,
        final TypeMirror fieldType,
        final Property property
    ) {
        final ExecutableElement mutator = property.getMutator().get();

        final MethodVisitor mv = this.visitMethod(
            ACC_PUBLIC,
            mutator.getSimpleName().toString(),
            this.descriptors.getDescriptor(mutator),
            this.signatures.ofMethod(type, mutator),
            null
        );
        mv.visitParameter(fieldName, 0);

        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitVarInsn(Type.getType(this.descriptors.getTypeDescriptor(property.getType())).getOpcode(ILOAD), 1);

        if (this.types.isSameType(this.types.erasure(property.getAccessor().asType()), this.elements.getTypeElement(Optional.class.getName()).asType())) {
            mv.visitMethodInsn(INVOKESTATIC, "java/util/Optional", "ofNullable",
                "(Ljava/lang/Object;)Ljava/util/Optional;", false);
        }

        if (!property.getType().getKind().isPrimitive() && !this.types.isSameType(property.getMostSpecificType(), property.getLeastSpecificType())) {
            final TypeMirror mostSpecificReturn = property.getMostSpecificType();
            //CtMethod<?> specific =  type.getMethod(property.getAccessor().getSimpleName(), getParameterTypes(property.getAccessor()));

            final Label afterException = new Label();
            mv.visitInsn(DUP);
            mv.visitJumpInsn(IFNULL, afterException);
            mv.visitInsn(DUP);
            mv.visitTypeInsn(INSTANCEOF, this.descriptors.getInternalName(mostSpecificReturn));

            mv.visitJumpInsn(IFNE, afterException);

            mv.visitTypeInsn(NEW, "java/lang/RuntimeException");
            mv.visitInsn(DUP);

            mv.visitTypeInsn(NEW, "java/lang/StringBuilder");
            mv.visitInsn(DUP);
            mv.visitMethodInsn(INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false);

            mv.visitLdcInsn("You've attempted to call the method '" + mutator.getSimpleName() + "' with an object of type ");
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/Object;)Ljava/lang/StringBuilder;", false);

            mv.visitVarInsn(Type.getType(this.descriptors.getTypeDescriptor(property.getType())).getOpcode(ILOAD), 1);
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Object", "getClass", "()Ljava/lang/Class;", false);
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Class", "getName", "()Ljava/lang/String;", false);
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/Object;)Ljava/lang/StringBuilder;", false);

            mv.visitLdcInsn(", instead of " + mostSpecificReturn + ". Though you may have been listening for a supertype of this "
                + "event, it's actually a " + type.getQualifiedName() + ". You need to ensure that the type of the event is what you think"
                + " it is, before calling the method (e.g TileEntityChangeEvent#setNewData");
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/Object;)Ljava/lang/StringBuilder;", false);
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false);

            mv.visitMethodInsn(INVOKESPECIAL, "java/lang/RuntimeException", "<init>", "(Ljava/lang/String;)V", false);
            mv.visitInsn(ATHROW);

            mv.visitLabel(afterException);
        }

        mv.visitFieldInsn(PUTFIELD, internalName, property.getName(), this.descriptors.getTypeDescriptor(property.getLeastSpecificType()));
        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }
}
