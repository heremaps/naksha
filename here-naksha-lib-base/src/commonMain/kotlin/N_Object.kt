@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.base

import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * The native object type, which is the base for [N_DataView], [N_Array], [N_Map], [N_ConcurrentMap] and [Proxy]. Note that
 * dependent on the platform, many other objects may extend native object as well, therefore you should never test if something is an
 * instance of [N_Object], because this may not be what you expect!
 * <p>
 * All native objects will have members and properties. Properties are serialized, when an object is serialized that is no [N_Map],
 * [N_ConcurrentMap] or a [N_Array], while members are hidden.
 */
@JsExport
@JsName("Object")
interface N_Object
