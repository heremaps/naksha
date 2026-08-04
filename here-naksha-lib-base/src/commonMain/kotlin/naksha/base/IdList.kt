@file:Suppress("OPT_IN_USAGE")

package naksha.base

import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * Standard declaration of a list of strings.
 * @since 3.0
 */
@JsExport
open class IdList() : PTypedArray<Id>(Id::class) {

    /**
     * Create an initialized string list.
     * @since 3.0
     */
    @JsName("newIdListFromId")
    constructor(vararg ids: Id?) : this() {
        addAll(ids)
    }

    /**
     * Create an initialized string list.
     * @since 3.0
     */
    @JsName("newIdListFromString")
    constructor(vararg ids: String?) : this() {
        setCapacity(ids.size)
        for (id in ids) if (id != null) add(Id(id)) else add(null)
    }

    /**
     * Create an initialized string list.
     * @since 3.0
     */
    @JsName("newIdListFromBigInt")
    constructor(vararg ids: Long?) : this() {
        setCapacity(ids.size)
        for (id in ids) if (id != null) add(Id(id)) else add(null)
    }

    /**
     * Create an initialized [Id] list.
     * @param ids either a list of [Int64], [Long], [String], or [Id].
     * @since 3.0
     */
    @JsName("newIdListFromList")
    constructor(ids: List<*>) : this() {
        setCapacity(ids.size)
        for (id in ids) {
            when (id) {
                is Id -> add(id)
                is String -> add(Id(id))
                is Long -> add(Id(id))
                is Int -> add(Id(id.toLong()))
                is Number -> {
                    val v = id.toLong()
                    if (id == v) add(Id(v))
                }
                is CharSequence -> add(Id(id.toString()))
            }
        }
    }

    /**
     * Adds the specified element to the end of this list.
     * @param element the element to add.
     * @return this.
     * @since 3.0
     */
    fun append(element: Id?): IdList {
        super.add(element)
        return this
    }

    /**
     * Checks whether this list contains all supplied elements, order matters
     * @param elements Elements to check for presence
     * @return whether this list contains all elements
     */
    fun containsStringsInOrder(vararg elements: Id): Boolean {
        if (elements.size != this.size) return false
        elements.forEachIndexed { index, element ->
            if (element != this[index]) return false
        }
        return true
    }
}

