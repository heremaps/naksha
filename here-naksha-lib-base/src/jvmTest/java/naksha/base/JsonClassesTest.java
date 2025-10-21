package naksha.base;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

public class JsonClassesTest {

    @Test
    void testEmptyMap() {
        JsonMap map = new JsonMap();
        assertTrue(map.isEmpty());
        assertEquals(0, map.size());
    }

    @Test
    void testPutAndGet() {
        JsonMap map = new JsonMap();
        assertNull(map.put("key1", "value1"));
        assertEquals("value1", map.get("key1"));
        assertEquals(1, map.size());

        assertEquals("value1", map.put("key1", "value2"));
        assertEquals("value2", map.get("key1"));
    }

    @Test
    void testContainsKeyAndValue() {
        JsonMap map = new JsonMap();
        map.put("key1", "value1");
        map.put("key2", "value2");

        assertTrue(map.containsKey("key1"));
        assertTrue(map.containsKey("key2"));
        assertFalse(map.containsKey("key3"));

        assertTrue(map.containsValue("value1"));
        assertTrue(map.containsValue("value2"));
        assertFalse(map.containsValue("value3"));
    }

    @Test
    void testRemove() {
        JsonMap map = new JsonMap();
        map.put("key1", "value1");
        map.put("key2", "value2");

        assertEquals("value1", map.remove("key1"));
        assertNull(map.get("key1"));
        assertEquals(1, map.size());

        assertNull(map.remove("nonexistent"));
    }

    @Test
    void testPutAll() {
        JsonMap map = new JsonMap();
        Map<String, Object> source = new HashMap<>();
        source.put("key1", "value1");
        source.put("key2", "value2");

        map.putAll(source);
        assertEquals(2, map.size());
        assertEquals("value1", map.get("key1"));
        assertEquals("value2", map.get("key2"));
    }

    @Test
    void testClear() {
        JsonMap map = new JsonMap();
        map.put("key1", "value1");
        map.put("key2", "value2");

        assertFalse(map.isEmpty());
        map.clear();
        assertTrue(map.isEmpty());
        assertEquals(0, map.size());
    }

    @Test
    void testKeySet() {
        JsonMap map = new JsonMap();
        map.put("key1", "value1");
        map.put("key2", "value2");

        var keys = map.keySet();
        assertEquals(2, keys.size());
        assertTrue(keys.contains("key1"));
        assertTrue(keys.contains("key2"));

        // Test iterator
        var iterator = keys.iterator();
        assertTrue(iterator.hasNext());
        assertNotNull(iterator.next());
        assertTrue(iterator.hasNext());
        assertNotNull(iterator.next());
        assertFalse(iterator.hasNext());
    }

    @Test
    void testValues() {
        JsonMap map = new JsonMap();
        map.put("key1", "value1");
        map.put("key2", "value2");

        var values = map.values();
        assertEquals(2, values.size());
        assertTrue(values.contains("value1"));
        assertTrue(values.contains("value2"));

        // Test iterator
        var iterator = values.iterator();
        assertTrue(iterator.hasNext());
        assertNotNull(iterator.next());
        assertTrue(iterator.hasNext());
        assertNotNull(iterator.next());
        assertFalse(iterator.hasNext());
    }

    @Test
    void testEntrySet() {
        JsonMap map = new JsonMap();
        map.put("key1", "value1");
        map.put("key2", "value2");

        var entries = map.entrySet();
        assertEquals(2, entries.size());

        // Test iterator and entry operations
        var iterator = entries.iterator();
        assertTrue(iterator.hasNext());
        var entry = iterator.next();
        assertNotNull(entry);
        assertTrue(entry.getKey().equals("key1") || entry.getKey().equals("key2"));
        String oldValue = (String) entry.getValue();
        assertEquals(oldValue, entry.setValue("newValue"));
        assertEquals("newValue", map.get(entry.getKey()));
    }

    @Test
    void testIteratorRemove() {
        JsonMap map = new JsonMap();
        map.put("key1", "value1");
        map.put("key2", "value2");

        // Test keySet iterator remove
        var keyIterator = map.keySet().iterator();
        keyIterator.next();
        keyIterator.remove();
        assertEquals(1, map.size());

        // Test values iterator remove
        map.put("key3", "value3");
        var valueIterator = map.values().iterator();
        valueIterator.next();
        valueIterator.remove();
        assertEquals(1, map.size());

        // Test entrySet iterator remove
        map.put("key4", "value4");
        var entryIterator = map.entrySet().iterator();
        entryIterator.next();
        entryIterator.remove();
        assertEquals(1, map.size());
    }

    @Test
    void testIteratorExceptions() {
        JsonMap map = new JsonMap();

        // Test empty iterators
        var keyIterator = map.keySet().iterator();
        assertFalse(keyIterator.hasNext());
        assertThrows(NoSuchElementException.class, keyIterator::next);

        var valueIterator = map.values().iterator();
        assertFalse(valueIterator.hasNext());
        assertThrows(NoSuchElementException.class, valueIterator::next);

        var entryIterator = map.entrySet().iterator();
        assertFalse(entryIterator.hasNext());
        assertThrows(NoSuchElementException.class, entryIterator::next);

        // Test illegal remove
        map.put("key1", "value1");
        keyIterator = map.keySet().iterator();
        assertThrows(IllegalStateException.class, keyIterator::remove);
    }
}
