@file:Suppress("OPT_IN_USAGE")

package naksha.base

import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

/**
 * Standard declaration of a list of strings.
 * @since 3.0
 */
@JsExport
open class StringList() : ListProxy<String>(String::class){

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

    companion object StringList_C {
        @JvmStatic
        fun fromList(strings: List<String>): StringList =
            StringList().apply { addAll(strings) }

        @JvmStatic
        fun of(vararg strings: String): StringList =
            StringList().apply { addAll(strings) }
    }
}

