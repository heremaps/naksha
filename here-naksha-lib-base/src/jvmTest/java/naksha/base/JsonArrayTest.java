package naksha.base;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class JsonArrayTest {
  Boolean wasNewParserEnabled;

  @BeforeEach
  void setUp() {
    wasNewParserEnabled = Platform.PlatformCompanion.useNewJson();
    Platform.PlatformCompanion.enableNewJsonParser();
  }

  @AfterEach
  void tearDown() {
    if (!wasNewParserEnabled) {
      Platform.PlatformCompanion.disableNewJsonParser();
    }
  }

  @Test
  public void test_capacity() {
    Platform.PlatformCompanion.enableNewJsonParser();
    final var list = new JvmList();
    assertEquals(0, list.getCapacity());
    list.setCapacity(0);
    assertEquals(0, list.getCapacity());
    Platform.PlatformCompanion.disableNewJsonParser();
  }

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
