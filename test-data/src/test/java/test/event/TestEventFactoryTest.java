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
package test.event;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import test.event.lifecycle.NestedTest;

import java.util.List;

class TestEventFactoryTest {

    @Test
    void testTestEventFactory() {
        // Most of our validation is that the test set compiles, this just executes a basic implementation.
        final NestedTest.Post conn = TestEventFactory.createNestedTestPost(false, 5);
        Assertions.assertEquals(conn.toString(), "Post{cancelled=false, count=5}");
    }

    @Test
    void testIndirectlyAnnotatedPackageGenerated() {
        Assertions.assertNotNull(TestEventFactory.createPartyEvent(true, false, 100));
    }

    @Test
    void testExplicitlyFilteredPackageByArgument() {
        // The Listener class is not generated because the build.gradle explicitly filters the package out
        Assertions.assertThrows(
            NoSuchMethodException.class,
            () -> TestEventFactory.class.getMethod("createListener")
        );
    }

    @Test
    void testExplicitlyAnnotatedPackageNotGenerated() {
        // The NonGenerated class is not generated because it is annotated with @DoNotGenerate
        Assertions.assertThrows(
            NoSuchMethodException.class,
            () -> TestEventFactory.class.getMethod("createNonGenerated")
        );
    }


    @Test
    void testImplicitlyAllowedByAnnotation() {
        // The InclusiveEvent gets generated because test.event.cause package-info.java is
        // annotated with @NoFactoryMethod(ignoreNested = true), which allows for nested packages to
        // generate, with the exception when the package is explicitly filtered by annotation
        // arguments.
        Assertions.assertNotNull(TestEventFactory.createInclusiveEvent(List.of(), false));
    }

}
