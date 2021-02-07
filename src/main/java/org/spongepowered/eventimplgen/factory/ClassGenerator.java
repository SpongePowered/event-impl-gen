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
import static org.objectweb.asm.Opcodes.ACC_SUPER;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ARETURN;
import static org.objectweb.asm.Opcodes.ASTORE;
import static org.objectweb.asm.Opcodes.ATHROW;
import static org.objectweb.asm.Opcodes.CHECKCAST;
import static org.objectweb.asm.Opcodes.DUP;
import static org.objectweb.asm.Opcodes.GETFIELD;
import static org.objectweb.asm.Opcodes.IFNE;
import static org.objectweb.asm.Opcodes.IFNONNULL;
import static org.objectweb.asm.Opcodes.IFNULL;
import static org.objectweb.asm.Opcodes.ILOAD;
import static org.objectweb.asm.Opcodes.INSTANCEOF;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.objectweb.asm.Opcodes.IRETURN;
import static org.objectweb.asm.Opcodes.ISUB;
import static org.objectweb.asm.Opcodes.NEW;
import static org.objectweb.asm.Opcodes.PUTFIELD;
import static org.objectweb.asm.Opcodes.RETURN;
import static org.objectweb.asm.Opcodes.V1_8;
import static org.spongepowered.eventimplgen.EventImplGenTask.getValue;

import org.gradle.api.JavaVersion;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.spongepowered.eventimplgen.EventImplGenTask;
import org.spongepowered.eventimplgen.eventgencore.Property;
import org.spongepowered.eventimplgen.eventgencore.PropertySorter;
import org.spongepowered.eventimplgen.factory.plugin.EventFactoryPlugin;
import spoon.reflect.declaration.CtAnnotation;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.CtType;
import spoon.reflect.declaration.ModifierKind;
import spoon.reflect.reference.CtArrayTypeReference;
import spoon.reflect.reference.CtTypeParameterReference;
import spoon.reflect.reference.CtTypeReference;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Generates the bytecode for classes needed by {@link ClassGeneratorProvider}.
 */
public class ClassGenerator {

    private NullPolicy nullPolicy = NullPolicy.DISABLE_PRECONDITIONS;
    private JavaVersion targetVersion = JavaVersion.VERSION_1_8;

    private static CtAnnotation<?> getPropertySettings(final Property property) {
        // Check the most specific method first.
        // This ensures that users can add @PropertySettings on an
        // overridden method.
        final CtAnnotation<?> anno = EventImplGenTask.getAnnotation(
                property.getMostSpecificMethod(),
                "org.spongepowered.api.util.annotation.eventgen.PropertySettings");

        if (anno != null) {
            return anno;
        }
        return EventImplGenTask.getAnnotation(property.getAccessor(), "org.spongepowered.api.util.annotation.eventgen.PropertySettings");
    }

    private static boolean isRequired(final Property property) {
        final CtAnnotation<?> settings = ClassGenerator.getPropertySettings(property);
        if (settings != null) {
            return getValue(settings, "requiredParameter");
        }
        return true;
    }

    private static boolean generateMethods(final Property property) {
        final CtAnnotation<?> settings = ClassGenerator.getPropertySettings(property);
        if (settings != null) {
            return getValue(settings, "generateMethods");
        }
        return true;
    }

    private static CtAnnotation<?> getUseField(final CtTypeReference<?> clazz, final String fieldName) {
        final CtType<?> type = clazz.getDeclaration();
        if (type == null) {
            return null;
        }

        final CtField<?> field = type.getField(fieldName);
        if (field != null) {
            return EventImplGenTask.getAnnotation(field, "org.spongepowered.api.util.annotation.eventgen.UseField");
        }
        return null;
    }

    public static boolean hasDeclaredMethod(final CtTypeReference<?> clazz, final String name, final CtTypeReference<?>... params) {
        CtMethod<?> method;
        CtType<?> type = clazz.getDeclaration();
        while (type != null) {
            method = type.getMethod(name, params);
            if (method != null) {
                return true;
            }
            type = type.getSuperclass() == null ? null : type.getSuperclass().getDeclaration();
        }

        return false;
    }

    public static CtField<?> getField(final CtTypeReference<?> clazz, final String fieldName) {
        CtField<?> field;
        CtType<?> type = clazz.getDeclaration();
        while (type != null) {
            field = type.getField(fieldName);
            if (field != null) {
                return field;
            }

            type = type.getSuperclass() == null ? null : type.getSuperclass().getDeclaration();
        }
        return null;
    }

    /**
     * Get the policy regarding how null parameters are handled.
     *
     * @return The null policy
     */
    public NullPolicy getNullPolicy() {
        return this.nullPolicy;
    }

    /**
     * Set the policy regarding how null parameters are handled.
     *
     * @param nullPolicy The null policy
     */
    public void setNullPolicy(final NullPolicy nullPolicy) {
        this.nullPolicy = Objects.requireNonNull(nullPolicy, "nullPolicy");
    }

    public void setTargetCompatibility(final JavaVersion version) {
        this.targetVersion = Objects.requireNonNull(version, "version");
    }

    private boolean hasNullable(final CtMethod<?> method) {
        return ClassGenerator.hasAnnotationOnSelfOrReturnType(method, name -> name.equals("Nullable"));
    }

    private boolean hasNonNull(final CtMethod<?> method) {
        return ClassGenerator.hasAnnotationOnSelfOrReturnType(method, name -> name.equals("NotNull") || name.equals("Nonnull"));
    }

    private static boolean hasAnnotationOnSelfOrReturnType(final CtMethod<?> method, final Predicate<String> annotationTest) {
        // On the method itself
        final List<CtAnnotation<?>> annotations = method.getAnnotations();
        if (!annotations.isEmpty()) {
            for (final CtAnnotation<?> annotation : annotations) {
                if (annotationTest.test(annotation.getType().getSimpleName())) {
                    return true;
                }
            }
        }

        // On return type
        final List<CtAnnotation<?>> typeUseAnnotations = method.getType().getAnnotations();
        if (!typeUseAnnotations.isEmpty()) {
            for (final CtAnnotation<?> annotation : annotations) {
                if (annotationTest.test(annotation.getType().getSimpleName())) {
                    return true;
                }
            }
        }

        return false;
    }

    public static void generateField(final ClassWriter classWriter, final CtType<?> container, final Property property) {
        final FieldVisitor fv = classWriter.visitField(ACC_PRIVATE, property.getName(), ClassGenerator.getTypeDescriptor(property.getType()),
                                                       Signatures.ofField(container, property), null);
        fv.visitEnd();
    }

    public static String getDescriptor(final CtMethod<?> method) {
        return ClassGenerator.getDescriptor(method, true);
    }

    public static String getDescriptor(final CtMethod<?> method, final boolean includeReturnType) {
        final StringBuilder builder = new StringBuilder();
        builder.append("(");
        for (final CtParameter<?> type: method.getParameters()) {
            builder.append(ClassGenerator.getTypeDescriptor(type.getType()));
        }
        builder.append(")");
        if (includeReturnType) {
            builder.append(ClassGenerator.getTypeDescriptor(method.getType()));
        } else {
            builder.append("V");
        }
        return builder.toString();
    }

    public static CtTypeReference<?>[] getParameterTypes(final CtMethod<?> method) {
        final List<CtTypeReference<?>> types = new ArrayList<>();
        for (final CtParameter<?> parameter: method.getParameters()) {
            types.add(parameter.getType());
        }
        return types.toArray(new CtTypeReference<?>[types.size()]);
    }

    public static String getTypeDescriptor(CtTypeReference<?> type) {
        // For descriptors, we want the actual type
        if (type instanceof CtTypeParameterReference) {
            type = ((CtTypeParameterReference) type).getBoundingType();
        }

        final String name = type.getQualifiedName();
        if (type.isPrimitive()) {
            if (name.equals("boolean")) {
                return "Z";
            } else if (name.equals("int")) {
                return "I";
            } else if (name.equals("byte")) {
                return "B";
            } else if (name.equals("short")) {
                return "S";
            } else if (name.equals("char")) {
                return "C";
            } else if (name.equals("void")) {
                return "V";
            } else if (name.equals("float")) {
                return "F";
            } else if (name.equals("long")) {
                return "J";
            } else if (name.equals("double")) {
                return "D";
            } else {
                return "V";
            }
        } else if (type instanceof CtArrayTypeReference) {
            return "[" + ClassGenerator.getTypeDescriptor(((CtArrayTypeReference) type).getComponentType());
        } else {
            return "L" + name.replace(".", "/") + ";";
        }
    }

    private void contributeField(final ClassWriter classWriter, final CtType<?> event, final CtTypeReference<?> parentType, final Property property) {
        if (property.isLeastSpecificType()) {
            final CtField<?> field = ClassGenerator.getField(parentType, property.getName());
            if (field == null || EventImplGenTask.getAnnotation(field, "org.spongepowered.api.util.annotation.eventgen.UseField") == null) {
                ClassGenerator.generateField(classWriter, event, property);
            } else if (field.getModifiers().contains(ModifierKind.PRIVATE)) {
                throw new RuntimeException("You've annotated the field " + property.getName() + " with @SetField, "
                        + "but it's private. This just won't work.");
            } else if (!field.getType().isSubtypeOf(property.getType())) {
                throw new RuntimeException(String.format("In event %s with parent %s - you've specified field '%s' of type %s"
                        + " but the property has the expected type of %s", property.getAccessor().getDeclaringType().getQualifiedName(),
                        parentType.getQualifiedName(), field.getSimpleName(), field.getType(), property.getType()));
            }
        }
    }

    public static List<Property> getRequiredProperties(final List<Property> properties) {
        return properties.stream().filter(p -> p.isMostSpecificType() && ClassGenerator.isRequired(p)).collect(Collectors.toList());
    }

    private void generateConstructor(
            final ClassWriter classWriter,
            final CtType<?> interfaceType,
            final String internalName,
            final CtTypeReference<?> parentType,
            final List<Property> properties) {
        final List<? extends Property> requiredProperties = ClassGenerator.getRequiredProperties(properties);

        final StringBuilder builder = new StringBuilder();
        builder.append("(");
        for (final Property property : requiredProperties) {
            builder.append(ClassGenerator.getTypeDescriptor(property.getType()));
        }
        builder.append(")V");

        final String methodDesc = builder.toString();

        final MethodVisitor mv = classWriter.visitMethod(
                0,
                "<init>",
                methodDesc,
                Signatures.ofConstructor(interfaceType, requiredProperties),
                null);

        // Parameter names
        for (final Property property : properties) {
            mv.visitParameter(property.getName(), 0);
        }

        // The implementation
        mv.visitCode();

        // super()
        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(INVOKESPECIAL, ClassGenerator.getInternalName(parentType.getQualifiedName()), "<init>", "()V", false);

        // 0 is 'this', parameters start at 1
        for (int i = 0, paramIndex = 1; i < requiredProperties.size(); i++, paramIndex++) {
            final Property property = requiredProperties.get(i);

            final Type type = Type.getType(ClassGenerator.getTypeDescriptor(property.getType()));
            final int loadOpcode = type.getOpcode(Opcodes.ILOAD);

            final boolean isPrimitive = property.getType().isPrimitive();

            // Only if we have a null policy:
            // if (value == null) throw new NullPointerException(...)
            if (this.nullPolicy != NullPolicy.DISABLE_PRECONDITIONS) {
                final boolean useNullTest = !isPrimitive && (((this.nullPolicy == NullPolicy.NON_NULL_BY_DEFAULT && !this.hasNullable(property.getAccessor()))
                        || (this.nullPolicy == NullPolicy.NULL_BY_DEFAULT && this.hasNonNull(property.getAccessor())))
                        && ClassGenerator.isRequired(property));

                if (useNullTest) {
                    final Label afterNullTest = new Label();
                    mv.visitVarInsn(loadOpcode, paramIndex);
                    mv.visitJumpInsn(IFNONNULL, afterNullTest);
                    mv.visitTypeInsn(NEW, "java/lang/NullPointerException");
                    mv.visitInsn(DUP);
                    mv.visitLdcInsn("The property '" + property.getName() + "' was not provided!");
                    mv.visitMethodInsn(INVOKESPECIAL, "java/lang/NullPointerException", "<init>", "(Ljava/lang/String;)V", false);
                    mv.visitInsn(ATHROW);
                    mv.visitLabel(afterNullTest);
                }
            }

            final boolean hasUseField = ClassGenerator.getUseField(parentType, property.getName()) != null;

            // stack: -> this
            mv.visitVarInsn(ALOAD, 0);

            // ProperObject newValue = (ProperObject) value
            mv.visitVarInsn(loadOpcode, paramIndex);
            //visitUnboxingMethod(mv, Type.getType(property.getType()));

            // this.field = newValue

            final String desc = ClassGenerator.getTypeDescriptor(property.getLeastSpecificType());

            if (hasUseField) {
                mv.visitFieldInsn(PUTFIELD, ClassGenerator.getInternalName(parentType.getQualifiedName()), property.getName(), desc);
            } else {
                mv.visitFieldInsn(PUTFIELD, internalName, property.getName(), desc);
            }
            // }

            if (type.equals(Type.LONG_TYPE) || type.equals(Type.DOUBLE_TYPE)) {
                paramIndex++; // Skip empty slot
            }
        }

        // super.init();
        if (ClassGenerator.hasDeclaredMethod(parentType, "init")) {
            mv.visitVarInsn(ALOAD, 0);
            mv.visitMethodInsn(INVOKESPECIAL, ClassGenerator.getInternalName(parentType.getQualifiedName()), "init", "()V", false);
        }

        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private void generateAccessor(
            final ClassWriter cw,
            final CtType<?> eventClass,
            final String internalName,
            final Property property) {
        final CtMethod<?> accessor = property.getAccessor();

        final MethodVisitor mv = cw.visitMethod(
            ACC_PUBLIC,
            accessor.getSimpleName(),
            ClassGenerator.getDescriptor(accessor),
            Signatures.ofMethod(eventClass, accessor),
            null
        );
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitFieldInsn(GETFIELD, internalName, property.getName(), ClassGenerator.getTypeDescriptor(property.getLeastSpecificType()));

        if (!property.isLeastSpecificType()) {
            mv.visitTypeInsn(CHECKCAST, ClassGenerator.getInternalName(property.getType().getQualifiedName()));
        }
        mv.visitInsn(Type.getType(ClassGenerator.getTypeDescriptor(property.getType())).getOpcode(IRETURN));
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    /**
     * Generates a standard mutator method.
     *
     * <p>This method assumes that a standard field has been generated for the
     * provided {@link Property}</p>
     *
     * @param cw The {@link ClassWriter} to generate the mutator in
     * @param type The {@link Class} of the event that's having an
     *        implementation generated
     * @param internalName The internal name (slashes instead of periods in the
     *        package) of the new class being generated
     * @param fieldName The name of the field to mutate
     * @param fieldType The type of the field to mutate
     * @param property The {@link Property} containing the mutator method to
     *        generate for
     */
    public static void generateMutator(
            final ClassWriter cw,
            final CtType<?> type,
            final String internalName,
            final String fieldName,
            final CtType<?> fieldType,
            final Property property) {
        final CtMethod<?> mutator = property.getMutator().get();

        final MethodVisitor mv = cw.visitMethod(
            ACC_PUBLIC,
            mutator.getSimpleName(),
            ClassGenerator.getDescriptor(mutator),
            Signatures.ofMethod(type, mutator),
            null
        );
        mv.visitParameter(fieldName, 0);

        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitVarInsn(Type.getType(ClassGenerator.getTypeDescriptor(property.getType())).getOpcode(ILOAD), 1);

        if (property.getAccessor().getType().getQualifiedName().equals(Optional.class.getName())) {
            mv.visitMethodInsn(INVOKESTATIC, "java/util/Optional", "ofNullable",
                               "(Ljava/lang/Object;)Ljava/util/Optional;", false);
        }

        if (!property.getType().isPrimitive() && !property.getMostSpecificType().getQualifiedName().equals(property.getLeastSpecificType().getQualifiedName())) {
            final CtTypeReference<?> mostSpecificReturn = property.getMostSpecificType();
            //CtMethod<?> specific =  type.getMethod(property.getAccessor().getSimpleName(), getParameterTypes(property.getAccessor()));

            final Label afterException = new Label();
            mv.visitInsn(DUP);
            mv.visitJumpInsn(IFNULL, afterException);
            mv.visitInsn(DUP);
            mv.visitTypeInsn(INSTANCEOF, ClassGenerator.getInternalName(mostSpecificReturn.getQualifiedName()));

            mv.visitJumpInsn(IFNE, afterException);

            mv.visitTypeInsn(NEW, "java/lang/RuntimeException");
            mv.visitInsn(DUP);

            mv.visitTypeInsn(NEW, "java/lang/StringBuilder");
            mv.visitInsn(DUP);
            mv.visitMethodInsn(INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false);

            mv.visitLdcInsn("You've attempted to call the method '" + mutator.getSimpleName() + "' with an object of type ");
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/Object;)Ljava/lang/StringBuilder;", false);

            mv.visitVarInsn(Type.getType(ClassGenerator.getTypeDescriptor(property.getType())).getOpcode(ILOAD), 1);
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Object", "getClass", "()Ljava/lang/Class;", false);
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Class", "getName", "()Ljava/lang/String;", false);
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/Object;)Ljava/lang/StringBuilder;", false);

            mv.visitLdcInsn(", instead of " + mostSpecificReturn.getSimpleName() + ". Though you may have been listening for a supertype of this "
                                    + "event, it's actually a " + type.getQualifiedName() + ". You need to ensure that the type of the event is what you think"
                                    + " it is, before calling the method (e.g TileEntityChangeEvent#setNewData");
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/Object;)Ljava/lang/StringBuilder;", false);
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false);

            mv.visitMethodInsn(INVOKESPECIAL, "java/lang/RuntimeException", "<init>", "(Ljava/lang/String;)V", false);
            mv.visitInsn(ATHROW);

            mv.visitLabel(afterException);
        }

        mv.visitFieldInsn(PUTFIELD, internalName, property.getName(), ClassGenerator.getTypeDescriptor(property.getLeastSpecificType()));
        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private void generateAccessorsAndMutator(
            final ClassWriter cw,
            final CtType<?> type,
            final CtTypeReference<?> parentType,
            final String internalName,
            final Property property) {
        if (ClassGenerator.generateMethods(property)) {
            this.generateAccessor(cw, type, internalName, property);

            final Optional<CtMethod<?>> mutatorOptional = property.getMutator();
            if (mutatorOptional.isPresent()) {
                ClassGenerator.generateMutator(cw, type, internalName, property.getName(), property.getType().getDeclaration(), property);
            }
        }
    }

    private MethodVisitor initializeToString(final ClassWriter cw, final CtType<?> type) {
        final MethodVisitor toStringMv = cw.visitMethod(ACC_PUBLIC, "toString", "()Ljava/lang/String;", null, null);
        toStringMv.visitCode();
        toStringMv.visitTypeInsn(NEW, "java/lang/StringBuilder");
        toStringMv.visitInsn(DUP);
        toStringMv.visitMethodInsn(INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false);
        toStringMv.visitLdcInsn(type.getSimpleName() + "{");
        toStringMv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/Object;)Ljava/lang/StringBuilder;", false);

        toStringMv.visitVarInsn(ASTORE, 1);

        return toStringMv;
    }

    private void contributeToString(
            final String internalName,
            final CtTypeReference<?> parentType,
            final Property property,
            final MethodVisitor toStringMv) {

        if (property.isLeastSpecificType()) {
            boolean overrideToString = false;
            final CtAnnotation<?> useField = ClassGenerator.getUseField(parentType, property.getName());
            if (useField != null) {
                overrideToString = EventImplGenTask.getValue(useField, "overrideToString");
            }

            toStringMv.visitVarInsn(ALOAD, 0);

            toStringMv.visitVarInsn(ALOAD, 1);
            toStringMv.visitLdcInsn(property.getName());
            toStringMv
                    .visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/Object;)Ljava/lang/StringBuilder;", false);

            toStringMv.visitLdcInsn("=");
            toStringMv
                    .visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/Object;)Ljava/lang/StringBuilder;", false);

            toStringMv.visitVarInsn(ALOAD, 0);
            if (overrideToString) {
                toStringMv.visitFieldInsn(GETFIELD, internalName, property.getName(), ClassGenerator.getTypeDescriptor(property.getLeastSpecificType()));
            } else {
                toStringMv.visitMethodInsn(INVOKESPECIAL, internalName, property.getAccessor().getSimpleName(),
                    ClassGenerator.getDescriptor(property.getAccessor()), false);
            }

            final String desc = property.getType().isPrimitive() ? ClassGenerator.getTypeDescriptor(property.getType()) : "Ljava/lang/Object;";

            toStringMv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append",
                                       "(" + desc + ")Ljava/lang/StringBuilder;", false);

            toStringMv.visitLdcInsn(", ");
            toStringMv
                    .visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/Object;)Ljava/lang/StringBuilder;", false);
        }
    }

    private void finalizeToString(final MethodVisitor mv) {
        // The StringBuilder is on the top of the stack from the last append() -
        // duplicate it for call to replace()
        mv.visitVarInsn(ALOAD, 1);
        mv.visitInsn(DUP);

        // The replace starts at 2 characters before the end, to remove the
        // extra command and space added
        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "length", "()I", false);
        mv.visitLdcInsn(2);
        mv.visitInsn(ISUB);

        mv.visitVarInsn(ALOAD, 1);
        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "length", "()I", false);

        mv.visitLdcInsn("}");

        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "replace", "(IILjava/lang/String;)Ljava/lang/StringBuilder;", false);
        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false);

        mv.visitInsn(ARETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    public static String getInternalName(final String name) {
        return name.replace('.', '/');
    }

    /**
     * Create the event class.
     *
     * @param type The type
     * @param name The canonical of the generated class
     * @param parentType The parent type
     * @return The class' contents, to be loaded via a {@link ClassLoader}
     */
    public byte[] createClass(
            final CtType<?> type,
            final String name,
            final CtTypeReference<?> parentType,
            final List<Property> properties,
            final PropertySorter sorter,
            final List<? extends EventFactoryPlugin> plugins) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(parentType, "parentType");

        final String internalName = ClassGenerator.getInternalName(name);

        final ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cw.visit(
            V1_8,
            ACC_SUPER,
            internalName,
            Signatures.ofImplClass(parentType.getTypeDeclaration(), Collections.singletonList(type)),
            ClassGenerator.getInternalName(parentType.getQualifiedName()),
            new String[] {ClassGenerator.getInternalName(type.getQualifiedName())}
        );


        // Create the constructor
        this.generateConstructor(cw, type, internalName, parentType, sorter.sortProperties(properties));

        final MethodVisitor toStringMv = this.initializeToString(cw, type);

        // Create the fields
        // this.contributeFields(cw, parentType, properties, plugins);

        // The return value of toString takes the form of
        // "ClassName{param1=value1, param2=value2, ...}"

        // Create the accessors and mutators, and fill out the toString method
        this.generateWithPlugins(cw, type, parentType, internalName, properties, toStringMv, plugins);

        // Now build the toString
        this.finalizeToString(toStringMv);

        cw.visitEnd();

        return cw.toByteArray();
    }

    private void generateWithPlugins(
            final ClassWriter cw,
            final CtType<?> eventClass,
            final CtTypeReference<?> parentType,
            final String internalName,
            final List<? extends Property> properties,
            final MethodVisitor toStringMv,
            final List<? extends EventFactoryPlugin> plugins) {

        for (final Property property : properties) {
            boolean processed = false;

            for (final EventFactoryPlugin plugin : plugins) {
                processed = plugin.contributeProperty(eventClass, internalName, cw, property);
                if (processed) {
                    break;
                }
            }

            this.contributeToString(internalName, parentType, property, toStringMv);

            if (!processed) {
                this.contributeField(cw, eventClass, parentType, property);
                this.generateAccessorsAndMutator(cw, eventClass, parentType, internalName, property);
            }
        }
    }

    public static String getEventName(final CtType<?> event, final ClassGeneratorProvider classGeneratorProvider) {
        return classGeneratorProvider.getClassName(event, "Impl");
    }
}
