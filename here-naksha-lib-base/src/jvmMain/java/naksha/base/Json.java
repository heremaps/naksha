package naksha.base;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static naksha.base.JvmUtil.*;
import static naksha.base.JvmUtil.optimalObjectArrayLength;

/**
 * Tooling around storing JSON data in dense arrays.
 *
 * <p>This tooling is based upon the assumption that we do not encounter big maps or arrays in JSON, so we rather handle large amount of small objects. Therefore, we store all data in a simple array, that should always exactly fit in a given number of L1 cache pages. For maps, we ensure that the keys are always interned, so that when searching for a key, we can actually compare just the references. Additionally, for maps we use all slots to store data.
 *
 * <h3>Warning</h3>
 * All static functions are low-level function with zero bound checks, only assertions!
 * @since 3.0
 */
public final class Json {
  /**
   * All interface to default implementation.
   * @since 3.0
   */
  static final ConcurrentHashMap<Class<?>, Class<?>> implementations = new ConcurrentHashMap<>();
  static {
    implementations.put(List.class, JsonArray.class);
    implementations.put(Map.class, JsonMap.class);
  }

  /**
   * Registers a certain default implementation with a given interface.
   * @param interfaceClass the interface to register a default implementation for.
   * @param implementation the implementation to register.
   * @param override if true, overrides existing default implementations.
   * @param <I> the interface to provide a default implementation for.
   * @param <T> the class that implements the given interface, and extends either {@link JsonArrayProxy} or {@link JsonMapProxy}.
   * @return the previously registered implementation, always null, if {@code override} is false.
   * @throws NullPointerException if {@code interfaceClass} or {@code implementation} are null.
   * @throws IllegalArgumentException if {@code interfaceClass} is no interface, if the given {@code implementation} has no parameterless constructor, or does not implement the given {@code interfaceClass}, or if {@code implementation} does not extend {@link JsonArrayProxy} or {@link JsonMapProxy}.
   * @throws IllegalStateException if there is already an implementation registered, and {@code override} is false.
   */
  public static <I, T extends I> @Nullable Class<? extends I> setDefaultImplementation(
      final @NotNull Class<I> interfaceClass,
      final @NotNull Class<T> implementation,
      final boolean override
  ) {
    if (!interfaceClass.isInterface()) {
      throw new IllegalArgumentException("The given interface is no interface: " + interfaceClass.getName());
    }
    if (!JsonArrayProxy.class.isAssignableFrom(implementation) && !JsonMapProxy.class.isAssignableFrom(implementation)) {
      throw new IllegalArgumentException("The given implementation must extend either JsonArrayProxy or JsonMapProxy: " + implementation.getName());
    }
    if (!interfaceClass.isAssignableFrom(implementation)) {
      throw new IllegalArgumentException("The given implementation ("+implementation.getName()+") must implement the given interface: " + interfaceClass.getName());
    }
    try {
      implementation.getDeclaredConstructor();
    } catch (NoSuchMethodException e) {
      throw new IllegalArgumentException("The given implementation ("+implementation.getName()+") does not have a parameterless constructor");
    }
    final var existing = implementations.putIfAbsent(interfaceClass, implementation);
    if (existing != null && !override) {
      throw new IllegalStateException("Already registered implementation for " + interfaceClass.getName() + ", being: " + implementation.getName());
    }
    //noinspection unchecked
    return (Class<? extends I>) existing;
  }

  /**
   * Removes the given default implementation.
   * @param interfaceClass the interface to register a default implementation for.
   * @param implementation the implementation to register.
   * @param <I> the interface to provide a default implementation for.
   * @param <T> the class that implements the given interface, and extends either {@link JsonArrayProxy} or {@link JsonMapProxy}.
   * @return true if the given {@code implementation} was removed; false otherwise.
   * @throws NullPointerException if {@code interfaceClass} or {@code implementation} are null.
   */
  public static <I, T extends I> boolean removeDefaultImplementation(@NotNull Class<I> interfaceClass, @NotNull Class<T> implementation) {
    return implementations.remove(interfaceClass, implementation);
  }

  /**
   * Create a new instance of the given class, invoking the default parameterless constructor.
   * @param clazz the class to create an instance of.
   * @return the instance.
   * @param <T> The type.
   */
  public static <T> @NotNull T newInstance(@NotNull Class<T> clazz) {
    try {
      return clazz.getDeclaredConstructor().newInstance();
    } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
      throw new RuntimeException(e);
    }
  }

  /// **Note**: The JVM will not move a class in memory as long as it has references (because linker).
  /// This is important later, when we want to apply SIMD instructions to `Object[]`.
  private static class Undefined {
    private Undefined() {}
  }

  /**
   * The singleton for the JavaScript value {@code undefined}.
   * @since 3.0
   */
  public static final Object UNDEFINED = Undefined.class;

  /// **Note**: The JVM will not move a class in memory as long as it has references (because linker).
  /// This is important later, when we want to apply SIMD instructions to `Object[]`.
  private static class Tombstone {
    private Tombstone() {}
  }

  /**
   * The singleton internally used to mark slots in an array that are to be removed by the next compaction. This is the only purpose of this value, temporary mark a slot for compaction.
   * @since 3.0
   */
  public static final Object TOMBSTONE = Tombstone.class;

  /**
   * A singleton for all empty array's.
   * @since 3.0
   */
  public static final Object[] EMPTY_ARRAY = new Object[0];

  /**
   * A singleton for all empty map's.
   * @since 3.0
   */
  public static final Object[] EMPTY_MAP = new Object[0];

  /**
   * Stores the capacity from where we should no longer reuse an array, defaults to 3 L1 cache lines; can be modified.
   * @since 3.0
   */
  public static int MAX_EMPTY_CAPACITY = JVM_MIN_CAPACITY + JVM_OOPS_PER_CACHE_LINE * 2;

  /**
   * Tests if the given map or array is empty.
   * @param data the map or array to test.
   * @return true if the map or array is empty; false otherwise.
   * @since 3.0
   */
  public static boolean isEmpty(Object @NotNull [] data) {
    return data.length == 0 || data[0] == UNDEFINED;
  }

  /**
   * Treats the given array as a map, returns the length.
   *
   * @implNote Iterates all entries, and count the amount of keys not being {@link #UNDEFINED}.
   * @param entries the map.
   * @return the amount of valid entries <i>(key-value pairs)</i>.
   * @since 3.0
   */
  public static int map_length(Object @NotNull [] entries) {
    assert (entries.length & 1) == 0;
    int length = 0;
    for (int i = 0; i < entries.length; i += 2) {
      if (entries[i] != UNDEFINED) length++;
    }
    return length;
  }

  /**
   * Returns the amount of entries that can be stored in the given map.
   * @param entries the map.
   * @return the amount of entries that can be stored in the given map.
   */
  public static int map_capacity(Object @NotNull [] entries) {
    return entries.length >> 1;
  }

  /**
   * Returns the amount of elements that can be stored in the given array.
   * @param elements the array.
   * @return the amount of elements that can be stored in the given array.
   */
  public static int array_capacity(Object @NotNull [] elements) {
    return elements.length;
  }

  /**
   * Remove all elements that are {@link #TOMBSTONE}.
   *
   * <p>Algorithmically, iterates the {@code elements} array, and copies back all elements that are not {@link #TOMBSTONE} to positions where previously {@link #TOMBSTONE} was stored. Eventually, filling the rest of the array with {@link #UNDEFINED}. So, removing all {@link #TOMBSTONE} values from the array, compacting the rest of the values, including {@link #UNDEFINED}.
   * @param elements the elements to compact.
   * @param length the length of the array, so the amount of valid values in it.
   * @return the new length of the compact array, same as length when nothing was done.
   */
  public static int array_compact(Object @NotNull [] elements, int length) {
    int new_end = 0;
    for (int i=0; i < length; i++) {
      final var element = elements[i];
      if (element == TOMBSTONE) continue;
      // Avoid copy from `i` to `i`.
      if (new_end < i) elements[new_end] = element;
      new_end++;
    }
    // If we did some compaction, fill values behind the last target with UNDEFINED.
    if (new_end < length) {
      Arrays.fill(elements, new_end, length, UNDEFINED);
    }
    return new_end;
  }

  /**
   * Search through the given {@code elements} and find the first occurrence of the given {@code value}.
   * @param elements the elements array to search.
   * @param from the first element to search.
   * @param to the first element <b>NOT</b> to search.
   * @param value the value to search.
   * @return the first found position or {@code -1}, if not found between {@code from} and {@code to}.
   */
  public static int array_index_of(Object @NotNull [] elements, int from, int to, @Nullable Object value) {
    assert from >= 0 && from <= to && to <= elements.length;
    for (int i = from; i < to; i++) {
      if (elements[i] == value) return i;
    }
    return -1;
  }

  /**
   * Search backwards through the given {@code elements} and find the last occurrence of the given {@code value}.
   * @param elements the elements array to search.
   * @param from the first element to search.
   * @param to the first element <b>NOT</b> to search.
   * @param value the value to search.
   * @return the first found position or {@code -1}, if not found between {@code from} and {@code to}.
   */
  public static int array_last_index_of(Object @NotNull [] elements, int from, int to, @Nullable Object value) {
    assert from >= 0 && from <= to && to <= elements.length;
    for (int i = to - 1; i >= from; i--) {
      if (elements[i] == value) return i;
    }
    return -1;
  }

  /**
   * A method optimized for small objects/arrays that we normally encounter in JSON. It is unusual in JSON, to ever encounter a map or array with more than 20 elements. So, we optimize for memory consumption and CPU caching of small data structures here. This method ensures that the given array has enough slots to store at least the given `amount` of element. If the array is increased in size, it will fill the new added slots with the value of {@code emptyValue}, should be mostly {@link #UNDEFINED}.
   * @param array the data to ensure a specific capacity.
   * @param amount the amount of values that should be stored <i>(for a map, 2 values are needed for each entry)</i>.
   * @param shrink if shrinking is wanted.
   * @param emptyValue the empty value to insert at the new places.
   * @return either the given array or a new one with the data copied over, so that the desired amount of values fit into it.
   * @since 3.0
   */
  public static @Nullable Object @NotNull [] ensure_size(Object @NotNull [] array, final int amount, final boolean shrink, @Nullable Object emptyValue) {
    if (amount <= 0) {
      if (!shrink) return array;
      return EMPTY_ARRAY;
    }
    final int current_length = array.length;
    if (current_length == amount) {
      // We have already exactly this capacity (do not have to be L1 cache optimized!).
      return array;
    }
    final int new_length = optimalObjectArrayLength(amount);
    if (current_length == new_length) {
      // Unchanged size (we need as much L1 cache lines for 2 as we need for 3!).
      return array;
    }
    if (new_length > current_length) {
      // Expand array, fill the new slots with UNDEFINED.
      final var new_data = Arrays.copyOf(array, new_length);
      if (emptyValue != null) {
        Arrays.fill(new_data, current_length, new_data.length, emptyValue);
      }
      return new_data;
    }
    if (!shrink) return array;
    // We know that new_length is not bigger than current_length, and we know that they are not equal, so, shrink it!
    return Arrays.copyOf(array, new_length);
  }
}