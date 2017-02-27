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

import static com.google.common.base.Preconditions.checkNotNull;
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
import static org.objectweb.asm.Opcodes.V1_6;
import static org.spongepowered.eventimplgen.EventImplGenTask.getValue;

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
import spoon.reflect.reference.CtTypeReference;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Generates the bytecode for classes needed by {@link ClassGeneratorProvider}.
 */
public class ClassGenerator {

    private NullPolicy nullPolicy = NullPolicy.DISABLE_PRECONDITIONS;

    private static CtAnnotation<?> getPropertySettings(Property property) {
        return EventImplGenTask.getAnnotation(property.getAccessor(), "org.spongepowered.api.util.annotation.eventgen.PropertySettings");
    }

    private static boolean isRequired(Property property) {
        CtAnnotation<?> settings = getPropertySettings(property);
        if (settings != null) {
            return getValue(settings, "requiredParameter");
        }
        return true;
    }

    private static boolean generateMethods(Property property) {
        CtAnnotation<?> settings = getPropertySettings(property);
        if (settings != null) {
            return getValue(settings, "generateMethods");
        }
        return true;
    }

    private static CtAnnotation<?> getUseField(CtTypeReference<?> clazz, String fieldName) {
        CtType<?> type = clazz.getDeclaration();
        if (type == null) {
            return null;
        }

        CtField<?> field = type.getField(fieldName);
        if (field != null) {
            return EventImplGenTask.getAnnotation(field, "org.spongepowered.api.util.annotation.eventgen.UseField");
        }
        return null;
    }

    public static boolean hasDeclaredMethod(CtTypeReference<?> clazz, String name, CtTypeReference<?>... params) {
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

    public static CtField<?> getField(CtTypeReference<?> clazz, String fieldName) {
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
    public void setNullPolicy(NullPolicy nullPolicy) {
        checkNotNull(nullPolicy, "nullPolicy");
        this.nullPolicy = nullPolicy;
    }

    private boolean hasNullable(CtMethod<?> method) {
        return method.getAnnotation(Nullable.class) != null;
    }

    private boolean hasNonnull(CtMethod<?> method) {
        return method.getAnnotation(Nonnull.class) != null;
    }

    public static void generateField(ClassWriter classWriter, Property property) {
        FieldVisitor fv = classWriter.visitField(ACC_PRIVATE, property.getName(), getTypeDescriptor(property.getType()), null, null);
        fv.visitEnd();
    }

    public static String getDescriptor(CtMethod<?> method) {
        return getDescriptor(method, true);
    }

    public static String getDescriptor(CtMethod<?> method, boolean includeReturnType) {
        StringBuilder builder = new StringBuilder();
        builder.append("(");
        for (CtParameter<?> type: method.getParameters()) {
            builder.append(getTypeDescriptor(type.getType()));
        }
        builder.append(")");
        if (includeReturnType) {
            builder.append(getTypeDescriptor(method.getType()));
        } else {
            builder.append("V");
        }
        return builder.toString();
    }

    public static CtTypeReference<?>[] getParameterTypes(CtMethod<?> method) {
        List<CtTypeReference<?>> types = new ArrayList<>();
        for (CtParameter<?> parameter: method.getParameters()) {
            types.add(parameter.getType());
        }
        return types.toArray(new CtTypeReference<?>[types.size()]);
    }

    public static String getTypeDescriptor(CtTypeReference<?> type) {
        String name = type.getQualifiedName();
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
            return "[" + getTypeDescriptor(((CtArrayTypeReference) type).getComponentType());
        } else {
            return "L" + name.replace(".", "/") + ";";
        }
    }

    private void contributeField(ClassWriter classWriter, CtTypeReference<?> parentType, Property property) {
        if (property.isLeastSpecificType()) {
            CtField<?> field = getField(parentType, property.getName());
            if (field == null || EventImplGenTask.getAnnotation(field, "org.spongepowered.api.util.annotation.eventgen.UseField") == null) {
                generateField(classWriter, property);
            } else if (field.getModifiers().contains(ModifierKind.PRIVATE)) {
                throw new RuntimeException("You've annotated the field " + property.getName() + " with @SetField, "
                        + "but it's private. This just won't work.");
            } else if (!property.getType().isSubtypeOf(field.getType())) {
                throw new RuntimeException(String.format("In event %s with parent %s - you've specified field '%s' of type %s"
                        + " but the property has the type of %s", property.getAccessor().getDeclaringType().getQualifiedName(), parentType.getQualifiedName(), field.getSimpleName(), field.getType().getQualifiedName(), property.getType().getQualifiedName()));
            }
        }
    }

    public static List<Property> getRequiredProperties(List<Property> properties) {
        return properties.stream().filter(p -> p.isMostSpecificType() && isRequired(p)).collect(Collectors.toList());
    }

    private void generateConstructor(ClassWriter classWriter, String internalName, CtTypeReference<?> parentType,
           List<Property> properties) {

        List<? extends Property> requiredProperties = getRequiredProperties(properties);

        StringBuilder builder = new StringBuilder();
        builder.append("(");
        for (Property property: requiredProperties) {
            builder.append(getTypeDescriptor(property.getType()));
        }
        builder.append(")V");

        String methodDesc = builder.toString();

        MethodVisitor mv =
                classWriter.visitMethod(0, "<init>", methodDesc, null, null);
        mv.visitCode();

        // super()
        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(INVOKESPECIAL, ClassGenerator.getInternalName(parentType.getQualifiedName()), "<init>", "()V", false);

        // 0 is 'this', parameters start at 1
        for (int i = 0, paramIndex = 1; i < requiredProperties.size(); i++, paramIndex++) {
            Property property = requiredProperties.get(i);

            Type type = Type.getType(getTypeDescriptor(property.getType()));
            int loadOpcode = type.getOpcode(Opcodes.ILOAD);

            boolean isPrimitive = property.getType().isPrimitive();

            // Only if we have a null policy:
            // if (value == null) throw new NullPointerException(...)
            if (this.nullPolicy != NullPolicy.DISABLE_PRECONDITIONS) {
                boolean useNullTest = !isPrimitive && (((this.nullPolicy == NullPolicy.NON_NULL_BY_DEFAULT && !this.hasNullable(property.getAccessor()))
                        || (this.nullPolicy == NullPolicy.NULL_BY_DEFAULT && this.hasNonnull(property.getAccessor())))
                        && isRequired(property));

                if (useNullTest) {
                    Label afterNullTest = new Label();
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

            final boolean hasUseField = getUseField(parentType, property.getName()) != null;

            // stack: -> this
            mv.visitVarInsn(ALOAD, 0);

            // ProperObject newValue = (ProperObject) value
            mv.visitVarInsn(loadOpcode, paramIndex);
            //visitUnboxingMethod(mv, Type.getType(property.getType()));

            // this.field = newValue

            String desc = getTypeDescriptor(property.getLeastSpecificType());

            if (hasUseField) {
                mv.visitFieldInsn(PUTFIELD, getInternalName(parentType.getQualifiedName()), property.getName(), desc);
            } else {
                mv.visitFieldInsn(PUTFIELD, internalName, property.getName(), desc);
            }
            // }

            if (type.equals(Type.LONG_TYPE) || type.equals(Type.DOUBLE_TYPE)) {
                paramIndex++; // Skip empty slot
            }
        }

        // super.init();
        if (hasDeclaredMethod(parentType, "init")) {
            mv.visitVarInsn(ALOAD, 0);
            mv.visitMethodInsn(INVOKESPECIAL, getInternalName(parentType.getQualifiedName()), "init", "()V", false);
        }

        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private void generateAccessor(ClassWriter cw, CtTypeReference<?> parentType, String internalName, Property property) {
        CtMethod<?> accessor = property.getAccessor();

        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, accessor.getSimpleName(), getDescriptor(accessor), null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitFieldInsn(GETFIELD, internalName, property.getName(), getTypeDescriptor(property.getLeastSpecificType()));

        if (!property.isLeastSpecificType()) {
            mv.visitTypeInsn(CHECKCAST, getInternalName(property.getType().getQualifiedName()));
        }
        mv.visitInsn(Type.getType(getTypeDescriptor(property.getType())).getOpcode(IRETURN));
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
    public static void generateMutator(ClassWriter cw, CtType<?> type, String internalName, String fieldName, CtType<?> fieldType,
            Property property) {
        CtMethod<?> mutator = property.getMutator().get();

        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, mutator.getSimpleName(), getDescriptor(mutator), null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitVarInsn(Type.getType(getTypeDescriptor(property.getType())).getOpcode(ILOAD), 1);

        if (property.getAccessor().getType().getQualifiedName().equals(Optional.class.getName())) {
            mv.visitMethodInsn(INVOKESTATIC, "java/util/Optional", "ofNullable",
                    "(Ljava/lang/Object;)Ljava/util/Optional;", false);
        }

        if (!property.getType().isPrimitive() && !property.getMostSpecificType().getQualifiedName().equals(property.getLeastSpecificType().getQualifiedName())) {
            CtTypeReference<?> mostSpecificReturn = property.getMostSpecificType();
            //CtMethod<?> specific =  type.getMethod(property.getAccessor().getSimpleName(), getParameterTypes(property.getAccessor()));

            Label afterException = new Label();
            mv.visitInsn(DUP);
            mv.visitJumpInsn(IFNULL, afterException);
            mv.visitInsn(DUP);
            mv.visitTypeInsn(INSTANCEOF, getInternalName(mostSpecificReturn.getQualifiedName()));

            mv.visitJumpInsn(IFNE, afterException);

            mv.visitTypeInsn(NEW, "java/lang/RuntimeException");
            mv.visitInsn(DUP);

            mv.visitTypeInsn(NEW, "java/lang/StringBuilder");
            mv.visitInsn(DUP);
            mv.visitMethodInsn(INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false);

            mv.visitLdcInsn("You've attempted to call the method '" + mutator.getSimpleName() + "' with an object of type ");
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/Object;)Ljava/lang/StringBuilder;", false);

            mv.visitVarInsn(Type.getType(getTypeDescriptor(property.getType())).getOpcode(ILOAD), 1);
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

        mv.visitFieldInsn(PUTFIELD, internalName, property.getName(), getTypeDescriptor(property.getLeastSpecificType()));
        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private void generateAccessorsandMutator(ClassWriter cw, CtType<?> type, CtTypeReference<?> parentType, String internalName,
            Property property) {
        if (generateMethods(property)) {
            this.generateAccessor(cw, parentType, internalName, property);

            Optional<CtMethod<?>> mutatorOptional = property.getMutator();
            if (mutatorOptional.isPresent()) {
                generateMutator(cw, type, internalName, property.getName(), property.getType().getDeclaration(), property);
            }
        }
    }

    private MethodVisitor initializeToString(ClassWriter cw, CtType<?> type) {
        MethodVisitor toStringMv = cw.visitMethod(ACC_PUBLIC, "toString", "()Ljava/lang/String;", null, null);
        toStringMv.visitCode();
        toStringMv.visitTypeInsn(NEW, "java/lang/StringBuilder");
        toStringMv.visitInsn(DUP);
        toStringMv.visitMethodInsn(INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false);
        toStringMv.visitLdcInsn(type.getSimpleName() + "{");
        toStringMv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/Object;)Ljava/lang/StringBuilder;", false);

        toStringMv.visitVarInsn(ASTORE, 1);

        return toStringMv;
    }

    private void contributeToString(String internalName, CtTypeReference<?> parentType, Property property, MethodVisitor toStringMv) {
        if (property.isLeastSpecificType()) {

            boolean overrideToString = false;
            CtAnnotation<?> useField = getUseField(parentType, property.getName());
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
                toStringMv.visitFieldInsn(GETFIELD, internalName, property.getName(), getTypeDescriptor(property.getLeastSpecificType()));
            } else {
                toStringMv.visitMethodInsn(INVOKESPECIAL, internalName, property.getAccessor().getSimpleName(),
                        getDescriptor(property.getAccessor()), false);
            }

            String desc = property.getType().isPrimitive() ? getTypeDescriptor(property.getType()) : "Ljava/lang/Object;";

            toStringMv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append",
                    "(" + desc + ")Ljava/lang/StringBuilder;", false);

            toStringMv.visitLdcInsn(", ");
            toStringMv
                    .visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/Object;)Ljava/lang/StringBuilder;", false);
        }
    }

    private void finalizeToString(MethodVisitor mv) {
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

    public static String getInternalName(String name) {
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
    public byte[] createClass(final CtType<?> type, final String name, final CtTypeReference<?> parentType, List<Property> properties, PropertySorter sorter, List<? extends EventFactoryPlugin> plugins) {
        checkNotNull(type, "type");
        checkNotNull(name, "name");
        checkNotNull(parentType, "parentType");

        final String internalName = getInternalName(name);

        final ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cw.visit(V1_6, ACC_SUPER, internalName, null, getInternalName(parentType.getQualifiedName()), new String[] {getInternalName(type.getQualifiedName())});

        MethodVisitor toStringMv = this.initializeToString(cw, type);

        this.generateWithPlugins(cw, type, parentType, internalName, properties, toStringMv, plugins);

        // Create the fields
        // this.contributeFields(cw, parentType, properties, plugins);

        // Create the constructor
        this.generateConstructor(cw, internalName, parentType, sorter.sortProperties(properties));

        // The return value of toString takes the form of
        // "ClassName{param1=value1, param2=value2, ...}"

        // Create the accessors and mutators, and fill out the toString method

        this.finalizeToString(toStringMv);

        cw.visitEnd();

        return cw.toByteArray();
    }

    private void generateWithPlugins(ClassWriter cw, CtType<?> eventClass, CtTypeReference<?> parentType, String internalName,
            List<? extends Property> properties, MethodVisitor toStringMv, List<? extends EventFactoryPlugin> plugins) {

        for (Property property : properties) {
            boolean processed = false;

            for (EventFactoryPlugin plugin : plugins) {
                processed = plugin.contributeProperty(eventClass, internalName, cw, property);
                if (processed) {
                    break;
                }
            }

            this.contributeToString(internalName, parentType, property, toStringMv);

            if (!processed) {
                this.contributeField(cw, parentType, property);
                this.generateAccessorsandMutator(cw, eventClass, parentType, internalName, property);
            }
        }
    }

    public static String getEventName(CtType<?> event, ClassGeneratorProvider classGeneratorProvider) {
        return classGeneratorProvider.getClassName(event, "Impl");
    }
}
