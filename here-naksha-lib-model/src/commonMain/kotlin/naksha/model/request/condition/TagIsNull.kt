@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.condition

import kotlin.js.JsExport
import kotlin.jvm.JvmField

/**
 * Tests if the tag is assigned to the value _null_.
 */
@JsExport
class TagIsNull(@JvmField var name: String)