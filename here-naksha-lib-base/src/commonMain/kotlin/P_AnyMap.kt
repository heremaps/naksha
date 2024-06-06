@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.base

import kotlin.js.JsExport

@JsExport
open class P_AnyMap : P_Map<Any, Any>(Any::class, Any::class)
