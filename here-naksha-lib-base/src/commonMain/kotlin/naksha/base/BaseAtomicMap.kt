@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "OPT_IN_USAGE")

package naksha.base

import kotlin.js.JsExport

/**
 * A thread-safe (atomic) variant of [BaseMap].
 *
 * On the JVM this is backed by a [java.util.concurrent.ConcurrentHashMap] and all mutating
 * operations are truly atomic. `null` values are supported via an internal sentinel.
 * In JavaScript, where execution is single-threaded, this is identical to [BaseMap].
 * @since 3.0
 */
@JsExport
expect class BaseAtomicMap() : BaseMap
