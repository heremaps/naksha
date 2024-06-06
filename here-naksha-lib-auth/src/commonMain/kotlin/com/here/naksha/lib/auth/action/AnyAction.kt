@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.auth.action

import com.here.naksha.lib.auth.attribute.ResourceAttributes
import kotlin.js.JsExport

/**
 * Default type to represent any arbitrary action.
 */
@JsExport
class AnyAction : AccessRightsAction<ResourceAttributes, AnyAction>(ResourceAttributes::class)
