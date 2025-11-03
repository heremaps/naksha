package naksha.base;

import org.jetbrains.annotations.NotNull;

/**
 * An object that can provide proxies for {@link JsonArray}.
 * @since 3.0
 * @see JsonArray
 * @see JsonArrayProxiable
 * @see JsonArrayProxy
 * @see JsonMap
 * @see JsonMapProxiable
 * @see JsonMapProxy
 */
public interface JsonArrayProxiable {
  /**
   * Returns a proxy for this array.
   * @param as the proxy to return.
   * @return the proxy or a type extending the given proxy.
   * @since 3.0
   */
  default <T, P extends JsonArrayProxy<T>> @NotNull P proxy(@NotNull Class<P> as) {
    return proxy(as, false);
  }

  /**
   * Returns a proxy for this array.
   * @param as the proxy to return.
   * @param exact if true, then assignable <i>(extending)</i> classes are not acceptable, we want exactly {@code as}.
   * @return the proxy or a type extending the given proxy <i>(if {@code exact} is false)</i>.
   * @since 3.0
   */
  <T, P extends JsonArrayProxy<T>> @NotNull P proxy(@NotNull Class<P> as, boolean exact);
}