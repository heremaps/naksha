package naksha.base

/**
 * This exception is internally thrown when a new proxy is created, constructor called, but then detects that another thread concurrently was doing the same. This can only happen when [AbstractProxy.proxy] is called, and only internally within this prox.
 *
 * When a user creates a new proxy object by himself, then the underlying [BaseObject] will be created together with the proxy, therefore there can't a concurrent linking ongong. This exception is caught internally and fixed internally.
 * @since 3.0
 */
class BaseProxyLinkError(
    /**
     * The proxy that is already linked and has the same class as the one that should have been linked. The linker will use the existing proxy and discard the new proxy.
     * @since 3.0
     */
    val existing: AbstractProxy
) : Exception(null, null, false, false)
