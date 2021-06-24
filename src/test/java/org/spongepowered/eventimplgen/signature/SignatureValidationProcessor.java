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

import org.assertj.core.api.SoftAssertions;
import org.objectweb.asm.signature.SignatureWriter;
import org.objectweb.asm.util.CheckSignatureAdapter;

import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

@SupportedAnnotationTypes("org.spongepowered.eventimplgen.signature.AssertSignatureEquals")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class SignatureValidationProcessor extends AbstractProcessor {

    private final SoftAssertions soft;

    public SignatureValidationProcessor(final SoftAssertions soft) {
        this.soft = soft;
    }

    @Override
    public boolean process(final Set<? extends TypeElement> annotations, final RoundEnvironment roundEnv) {
        final TypeToSignatureWriter writer = new TypeToSignatureWriter(this.processingEnv.getElementUtils());
        for (final Element element : roundEnv.getElementsAnnotatedWith(AssertSignatureEquals.class)) {
            final String expectedSignature = element.getAnnotation(AssertSignatureEquals.class).value();
            final TypeMirror type = element.asType();
            final String producedSignature;
            if (this.processingEnv.getTypeUtils().isSameType(this.processingEnv.getTypeUtils().erasure(type), type)) {
                producedSignature = "";
            } else if (element.getKind().isInterface() || element.getKind().isClass()) {
                final SignatureWriter sigWriter = new SignatureWriter();
                writer.visitDeclaredAsClassSignature((DeclaredType) type, new CheckSignatureAdapter(CheckSignatureAdapter.CLASS_SIGNATURE, sigWriter));
                producedSignature = sigWriter.toString();
            } else {
                final SignatureWriter sigWriter = new SignatureWriter();
                element.asType().accept(
                    writer,
                    new CheckSignatureAdapter(element.getKind().isField() ? CheckSignatureAdapter.TYPE_SIGNATURE : CheckSignatureAdapter.METHOD_SIGNATURE, sigWriter)
                );
                producedSignature = sigWriter.toString();
            }

            this.soft.assertThat(producedSignature)
                .describedAs("Signature of %s %s", element.getKind(), element)
                .isEqualTo(expectedSignature);
        }

        return false;
    }
}
