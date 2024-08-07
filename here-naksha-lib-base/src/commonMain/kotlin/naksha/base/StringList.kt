@file:Suppress("OPT_IN_USAGE")

package naksha.base

import kotlin.js.JsExport

/**
 * Standard declaration of a list of strings.
 */
@JsExport
open class StringList : ListProxy<String>(String::class)

