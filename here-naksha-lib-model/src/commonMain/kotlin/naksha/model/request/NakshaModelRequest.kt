// This will be exposed
// - in JavaScript at the namespace: naksha.model.request.{name}
// - jn Java at the class naksha.model.request.NakshaModelRequestKt.{name}
package naksha.model.request

import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformType

/**
 * The package name `naksha.model.request`.
 * @since 3.0
 */
const val PACKAGE_NAME = "naksha.model.request"

/**
 * The [PlatformType] of [ResultFilter].
 * @since 3.0
 */
val ResultFilter_TYPE = forKClass(ResultFilter::class).initialize()
