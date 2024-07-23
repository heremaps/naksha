@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.condition

import kotlin.js.JsExport
import kotlin.jvm.JvmField

/**
 * Tests if the value of the tag with the given name is a double, and if it is, compares it against the given value.
 * @param name the tag name.
 * @param op if the value is a double, the operation to be used to compare against the given value.
 * @param value the value against which to compare using the given op.
 */
@JsExport
class TagIsDouble(@JvmField var name: String, @JvmField var op: QueryNumber, @JvmField var value: Double)