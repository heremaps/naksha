package com.here.naksha.lib.core.models.naksha;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class EventHandlerConfigListTest {
  @SuppressWarnings("DataFlowIssue")
  @Test
  void testSort() {
    final var space = new Space();
    space.setId("test");
    space.addHandler("bar");
    space.addHandler("foo");
    space.addHandler("zoo");

    final var handlers = new EventHandlerConfigList();
    EventHandlerConfig handler;

    // Add zoo
    handler = new EventHandlerConfig();
    handler.setId("zoo");
    handlers.add(handler);

    // Add null
    handlers.add(null);

    // Add bar
    handler = new EventHandlerConfig();
    handler.setId("foo");
    handlers.add(handler);

    // Add foo
    handler = new EventHandlerConfig();
    handler.setId("bar");
    handlers.add(handler);

    // We expect current 4 elements
    assertEquals(4, handlers.size());
    assertEquals("zoo", handlers.get(0).getId());
    assertNull(handlers.get(1));
    assertEquals("foo", handlers.get(2).getId());
    assertEquals("bar", handlers.get(3).getId());

    // Order and remove nulls.
    handlers.orderBySpace(space, true);

    // Assert that we now have three handlers, in the order: bar, foo, zoo
    assertEquals(3, handlers.size());
    assertEquals("bar", handlers.get(0).getId());
    assertEquals("foo", handlers.get(1).getId());
    assertEquals("zoo", handlers.get(2).getId());
  }
}
