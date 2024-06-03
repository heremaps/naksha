@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.base

import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * The platform object, which is the base for [PlatformDataViewApi], [PlatformList], [PlatformMap], [PlatformConcurrentMap]
 * and [Proxy]. Note that dependent on the platform, many other objects may extend platform object as well, therefore you
 * should never test if something is an instance of [PlatformObject], because this may not result in what you expect!
 * <p>
 * All platform objects can store symbols, which will not be serialized and are hidden properties of objects.
 */
@JsExport
@JsName("Object")
interface PlatformObject
