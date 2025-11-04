package naksha.base;

import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;

final class UnsafeUtil {
  static @NotNull Field getField(@NotNull Class<?> clazz, @NotNull String fieldName) {
    try {
      return clazz.getDeclaredField(fieldName);
    } catch (NoSuchFieldException e) {
      throw new Error(e);
    }
  }
}
