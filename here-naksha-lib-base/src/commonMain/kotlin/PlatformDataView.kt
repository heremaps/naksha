@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.base

import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * A data-view that allows to read and mutate the bytes of a byte-array.
 */
@JsExport
@JsName("DataView")
interface PlatformDataView : PlatformObject
