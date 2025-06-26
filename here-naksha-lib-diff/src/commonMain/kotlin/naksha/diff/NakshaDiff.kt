// This will be exposed
// - in JavaScript at the namespace: naksha.diff.{name}
// - jn Java at the class naksha.diff.NakshaDiffKt.{name}
@file:Suppress("OPT_IN_USAGE")

package naksha.diff

import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformType
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * The package of `lib-diff`.
 *
 * @since 3.0
 */
const val PACKAGE_NAME = "naksha.diff"

/**
 * The [PlatformType] for [Difference].
 * @since 3.0
 */
@JvmField
@JsStatic
val Difference_TYPE = forKClass(Difference::class).withPackageName(PACKAGE_NAME)

/**
 * The [PlatformType] for [DiffContext].
 * @since 3.0
 */
@JvmField
@JsStatic
val DiffContext_TYPE = forKClass(DiffContext::class).withPackageName(PACKAGE_NAME)
