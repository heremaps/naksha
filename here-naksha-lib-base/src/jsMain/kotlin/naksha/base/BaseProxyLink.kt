package naksha.base

internal class BaseProxyLink(val baseObject: BaseObject, val proxy: AbstractProxy) {
    var next: BaseProxyLink? = null
}