@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.nak

import kotlin.js.JsExport

@JsExport
abstract class NakObjectKlass<out T : NakObject> : NakMapKlass<Any?, T>()
