package naksha.base

import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformUtil.PlatformUtil_C.randomString
import naksha.base.bugs.KT_68775_infinite_loop_for_calling_super_getter
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * The base variant of an object with `id` and `type`, keys being [String], and values can be anything.
 *
 * @see DataViewProxy
 * @see AnyList
 * @see AnyMap
 * @see AnyObject
 * @see AnyTypedObject
 * @see AnyTypedIdObject
 */
@Suppress("unused", "OPT_IN_USAGE")
@JsExport
open class AnyTypedIdObject : AnyTypedObject() {

    companion object AnyTypedIdObject_C {
        /**
         * The [PlatformType] of [AnyTypedIdObject].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(AnyTypedIdObject::class).withPackageName(PACKAGE_NAME)

        init { initialize() }
    }
    
    /**
     * The unique identifier of the feature.
     * @since 3.0
     */
    @KT_68775_infinite_loop_for_calling_super_getter
    open var id: String
        get() = id_get()
        set(value) {
            id_set(value)
        }

    @KT_68775_infinite_loop_for_calling_super_getter
    protected open fun id_get(): String {
        val raw = getRaw("id")
        if (raw is String) return raw
        val id = randomString()
        setRaw("id", id)
        return id
    }

    @KT_68775_infinite_loop_for_calling_super_getter
    protected open fun id_set(id: String) {
        setRaw("id", id)
    }

    open fun withId(id: String): AnyTypedIdObject {
        this.id = id
        return this
    }
}