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
package org.spongepowered.eventimplgen.signature;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ErrorType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.IntersectionType;
import javax.lang.model.type.NoType;
import javax.lang.model.type.NullType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.UnionType;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TypeToDescriptorWriterTest {

    TypeToDescriptorWriter writer;
    Types types;
    Elements elements;

    @BeforeEach
    void setUp() {
        // Create the Dagger component and inject dependencies
        types = Mockito.mock(Types.class);
        elements = Mockito.mock(Elements.class);
        writer = TypeToDescriptorWriter_Factory.newInstance(types, elements);
    }


    @ParameterizedTest
    @MethodSource("provideTypesForDescriptors")
    void testPrimitiveTypeDescriptor(TypeKind kind, String expected) {
        // Test all primitive types
        PrimitiveType booleanType = Mockito.mock(PrimitiveType.class);
        when(booleanType.getKind()).thenReturn(kind);
        var builder = new StringBuilder();
        writer.visitPrimitive(booleanType, builder);
        assertEquals(expected, builder.toString());
    }

    private static Stream<Arguments> provideTypesForDescriptors() {
        return Stream.of(
            Arguments.of(TypeKind.BOOLEAN, "Z"),
            Arguments.of(TypeKind.CHAR, "C"),
            Arguments.of(TypeKind.INT, "I"),
            Arguments.of(TypeKind.LONG, "J"),
            Arguments.of(TypeKind.FLOAT, "F"),
            Arguments.of(TypeKind.DOUBLE, "D"),
            Arguments.of(TypeKind.SHORT, "S"),
            Arguments.of(TypeKind.BYTE, "B")
        );
    }

    @Test
    void testInvalidPrimitiveType() {
        // Test invalid primitive type
        PrimitiveType invalidType = Mockito.mock(PrimitiveType.class);
        when(invalidType.getKind()).thenReturn(TypeKind.VOID);

        StringBuilder builder = new StringBuilder();
        assertThrows(IllegalArgumentException.class, () -> writer.visitPrimitive(invalidType, builder));
    }

    @Test
    void testArrayTypeDescriptor() {
        // Test array types
        ArrayType arrayType = Mockito.mock(ArrayType.class);
        PrimitiveType intType = Mockito.mock(PrimitiveType.class);
        when(arrayType.getComponentType()).thenReturn(intType);
        when(intType.accept(Mockito.any(), Mockito.any())).thenAnswer(invocation -> {
            StringBuilder builder = invocation.getArgument(1);
            return builder.append("I");
        });

        StringBuilder builder = new StringBuilder();
        writer.visitArray(arrayType, builder);
        assertEquals("[I", builder.toString());
    }

    @Test
    void testDeclaredTypeDescriptor() {
        // Test declared types
        DeclaredType declaredType = Mockito.mock(DeclaredType.class);
        TypeElement typeElement = Mockito.mock(TypeElement.class);
        final var name = Mockito.mock(Name.class);
        when(name.toString()).thenReturn("java.lang.String");

        when(declaredType.asElement()).thenReturn(typeElement);
        when(elements.getBinaryName(typeElement)).thenReturn(name);

        StringBuilder builder = new StringBuilder();
        writer.visitDeclared(declaredType, builder);
        assertEquals("Ljava/lang/String;", builder.toString());
    }

    @Test
    void testTypeVariableDescriptor() {
        // Test type variables
        TypeVariable typeVariable = Mockito.mock(TypeVariable.class);
        DeclaredType declaredType = Mockito.mock(DeclaredType.class);
        when(typeVariable.getUpperBound()).thenReturn(declaredType);
        when(declaredType.accept(Mockito.any(), Mockito.any())).thenAnswer(invocation -> {
            StringBuilder builder = invocation.getArgument(1);
            return builder.append("Ljava/lang/Object;");
        });

        StringBuilder builder = new StringBuilder();
        writer.visitTypeVariable(typeVariable, builder);
        assertEquals("Ljava/lang/Object;", builder.toString());
    }

    @Test
    void testWildcardTypeDescriptor() {
        // Test wildcard types
        WildcardType wildcardType = Mockito.mock(WildcardType.class);
        DeclaredType declaredType = Mockito.mock(DeclaredType.class);
        when(wildcardType.getExtendsBound()).thenReturn(declaredType);
        when(declaredType.accept(Mockito.any(), Mockito.any())).thenAnswer(invocation -> {
            StringBuilder builder = invocation.getArgument(1);
            return builder.append("Ljava/lang/Object;");
        });

        StringBuilder builder = new StringBuilder();
        writer.visitWildcard(wildcardType, builder);
        assertEquals("Ljava/lang/Object;", builder.toString());
    }

    @Test
    void testExecutableTypeDescriptor() {
        // Test executable types (method signatures)
        ExecutableType executableType = Mockito.mock(ExecutableType.class);
        PrimitiveType returnType = Mockito.mock(PrimitiveType.class);
        when(executableType.getReturnType()).thenReturn(returnType);
        when(returnType.accept(Mockito.any(), Mockito.any())).thenAnswer(invocation -> {
            StringBuilder builder = invocation.getArgument(1);
            return builder.append('V');
        });

        PrimitiveType paramType = Mockito.mock(PrimitiveType.class);
        when(paramType.accept(Mockito.any(), Mockito.any())).thenAnswer(invocation -> {
            StringBuilder builder = invocation.getArgument(1);
            return builder.append('I');
        });

        // We have to fool the compiler here because we know the implementation of TypeMirror
        // includes an implementation of PrimitiveType, but they're disconnected interfaces.
        Mockito.<Object>when(executableType.getParameterTypes()).thenReturn(List.of(paramType));

        StringBuilder builder = new StringBuilder();
        writer.visitExecutable(executableType, builder);
        assertEquals("(I)V", builder.toString());
    }

    @Test
    void testNoTypeDescriptor() {
        // Test void type
        NoType noType = Mockito.mock(NoType.class);
        when(noType.getKind()).thenReturn(TypeKind.VOID);

        StringBuilder builder = new StringBuilder();
        writer.visitNoType(noType, builder);
        assertEquals("V", builder.toString());
    }

    @Test
    void testErrorTypeDescriptor() {
        // Test error type
        ErrorType errorType = Mockito.mock(ErrorType.class);

        StringBuilder builder = new StringBuilder();
        writer.visitError(errorType, builder);
        assertEquals("L!ERROR!;", builder.toString());
    }

    @Test
    void testNullTypeDescriptor() {
        // Test null type (should not append anything)
        NullType nullType = Mockito.mock(NullType.class);

        StringBuilder builder = new StringBuilder();
        writer.visitNull(nullType, builder);
        assertEquals("", builder.toString());
    }

    @Test
    void testUnionTypeDescriptor() {
        // Test union type (should return null, as not representable)
        UnionType unionType = Mockito.mock(UnionType.class);

        assertNull(writer.visitUnion(unionType, new StringBuilder()));
    }

    // write a unit test for intersection types
    @Test
    void testIntersectionTypeDescriptor() {
        // Intersection types are effectively compiled down to their first bound
        // so we can verify the erased type is written to the descriptor.
        IntersectionType intersectionType = Mockito.mock(IntersectionType.class);
        DeclaredType declaredType = Mockito.mock(DeclaredType.class);
        when(types.erasure(intersectionType)).thenReturn(declaredType);
        when(declaredType.accept(Mockito.any(), Mockito.any())).thenAnswer(invocation -> {
            StringBuilder builder = invocation.getArgument(1);
            return builder.append("Ljava/lang/Number;");
        });


        final var builder = new StringBuilder();
        writer.visitIntersection(intersectionType, builder);
        assertEquals("Ljava/lang/Number;", builder.toString());
    }
}
