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

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;
import org.spongepowered.api.util.annotation.eventgen.UseField;
import org.spongepowered.eventimplgen.eventgencore.Property;

import java.util.Optional;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

/**
 * Generation context for a class
 */
public final class ClassContext {

    private static final ClassName OPTIONAL = ClassName.get(Optional.class);

    private final Types types;
    private final TypeSpec.Builder builder;
    private final CodeBlock.Builder toStringBuilder = CodeBlock.builder();
    private boolean toStringReceivedParam;
    private final TypeMirror erasedOptional;

    @AssistedInject
    public ClassContext(final Types types, final Elements elements, @Assisted final TypeSpec.Builder builder) {
        this.types = types;
        this.builder = builder;
        this.erasedOptional = types.erasure(elements.getTypeElement(Optional.class.getName()).asType());
    }

    @AssistedFactory
    interface Factory {
        ClassContext create(final TypeSpec.Builder builder);
    }

    public ClassContext addField(final FieldSpec field) {
        this.builder.addField(field);
        return this;
    }

    public ClassContext addField(final FieldSpec.Builder field) {
        this.builder.addField(field.build());
        return this;
    }

    public ClassContext addField(final TypeMirror typeMirror, final String name, final Modifier... modifiers) {
        this.builder.addField(TypeName.get(typeMirror), name, modifiers);
        return this;
    }

    public ClassContext addField(final Property property) {
        if ((!ClassGenerator.isRequired(property) && !ClassGenerator.generateMethods(property)) || !property.isLeastSpecificType(this.types)) {
            // If the field will be unused, don't generate it at all
            return this;
        }

        return this.addField(property.getType(), property.getName(), Modifier.PRIVATE);
    }

    public ClassContext addMethod(final MethodSpec methodSpec) {
        this.builder.addMethod(methodSpec);
        return this;
    }

    public ClassContext addMethod(final MethodSpec.Builder methodBuilder) {
        this.builder.addMethod(methodBuilder.build());
        return this;
    }

    /**
     * Generates a standard mutator method.
     *
     * <p>This method assumes that a standard field has been generated for the
     * provided {@link Property}</p>
     *
     * @param type The {@link Class} of the event that's having an
     *        implementation generated
     * @param fieldName The name of the field to mutate
     * @param property The {@link Property} containing the mutator method to
     *        generate for
     */
    public ClassContext addMutator(
        final TypeElement type,
        final String fieldName,
        final Property property
    ) {
        final ExecutableElement mutator = property.getMutator().get();
        final MethodSpec.Builder method = MethodSpec.methodBuilder(mutator.getSimpleName().toString())
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(Override.class)
            .addParameter(TypeName.get(mutator.getParameters().get(0).asType()), property.getName(), Modifier.FINAL);

        final String varName;
        if (this.types.isAssignable(property.getAccessor().getReturnType(), this.erasedOptional)) {
            method.addStatement("final $T wrapped = $T.ofNullable($L)", TypeName.get(property.getType()), OPTIONAL, property.getName());
            varName = "wrapped";
        } else {
            varName = property.getName();
        }

        if (!property.getType().getKind().isPrimitive() && !this.types.isSameType(property.getMostSpecificType(), property.getAccessor().getReturnType())) {
            final TypeMirror mostSpecificReturn = property.getMostSpecificType();

            method.addCode(CodeBlock.builder()
                .beginControlFlow("if ($1L != null && !($1L instanceof $2T))", varName, this.types.erasure(mostSpecificReturn))
                .addStatement(
                    "throw new RuntimeException(\"You've attempted to call the method '$L' with an object of type\" + $L.getClass().getName()\n" +
                    "+ \", instead of $L. Though you may have been listening for a supertype of this event, it's technically\"\n"
                    + "+ \"a $L. You need to ensure that the event is the type you think it is.\")",
                    mutator.getSimpleName(),
                    varName,
                    mostSpecificReturn,
                    type.getQualifiedName()
                )
                .endControlFlow()
                .build());
        }

        method.addStatement("this.$L = $L", fieldName, varName);

        return this.addMethod(method);
    }

    // toString handling
    void initializeToString(final TypeElement type) {
        this.toStringBuilder.add("return $S\n", type.getSimpleName() + "{")
            .indent();
    }

    void contributeToString(
        final TypeMirror parentType,
        final Property property
    ) {

        if (property.isLeastSpecificType(this.types) && (ClassGenerator.isRequired(property) || ClassGenerator.generateMethods(property))) {
            boolean overrideToString = false;
            final UseField useField = ClassGenerator.getUseField(parentType, property.getName());
            if (useField != null) {
                overrideToString = useField.overrideToString();
            }

            final Object value;
            if (overrideToString) {
                value = "this." + property.getName();
            } else {
                value = "this." + property.getAccessor().getSimpleName() + "()";
            }
            this.toStringBuilder.add("+ $S + $L\n", (this.toStringReceivedParam ? ", " : "") + property.getName() + "=", value);
            this.toStringReceivedParam = true;
        }
    }

    void finalizeToString(final TypeElement type) {
        this.toStringBuilder.add("+ '}';");
        this.addMethod(MethodSpec.methodBuilder("toString")
            .addModifiers(Modifier.PUBLIC)
            .returns(String.class)
            .addAnnotation(Override.class)
            .addCode(this.toStringBuilder.build()));
    }

}
