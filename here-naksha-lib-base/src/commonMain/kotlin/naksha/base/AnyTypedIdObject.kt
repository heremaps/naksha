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

        const val ID_KEY = "id"
        init { initialize() }
    }

    /**
     * The unique identifier of the feature.
     * @since 3.0
     * @see get_id
     * @see set_id
     */
    @KT_68775_infinite_loop_for_calling_super_getter
    var id: String
        get() = get_id()
        set(value) {
            set_id(value)
        }

    @KT_68775_infinite_loop_for_calling_super_getter
    protected open fun get_id(): String {
        val raw = getRaw(ID_KEY)
        if (raw is String) return raw
        val id = randomString()
        setRaw(ID_KEY, id)
        return id
    }

    @KT_68775_infinite_loop_for_calling_super_getter
    protected open fun set_id(id: String) {
        setRaw(ID_KEY, id)
    }
}