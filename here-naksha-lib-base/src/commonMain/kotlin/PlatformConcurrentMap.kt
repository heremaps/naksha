@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.base

import kotlin.js.JsExport

/**
 * A thread safe map, where the keys must not be _null_ and values must not be _undefined_. Note that JavaScript does not support
 * concurrency by design, therefore the underlying platform object will be an instance of
 * [Map](https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Map).
 */
@JsExport
interface PlatformConcurrentMap : PlatformObject
