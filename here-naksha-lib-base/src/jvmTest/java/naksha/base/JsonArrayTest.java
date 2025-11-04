package naksha.base;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class JsonArrayTest {
  @Test
  public void test_array_remove() {
    final var array = new JsonArray();
    array.add(1);
    array.add(2);
    array.add(3);

    assertEquals(3, array.size());
    assertEquals(1, array.get(0));
    assertEquals(2, array.get(1));
    assertEquals(3, array.get(2));

    assertEquals(2, array.remove(1));
    assertEquals(2, array.size());
    assertEquals(1, array.get(0));
    assertEquals(3, array.get(1));

    final var it = array.iterator();
    assertTrue(it.hasNext());
    assertEquals(1, it.next());
    assertTrue(it.hasNext());
    assertEquals(3, it.next());
    assertFalse(it.hasNext());
  }
}
