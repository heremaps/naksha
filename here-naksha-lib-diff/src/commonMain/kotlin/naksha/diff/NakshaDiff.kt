// This will be exposed
// - in JavaScript at the namespace: naksha.diff.{name}
// - jn Java at the class naksha.diff.NakshaDiffKt.{name}
package naksha.diff

import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformType

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
val DIFFERENCE = forKClass(Difference::class).withPackageName(PACKAGE_NAME)

/**
 * The [PlatformType] for [DiffContext].
 * @since 3.0
 */
val DIFF_CONTEXT = forKClass(DiffContext::class).withPackageName(PACKAGE_NAME)
