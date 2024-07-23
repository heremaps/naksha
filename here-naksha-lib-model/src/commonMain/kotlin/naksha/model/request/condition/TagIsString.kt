@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.condition

import kotlin.js.JsExport
import kotlin.jvm.JvmField

/**
 * Tests if the tag with the name equals to the given string value.
 * @property name the tag name.
 * @property value the value of the tag.
 */
@JsExport
class TagIsString(@JvmField var name: String, @JvmField var value: String)
