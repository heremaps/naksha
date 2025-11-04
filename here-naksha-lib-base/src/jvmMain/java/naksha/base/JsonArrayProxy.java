package naksha.base;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Base class that allows writing proxies for {@link JsonArray}.
 * @param <E> The element type.
 * @since 3.0
 * @see JsonArray
 * @see JsonArrayProxiable
 * @see JsonArrayProxy
 * @see JsonMap
 * @see JsonMapProxiable
 * @see JsonMapProxy
 */
public class JsonArrayProxy<E> implements JsonProxy, JsonArrayProxiable {

  /**
   * The {@link JsonArray} that this proxy backs.
   * @since 3.0
   */
  @Nullable JsonArray jsonArray;

  /**
   * Returns the {@link JsonArray} that this proxy backs.
   * @return the {@link JsonArray} that this proxy backs.
   * @since 3.0
   */
  public @NotNull JsonArray jsonArray() {
    var jsonArray = this.jsonArray;
    if (jsonArray == null) {
      jsonArray = new JsonArray();
      jsonArray.proxies = new JsonArrayProxy[]{ this };
      this.jsonArray = jsonArray;
    }
    return jsonArray;
  }

  @Override
  public <T, P extends JsonArrayProxy<T>> @NotNull P proxy(@NotNull Class<P> as, boolean exact) {
    return jsonArray().proxy(as, exact);
  }
}