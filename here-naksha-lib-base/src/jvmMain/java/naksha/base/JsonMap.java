package naksha.base;


import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/// Used as internal implementation of {@link JvmMap}.
///
class JsonMap implements Map<String, Object> {
    JsonMap(){
        map = EMPTY;
    }

    /// The internal map representation, \[key1, value1, key2, value2,...].
    private @NotNull Object[] map;
    private static final Object[] EMPTY = new Object[0];

    @Override
    public int size() {
        return map.length/2;
    }

    @Override
    public boolean isEmpty() {
        return size()==0;
    }

    @Override
    public boolean containsKey(@NotNull Object key) {
        for( int i=0; i < map.length; i+=2 ) {
            if( map[i].equals(key) ) return true;
        }
        return false;
    }

    @Override
    public boolean containsValue(@NotNull Object value) {
        for( int i=1; i < map.length; i+=2 ) {
            if( map[i].equals(value) ) return true;
        }
        return false;
    }

    @Override
    public Object get(@NotNull Object key) {
        for( int i=0; i < map.length; i+=2 ) {
            if( map[i].equals(key) ) return map[i+1];
        }
        return null;
    }

    @Nullable
    @Override
    public Object put(@NotNull String key, @Nullable Object value) {
        for ( int i=0; i < map.length; i+=2 ) {
            if( map[i].equals(key) ) {
                Object old = map[i+1];
                map[i+1] = value;
                return old;
            }
        }
        map = Arrays.copyOf(map, map.length+2);
        map[map.length-2] = key;
        map[map.length-1] = value;
        return null;
    }

    @Override
    public Object remove(@NotNull Object key) {
        for( int i=0; i < map.length; i+=2 ) {
            if( map[i].equals(key) ) {
                final Object old = map[i+1];
                Object[] newArr = new Object[map.length - 2];
                System.arraycopy(map, 0, newArr, 0, i);
                System.arraycopy(map, i + 2, newArr, i, map.length - i - 2);
                map = newArr;
                return old;
            }
        }
        return null;
    }

    @Override
    public void putAll(@NotNull Map<? extends String, ?> m) {
        for ( Entry<? extends String, ?> e : m.entrySet() ) {
            put(e.getKey(), e.getValue());
        }
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
                        return index < map.length-2;
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
                        JsonMap.this.remove(map[index-2]); // remove by key, but remove both key and value
                        index-=2;
                        canRemove = false;
                    }
                };
            }

            @Override
            public int size() {
                return map.length/2;
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
                        return index < map.length-1;
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
                        JsonMap.this.remove(map[index-1]); // remove by key, but remove both key and value
                        index-=2;
                        canRemove = false;
                    }
                };
            }

            @Override
            public int size() {
                return map.length/2;
            }
        };
    }

    @NotNull
    @Override
    public Set<Entry<String, Object>> entrySet() {
        return new AbstractSet<>() {
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

                    public void remove() {}
                };
            }

            @Override
            public int size() {
                return map.length/2;
            }
        };
    }
}
