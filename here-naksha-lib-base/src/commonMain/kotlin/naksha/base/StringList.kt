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
    @JvmOverloads
    constructor(vararg strings: String) : this() {
        addAll(strings)
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

