package naksha.base

import kotlin.reflect.KClass

@JsExport
actual open class AbstractProxy internal actual constructor(baseObject: BaseObject): IProxyable {
    /**
     * We link proxies together. This property is used form [BaseObject]!
     * @see BaseObject
     */
    internal var next: AbstractProxy? = null

    actual val baseObject: BaseObject = baseObject.linkNewProxy(this)

    @Suppress("NON_EXPORTABLE_TYPE")
    actual override fun <T : AbstractProxy> proxy(klass: KClass<T>): T = proxy(klass.js)
    override fun <T : AbstractProxy> proxy(constructor: JsClass<T>): T = baseObject.proxy(constructor)
}