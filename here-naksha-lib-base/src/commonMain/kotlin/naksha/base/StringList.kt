@file:Suppress("OPT_IN_USAGE")

package naksha.base

import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.jvm.JvmStatic

/**
 * Standard declaration of a list of strings.
 * @since 3.0
 */
@JsExport
open class StringList() : PTypedArray<String>(String::class) {

    /**
     * Create an initialized string list.
     * @since 3.0
     */
    @JsName("fromStrings")
    constructor(vararg strings: String) : this() {
        addAll(strings)
    }

    /**
     * Adds the specified element to the end of this list.
     * @param element the element to add.
     * @return this.
     * @since 3.0
     */
    fun append(element: String?): StringList {
        super.add(element)
        return this
    }

    /**
     * Checks whether this list contains all supplied elements, order matters
     * @param elements Elements to check for presence
     * @return whether this list contains all elements
     */
    fun containsStringsInOrder(vararg elements: String): Boolean {
        if (elements.size != this.size) return false
        elements.forEachIndexed { index, element ->
            if (element != this[index]) return false
        }
        return true
    }

    companion object StringList_C {
        @JvmStatic
        fun fromList(strings: List<String>): StringList =
            StringList().apply { addAll(strings) }

        @JvmStatic
        fun of(vararg strings: String): StringList =
            StringList().apply { addAll(strings) }
    }
}

