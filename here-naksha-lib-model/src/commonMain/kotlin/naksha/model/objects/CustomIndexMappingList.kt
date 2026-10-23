@file:Suppress("OPT_IN_USAGE")

package naksha.model.objects

import naksha.base.ListProxy
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * A list of [CustomIndexMapping]s of a [NakshaCollection].
 * @since 3.0
 */
@JsExport
open class CustomIndexMappingList() : ListProxy<CustomIndexMapping>(CustomIndexMapping::class) {

    /**
     * Construct a list from a vararg of mappings.
     * @since 3.0
     */
    @JsName("of")
    constructor(vararg mappings: CustomIndexMapping) : this() {
        for (mapping in mappings) add(mapping)
    }
}
