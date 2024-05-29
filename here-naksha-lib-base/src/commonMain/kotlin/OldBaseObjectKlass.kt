@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.base

import kotlin.js.JsExport

@JsExport
abstract class OldBaseObjectKlass<out T : P_Object> : OldBasePairsKlass<Any?, T>()
