@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.nak

import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * Symbols represent namespaces that can be attached to platform objects to manage multi-platform data models and business logic.
 * The symbols coexist with the real data, but are not serialized or deserialized, they are meta-data added to the in-memory data.
 */
@JsExport
@JsName("Symbol")
interface PSymbol
