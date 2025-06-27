@file:Suppress("OPT_IN_USAGE")

package naksha.model.objects

import naksha.base.*
import naksha.base.NakshaError
import naksha.base.Platform.Platform_C.forKClass
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

// TODO: Improve and document me!

/**
 * TODO
 * @since 3.0
 * @see NakshaObject
 * @see NakshaStorage
 * @see NakshaMap
 * @see NakshaCollection
 * @see NakshaDictionary
 * @see NakshaSubscriptionState
 * @see NakshaTx
 */
@JsExport
open class NakshaSubscriptionState() : AnyObject() {

    @JsName("of")
    constructor(id: String) : this() {
        set("id", id)
    }

    companion object NakshaSubscriptionState_C {
        /**
         * The [PlatformType] of [NakshaSubscriptionState].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(NakshaSubscriptionState::class)
            .withPackageName(PACKAGE_NAME)
            .withJsonType("naksha.SubscriptionState")

        private val ID = NotNullProperty<NakshaSubscriptionState, String>(String_TYPE) { _, _ -> PlatformUtil.randomString() }
        private val INT_0 = NotNullProperty<NakshaSubscriptionState, Int>(Int_TYPE) { _, _ -> 0 }
        private val INT64_0 = NotNullProperty<NakshaSubscriptionState, Int64>(Int64_TYPE) { _, _ -> Int64(0) }
        private val ERROR_NULL = NullableProperty<NakshaSubscriptionState, NakshaError>(NakshaError.TYPE)
        private val ANY_OBJECT = NotNullProperty<NakshaSubscriptionState, AnyObject>(AnyObject.TYPE)
    }

    /**
     * The unique identifier of the subscription state.
     */
    val id by ID
    var callCount by INT_0
    var errCount by INT_0
    var lastError by ERROR_NULL
    val handlerStates by ANY_OBJECT
    val seqNumber by INT64_0
    val uid by INT_0
}
