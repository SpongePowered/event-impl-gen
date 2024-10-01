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
import test.event.entity.EntityInteractEvent;

import java.util.List;

public class CompositeEventTest {

    @Test
    void testCompositedGenericEvent() {
        EntityInteractEvent.Secondary secondary = TestEventFactory.createEntityInteractEventSecondary(true, false);
        Assertions.assertNotNull(secondary);
        final var child1 = TestEventFactory.createAnotherEventPost(false);
        final var child2 = TestEventFactory.createAnotherEventPost(false);
        final var post = TestEventFactory.createEntityInteractEventSecondaryPost(secondary, List.of(child1, child2), false, false);
        Assertions.assertNotNull(post);
        Assertions.assertEquals(post.baseEvent(), secondary);
        final var composite = TestEventFactory.createCompositeEvent(secondary, List.of(child1, child2), false);
        // Assert the base event is the same object. We can't assert the direct declared types, but that's
        // ok, we're generating source code first, not bytecode.
        Assertions.assertEquals(composite.baseEvent(), secondary);
        Assertions.assertEquals(composite.baseEvent(), post.baseEvent());
        // Now we delegate setCancelled to AbstractCompositeEvent that has soft-implemented setCancelled
        // to set itself, the base event, and all children to the same cancelled state.
        post.setCancelled(true);
        Assertions.assertTrue(post.cancelled());
        Assertions.assertTrue(secondary.cancelled());
        Assertions.assertTrue(child1.cancelled());
        Assertions.assertTrue(child2.cancelled());
        post.setCancelled(false);
        Assertions.assertFalse(post.cancelled());
        Assertions.assertFalse(secondary.cancelled());
        Assertions.assertFalse(child1.cancelled());
        Assertions.assertFalse(child2.cancelled());
        composite.setCancelled(true);
        Assertions.assertTrue(composite.cancelled());
        Assertions.assertTrue(secondary.cancelled());
        Assertions.assertTrue(child1.cancelled());
        Assertions.assertTrue(child2.cancelled());
    }

    @Test
    void testCompositeIgnoresBaseEventToString() {
        EntityInteractEvent.Secondary secondary = TestEventFactory.createEntityInteractEventSecondary(true, false);
        final var child1 = TestEventFactory.createAnotherEventPost(false);
        final var composite = TestEventFactory.createCompositeEvent(secondary, List.of(child1), false);
        // Note the lack of baseEvent being in here, but the children are included.
        Assertions.assertEquals("CompositeEvent{cancelled=false, children=[Post{cancelled=false}]}",composite.toString());
    }

}
