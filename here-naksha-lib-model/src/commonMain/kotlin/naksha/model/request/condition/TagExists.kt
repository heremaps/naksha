@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.condition

import kotlin.js.JsExport
import kotlin.jvm.JvmField

/**
 * Tests if the tag with given name exists, ignoring the value.
 */
@JsExport
class TagExists(@JvmField var name: String) : TagQuery()
