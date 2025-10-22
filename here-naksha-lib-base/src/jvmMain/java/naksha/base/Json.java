package naksha.base;

import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

/**
 * Tooling around arrays.
 * @since 3.0
 */
final class Json {
  /** The singleton for the JavaScript value {@code undefined}, which will not be the same as {@link #EMPTY_ARRAY}, so {@code EMPTY != UNDEFINED}, but {@code EMPTY.equals(UNDEFINED)}. */
  public static final String UNDEFINED = new String(new char[]{});

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
   * Tests if the given map or array is empty.
   * @param data the map or array to test.
   * @return true if the map or array is empty; false otherwise.
   * @since 3.0
   */
  static boolean isEmpty(Object @NotNull [] data) {
    return data.length == 0 || data[0] == UNDEFINED;
  }

  /**
   * Treats the given array as a map, returns the length.
   *
   * @param data the map.
   * @return the amount of valid entries <i>(key-value pairs)</i>.
   * @since 3.0
   */
  static int map_length(Object @NotNull [] data) {
    assert (data.length & 1) == 0;
    for (int i = 0; i < data.length; i += 2) {
      if (data[i] == UNDEFINED) return i >> 1;
    }
    return data.length >> 1;
  }

  /**
   * Treats the given array as an array, returns the length.
   *
   * @param data the array.
   * @return the amount of valid elements.
   * @since 3.0
   */
  static int array_length(Object @NotNull [] data) {
    for (int i = 0; i < data.length; i++) {
      if (data[i] == UNDEFINED) return i;
    }
    return data.length;
  }

  /**
   * Returns the amount of entries that can be stored in the given map.
   * @param data the map.
   * @return the amount of entries that can be stored in the given map.
   */
  static int map_capacity(Object @NotNull [] data) {
    return data.length >> 1;
  }

  /**
   * Returns the amount of elements that can be stored in the given array.
   * @param data the array.
   * @return the amount of elements that can be stored in the given array.
   */
  static int array_capacity(Object @NotNull [] data) {
    return data.length;
  }

  /**
   * A method optimized for 64-bit CPU, and for small objects/arrays as we normally encounter in JSON. It is unusual in JSON, to ever encounter a map or array with more than 20 elements. So, we optimize for memory consumption and CPU caching of small data structures here.
   * @param data the data to ensure a specific capacity.
   * @param size the amount of values that should be stored <i>(for a map, 2 values are needed for each entry)</i>.
   * @param shrink if shrinking is wanted.
   * @return either the given array or a new one with the data copied over, so that the desired amount of values fit into it.
   */
  static Object @NotNull [] ensure_size(Object @NotNull [] data, final int size, final boolean shrink) {
    if (size < 0) throw new IllegalArgumentException("Capacity must not be negative");
    if (size == 0) {
      if (!shrink) return data;
      return EMPTY_ARRAY;
    }
    final int current_size = data.length;
    if (current_size == size) return data;

    // The want the Object[] to fit exactly into L1 cache lines.
    // Therefore, the first "page" can only hold 6 values, every following each 8.
    // The reason is that each value is a pointer, and we optimize this for 64-bit machines with no pointer compression!
    // - If we request 1 to 6 values, we expect the result to be 6.
    // - If we request 7 values, we expect the result to be 8 + 6 = 14.
    // - If we request 14 values, we expect the result to be 8 + 6 = 14.
    // - If we request 15 values, we expect the result to be 16 + 6 = 24.
    var new_size = (((size-6) + 7) & 0xffff_fff8) + 6;
    if (current_size == new_size) { // unchanged size (actually, there is still room left).
      return data;
    }
    if (new_size > current_size) { // we need to expand.
      var new_data = Arrays.copyOf(data, new_size);
      Arrays.fill(new_data, current_size, new_data.length, UNDEFINED);
      return new_data;
    }
    if (!shrink) return data;
    // We need to shrink.
    return Arrays.copyOf(data, new_size);
  }
}