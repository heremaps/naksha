package naksha.base;


import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
/**
 * Used as internal implementation of {@link JvmMap}. Assumption is that each JSON object has only around 4 or fewer key-value pairs.
 * This means each object of this class can fit into CPU L1 which makes it very fast to access.
 * Not to be confused with the class of the same name in {@link com.here.naksha.lib.core.util.json}.
 */
class JsonMap implements Map<String, Object> {
    JsonMap(){
        map = EMPTY;
    }

    /// Called by the JSON parser, the ownership of the given array is give to this.
    JsonMap(@Nullable Object @NotNull [] map_content){
      this.map = map_content;
    }

    /**
     * The internal map representation, [key1, value1, key2, value2,...].
     */
    @Nullable Object @NotNull [] map;
    private static final @Nullable Object @NotNull [] EMPTY = new Object[0];

    @Override
    public int size() {
        return map.length >> 1;
    }

    @Override
    public boolean isEmpty() {
        return map.length==0;
    }

    /**
     * Tries to convert the given string into a key. A key is a string of which only one instance exists, so a singleton. If no such key singleton exists, returns {@code null}.
     *
     * <p>We always intern keys. If the given key is interned already, this check is cheap, if not, the test is slightly slower. The method actually boils down to one array access by hash-code of the given character sequence, followed by an iteration above all interned strings with the same hash-code. We assume there are no or maximal two hash collisions, therefore we expect that most compares are just numeric, except for a few hash collisions, where all characters have to be compared. So, unless this is the worst case (a hash collision), it just is comparing a bunch of references and integers.
     * @param key the key to turn into an interned.
     * @return the interned key or {@code null}, if this key is not yet interned, therefore it can't be part of map!
     */
    private static @Nullable String toKeyOrNull(@Nullable Object key) {
        return key instanceof CharSequence ? StringUtil.get((CharSequence) key) : null;
    }

    /**
     * Convert the given character sequence into a key, which is an interned string.
     * @param key the key character sequence.
     * @return the interned string.
     */
    private static @NotNull String toKey(@NotNull CharSequence key) {
        return StringUtil.intern(key, false, false);
    }

    private static int indexOf(@Nullable Object @NotNull [] map, @Nullable Object e, int start) {
        // Note: Searching for values does always expect a compare by reference, and it allows `null` values.
        //       We intern keys, therefore, comparing by reference is true for keys too.
        //       Even while keys can never be null, so searching for null at an even position will fail, we do no
        //       pre-check, because every branch would slow us down for normal JSON maps with just a few entries,
        //       we expect that the array is in L1 cache and using the CPU is cheaper than branching!
        assert start >= 0;
        for (int i=start; i < map.length; i+=2) {
            if (e == map[i]) return i;
        }
        return -1;
    }

  @Override
    public boolean containsKey(@NotNull Object key) {
        return indexOf(this.map, toKeyOrNull(key), 0) >= 0;
    }

    @Override
    public boolean containsValue(@NotNull Object value) {
        return indexOf(this.map, value, 1) >= 0;
    }

    @Override
    public @Nullable Object get(@Nullable Object key) {
        final var i = indexOf(map, toKeyOrNull(key), 0);
        return i >= 0 ? map[i+1] : null;
    }

    @Nullable
    @Override
    public Object put(String key, @Nullable Object value) {
        var map = this.map;
        key = toKey(key);
        for (int i = 0; i < map.length; i+=2 ) {
            if (key == map[i]) {
                var oldValue = map[i+1];
                map[i+1] = value;
                return oldValue;
            }
        }
        map = Arrays.copyOf(map, map.length+2);
        map[map.length-2] = key;
        map[map.length-1] = value;
        this.map = map;
        return null;
    }

    /**
     * @return the previous value that was removed.
     */
    private @Nullable Object removeAt(int index) {
        if( index < 0 ) {
            return null;
        }
        var map = this.map;
        if (map.length == 2) { //removing the only element
            this.map = EMPTY;
            return map[1];
        }
        Object[] newArr = new Object[map.length - 2];
        System.arraycopy(map, 0, newArr, 0, index);
        if (index < map.length-2) { //deleting not the last element
            System.arraycopy(map, index + 2, newArr, index, map.length - index - 2);
        }
        this.map = newArr;
        return map[index+1];
    }

    @Override
    public @Nullable Object remove(@NotNull Object key) {
        var index = indexOf(map, key, 0);
        return removeAt(index);
    }

    @Override
    public void putAll(@NotNull Map<? extends String, ?> m) {
        int toAdd = 0;
        var map = this.map;
        for (var rawKey : m.keySet()) {
            final var key = toKey(rawKey);
            if (indexOf(map, key, 0) < 0) {
                toAdd += 2;
            }
        }
        final var newMap = Arrays.copyOf(map, map.length + toAdd); //Resize only once
        toAdd = map.length; // Reuse, now toAdd is the index where we can add new elements
        for (var entry : m.entrySet() ) {
            final var key = toKey(entry.getKey());
            int index = indexOf(map, key, 0);
            if( index < 0 ) {
                newMap[toAdd] = key;
                newMap[toAdd+1] = entry.getValue();
                toAdd += 2;
            } else {
                newMap[index+1] = entry.getValue();
            }
        }
        this.map = newMap;
    }

    @Override
    public void clear() {
        map = EMPTY;
    }

    @NotNull
    @Override
    public Set<String> keySet() {
        return new AbstractSet<>() {
            @NotNull
            @Override
            public Iterator<String> iterator() {
                return new Iterator<>() {
                    private int index = 0;
                    private boolean canRemove = false;
                    @Override
                    public boolean hasNext() {
                        return index < map.length;
                    }

                    @Override
                    public String next() {
                        if( !hasNext() ) throw new NoSuchElementException();
                        canRemove = true;
                        final Object element = map[index];
                        index += 2;
                        return element.toString();
                    }

                    public void remove() {
                        if (!canRemove) throw new IllegalStateException();
                        removeAt(index-=2); // remove by key, but remove both key and value
                        canRemove = false;
                    }
                };
            }

            @Override
            public int size() {
                return map.length >> 1;
            }
        };
    }

    @NotNull
    @Override
    public Collection<Object> values() {
        return new AbstractCollection<>() {
            @NotNull
            @Override
            public Iterator<Object> iterator() {
                return new Iterator<>() {
                    int index = 1;
                    boolean canRemove = false;

                    @Override
                    public boolean hasNext() {
                        return index < map.length;
                    }

                    @Override
                    public Object next() {
                        if( !hasNext() ) throw new NoSuchElementException();
                        canRemove = true;
                        final Object element = map[index];
                        index += 2;
                        return element;
                    }

                    public void remove() {
                        if (!canRemove) throw new IllegalStateException();
                        removeAt(index-1); // remove by key, but remove both key and value
                        index-=2;
                        canRemove = false;
                    }
                };
            }

            @Override
            public int size() {
                return map.length >> 1;
            }
        };
    }

    @NotNull
    @Override
    public Set<Entry<String, Object>> entrySet() {
        return new AbstractSet<>() {
            @NotNull
            @Override
            public Iterator<Entry<String, Object>> iterator() {
                return new Iterator<>() {
                    int index = 0;
                    boolean canRemove = false;

                    @Override
                    public boolean hasNext() {
                        return index < map.length-2;
                    }

                    @Override
                    public Entry<String, Object> next() {
                        if( !hasNext() ) throw new NoSuchElementException();
                        canRemove = true;
                        final Entry<String, Object> entry = new Entry<>() {
                            final int keyIndex = index;
                            @Override
                            public String getKey() {
                                return map[keyIndex].toString();
                            }

                            @Override
                            public Object getValue() {
                                return map[keyIndex+1];
                            }

                            @Override
                            public Object setValue(@NotNull Object value) {
                                final var old = map[keyIndex+1];
                                map[keyIndex+1] = value;
                                return old;
                            }
                        };
                        index += 2;
                        return entry;                    }

                    public void remove() {
                        if (!canRemove) throw new IllegalStateException();
                        removeAt(index-=2); // remove by key, but remove both key and value
                        canRemove = false;
                    }
                };
            }

            @Override
            public int size() {
                return map.length >> 1;
            }
        };
    }
}
