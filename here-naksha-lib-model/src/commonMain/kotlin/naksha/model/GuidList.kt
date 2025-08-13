@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.ListProxy
import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformType
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * A list of [Guid]'s.
 *
 * **Warning**: A [Guid] is not serializable, and it is not possible to create it without parameters.
 */
@JsExport
class GuidList() : ListProxy<Guid>(Guid.TYPE) {
    /**
     * Create an initialized list of [Guid].
     * @param items the [Guid] to initialize the list with.
     */
    @JsName("GuidListOf")
    constructor(vararg items: Guid?) : this() {
        setCapacity(items.size)
        for (item in items) {
            add(item)
        }
    }

    companion object GuidList_C {
        /**
         * The [PlatformType] of [GuidList].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(GuidList::class).withPackageName(PACKAGE_NAME)
    }
}
