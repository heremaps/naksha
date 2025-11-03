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

  @Test
    public  void parserTest() {
      final String json = "{\"type\":\"Feature\",\"momType\":\"Topology\",\"id\":\"urn:here::ipc:Topology:12345\",\"geometry\":{\"type\":\"LineString\",\"coordinates\":[[45.0,45.0],[45.0,46.0]]},\"properties\":{\"startNodeId\":\"So long, and thanks for all the fish.\",\"endNodeId\":\"So long, and thanks for all the fish.\",\"leftAdmin\":[{\"range\":{\"endOffset\":1.0,\"startOffset\":0.0},\"value\":{\"id\":\"urn:here::here:admin:82928227\"}}]}}";
      Platform.PlatformCompanion.enableNewJsonParser();
      Object o = Platform.fromJSON(json);
      assertInstanceOf(JvmMap.class, o);
      var jvmMap = (JvmMap) o;
      assertTrue(jvmMap.containsKey("properties"));
  }
}
