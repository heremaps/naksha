@file:OptIn(ExperimentalJsExport::class)

package com.here.naksha.lib.jbon

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * A mapper for text, which are strings that contain references to other strings.
 */
@JsExport
class JbText : JbObjectMapper<JbText>() {
    override fun parseHeader(mandatory: Boolean) {

    }
}