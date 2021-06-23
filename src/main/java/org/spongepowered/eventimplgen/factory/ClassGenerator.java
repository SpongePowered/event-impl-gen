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

import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.ACC_SUPER;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ARETURN;
import static org.objectweb.asm.Opcodes.ASTORE;
import static org.objectweb.asm.Opcodes.ATHROW;
import static org.objectweb.asm.Opcodes.CHECKCAST;
import static org.objectweb.asm.Opcodes.DUP;
import static org.objectweb.asm.Opcodes.GETFIELD;
import static org.objectweb.asm.Opcodes.IFNONNULL;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.objectweb.asm.Opcodes.IRETURN;
import static org.objectweb.asm.Opcodes.ISUB;
import static org.objectweb.asm.Opcodes.NEW;
import static org.objectweb.asm.Opcodes.PUTFIELD;
import static org.objectweb.asm.Opcodes.RETURN;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.spongepowered.api.util.annotation.eventgen.PropertySettings;
import org.spongepowered.api.util.annotation.eventgen.UseField;
import org.spongepowered.eventimplgen.AnnotationUtils;
import org.spongepowered.eventimplgen.eventgencore.Property;
import org.spongepowered.eventimplgen.eventgencore.PropertySorter;
import org.spongepowered.eventimplgen.factory.plugin.EventFactoryPlugin;
import org.spongepowered.eventimplgen.processor.PreviewFeatures;
import org.spongepowered.eventimplgen.signature.Descriptors;
import org.spongepowered.eventimplgen.signature.Signatures;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.annotation.processing.Messager;
import javax.inject.Inject;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;

/**
 * Generates the bytecode for classes needed by {@link ClassNameProvider}.
 */
public class ClassGenerator {

    private final Types types;
    private final Elements elements;
    private final Signatures signatures;
    private final Descriptors descriptors;
    private final Messager messager;
    private final SourceVersion targetVersion;
    private final boolean previewEnabled;
    private final ClassNameProvider classNameProvider;
    private final EventImplClassWriter.Factory classWriterFactory;

    private NullPolicy nullPolicy = NullPolicy.DISABLE_PRECONDITIONS;

    @Inject
    ClassGenerator(
        final ClassNameProvider classNameProvider,
        final Types types,
        final Elements elements,
        final Signatures signatures,
        final Descriptors descriptors,
        final Messager messager,
        final EventImplClassWriter.Factory classWriterFactory,
        final SourceVersion targetVersion,
        @PreviewFeatures final boolean previewEnabled
    ) {
        this.classNameProvider = classNameProvider;
        this.types = types;
        this.elements = elements;
        this.signatures = signatures;
        this.descriptors = descriptors;
        this.messager = messager;
        this.classWriterFactory = classWriterFactory;
        this.targetVersion = targetVersion;
        this.previewEnabled = previewEnabled;
    }

    private static PropertySettings getPropertySettings(final Property property) {
        // Check the most specific method first.
        // This ensures that users can add @PropertySettings on an
        // overridden method.
        final PropertySettings anno = property.getMostSpecificMethod().getAnnotation(PropertySettings.class);

        if (anno != null) {
            return anno;
        }
        return property.getAccessor().getAnnotation(PropertySettings.class);
    }

    static boolean isRequired(final Property property) {
        final PropertySettings settings = ClassGenerator.getPropertySettings(property);
        if (settings == null) {
            return !property.getMostSpecificMethod().isDefault();
        } else {
            return settings.requiredParameter();
        }
    }

    static boolean generateMethods(final Property property) {
        final PropertySettings settings = ClassGenerator.getPropertySettings(property);
        if (settings != null) {
            return settings.generateMethods();
        } else {
            return !property.getMostSpecificMethod().isDefault();
        }
    }

    private static AnnotationMirror getUseField(final TypeMirror clazz, final String fieldName) {
        if (clazz.getKind() != TypeKind.DECLARED) {
            return null;
        }
        final Element type = ((DeclaredType) clazz).asElement();
        if (type == null) {
            return null;
        }

        for (final VariableElement element : ElementFilter.fieldsIn(type.getEnclosedElements())) {
            if (element.getSimpleName().contentEquals(fieldName)) {
                return AnnotationUtils.getAnnotation(element, "org.spongepowered.api.util.annotation.eventgen.UseField");
            }
        }

        return null;
    }

    public boolean hasDeclaredMethod(final DeclaredType clazz, final String name, final TypeMirror... params) {
        for (final ExecutableElement method : ElementFilter.methodsIn(this.elements.getAllMembers((TypeElement) clazz.asElement()))) {
            if (method.getSimpleName().contentEquals(name) && this.parametersEqual(method.getParameters(), params)) {
                return true;
            }
        }

        return false;
    }

    private boolean parametersEqual(final List<? extends VariableElement> a, final TypeMirror... b) {
        if (a.size() != b.length) {
            return false;
        }

        for (int i = 0; i < b.length; ++i) {
            if (!this.types.isSameType(a.get(i).asType(), b[i])) {
                return false;
            }
        }
        return true;
    }

    public VariableElement getField(final DeclaredType clazz, final String fieldName) {
        for (final VariableElement field : ElementFilter.fieldsIn(this.elements.getAllMembers((TypeElement) clazz.asElement()))) {
            if (field.getSimpleName().contentEquals(fieldName)) {
                return field;
            }
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

    private boolean hasNullable(final ExecutableElement method) {
        return ClassGenerator.hasAnnotationOnSelfOrReturnType(method, name -> name.contentEquals("Nullable"));
    }

    private boolean hasNonNull(final ExecutableElement method) {
        return ClassGenerator.hasAnnotationOnSelfOrReturnType(method, name -> name.contentEquals("NotNull") || name.contentEquals("Nonnull"));
    }

    private static boolean hasAnnotationOnSelfOrReturnType(final ExecutableElement method, final Predicate<Name> annotationTest) {
        // On the method itself
        final List<? extends AnnotationMirror> annotations = method.getAnnotationMirrors();
        if (!annotations.isEmpty()) {
            for (final AnnotationMirror annotation : annotations) {
                if (annotationTest.test(annotation.getAnnotationType().asElement().getSimpleName())) {
                    return true;
                }
            }
        }

        // On return type
        final List<? extends AnnotationMirror> typeUseAnnotations = method.getReturnType().getAnnotationMirrors();
        if (!typeUseAnnotations.isEmpty()) {
            for (final AnnotationMirror annotation : annotations) {
                if (annotationTest.test(annotation.getAnnotationType().asElement().getSimpleName())) {
                    return true;
                }
            }
        }

        return false;
    }

    public boolean contributeField(final EventImplClassWriter classWriter, final TypeElement event, final DeclaredType parentType, final Property property) {
        if (property.isLeastSpecificType(this.types)) {
            final VariableElement field = this.getField(parentType, property.getName());
            if (field == null || field.getAnnotation(UseField.class) == null) {
                classWriter.generateField(event, property);
                return true;
            } else if (field.getModifiers().contains(Modifier.PRIVATE)) {
                this.messager.printMessage(Diagnostic.Kind.ERROR, "You've annotated the field " + property.getName() + " with @UseField, "
                    + "but it's private. This just won't work.", field);
                return false;
            } else if (!this.types.isSubtype(field.asType(), property.getType())) {
                this.messager.printMessage(Diagnostic.Kind.ERROR, String.format("In event %s with parent %s - you've specified field '%s' of type %s"
                        + " but the property has the expected type of %s", this.elements.getBinaryName((TypeElement) property.getAccessor().getEnclosingElement()),
                    parentType, field.getSimpleName(), field.asType(), property.getType()), field);
                return false;
            }
        }
        return true;
    }

    public List<Property> getRequiredProperties(final List<Property> properties) {
        return properties.stream().filter(p -> p.isMostSpecificType(this.types) && ClassGenerator.isRequired(p)).collect(Collectors.toList());
    }

    private void generateConstructor(
            final ClassWriter classWriter,
            final TypeElement interfaceType,
            final String internalName,
            final DeclaredType parentType,
            final List<Property> properties) {
        final List<Property> requiredProperties = this.getRequiredProperties(properties);
        final String methodDesc = this.descriptors.getDescriptor(requiredProperties, this.types.getNoType(TypeKind.VOID));

        final MethodVisitor mv = classWriter.visitMethod(
                0,
                "<init>",
                methodDesc,
                this.signatures.ofConstructor(interfaceType, requiredProperties),
                null);

        // Parameter names
        for (final Property property : requiredProperties) {
            mv.visitParameter(property.getName(), 0);
        }

        // The implementation
        mv.visitCode();

        // super()
        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(INVOKESPECIAL, this.descriptors.getInternalName(parentType), "<init>", "()V", false);

        // 0 is 'this', parameters start at 1
        for (int i = 0, paramIndex = 1; i < requiredProperties.size(); i++, paramIndex++) {
            final Property property = requiredProperties.get(i);

            final Type type = Type.getType(this.descriptors.getTypeDescriptor(property.getType()));
            final int loadOpcode = type.getOpcode(Opcodes.ILOAD);

            final boolean isPrimitive = property.getType().getKind().isPrimitive();

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

            final String desc = this.descriptors.getTypeDescriptor(property.getLeastSpecificType());

            if (hasUseField) {
                mv.visitFieldInsn(PUTFIELD, this.descriptors.getInternalName(parentType), property.getName(), desc);
            } else {
                mv.visitFieldInsn(PUTFIELD, internalName, property.getName(), desc);
            }
            // }

            if (type.equals(Type.LONG_TYPE) || type.equals(Type.DOUBLE_TYPE)) {
                paramIndex++; // Skip empty slot
            }
        }

        // super.init();
        if (this.hasDeclaredMethod(parentType, "init")) {
            mv.visitVarInsn(ALOAD, 0);
            mv.visitMethodInsn(INVOKESPECIAL, this.descriptors.getInternalName(parentType), "init", "()V", false);
        }

        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private void generateAccessor(
            final ClassWriter cw,
            final TypeElement eventClass,
            final String internalName,
            final Property property) {
        final ExecutableElement accessor = property.getAccessor();

        final MethodVisitor mv = cw.visitMethod(
            ACC_PUBLIC,
            accessor.getSimpleName().toString(),
            this.descriptors.getDescriptor(accessor),
            this.signatures.ofMethod(eventClass, accessor),
            null
        );
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitFieldInsn(GETFIELD, internalName, property.getName(), this.descriptors.getTypeDescriptor(property.getLeastSpecificType()));

        if (!property.isLeastSpecificType(this.types)) {
            mv.visitTypeInsn(CHECKCAST, this.descriptors.getInternalName(property.getType()));
        }
        mv.visitInsn(Type.getType(this.descriptors.getTypeDescriptor(property.getType())).getOpcode(IRETURN));
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }


    private void generateAccessorsAndMutator(
        final EventImplClassWriter cw,
        final TypeElement type,
        final TypeMirror parentType,
        final String internalName,
        final Property property
    ) {
        if (ClassGenerator.generateMethods(property)) {
            this.generateAccessor(cw, type, internalName, property);

            final Optional<ExecutableElement> mutatorOptional = property.getMutator();
            if (mutatorOptional.isPresent()) {
                cw.generateMutator(type, internalName, property.getName(), property);
            }
        }
    }

    private MethodVisitor initializeToString(final ClassWriter cw, final TypeElement type) {
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
            final TypeMirror parentType,
            final Property property,
            final MethodVisitor toStringMv) {

        if (property.isLeastSpecificType(this.types) && (ClassGenerator.isRequired(property) || ClassGenerator.generateMethods(property))) {
            boolean overrideToString = false;
            final AnnotationMirror useField = ClassGenerator.getUseField(parentType, property.getName());
            if (useField != null) {
                overrideToString = AnnotationUtils.getValue(useField, "overrideToString");
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
                toStringMv.visitFieldInsn(GETFIELD, internalName, property.getName(), this.descriptors.getTypeDescriptor(property.getLeastSpecificType()));
            } else {
                toStringMv.visitMethodInsn(INVOKESPECIAL, internalName, property.getAccessor().getSimpleName().toString(),
                    this.descriptors.getDescriptor(property.getAccessor()), false);
            }

            final String desc = property.getType().getKind().isPrimitive() ? this.descriptors.getTypeDescriptor(property.getType()) : "Ljava/lang/Object;";

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

    /**
     * Create the event class.
     *
     * @param type The type
     * @param name The canonical of the generated class
     * @param parentType The parent type
     * @return The class' contents, or {@code null} if an error was reported while generating the class
     */
    public byte@Nullable [] createClass(
        final TypeElement type,
        final String name,
        final DeclaredType parentType,
        final List<Property> properties,
        final PropertySorter sorter,
        final Set<? extends EventFactoryPlugin> plugins
    ) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(parentType, "parentType");

        final String internalName = this.descriptors.getInternalName(name);

        final EventImplClassWriter cw = this.classWriterFactory.create(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cw.visit(
            ClassGenerator.bytecodeVersion(this.targetVersion, this.previewEnabled),
            ACC_SUPER | ACC_PUBLIC,
            internalName,
            this.signatures.ofImplClass(parentType, Collections.singletonList(type.asType())),
            this.descriptors.getInternalName(parentType),
            new String[] {this.descriptors.getInternalName(type.asType())}
        );


        // Create the constructor
        this.generateConstructor(cw, type, internalName, parentType, sorter.sortProperties(properties));

        final MethodVisitor toStringMv = this.initializeToString(cw, type);

        // Create the fields
        // this.contributeFields(cw, parentType, properties, plugins);

        // The return value of toString takes the form of
        // "ClassName{param1=value1, param2=value2, ...}"

        // Create the accessors and mutators, and fill out the toString method
        if (!this.generateWithPlugins(cw, type, parentType, internalName, properties, toStringMv, plugins)) {
            return null;
        }

        // Now build the toString
        this.finalizeToString(toStringMv);

        cw.visitEnd();

        return cw.toByteArray();
    }

    private boolean generateWithPlugins(
        final EventImplClassWriter cw,
        final TypeElement eventClass,
        final DeclaredType parentType,
        final String internalName,
        final List<? extends Property> properties,
        final MethodVisitor toStringMv,
        final Set<? extends EventFactoryPlugin> plugins
    ) {
        boolean success = true;

        for (final Property property : properties) {
            boolean processed = false;

            for (final EventFactoryPlugin plugin : plugins) {
                final EventFactoryPlugin.Result result = plugin.contributeProperty(eventClass, internalName, cw, property);
                processed = result != EventFactoryPlugin.Result.IGNORE;
                success &= result != EventFactoryPlugin.Result.FAILURE;
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
        return success;
    }

    public String qualifiedName(final TypeElement event) {
        return this.classNameProvider.getClassName(event, "Impl");
    }

    private static int bytecodeVersion(final SourceVersion sourceVersion, final boolean previewEnabled) {
        if (sourceVersion == SourceVersion.RELEASE_1) {
            return Opcodes.V1_1; // last version where minor version is used
        } else {
            return (Opcodes.V1_2 + sourceVersion.ordinal() - SourceVersion.RELEASE_2.ordinal()) | (previewEnabled ? Opcodes.V_PREVIEW : 0);
        }
    }
}
