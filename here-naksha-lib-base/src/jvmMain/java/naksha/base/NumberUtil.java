package naksha.base;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicReferenceArray;

public class NumberUtil {
  private static final int BITS = 21;

  /** The mask for the index of a long. */
  private static final int MASK_LONG = (1 << BITS) - 1;

  /** The amount of bit to shift a double right. */
  private static final int SHIFT_DOUBLE = 64 - BITS;

  private static final class WeakDouble extends WeakReference<Double> {
    public WeakDouble(@NotNull Double referent) {
      super(referent);
    }
  }
  private static final class WeakLong extends WeakReference<Long> {
    public WeakLong(@NotNull Long referent) {
      super(referent);
    }
  }
  private static final AtomicReferenceArray<@Nullable WeakDouble> doubles = new AtomicReferenceArray<>(1 << BITS);
  private static final AtomicReferenceArray<@Nullable WeakLong> longs = new AtomicReferenceArray<>(1 << BITS);

  public static @NotNull Double boxDouble(double value) {
    Double boxed = null;
    WeakDouble boxedRef = null;
    while (true) {
      final long raw = Double.doubleToRawLongBits(value);
      final int index = (int) (raw >>> SHIFT_DOUBLE);
      final WeakDouble currentRef = doubles.getPlain(index);
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
      final WeakDouble existingRef = doubles.compareAndExchange(index, currentRef, boxedRef);
      if (existingRef == currentRef) {
        // We exchanged the old value (either null or a collected double-reference).
        return boxed;
      }
      // Concurrency issue, someone else replaced the existing value with something new.
    }
  }

  public static @NotNull Long boxLong(long value) {
    Long boxed = null;
    WeakLong boxedRef = null;
    while (true) {
      final int index = (int) (value & MASK_LONG);
      final WeakLong currentRef = longs.getPlain(index);
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
      final WeakLong existingRef = longs.compareAndExchange(index, currentRef, boxedRef);
      if (existingRef == currentRef) {
        // We exchanged the old value (either null or a collected long-reference).
        return boxed;
      }
      // Concurrency issue, someone else replaced the existing value with something new.
    }
  }

}
