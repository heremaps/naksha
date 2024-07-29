@file:Suppress("OPT_IN_USAGE")

package naksha.auth.attribute

import naksha.base.AnyObject
import kotlin.js.JsExport

/**
 * When accessing a resource, for each accessed resource one [ResourceAttributes] must be created that has
 * all attributes set.
 */
@JsExport
open class ResourceAttributes : AnyObject()
