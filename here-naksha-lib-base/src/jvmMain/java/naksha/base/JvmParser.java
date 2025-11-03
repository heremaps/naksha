package naksha.base;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JvmParser extends JsonParser {
  @Override
  protected @NotNull Object newJsonMap(@Nullable Object @NotNull [] entries, int length) {
    return new JvmMap(new JsonMap(entries, 0, length, false));
  }

  @Override
  protected @NotNull Object newJsonArray(@Nullable Object @NotNull [] elements, int length) {
    return new JvmList(new JsonArray(elements, 0, length));
  }

  @Override
  protected @NotNull Object newLong(long value) {
    if (value < Integer.MIN_VALUE ||  value > Integer.MAX_VALUE) {
      return NumberUtil.boxInt64(value);
    }
    return NumberUtil.boxInt((int)value);
  }
}
