@file:Suppress("OPT_IN_USAGE")

package naksha.base

import naksha.base.Platform.PlatformCompanion.forKClass
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic

/**
 * Standard declaration of a list of strings.
 * @since 3.0
 */
@JsExport
open class StringList() : ListProxy<String>(String_TYPE){

    /**
     * Create an initialized string list.
     * @since 3.0
     */
    @JsName("of")
    constructor(vararg strings: String?) : this() {
        addAll(strings)
    }

    /**
     * Adds the specified element to the end of this list.
     * @param element the element to add.
     * @return this.
     * @since 3.0
     */
    fun append(element: String?): StringList {
        add(element)
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

    /**
     * Adds all given strings to the end of this list.
     * @param element the strings to add.
     * @return this.
     * @since 3.0
     */
    fun appendAll(vararg element: String?): StringList {
        addAll(element)
        return this
    }

    companion object StringListCompanion {
        /**
         * The [PlatformType] of [StringList].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE: PlatformType<StringList> = forKClass(StringList::class).withPackageName(PACKAGE_NAME)

        @JvmStatic
        fun fromList(strings: List<String>): StringList =
            StringList().apply { addAll(strings) }

        @JvmStatic
        fun of(vararg strings: String): StringList =
            StringList().apply { addAll(strings) }
    }
}

