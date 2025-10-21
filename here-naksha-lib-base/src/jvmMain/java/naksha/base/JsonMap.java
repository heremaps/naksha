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

    /**
     * The internal map representation, [key1, value1, key2, value2,...].
     */
    private @NotNull Object[] map;
    private static final Object[] EMPTY = new Object[0];

    @Override
    public int size() {
        return map.length >> 1;
    }

    @Override
    public boolean isEmpty() {
        return map.length==0;
    }

    private static int indexOf(@NotNull Object[] map, Object e, int start) {
        assert start >= 0;
        if (e != null) {
            for( int i=start; i < map.length; i+=2 ) {
                if( e.equals(map[i]) ) return i;
            }
        }
        return -1;
    }

    @Override
    public boolean containsKey(@NotNull Object key) {
        return indexOf(this.map, key, 0) >= 0;
    }

    @Override
    public boolean containsValue(@NotNull Object value) {
        return indexOf(this.map, value, 1) >= 0;
    }

    @Override
    public @Nullable Object get(Object key) {
        var i = indexOf(map, key, 0);
        return i >= 0 ? map[i+1] : null;
    }

    @Nullable
    @Override
    public Object put(@NotNull String key, @Nullable Object value) {
        var localMapCopy = this.map;
        for (int i = 0; i < localMapCopy.length; i+=2 ) {
            if( key.equals(localMapCopy[i]) ) {
                var oldValue = localMapCopy[i+1];
                localMapCopy[i+1] = value;
                return oldValue;
            }
        }
        localMapCopy = Arrays.copyOf(localMapCopy, localMapCopy.length+2);
        localMapCopy[localMapCopy.length-2] = key;
        localMapCopy[localMapCopy.length-1] = value;
        map = localMapCopy;
        return null;
    }

    /**
     * @return the previous value that was removed.
     */
    private Object removeAt(int index) {
        if( index < 0 ) {
            return null;
        }
        var localMapCopy = map;
        if (localMapCopy.length == 2) { //removing the only element
            map = EMPTY;
            return localMapCopy[1];
        }
        Object[] newArr = new Object[localMapCopy.length - 2];
        System.arraycopy(localMapCopy, 0, newArr, 0, index);
        if (index < localMapCopy.length-2) { //deleting not the last element
            System.arraycopy(localMapCopy, index + 2, newArr, index, localMapCopy.length - index - 2);
        }
        map = newArr;
        return localMapCopy[index+1];
    }

    @Override
    public Object remove(@NotNull Object key) {
        var index = indexOf(map, key, 0);
        return removeAt(index);
    }

    @Override
    public void putAll(@NotNull Map<? extends String, ?> m) {
        int toAdd = 0;
        var localMapCopy = this.map;
        for ( var key : m.keySet() ) {
            if( indexOf(localMapCopy, key, 0) < 0 ) {
                toAdd+=2;
            }
        }
        var newMap = Arrays.copyOf(localMapCopy, localMapCopy.length + toAdd); //Resize only once
        toAdd = localMapCopy.length; // Reuse, now toAdd is the index where we can add new elements
        for ( var entry : m.entrySet() ) {
            final var key = entry.getKey();
            int index = indexOf(localMapCopy, key, 0);
            if( index < 0 ) {
                newMap[toAdd++] = key;
                newMap[toAdd++] = entry.getValue();
            } else {
                newMap[index+1] = entry.getValue();
            }
        }
        map = newMap;
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
                    int index = 0;
                    boolean canRemove = false;
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
