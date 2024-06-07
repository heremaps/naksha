@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.base

import kotlin.js.JsExport

/**
 * The Naksha type for an object.
 */
@Suppress("unused")
@JsExport
open class P_Object : P_Map<String, Any>(String::class, Any::class)