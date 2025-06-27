@file:Suppress("OPT_IN_USAGE")

package naksha.base

import naksha.base.Platform.Platform_C.asPlatformObject
import naksha.base.Platform.Platform_C.forKClass
import naksha.base.Platform.Platform_C.isPlatformObject
import naksha.base.Platform.Platform_C.unbox
import naksha.base.PlatformListApi.PlatformListApi_C.list_get
import naksha.base.PlatformListApi.PlatformListApi_C.list_get_capacity
import naksha.base.PlatformListApi.PlatformListApi_C.list_get_length
import naksha.base.PlatformListApi.PlatformListApi_C.list_set
import naksha.base.PlatformListApi.PlatformListApi_C.list_set_capacity
import naksha.base.PlatformMapApi.PlatformMapApi_C.map_contains_key
import naksha.base.PlatformMapApi.PlatformMapApi_C.map_get
import naksha.base.PlatformMapApi.PlatformMapApi_C.map_key_iterator
import naksha.base.PlatformMapApi.PlatformMapApi_C.map_set
import naksha.base.PlatformUtil.PlatformUtil_C.deepEquals
import naksha.base.fn.Fn0
import naksha.base.fn.Fn1
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.jvm.JvmOverloads

/**
 * The base class for proxy types bound to [PlatformList], [PlatformMap], or [PlatformDataView].
 *
 * A proxy is an attachment class that is [bound][bind] to a platform _(native)_ object at runtime, so that it allows accessing the platform object through the proxy methods. The concept behind this approach is [Duck-Typing](https://en.wikipedia.org/wiki/Duck_typing).
 *
 * **This allows runtime linking of types and data.**
 * @since 3.0
 */
@Suppress("EqualsOrHashCode")
@JsExport
abstract class Proxy : PlatformObject {
    companion object Proxy_C {
        /**
         * The [PlatformType] of [Proxy].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(Proxy::class).withPackageName(PACKAGE_NAME)

        init { initialize() }
    }

    /**
     * The native object to which this type is linked.
     * @since 3.0
     */
    internal var data: PlatformObject? = null

    /**
     * A helper method that creates a new data object.
     * @since 3.0
     */
    protected abstract fun createData(): PlatformObject // TODO: Rename to createPlatformObject

    /**
     * Binds this proxy to the given [PlatformObject] using the given [Symbol], normally only invoke from [PlatformType.proxy].
     *
     * It can be overloaded by extending types to perform late initialization. It is guaranteed that the method is invoked at least ones in the lifetime of a proxy.
     *
     * - Throw an [NakshaError.ILLEGAL_STATE], if this method is called for existing
     *
     * @param data The native object to which to bind.
     * @param symbol The symbol to which to bind.
     * @throws IllegalArgumentException If the given data object is not of the expected type, for example when only [PlatformMap] can be bound.
     * @throws IllegalStateException If the proxy is already linked.
     * @since 3.0
     * @see PlatformType.proxy
     */
    open fun bind(data: PlatformObject, symbol: Symbol) {
        if (this.data != null && this.data !== data) {
            // When bound to itself, we can simply ignore the call.
            if (!reBind) throw illegalState("Rebinding is not allowed, this proxy has already a binding")
            // Rebinding only is needed for maps.
            // It means to copy the initialized properties from the data object that was created within
            // the constructor of a proxy into the new data object that the user wants to bind.
            //
            // It happens, when a new proxy is requested for an existing proxy, which causes the
            // parameterless constructor of the new proxy to be invoked, which may initialize some properties.
            // The issue is, that this data object, that now received the properties, is the wrong one, because
            // we want to bind to an existing object.
            //
            // This causes one final issue: Binding a new proxy, may mutate the underlying object!
            val initialized = this.data
            if (initialized is PlatformMap && data is PlatformMap) {
                val it = map_key_iterator(initialized)
                var it_result = it.next()
                while (!it_result.done) {
                    val key = it_result.value ?: continue
                    val value = map_get(initialized, key)
                    if (!map_contains_key(data, key)) {
                        // Do not copy constructor variables over existing variables!
                        map_set(data, key, value)
                    }
                    it_result = it.next()
                }
            } else if (initialized is PlatformList && data is PlatformList) {
                val end = list_get_length(initialized)
                var start = list_get_length(data)
                if (start < end) {
                    if (list_get_capacity(data) < end) list_set_capacity(data, end)
                    while (start < end) {
                        val value = list_get(initialized, start)
                        list_set(data, start, value)
                        start++
                    }
                }
            } else if (initialized is PlatformDataView) {
                TODO("Implement rebinding for platform data view")
            }
            reBind = false
        }
        this.data = data
        this._platformSymbol = symbol
        Symbols.set(data, symbol, this)
    }

    /**
     * Internally set before re-binding, will be cleared by [bind] method.
     * @since 3.0
     * @see PlatformType.proxy
     */
    internal var reBind: Boolean = false

    /**
     * Tests if this proxy is bound to an underlying object.
     * @return _true_ if the proxy is bound; _false_ otherwise.
     * @since 3.0
     */
    fun isBound(): Boolean = data != null

    /**
     * The [PlatformObject] to which this proxy is bound via the [platformSymbol].
     * - Java: `JvmList`, `JvmMap` or `JvmDataView`
     * - JavaScript: `Array`, `Map` or `DataView`
     * @since 3.0
     */
    open fun platformObject(): PlatformObject {
        var data = this.data
        if (data == null) {
            data = createData()
            bind(data, platformSymbol())
            onCreation()
        }
        return data
    }

    private var _platformType: PlatformType<*>? = null

    /**
     * The [PlatformType] of this proxy.
     * @since 3.0
     */
    open fun platformType(): PlatformType<*> {
        var type = _platformType
        if (type != null) return type
        type = Platform.forInstance(this)
        _platformType = type
        return type
    }

    /**
     * Function invoked after binding - can be used ie for initial data population.
     * @since 3.0
     */
    open fun onCreation(){ /* implement in subclass if needed */ }

    /**
     * The symbol though which this proxy is linked to the native object.
     */
    private var _platformSymbol: Symbol? = null

    /**
     * The symbol through which this proxy is bound to the [platformObject] object.
     * @since 3.0
     */
    open fun platformSymbol(): Symbol {
        var symbol = _platformSymbol
        if (symbol == null) {
            symbol = Symbols.of(platformType())
            _platformSymbol = symbol
        }
        return symbol
    }

    /**
     * Create a proxy or return the existing proxy.
     * @param type The proxy type.
     * @return The proxy instance.
     * @since 3.0
     */
    fun <T : Proxy> proxy(type: PlatformType<T>): T = type.proxy(this)

    /**
     * Box the given value into the given type.
     *
     * @param raw The raw value to convert.
     * @param alternative The alternative to return, when the raw value can't be converted.
     * @param init The initializer, when the raw value can't be converted, preferred above [alternative] if given.
     * @return The raw value as given type, the result of [init], or the given [alternative] (in that order).
     * @since 3.0
     */
    @JvmOverloads
    open fun <T> box(raw: Any?, type: PlatformType<T>, alternative: T? = null, init: Fn0<T?>? = null): T?
        = Platform.box(raw, type, alternative, init)

    /**
     * Box the given [raw] value for a key-value pair.
     *
     * So, provide a [raw] value, and box it into the given [type], where [raw] is the value assigned to the given [key].
     * @param raw the raw value to box.
     * @param key the key to forward to the [init] function.
     * @param type the type into which to box the value, expected return value.
     * @param init the initializer to produce an alternative result, when the raw-value can't be boxed into [type].
     * @return the given [raw] as [type], if [raw] can't be boxed into [type], the result of [init].
     * @since 3.0
     */
    @Suppress("UNCHECKED_CAST")
    open fun <T : Any, K : Any> boxPair(
        raw: Any?,
        type: PlatformType<out T>,
        key: K,
        init: Fn1<out T?, in K>
    ): T? {
        val data = Platform.unbox(raw) ?: return init.call(key)

        // The data value is a complex object
        if (isPlatformObject(data)) {
            // If a proxy is requested.
            if (type.isProxy()) return type.proxy(asPlatformObject(data))

            // A scalar type was requested, but a complex type found.
            // The only acceptable situation is that Any was requested.
            // Then, return the standard types.
            if (type == Any_TYPE) {
                if (data is PlatformMap) return AnyObject.TYPE.proxy(data) as T
                if (data is PlatformList) return AnyList.TYPE.proxy(data) as T
                if (data is PlatformDataView) return DataViewProxy.TYPE.proxy(data) as T
            }
        } else if (type.isInstance(data)) return data as T
        return init.call(key)
    }

    /**
     * Unboxes the given object so that the underlying native value is returned.
     * @param value The object to unbox.
     * @return The unboxed value.
     * @since 3.0
     */
    open fun unbox(value: Any?): Any? = Platform.unbox(value)

    /**
     * Internally called by setters to convert native objects to cross-platform variants.
     *
     * The result must be any of the following:
     * - `null`
     * - `Boolean`
     * - `Int`
     * - `Int64` - from `Long`
     * - `Double` - from `Float`
     * - `String` - from `CharSequence`
     * - `Array<*>`
     * - `PlatformList` - from `List<*,*>`
     * - `PlatformMap` - from `Map<*,*>`
     * - `ByteArray`
     * @param value The value to convert.
     * @param alternative The alternative to use, when the value can't be converted. This is not checked any further, if value is given again, it is returned as is.
     * @return the given `value` converted into platform object.
     * @see Platform.toPlatform
     */
    @JvmOverloads
    open fun toPlatform(value: Any?, alternative: Any? = value): Any? = Platform.toPlatform(value, alternative)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return platformObject() == Platform.unbox(other)
    }

    /**
     * Basically invokes [Platform.toJson].
     * @return this object as JSON string.
     */
    override fun toString(): String {
        return Platform.toJson(this)
    }

    /**
     * Create a copy of this object.
     *
     * @param recursive _true_ if this method should make a recursive copy; _false_ (default) and a shallow copy is made.
     * @return a (optionally recursive) copy.
     */
    @Suppress("UNCHECKED_CAST")
    @JvmOverloads
    fun <SELF : Proxy> copy(recursive: Boolean = false): SELF = platformType().proxy(Platform.copy(platformObject(), recursive)) as SELF

    /**
     * Recursively compare this object with another, checking for values instead of just referential. This is needed because for arrays, the == operation compares whether the arrays are the same object. This will work for any nested structures of maps, lists, and arrays.
     *
     * @param other The other proxy to compare against.
     * @return _true_, if this object and the given one are equal; _false_ otherwise.
     */
    fun contentDeepEquals(other: Proxy): Boolean = deepEquals(this, other)
}