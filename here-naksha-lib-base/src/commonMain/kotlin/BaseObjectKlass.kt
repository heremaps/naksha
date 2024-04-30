@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.base

import kotlin.js.JsExport

@JsExport
abstract class BaseObjectKlass<out T : BaseObject> : BasePairsKlass<Any?, T>()
