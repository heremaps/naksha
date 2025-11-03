package naksha.base;

import org.junit.jupiter.api.Test;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;

public class JsonMapTest {
  @Test
  public void test_map_entry_set() {
    final JsonMap map = new JsonMap();
    map.put("zoo", 5);
    map.put("foo", 1);
    map.put("bar", 2);

    assertEquals(3, map.size());
    assertEquals(5, map.get("zoo"));
    assertEquals(1, map.get("foo"));
    assertEquals(2, map.get("bar"));

    map.put("foo", 3);
    assertEquals(3, map.size());
    assertEquals(3, map.get("foo"));

    final var entrySet = map.entrySet();
    final var entryIt = entrySet.iterator();

    assertTrue(entryIt.hasNext());
    var entry = entryIt.next();
    assertEquals("zoo", entry.getKey());
    assertEquals(5, entry.getValue());

    assertTrue(entryIt.hasNext());
    entry = entryIt.next();
    assertEquals("foo", entry.getKey());
    assertEquals(3, entry.getValue());

    assertTrue(entryIt.hasNext());
    entry = entryIt.next();
    assertEquals("bar", entry.getKey());
    assertEquals(2, entry.getValue());

    assertFalse(entryIt.hasNext());
    assertThrowsExactly(NoSuchElementException.class, entryIt::next);
  }
}
