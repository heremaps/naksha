@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.base

import kotlin.js.JsExport

/**
 * The base class for all Naksha map types.
 */
@JsExport
abstract class BaseMapKlass<E, out T : BaseMap<E>> : BasePairsKlass<E, T>()
