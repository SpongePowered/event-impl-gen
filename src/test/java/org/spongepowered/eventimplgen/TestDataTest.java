package org.spongepowered.eventimplgen;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.util.CheckClassAdapter;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

import test.event.Event;
import test.event.TestEventFactory;

public class TestDataTest {

    @Test
    void testCreateEvent() throws IOException {
        // Validate the generated class
        final ClassReader reader = new ClassReader(this.getClass().getResourceAsStream("/test/event/TestEventFactory.class"));

        final StringWriter output = new StringWriter();
        try (final PrintWriter printer = new PrintWriter(output)) {
            CheckClassAdapter.verify(reader, false, printer);
        }

        final Event event = TestEventFactory.createEvent(true);
        event.setCancelled(true);

        System.out.println(event);

        Assertions.assertThat(output.toString())
            .withFailMessage(output::toString)
            .isEmpty();
    }

}
