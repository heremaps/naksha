@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.condition

import kotlin.js.JsExport
import kotlin.jvm.JvmField

/**
 * Tests if the tag with the given name is assigned to the given boolean value.
 */
@JsExport
class TagIsBool(@JvmField var name: String, @JvmField var value: Boolean)