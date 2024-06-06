@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.auth.attribute

import com.here.naksha.lib.auth.action.AccessRightsAction
import com.here.naksha.lib.auth.action.AnyAction
import com.here.naksha.lib.base.P_Map
import kotlin.js.JsExport

/**
 * A service is a map where the keys are the action names, and the values are the attributes for the actions.
 */
@JsExport
open class AccessRightsService : P_Map<String, AccessRightsAction<*,*>>(String::class, AnyAction::class) {

}
