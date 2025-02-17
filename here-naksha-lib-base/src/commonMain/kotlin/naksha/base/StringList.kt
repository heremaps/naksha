@file:Suppress("OPT_IN_USAGE")

package naksha.base

import kotlin.js.JsExport
import kotlin.jvm.JvmStatic

/**
 * Standard declaration of a list of strings.
 */
@JsExport
open class StringList : ListProxy<String>(String::class){

    companion object StringList_C {
        @JvmStatic
        fun fromList(strings: List<String>): StringList =
            StringList().apply { addAll(strings) }

        @JvmStatic
        fun of(vararg strings: String): StringList =
            StringList().apply { addAll(strings) }
    }
}

