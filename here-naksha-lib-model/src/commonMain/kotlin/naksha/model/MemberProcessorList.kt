@file:Suppress("OPT_IN_USAGE")

package naksha.model

import kotlin.js.JsExport

/**
 * An ordered list of [IMemberProcessor] instances registered for a single member name.
 *
 * Processors are invoked in the order in which they were added. This class extends [ArrayList]
 * to allow direct iteration and indexed access.
 * @since 3.0
 */
@JsExport
open class MemberProcessorList(private val delegate: MutableList<IMemberProcessor> = ArrayList()) : MutableList<IMemberProcessor> by delegate
