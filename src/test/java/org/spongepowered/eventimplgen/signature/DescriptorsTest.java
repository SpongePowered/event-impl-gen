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
import org.mockito.Mockito;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ErrorType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

public class DescriptorsTest {

    private Elements elements;
    private Types types;
    private TypeToDescriptorWriter descWriter;
    private Descriptors descriptors;

    @BeforeEach
    public void setUp() {
        elements = Mockito.mock(Elements.class);
        types = Mockito.mock(Types.class);
        descWriter = Mockito.mock(TypeToDescriptorWriter.class);
        descriptors = new Descriptors(elements, types, descWriter);
    }

    @Test
    public void testGetDescriptorWithReturnType() {
        ExecutableElement method = Mockito.mock(ExecutableElement.class);
        ExecutableType methodType = Mockito.mock(ExecutableType.class);
        when(method.asType()).thenReturn(methodType);
        when(methodType.accept(Mockito.eq(descWriter), Mockito.any(StringBuilder.class)))
            .thenAnswer(invocation -> {
                StringBuilder builder = invocation.getArgument(1);
                builder.append("()V");
                return builder;
            });

        String result = descriptors.getDescriptor(method);
        assertEquals("()V", result);
    }

    @Test
    public void testGetDescriptorWithoutReturnType() {
        ExecutableType methodType = Mockito.mock(ExecutableType.class);
        PrimitiveType intType = Mockito.mock(PrimitiveType.class);
        Mockito.<Object>when(methodType.getParameterTypes()).thenReturn(List.of(intType));
        when(intType.accept(Mockito.eq(descWriter), Mockito.any(StringBuilder.class)))
            .thenAnswer(invocation -> {
                StringBuilder builder = invocation.getArgument(1);
                builder.append("I");
                return builder;
            });

        String result = descriptors.getDescriptor(methodType, false);
        assertEquals("(I)V", result);
    }

    @Test
    public void testGetInternalNameDeclared() {
        DeclaredType declaredType = Mockito.mock(DeclaredType.class);
        TypeElement typeElement = Mockito.mock(TypeElement.class);
        Name name = Mockito.mock(Name.class);
        when(typeElement.getQualifiedName()).thenReturn(name);
        when(name.toString()).thenReturn("java.lang.String");
        when(types.erasure(declaredType)).thenReturn(declaredType);
        when(declaredType.getKind()).thenReturn(TypeKind.DECLARED);
        when(declaredType.asElement()).thenReturn(typeElement);
        when(elements.getBinaryName(typeElement)).thenReturn(name);
        when(name.toString()).thenReturn("java.lang.String");

        String result = descriptors.getInternalName(declaredType);
        assertEquals("java/lang/String", result);
    }

    @Test
    public void testGetInternalNameError() {
        ErrorType errorType = Mockito.mock(ErrorType.class);
        when(types.erasure(errorType)).thenReturn(errorType);
        when(errorType.getKind()).thenReturn(TypeKind.ERROR);

        String result = descriptors.getInternalName(errorType);
        assertEquals(Descriptors.TYPE_ERROR, result);
    }

    @Test
    public void testGetInternalNameUnknown() {
        TypeMirror unknownType = Mockito.mock(TypeMirror.class);
        when(types.erasure(unknownType)).thenReturn(unknownType);
        when(unknownType.getKind()).thenReturn(TypeKind.OTHER);

        String result = descriptors.getInternalName(unknownType);
        assertEquals(Descriptors.TYPE_UNKNOWN, result);
    }

    @Test
    public void testGetInternalNameString() {
        String result = descriptors.getInternalName("java.lang.String");
        assertEquals("java/lang/String", result);
    }
}