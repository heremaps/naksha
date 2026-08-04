package naksha.base

internal class JvmProxyLink(val jvmObject: JvmObject, val proxy: AbstractProxy) {
    var next: JvmProxyLink? = null
}