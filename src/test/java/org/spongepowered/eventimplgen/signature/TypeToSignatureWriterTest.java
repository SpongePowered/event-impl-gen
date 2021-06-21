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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.SoftAssertions;
import org.assertj.core.api.junit.jupiter.InjectSoftAssertions;
import org.assertj.core.api.junit.jupiter.SoftAssertionsExtension;
import org.joor.CompileOptions;
import org.joor.Reflect;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(SoftAssertionsExtension.class)
public class TypeToSignatureWriterTest {

  private static final AtomicInteger counter = new AtomicInteger();

  @InjectSoftAssertions
  private SoftAssertions soft;

  @Test
  void testClassSignature() throws IOException {
    this.compileSource(this.readResource("TestClass.java"));
  }

  @Test
  void testMethodSignature() throws IOException {
    this.compileSource(this.readResource("TestMethods.java"));
  }

  @Test
  void testFieldSignature() throws IOException {
    this.compileSource(this.readResource("TestFields.java"));
  }

  private String readResource(final String name) throws IOException {
    final InputStream stream = this.getClass().getResourceAsStream(name);
    if (stream == null) {
      throw new IllegalArgumentException(name);
    }
    final StringBuilder builder = new StringBuilder();
    try (final BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
      final char[] buf = new char[4096];
      int read;
      while ((read = reader.read(buf)) != -1) {
        builder.append(buf, 0, read);
      }
    }
    return builder.toString();
  }

  private void compileSource(final String javaSource) {
    Assertions.assertThatNoException()
      .isThrownBy(() -> Reflect.compile("test" + TypeToSignatureWriterTest.counter.incrementAndGet(),
      javaSource,
      new CompileOptions().processors(new SignatureValidationProcessor(this.soft))));
  }
}
