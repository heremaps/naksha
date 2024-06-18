package naksha.base

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.reflect.KClass

/**
 * A not thread safe map, where the keys must not be _null_ and values must not be _undefined_. This map does guarantee the
 * insertion order of the keys, so when iterating above the object, the keys stay in order. This is kind an important if the
 * key order is significant, for example when calculating a hash.
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
@JsName("Map")
interface PlatformMap : PlatformObject