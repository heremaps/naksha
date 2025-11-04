package naksha.base;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicReferenceArray;

import static naksha.base.JvmUtil.toZigZag;

/**
 * Caching for numbers.
 * @since 3.0
 */
public class NumberUtil {
  /** The amount of bits that we use to index. */
  private static final int BITS = 21;

  /** The mask to get the index from an integer. */
  private static final int MASK = (1 << BITS) - 1;

  private static final class WeakDouble extends WeakReference<Double> {
    public WeakDouble(@NotNull Double referent) {
      super(referent);
    }
  }

  private static final class WeakInt extends WeakReference<Integer> {
    public WeakInt(@NotNull Integer referent) {
      super(referent);
    }
  }

  private static final class WeakLong extends WeakReference<Long> {
    public WeakLong(@NotNull Long referent) {
      super(referent);
    }
  }

  @Deprecated
  private static final class WeakJvmInt64 extends WeakReference<JvmInt64> {
    public WeakJvmInt64(@NotNull JvmInt64 referent) {
      super(referent);
    }
  }

  private static final AtomicReferenceArray<@Nullable Object> doubles = new AtomicReferenceArray<>(1 << BITS);
  private static final AtomicReferenceArray<@Nullable Object> longs = new AtomicReferenceArray<>(1 << BITS);
  private static final AtomicReferenceArray<@Nullable Object> ints = new AtomicReferenceArray<>(1 << BITS);
  @Deprecated
  private static final AtomicReferenceArray<@Nullable Object> int64s = new AtomicReferenceArray<>(1 << BITS);
  static {
    // We pre-cache -256 to 256.
    for (int i=-256; i < 256; i++) {
      ints.set(cache_index_of_int(i), i);
      longs.set(cache_index_of_long(i), (long)i);
      int64s.set(cache_index_of_long(i), new JvmInt64(i));
      doubles.set(cache_index_of_double(i), (double)i);
    }
  }

  /// We move the sign bit to the bottom.
  /// Then we use the lowest bits as index, so that we store 0, -1, 1, -2, 2, ...
  /// The assumption is, that caching smaller values gets us more cache hits.
  private static int cache_index_of_int(int value) {
    return toZigZag(value) & MASK;
  }

  /// We move the sign bit to the bottom.
  /// Then we use the lowest bits as index, so that we store 0, -1, 1, -2, 2, ...
  /// The assumption is, that caching smaller values gets us more cache hits.
  private static int cache_index_of_long(long value) {
    return (int)toZigZag(value) & MASK;
  }

  /// Doubles are encoded as 1 sign bit, then 11 exponent bits, then 52 bit mantissa (biased by 1, except with min exponent).
  /// Therefore, the most significant values come first, so 0.5 is a dedicated value with just significant 12 bits, all others are 0.
  /// Note that 0.25, 0.125, aso have as well only 12 significant bits (basically 1 >> e, so mantissa is 0, only exponent set).
  /// The coordinates we use in Naksha are limited to 7 decimal digits, which means, we get best cache hits, when we index the top 21 bit.
  private static int cache_index_of_double(double value) {
    return (int) (Double.doubleToRawLongBits(value) >>> (64 - BITS));
  }

  /**
   * Returns a boxed singleton for the given double value, if available from cache.
   * @param value the value to box.
   * @return the boxed value, preferable a singleton.
   * @since 3.0
   */
  public static @NotNull Double boxDouble(double value) {
    final int index = cache_index_of_double(value);
    Double boxed = null;
    WeakDouble boxedRef = null;
    do {
      final Object raw = doubles.getPlain(index);
      if (raw instanceof Double) {
        boxed = (Double) raw;
        //noinspection UnnecessaryUnboxing
        return boxed.doubleValue() == value ? boxed : value;
      }
      assert raw == null || raw instanceof WeakDouble;
      final WeakDouble currentRef = (WeakDouble) raw;
      Double currentBoxed = currentRef != null ? currentRef.get() : null;
      if (currentBoxed != null) {
        if (currentBoxed.equals(value)) return currentBoxed;
        // auto-box, we do it this way for project valhalla.
        return value;
      }
      // No cached value.
      // auto-box only ones, we do it this way for project valhalla, and we need a strong reference!
      if (boxed == null) {
        boxed = value;
        boxedRef = new WeakDouble(boxed);
      }
      final var existingRef = doubles.compareAndExchange(index, currentRef, boxedRef);
      if (existingRef == currentRef) {
        // We exchanged the old value (either null or a collected double-reference).
        return boxed;
      }
      // Concurrency issue, someone else replaced the existing value with something new.
    } while (true);
  }

  /**
   * Returns a boxed singleton for the given long value, if available from cache.
   * @param value the value to box.
   * @return the boxed value, preferable an {@code Integer} singleton, only {@code Long} if out of integer range.
   * @since 3.0
   */
  public static @NotNull Number boxCompact(long value) {
    return value < Integer.MIN_VALUE || value > Integer.MAX_VALUE ? boxLong(value) : boxInt((int)value);
  }

  /**
   * Returns a boxed singleton for the given long value, if available from cache.
   * @param value the value to box.
   * @return the boxed value, preferable a singleton.
   * @since 3.0
   */
  public static @NotNull Long boxLong(long value) {
    final int index = cache_index_of_long(value);
    Long boxed = null;
    Reference<Long> boxedRef = null;
    do {
      final Object raw = longs.getPlain(index);
      if (raw instanceof Long) {
        boxed = (Long) raw;
        //noinspection UnnecessaryUnboxing
        return boxed.longValue() == value ? boxed : value;
      }
      assert raw == null || raw instanceof WeakLong;
      final WeakLong currentRef = (WeakLong) raw;
      Long currentBoxed = currentRef != null ? currentRef.get() : null;
      if (currentBoxed != null) {
        if (currentBoxed.equals(value)) return currentBoxed;
        // auto-box, we do it this way for project valhalla.
        return value;
      }
      // No cached value.
      // auto-box only ones, we do it this way for project valhalla, and we need a strong reference!
      if (boxed == null) {
        boxed = value;
        boxedRef = new WeakLong(boxed);
      }
      final var existingRef = longs.compareAndExchange(index, currentRef, boxedRef);
      if (existingRef == currentRef) {
        // We exchanged the old value (either null or a collected long-reference).
        return boxed;
      }
      // Concurrency issue, someone else replaced the existing value with something new.
    } while (true);
  }

  /**
   * Returns a boxed singleton for the given int value, if available from cache.
   * @param value the value to box.
   * @return the boxed value, preferable a singleton.
   * @since 3.0
   */
  public static @NotNull Integer boxInt(int value) {
    final int index = cache_index_of_int(value);
    Integer boxed = null;
    Reference<Integer> boxedRef = null;
    do {
      final Object raw = ints.getPlain(index);
      if (raw instanceof Integer) {
        boxed = (Integer) raw;
        //noinspection UnnecessaryUnboxing
        return boxed.intValue() == value ? boxed : value;
      }
      assert raw == null || raw instanceof WeakInt;
      final WeakInt currentRef = (WeakInt) raw;
      Integer currentBoxed = currentRef != null ? currentRef.get() : null;
      if (currentBoxed != null) {
        if (currentBoxed.equals(value)) return currentBoxed;
        // auto-box, we do it this way for project valhalla.
        return value;
      }
      // No cached value.
      // auto-box only ones, we do it this way for project valhalla, and we need a strong reference!
      if (boxed == null) {
        boxed = value;
        boxedRef = new WeakInt(boxed);
      }
      final var existingRef = ints.compareAndExchange(index, currentRef, boxedRef);
      if (existingRef == currentRef) {
        // We exchanged the old value (either null or a collected long-reference).
        return boxed;
      }
      // Concurrency issue, someone else replaced the existing value with something new.
    } while (true);
  }

  /**
   * Returns a boxed singleton for the given long value, if available from cache.
   * @param value the value to box.
   * @return the boxed value, preferable a singleton.
   * @since 3.0
   */
  @Deprecated
  public static @NotNull JvmInt64 boxInt64(long value) {
    final int index = cache_index_of_long(value);
    JvmInt64 boxed = null;
    Reference<JvmInt64> boxedRef = null;
    do {
      final Object raw = int64s.getPlain(index);
      if (raw instanceof JvmInt64) {
        boxed = (JvmInt64) raw;
        return boxed.longValue() == value ? boxed : new JvmInt64(value);
      }
      assert raw == null || raw instanceof WeakJvmInt64;
      final WeakJvmInt64 currentRef = (WeakJvmInt64) raw;
      JvmInt64 currentBoxed = currentRef != null ? currentRef.get() : null;
      if (currentBoxed != null) {
        if (currentBoxed.longValue() == value) return currentBoxed;
        return new JvmInt64(value);
      }
      // No cached value.
      // auto-box only ones, we do it this way for project valhalla, and we need a strong reference!
      if (boxed == null) {
        boxed = new JvmInt64(value);
        boxedRef = new WeakJvmInt64(boxed);
      }
      final var existingRef = int64s.compareAndExchange(index, currentRef, boxedRef);
      if (existingRef == currentRef) {
        // We exchanged the old value (either null or a collected long-reference).
        return boxed;
      }
      // Concurrency issue, someone else replaced the existing value with something new.
    } while (true);
  }

}
