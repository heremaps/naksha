@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.auth.attribute

import naksha.base.P_Object
import kotlin.js.JsExport

/**
 * When accessing a resource, for each accessed resource one [ResourceAttributes] must be created that has
 * all attributes set.
 */
@JsExport
open class ResourceAttributes : P_Object()
