package naksha.base;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Base class that allows writing proxies for {@link JsonMap}.
 * @param <V> The value type.
 * @see JsonArray
 * @see JsonArrayProxiable
 * @see JsonArrayProxy
 * @see JsonMap
 * @see JsonMapProxiable
 * @see JsonMapProxy
 */
public class JsonMapProxy<V> implements JsonProxy {
  @Nullable JsonMap jsonMap;
  public @NotNull JsonMap jsonMap() {
    var jsonMap = this.jsonMap;
    if (jsonMap == null) {
      jsonMap = new JsonMap();
      jsonMap.proxies = new JsonMapProxy[]{ this };
      this.jsonMap = jsonMap;
    }
    return jsonMap;
  }
}