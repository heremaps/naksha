@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.base

import kotlin.js.JsExport

@JsExport
open class P_JsMap : P_Map<String, Any>(String::class, Any::class)
