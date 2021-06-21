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

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.lang.model.element.ElementKind;
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
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.UnionType;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.AbstractTypeVisitor8;
import javax.lang.model.util.Elements;
import org.objectweb.asm.signature.SignatureVisitor;
import org.objectweb.asm.signature.SignatureWriter;

/**
 * A type visitor to convert a {@link javax.lang.model.type.TypeMirror} into a bytecode signature.
 */
@Singleton
public class TypeToSignatureWriter extends AbstractTypeVisitor8<SignatureWriter, SignatureWriter> {
  private final Elements elements;

  @Inject
  TypeToSignatureWriter(final Elements elements) {
    this.elements = elements;
  }

  @Override
  public SignatureWriter visitIntersection(final IntersectionType t, final SignatureWriter writer) {
    for (final TypeMirror type : t.getBounds()) {
      if (type.getKind() != TypeKind.DECLARED) {
        // todo warn
        continue;
      }

      final ElementKind elementKind = ((DeclaredType) type).asElement().getKind();
      if (elementKind.isClass()) {
        writer.visitClassBound();
      } else if (elementKind.isInterface()) {
        writer.visitInterfaceBound();
      }
      type.accept(this, writer);
    }
    return writer;
  }

  @Override
  public SignatureWriter visitPrimitive(final PrimitiveType t, final SignatureWriter writer) {
    writer.visitBaseType(TypeToDescriptorWriter.descriptor(t));
    return writer;
  }

  @Override
  public SignatureWriter visitArray(final ArrayType t, final SignatureWriter writer) {
    writer.visitArrayType();
    t.getComponentType().accept(this, writer);
    return writer;
  }

  @Override
  public SignatureWriter visitDeclared(final DeclaredType t, final SignatureWriter writer) {
    if (t.getEnclosingType().getKind() != TypeKind.NONE) {
      t.getEnclosingType().accept(this, writer);
      writer.visitInnerClassType(t.asElement().getSimpleName().toString());
    } else {
      writer.visitClassType(this.elements.getBinaryName((TypeElement) t.asElement()).toString()); // qualified name
    }

    // todo: aaa

    for (final TypeMirror arg : t.getTypeArguments()) {
      arg.accept(this, writer);
    }

    writer.visitEnd();
    return writer;
  }

  @Override
  public SignatureWriter visitTypeVariable(final TypeVariable t, final SignatureWriter writer) {
    writer.visitTypeVariable(t.asElement().getSimpleName().toString()); // todo: i don't think we need anything more?
    return writer;
  }

  @Override
  public SignatureWriter visitWildcard(final WildcardType t, final SignatureWriter writer) {
    if (t.getExtendsBound() != null) {
      writer.visitTypeArgument(SignatureVisitor.EXTENDS);
      t.getExtendsBound().accept(this, writer);
    } else if (t.getSuperBound() != null) {
      writer.visitTypeArgument(SignatureVisitor.SUPER);
      t.getSuperBound().accept(this, writer);
    } else {
      writer.visitTypeArgument();
    }
    return writer;
  }

  @Override
  public SignatureWriter visitExecutable(final ExecutableType t, final SignatureWriter writer) {
    for (final TypeVariable variable : t.getTypeVariables()) {
      variable.accept(this, writer);
    }

    // Parameters
    writer.visitParameterType();

    // Return type
    writer.visitReturnType();
    t.getReturnType().accept(this, writer);

    // Exception type
    for (final TypeMirror exceptionType : t.getThrownTypes()) {
      writer.visitExceptionType();
      exceptionType.accept(this, writer);
    }
    return writer;
  }

  @Override
  public SignatureWriter visitNoType(final NoType t, final SignatureWriter writer) {
    if (t.getKind() == TypeKind.VOID) {
      writer.visitBaseType('V');
    }
    return writer;
  }

  @Override
  public SignatureWriter visitUnion(final UnionType t, final SignatureWriter writer) {
    // todo: throw exception?
    return writer;
  }


  @Override
  public SignatureWriter visitNull(final NullType t, final SignatureWriter writer) {
    // todo: throw exception?
    return writer;
  }

  @Override
  public SignatureWriter visitError(final ErrorType t, final SignatureWriter writer) {
    // todo: throw exception?
    return writer;
  }

}
