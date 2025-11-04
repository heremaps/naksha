package naksha.base;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
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

    @Test
    void testJsonArrayConstructorAndBasicOperations() {
        JsonArray array = new JsonArray();
        assertTrue(array.isEmpty());
        assertEquals(0, array.size());

        array.add("test");
        assertFalse(array.isEmpty());
        assertEquals(1, array.size());
        assertEquals("test", array.get(0));
    }

    @Test
    void testJsonArrayAddAndGet() {
        JsonArray array = new JsonArray();
        array.add("first");
        array.add(2);
        array.add(true);
        array.add(null);

        assertEquals("first", array.get(0));
        assertEquals(2, array.get(1));
        assertTrue((Boolean) array.get(2));
        assertNull(array.get(3));
        assertEquals(4, array.size());
    }

    @Test
    void testJsonArrayContains() {
        JsonArray array = new JsonArray();
        array.add("test");
        array.add(42);

        assertTrue(array.contains("test"));
        assertTrue(array.contains(42));
        assertFalse(array.contains("nonexistent"));
    }

    @Test
    void testJsonArrayIndexOf() {
        JsonArray array = new JsonArray();
        array.add("first");
        array.add("second");
        array.add("first");

        assertEquals(0, array.indexOf("first"));
        assertEquals(1, array.indexOf("second"));
        assertEquals(-1, array.indexOf("nonexistent"));
        assertEquals(2, array.lastIndexOf("first"));
    }

    @Test
    void testJsonArrayAddAtIndex() {
        JsonArray array = new JsonArray();
        array.add("first");
        array.add("third");
        array.add(1, "second");

        assertEquals("first", array.get(0));
        assertEquals("second", array.get(1));
        assertEquals("third", array.get(2));

        array.add(4, "last");
        assertEquals(5 ,array.size());
        assertSame(Json.UNDEFINED, array.get(3));
        assertEquals("last", array.get(4));
    }

    @Test
    void testJsonArrayRemove() {
        JsonArray array = new JsonArray();
        array.add("first");
        array.add("second");
        array.add("third");

        assertEquals("second", array.remove(1));
        assertEquals(2, array.size());
        assertEquals("third", array.get(1));

        assertTrue(array.remove("first"));
        assertFalse(array.remove("nonexistent"));
        assertEquals(1, array.size());
    }

    @Test
    void testJsonArraySet() {
        JsonArray array = new JsonArray();
        array.add("original");
        array.add("test");

        assertEquals(2, array.size());
        assertEquals("original", array.get(0));
        assertEquals("test", array.get(1));

        assertEquals("original", array.set(0, "replaced"));
        assertEquals("replaced", array.get(0));

        array.add(2, "last");
        assertEquals(3, array.size());
        assertEquals("last", array.get(2));
    }

    @Test
    void testJsonArrayToArray() {
        JsonArray array = new JsonArray();
        array.add("first");
        array.add(2);

        Object[] result = array.toArray();
        assertEquals(2, result.length);
        assertEquals("first", result[0]);
        assertEquals(2, result[1]);

        // Test toArray with provided array
        Object[] objArray = new Object[3];
        Object[] returned = array.toArray(objArray);
        assertEquals(objArray, returned);
        assertNull(objArray[2]);
    }

    @Test
    void testJsonArrayAddAll() {
        JsonArray array = new JsonArray();
        List<Object> toAdd = Arrays.asList("first", 2, true);

        assertTrue(array.addAll(toAdd));
        assertEquals(3, array.size());
        assertEquals("first", array.get(0));
        assertEquals(2, array.get(1));
        assertEquals(true, array.get(2));

        // Test addAll at index
        List<Object> toAddAtIndex = Arrays.asList("inserted", "items");
        assertTrue(array.addAll(1, toAddAtIndex));
        assertEquals(5, array.size());
        assertEquals("first", array.get(0));
        assertEquals("inserted", array.get(1));
        assertEquals("items", array.get(2));
    }

    @Test
    void testJsonArraySubList() {
        JsonArray array = new JsonArray();
        array.add("one");
        array.add("two");
        array.add("three");
        array.add("four");

        List<Object> subList = array.subList(1, 3);
        assertEquals(2, subList.size());
        assertEquals("two", subList.get(0));
        assertEquals("three", subList.get(1));

        subList.set(0, "modified");
        assertEquals("modified", array.get(1));

        assertThrows(IndexOutOfBoundsException.class, () -> array.subList(-1, 3));
        assertThrows(IndexOutOfBoundsException.class, () -> array.subList(2, 5));
    }

    @Test
    void testJsonArrayListIterator() {
        JsonArray array = new JsonArray();
        array.add("first");
        array.add("second");
        array.add("third");

        ListIterator<Object> iterator = array.listIterator();
        assertTrue(iterator.hasNext());
        assertFalse(iterator.hasPrevious());
        assertEquals(0, iterator.nextIndex());
        assertEquals(-1, iterator.previousIndex());

        assertEquals("first", iterator.next());
        assertEquals("second", iterator.next());
        assertTrue(iterator.hasPrevious());
        assertEquals("second", iterator.previous());

        iterator.set("modified");
        assertEquals("modified", array.get(1));

        iterator.add("inserted");
        assertEquals(4, array.size());
        assertEquals("inserted", array.get(1));

        // Test remove
        iterator = array.listIterator();
        iterator.next();
        iterator.remove();
        assertEquals(3, array.size());
        assertEquals("inserted", array.get(0));
        assertThrows(IllegalStateException.class, iterator::remove);
    }

    @Test
    void testJsonArrayRetainAndRemoveAll() {
        JsonArray array = new JsonArray();
        array.add("keep");
        array.add("remove");
        array.add("also_keep");
        array.add("also_remove");

        List<Object> toKeep = Arrays.asList("keep", "also_keep");
        assertTrue(array.retainAll(toKeep));
        assertEquals(2, array.size());
        assertTrue(array.containsAll(toKeep));

        array.add("to_remove");
        List<Object> toRemove = Arrays.asList("to_remove", "nonexistent");
        assertTrue(array.removeAll(toRemove));
        assertEquals(2, array.size());
        assertFalse(array.contains("to_remove"));
    }

    @Test
    void testJsonArrayClear() {
        JsonArray array = new JsonArray();
        array.add("test1");
        array.add("test2");
        assertFalse(array.isEmpty());

        array.clear();
        assertTrue(array.isEmpty());
        assertEquals(0, array.size());
    }
}
