package naksha.base;


import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.Normalizer;
import java.util.*;

import static java.lang.Math.max;
import static naksha.base.Json.UNDEFINED;
import static naksha.base.Json.map_length;
import static naksha.base.StringUtil.intern;
import static naksha.base.StringUtil.interned;
import static naksha.base.JvmUtil.JVM_DEBUGGING;

/**
 * A Java implementation of a JSON map, not thread-safe!
 *
 * @implNote This class is highly optimized for small maps, as they are usually encountered in JSON. The implementation is very memory efficient, while maintaining acceptable performance. It will be bad for big arrays with hundreds of key-value pairs, but be very efficient for smaller maps with only a few entries. For example, on a 64-bit JVM an empty map only consumes 36-byte for the {@link JsonMap}. Adding a single key, adds one L1 cache line for administration, so 64-byte overhead. Therefore, a map with one key-value pair will have an overhead of 100-byte. Adding another key will not change the overhead, but the 3rd key-value pair requires the allocation of another L1 cache line, adding 64-byte overhead. So, the first 2 entries require 64-byte overhead, then every 4 further key-value pairs added will increase the overhead by another 64-byte.
 * <br/><p>Keys are looked-up using brute force, so iterating the array. However, this class ensures that all keys used in it are normalized and interned, therefore it is safe to compare keys by references, so {@code if (key1 == key2)} is totally valid, when the key comes from this map. This makes the search for keys much faster, because it is purely executed within the L1 cache line, but requires more effort, when new keys are added. To reduce this effort, we offer a way to "pin" keys, so to ensure that well-known keys are kept in memory forever, see {@link StringUtil#pin(String)}. Additionally, there is a way to create a map within nanoseconds, when prepared correctly, see {@link #wrap(Object[], int)}.
 * @since 3.0
 * @see JsonArray
 * @see JsonArrayProxiable
 * @see JsonArrayProxy
 * @see JsonMap
 * @see JsonMapProxiable
 * @see JsonMapProxy
 */
public final class JsonMap implements JsonObject, Map<String, Object> {
  /**
   * Creates a new {@link JsonMap} consuming the given array. This means, the ownership of the given array is passed over to this {@link JsonMap} instance, so no copy is made. The provider should not continue to use the array!
   *
   * <p>This is the most efficient way to create maps, but it requires that all keys are interned. It is basically free of cost to create such maps:
   * <pre>{@code
   * package demo;
   * import naksha.base.JsonMap;
   * import static naksha.base.StringUtil.pin;
   * import static naksha.base.NumberUtil.boxLong;
   * public class Demo {
   *   // The hard work is done here, as one time payment.
   *   // This normalizes and interns the strings as keys.
   *   static final String _name = pin("name");
   *   static final String _age = pin("age");
   *
   *   public JsonMap create(String name, int age) {
   *     // This is only three memory allocations!
   *     // On modern JVMs this will be only nanoseconds.
   *     return JsonMap.wrap(new Object[]{
   *       _name, name,
   *       _age, age
   *     });
   *   }
   * }
   * }</pre>
   * @param entries the array of key-value pairs to consume, must not contain null keys, but may contain {@link Json#UNDEFINED UNDEFINED} keys.
   * @param length the amount of valid entries within the {@code entries} array, {@code -1} if not known.
   * @return the {@link JsonMap} that uses the given elements.
   * @since 3.0
   */
  public static @NotNull JsonMap wrap(@Nullable Object @NotNull [] entries, int length) {
    final JsonMap map = new JsonMap();
    map.entries = entries;
    map.length = length < 0 ? map_length(entries) : length;
    return map;
  }

  /**
   * Create a new empty map.
   * @since 3.0
   */
  public JsonMap() {
    entries = Json.EMPTY_MAP;
    length = 0;
  }

  /**
   * Creates a new map by making a copy of the key-value pairs from given entries array.
   *
   * <p>To create a map from values, just do this:
   * <pre>{@code
   * var map = new JsonMap(new Object[]{"a",1,"b",2});
   * assert map.get("a") == 1;
   * assert map.get("b") == 2;
   * }</pre>
   * @param entries the key-value pairs to copy, must not contain null keys, but may contain {@link Json#UNDEFINED UNDEFINED} keys.
   */
  public <T> JsonMap(@Nullable T @NotNull [] entries) {
    this(entries, 0, entries.length, true);
  }

  /**
   * Creates a new map by making a copy of the key-value pairs from given entries array.
   * @param entries the key-value pairs to copy, must not contain null keys, but may contain {@link Json#UNDEFINED UNDEFINED} keys.
   * @param fromIndex the index of the first key to copy.
   */
  public <T> JsonMap(@Nullable T @NotNull [] entries, int fromIndex) {
    this(entries, fromIndex, entries.length, true);
  }

  /**
   * Creates a new map by making a copy of the key-value pairs from given entries array.
   * @param entries the key-value pairs to copy, must not contain null keys, but may contain {@link Json#UNDEFINED UNDEFINED} keys.
   * @param fromIndex the index of the first key to copy.
   * @param toIndex the index of the first key <b>NOT</b> to copy.
   * @see #wrap(Object[], int)
   */
  public <T> JsonMap(@Nullable T @NotNull [] entries, int fromIndex, int toIndex) {
    this(entries, fromIndex, toIndex, true);
  }
  /**
   * Creates a new map by making a copy of the key-value pairs from given entries array.
   * @param entries the key-value pairs to copy, must not contain null keys, but may contain {@link Json#UNDEFINED UNDEFINED} keys.
   * @param fromIndex the index of the first key to copy.
   * @param toIndex the index of the first key <b>NOT</b> to copy.
   * @param intern if the keys should be interned and {@link Normalizer.Form#NFKC NFKC} normalized; only select false, if the keys are already interned! In that case the length must be exactly {@code toIndex - fromIndex}.
   * @see #wrap(Object[], int)
   */
  public <T> JsonMap(@Nullable T @NotNull [] entries, int fromIndex, int toIndex, boolean intern) {
    int length = toIndex - fromIndex;
    if (length < 0 || length > entries.length || (length & 1) == 1) throw new IllegalArgumentException("Invalid entries array");

    assert fromIndex >= 0 && fromIndex <= toIndex && toIndex <= entries.length;
    if (!intern) {
      this.entries = Arrays.copyOfRange(entries, fromIndex, toIndex);
      this.length = length;
      return;
    }

    final var copy = new Object[toIndex - fromIndex];
    for (int i = fromIndex; i < toIndex; i+=2) {
      final var rawKey = entries[i];
      if (rawKey == null || rawKey.getClass() != String.class) {
        copy[i] = UNDEFINED;
        copy[i+1] = UNDEFINED;
        length--;
        continue;
      }
      // assert rawKey instanceof String;
      final var key = intern((String)rawKey);
      final var value = entries[i + 1];
      copy[i] = key;
      copy[i+1] = value;
    }
    this.entries = copy;
    this.length = length;
  }

  /**
   * The internal map representation, {@code [key1, value1, key2, value2, ...]}, with key being {@link Json#UNDEFINED UNDEFINED}, when being not used.
   * @since 3.0
   */
  @Nullable Object @NotNull [] entries;

  /**
   * The current length of the map, so the amount of valid key-value pairs within the {@link #entries}.
   * @since 3.0
   * @see #size()
   */
  int length;

  /**
   * All proxies currently being in use.
   * @since 3.0
   */
  @NotNull JsonMapProxy<?> @Nullable [] proxies;

  /**
   * Returns a proxy for this map.
   * @param as the proxy to return.
   * @param exact if true, then assignable <i>(extending)</i> classes are not acceptable, we want exactly {@code as}.
   * @return the proxy or a type extending the given proxy <i>(if {@code exact} is false)</i>.
   * @since 3.0
   */
  @SuppressWarnings("unchecked")
  public <E, P extends JsonMapProxy<E>> @NotNull P proxy(@NotNull Class<P> as, boolean exact) {
    var proxies = this.proxies;
    P proxy;
    if (proxies == null) {
      proxy = Json.newInstance(as);
      proxy.jsonMap = this;
      this.proxies = new JsonMapProxy[]{ proxy };
      return proxy;
    }
    P match = null;
    for (var p : proxies) {
      final var pClass = p.getClass();
      if (pClass == as) {
        return (P) p;
      }
      if (!exact && as.isAssignableFrom(pClass)) {
        match = (P) p;
      }
    }
    if (match != null) {
      return match;
    }
    proxy = Json.newInstance(as);
    proxy.jsonMap = this;
    proxies = Arrays.copyOf(proxies, proxies.length + 1);
    proxies[proxies.length - 1] = proxy;
    this.proxies = proxies;
    return proxy;
  }

  /**
   * Returns a proxy for this map.
   * @param as the proxy to return.
   * @return the proxy or a type extending the given proxy.
   * @since 3.0
   */
  public <E, P extends JsonMapProxy<E>> @NotNull P proxy(@NotNull Class<P> as) {
    return proxy(as, false);
  }

  @Override
  public int size() {
    var length = this.length;
    if (length < 0) {
      length = map_length(entries);
      this.length = length;
    }
    return length;
  }

  /**
   * Returns the capacity of the map (the amount of key-value pairs it can hold).
   * @return the capacity of the map.
   * @since 3.0
   */
  public int capacity() {
    return entries.length >> 1;
  }

  /**
   * Ensures that there is room in the map for at least the given amount of new key-value pairs.
   * @param amount the amount of key-value pairs that should have space in the map, before another expansion is needed.
   * @return this.
   * @since 3.0
   */
  public @NotNull JsonMap ensure(int amount) {
    entries = Json.ensure_size(entries, (length << 1) + (amount << 1), false, UNDEFINED);
    return this;
  }

  @Override
  public boolean isEmpty() {
    return size() == 0;
  }

  /**
   * Tries to convert the given string into a key. A key is a string of which only one instance exists, so a singleton. If no such key singleton exists, returns {@code null}.
   *
   * <p>We always intern keys. If the given key is interned already, this check is cheap, if not, the test is slightly slower. The method actually boils down to one array access by hash-code of the given character sequence, followed by an iteration above all interned strings with the same hash-code. We assume there are no or maximal two hash collisions, therefore we expect that most compares are just numeric, except for a few hash collisions, where all characters have to be compared. So, unless this is the worst case (a hash collision), it just is comparing a bunch of references and integers.
   * @param key the key to turn into an interned.
   * @return the interned key or {@code null}, if this key is not yet interned, therefore it can't be part of map!
   */
  private static @Nullable String toKeyOrNull(@Nullable Object key) {
    return key instanceof CharSequence ? interned((CharSequence) key) : null;
  }

  /**
   * Convert the given character sequence into a key, which is an interned string.
   * @param key the key character sequence.
   * @return the interned string.
   */
  private static @NotNull String toKey(@NotNull CharSequence key) {
    return intern(key, false, false);
  }

  /// Searching for keys is always done by reference. Returns the index of the key or -1, if not found.
  private static int indexOfKey(@Nullable Object @NotNull [] entries, @NotNull String key, int start) {
    assert start >= 0 && start <= entries.length && (start & 1) == 0;
    for (int i=start; i < entries.length; i+=2) {
      if (entries[i] == key) return i;
    }
    return -1;
  }

  /// Searching for values using equals. Returns the index of the key or -1, if not found.
  private static int indexOfValue(@Nullable Object @NotNull [] entries, @Nullable Object value, int start) {
    assert start >= 0 && start <= entries.length && (start & 1) == 0;
    for (int i = start; i < entries.length; i+=2) {
      final var key = entries[i];
      if (key == UNDEFINED) continue;
      if (Objects.equals(entries[i+1], value)) return i;
    }
    return -1;
  }

  /**
   * Returns the index of the given key in the {@link #entries}, if this map contains the given key.
   *
   * <p>The key can be read as {@code map.entries[index]}, and the value as {@code map.entries[index+1]}.
   * @param key the key to search for.
   * @param interned if the given key is a string and interned <i>(avoids lookup, in doubt use false)</i>.
   * @return the index of the entry or {@code -1}, if the key is not contained in the map.
   * @since 3.0
   */
  int indexOfKey(@Nullable Object key, boolean interned) {
    if (key == null) return -1;
    String k = null;
    if (key.getClass() == String.class) {
      k = interned ? (String) key : interned( (String) key );
    } else if (key instanceof CharSequence) {
      k = interned((CharSequence) key);
    }
    if (k == null) return -1;
    return indexOfKey(entries, k, 0);
  }

  /**
   * Returns the index of the key, storing the given value, in the {@link #entries}, if this map contains the given value.
   *
   * <p>The key can be read as {@code map.entries[index]}, and the value as {@code map.entries[index+1]}.
   * @param value the value to search for.
   * @param start the first entry to start searching at.
   * @return the index of the key that stores the searched value, or {@code -1}, if the value was not found.
   * @since 3.0
   */
  int indexOfValue(@Nullable Object value, int start) {
    return indexOfValue(entries, value, 0);
  }

  @Override
  public boolean containsKey(@Nullable Object key) {
    final String k = toKeyOrNull(key);
    return k != null && indexOfKey(entries, k, 0) >= 0;
  }

  @Override
  public boolean containsValue(@Nullable Object value) {
    return indexOfValue(this.entries, value, 1) >= 0;
  }

  @Override
  public @Nullable Object get(@Nullable Object key) {
    final int i = indexOfKey(key, false);
    return i >= 0 ? entries[i+1] : null;
  }

  /**
   * Returns the value to which the specified key is mapped,
   * or {@code null} if this map contains no mapping for the key.
   *
   * <p>More formally, if this map contains a mapping from a key
   * {@code k} to a value {@code v} such that
   * {@code Objects.equals(key, k)},
   * then this method returns {@code v}; otherwise
   * it returns {@code null}.  (There can be at most one such mapping.)
   *
   * <p>If this map permits null values, then a return value of
   * {@code null} does not <i>necessarily</i> indicate that the map
   * contains no mapping for the key; it's also possible that the map
   * explicitly maps the key to {@code null}.  The {@link #containsKey
   * containsKey} operation may be used to distinguish these two cases.
   *
   * @param key the key whose associated value is to be returned
   * @param interned if the given key is guaranteed to be interned.
   * @return the value to which the specified key is mapped, or {@code null} if this map contains no mapping for the key
   * @throws ClassCastException if the key is of an inappropriate type for this map ({@linkplain Collection##optional-restrictions optional})
   * @throws NullPointerException if the specified key is null and this map does not permit null keys ({@linkplain Collection##optional-restrictions optional})
   * @since 3.0
   */
  public @Nullable Object get(@NotNull String key, boolean interned) {
    final int i = indexOfKey(key, interned);
    return i >= 0 ? entries[i+1] : null;
  }

  @Override
  public @Nullable Object put(@NotNull String key, @Nullable Object value) {
    final var old = _put(key, false, value, 1);
    return old == UNDEFINED ? null : old;
  }

  /**
   * Associates the specified value with the specified key in this map. If the map previously contained a mapping for the key, the old value is replaced by the specified value.
   *
   * @param key key with which the specified value is to be associated
   * @param value value to be associated with the specified key
   * @return the previous value associated with {@code key}, or {@link Json#UNDEFINED UNDEFINED} if there was no mapping for {@code key}.
   */
  public @Nullable Object set(@NotNull String key, @Nullable Object value) {
    return _put(key, false, value, 1);
  }

  /**
   * Associates the specified value with the specified key in this map. If the map previously contained a mapping for the key, the old value is replaced by the specified value.
   *
   * @param key key with which the specified value is to be associated
   * @param value value to be associated with the specified key
   * @param interned if the given key is already interned, this makes the call much faster.
   * @return the previous value associated with {@code key}, or {@link Json#UNDEFINED UNDEFINED} if there was no mapping for {@code key}.
   */
  public @Nullable Object set(@NotNull String key, @Nullable Object value, boolean interned) {
    return _put(key, interned, value, 1);
  }

  private @Nullable Object _put(@NotNull String key, boolean interned, @Nullable Object value, int min_new_slots) {
    final var entries = this.entries;
    if (!interned) key = toKey(key);
    int firstEmptyIndex = -1;
    for (int i = 0; i < entries.length; i+=2) {
      final Object k = entries[i];
      if (key == k) {
        var oldValue = entries[i+1];
        entries[i+1] = value;
        return oldValue;
      }
      if (k == UNDEFINED && firstEmptyIndex < 0) {
        firstEmptyIndex = i;
      }
    }

    if (firstEmptyIndex >= 0) {
      entries[firstEmptyIndex] = key;
      entries[firstEmptyIndex + 1] = value;
      this.length++;
      return UNDEFINED;
    }

    final var new_entries = Json.ensure_size(entries, entries.length + max(2, min_new_slots << 1), false, UNDEFINED);
    assert entries != new_entries && entries.length < new_entries.length;
    new_entries[entries.length] = key;
    new_entries[entries.length+1] = value;
    this.entries = new_entries;
    this.length++;
    return UNDEFINED;
  }

  @Override
  public @Nullable Object remove(@NotNull Object key) {
    final int index = indexOfKey(key, false);
    Object oldValue = null;
    if (index >= 0) {
      final var entries = this.entries;
      oldValue = entries[index+1];
      entries[index] = UNDEFINED;
      entries[index+1] = UNDEFINED;
      length = length - 1;
    }
    return oldValue == UNDEFINED ? null : oldValue;
  }

  /**
   * Remove the entry specified by the key from this map.
   *
   * @param key the key of the entry to remove from the map.
   * @param interned if the given key is already interned, this makes the call much faster.
   * @return true if an entry in the map has been removed successfully. false if the key is not found.
   */
  public boolean delete(String key, boolean interned) {
    if (!interned) key = toKeyOrNull(key);
    if (key == null) return false;
    final var entries = this.entries;
    final int index = indexOfKey(entries, key, 0);
    if (index < 0) return false;
    entries[index] = UNDEFINED;
    entries[index+1] = UNDEFINED;
    length = length - 1;
    return true;
  }

  @Override
  public void putAll(@NotNull Map<? extends String, ?> m) {
    // The `min_new_entries` ensures that if we have to resize the entries, we reserve enough space to add all given entries.
    // This ensures that we maximally resize the entries array ones.
    final var min_new_entries = m.size();

    if (m.getClass() == JsonMap.class) {
      // This simplifies everything.
      final var map = (JsonMap) m;
      final var map_entries = map.entries;
      for (int i=0; i < map_entries.length; i+=2) {
        final Object rawKey = map_entries[i];
        if (rawKey != null && rawKey.getClass() == String.class) {
          final String key = (String) rawKey;
          final Object value = map_entries[i+1];
          _put(key, true, value, min_new_entries);
        }
      }
    } else {
      // We need to intern the keys, this is more effort.
      for (final var entry : m.entrySet()) {
        _put(entry.getKey(), false, entry.getValue(), min_new_entries);
      }
    }
  }

  @Override
  public void clear() {
    Arrays.fill(entries, UNDEFINED);
    length = 0;
  }

  @Override
  public @NotNull Set<@NotNull String> keySet() {
    return new AbstractSet<>() {
        @Override
        public @NotNull Iterator<@NotNull String> iterator() {
          return new Iterator<>() {
            private int index = 0;
            private boolean canRemove = false;

            private int index() {
              final var entries = JsonMap.this.entries;
              int index = this.index;
              while (index < entries.length && entries[index] == UNDEFINED) index += 2;
              this.index = index;
              return index;
            }

            @Override
            public boolean hasNext() {
              return index() < entries.length;
            }

            @Override
            public @NotNull String next() {
              final var entries = JsonMap.this.entries;
              final int index = index();
              if (index >= entries.length) throw new NoSuchElementException();
              canRemove = true;
              final Object key = entries[index];
              assert key != null && key.getClass() == String.class;
              this.index = index + 2;
              return key.toString();
            }

            public void remove() {
              if (!canRemove) throw new IllegalStateException();
              final int index = index();
              final var entries = JsonMap.this.entries;
              entries[index] = UNDEFINED;
              entries[index+1] = UNDEFINED;
              JsonMap.this.length = size() - 1;
              canRemove = false;
            }
          };
      }

      @Override
      public int size() {
        return JsonMap.this.size();
      }
    };
  }

  @Override
  public @NotNull Collection<@Nullable Object> values() {
    return new AbstractSet<>() {
      @Override
      public @NotNull Iterator<@Nullable Object> iterator() {
        return new Iterator<>() {
          private int index = 0;
          private boolean canRemove = false;

          private int index() {
            final var entries = JsonMap.this.entries;
            int index = this.index;
            while (index < entries.length && entries[index] == UNDEFINED) index += 2;
            this.index = index;
            return index;
          }

          @Override
          public boolean hasNext() {
            return index() < entries.length;
          }

          @Override
          public @Nullable Object next() {
            final var entries = JsonMap.this.entries;
            final int index = index();
            if (index >= entries.length) throw new NoSuchElementException();
            canRemove = true;
            this.index = index + 2;
            return entries[index+1];
          }

          public void remove() {
            if (!canRemove) throw new IllegalStateException();
            final int index = index();
            final var entries = JsonMap.this.entries;
            entries[index] = UNDEFINED;
            entries[index+1] = UNDEFINED;
            JsonMap.this.length = size() - 1;
            canRemove = false;
          }
        };
      }

      @Override
      public int size() {
        return JsonMap.this.size();
      }
    };
  }

  @Override
  public @NotNull Set<Map.Entry<@NotNull String, @Nullable Object>> entrySet() {
    return new AbstractSet<>() {
      @Override
      public @NotNull Iterator<Map.Entry<@NotNull String, @Nullable Object>> iterator() {
        return new Iterator<>() {
          private int index = 0;
          private boolean canRemove = false;
          private JsonMapEntry entry;

          private int index() {
            final var entries = JsonMap.this.entries;
            int index = this.index;
            while (index < entries.length && entries[index] == UNDEFINED) index += 2;
            this.index = index;
            return index;
          }

          @Override
          public boolean hasNext() {
            return index() < (entries.length-2);
          }

          @Override
          public @NotNull JsonMapEntry next() {
            final var entries = JsonMap.this.entries;
            final int index = index();
            if (index >= entries.length) throw new NoSuchElementException();
            canRemove = true;
            if (entry == null || JVM_DEBUGGING) {
              entry = new JsonMapEntry(JsonMap.this, index);
            } else {
              entry.index = index;
            }
            this.index = index + 2;
            return entry;
          }

          public void remove() {
            if (!canRemove) throw new IllegalStateException();
            final int index = index();
            final var entries = JsonMap.this.entries;
            entries[index] = UNDEFINED;
            entries[index+1] = UNDEFINED;
            JsonMap.this.length--;
            assert JsonMap.this.length >= 0;
            canRemove = false;
          }
        };
      }

      @Override
      public int size() {
        return JsonMap.this.size();
      }
    };
  }
}