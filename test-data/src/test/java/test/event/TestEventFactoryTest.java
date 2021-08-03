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

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import test.TypeToken;
import test.event.lifecycle.ConnectionEvent;
import test.event.lifecycle.NestedTest;

import java.nio.ByteBuffer;
import java.util.Collections;

class TestEventFactoryTest {

    @Test
    void testTestEventFactory() {
        // Most of our validation is that the test set compiles, this just executes a basic implementation.
        final NestedTest.Post conn = TestEventFactory.createNestedTestPost(false, 5);
        Assertions.assertThat(conn.toString())
            .isEqualTo("Post{cancelled=false, count=5}");
    }

    @Test
    void testIndirectlyAnnotatedPackageGenerated() {
        Assertions.assertThat(TestEventFactory.createPartyEvent(true, false, 100))
            .isNotNull();
    }

}
