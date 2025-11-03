package naksha.base;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ConcurrentModificationException;
import java.util.Map;

final class JsonMapEntry implements Map.Entry<@NotNull String, @Nullable Object> {
  JsonMapEntry(@NotNull JsonMap map, int index) {
    this.map = map;
    this.index = index;
  }

  final @NotNull JsonMap map;
  int index;

  @Override
  public @NotNull String getKey() {
    final Object key = map.entries[index];
    if (key == null || key.getClass() != String.class) throw new ConcurrentModificationException();
    return (String) key;
  }

  @Override
  public Object getValue() {
    final Object key = map.entries[index];
    final Object value = map.entries[index + 1];
    if (key == null || key.getClass() != String.class) throw new ConcurrentModificationException();
    return value;
  }

  @Override
  public @Nullable Object setValue(@Nullable Object value) {
    final Object key = map.entries[index];
    if (key == null || key.getClass() != String.class) throw new ConcurrentModificationException();
    final var old = map.entries[index + 1];
    map.entries[index + 1] = value;
    return old;
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) return true;
    if (o.getClass() != JsonMapEntry.class) return false;
    final JsonMapEntry other = (JsonMapEntry) o;
    return this.map == other.map && this.index == other.index;
  }

  @Override
  public int hashCode() {
    return System.identityHashCode(this);
  }
}
