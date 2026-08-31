package naksha.base;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JvmParser extends JsonParser {
  @Override
  protected @NotNull Object newJsonMap(@Nullable Object @NotNull [] entries, int length) {
    final var jvmMap = new JvmMap();
    jvmMap.jsonMap = new JsonMap(entries, 0, length, false);
    return jvmMap;
  }

  @Override
  protected @NotNull Object newJsonArray(@Nullable Object @NotNull [] elements, int length) {
    final var jvmList = new JvmList();
    jvmList.list = new JsonArray(elements, 0, length);
    return jvmList;
  }

  @Override
  protected @NotNull Object newLong(long value) {
    if (value < Integer.MIN_VALUE ||  value > Integer.MAX_VALUE) {
      return value;
    }
    return NumberUtil.boxInt((int)value);
  }
}
