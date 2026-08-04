@file:Suppress("OPT_IN_USAGE")

package naksha.model.objects

import naksha.base.*
import naksha.base.NakshaError
import kotlin.js.JsExport
import kotlin.js.JsName

// TODO: Improve and document me!

@JsExport
open class NakshaSubscriptionState() : PAnyMap() {

    @JsName("of")
    constructor(id: String) : this() {
        setRaw("id", id)
    }

    companion object {
        private val ID = NotNullProperty<NakshaSubscriptionState, String>(String::class) { _, _ -> BaseUtil.randomAtoZ() }
        private val INT_0 = NotNullProperty<NakshaSubscriptionState, Int>(Int::class) { _, _ -> 0 }
        private val INT64_0 = NotNullProperty<NakshaSubscriptionState, Int64>(Int64::class) { _, _ -> Int64(0) }
        private val ERROR_NULL = NullableProperty<NakshaSubscriptionState, NakshaError>(NakshaError::class)
        private val ANY_OBJECT = NotNullProperty<NakshaSubscriptionState, PAnyMap>(PAnyMap::class)
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
