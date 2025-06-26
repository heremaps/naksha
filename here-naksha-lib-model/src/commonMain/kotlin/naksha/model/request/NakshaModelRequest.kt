// This will be exposed
// - in JavaScript at the namespace: naksha.model.request.{name}
// - jn Java at the class naksha.model.request.NakshaModelRequestKt.{name}
@file:Suppress("OPT_IN_USAGE")

package naksha.model.request

import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformType
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * The package name `naksha.model.request`.
 * @since 3.0
 */
const val PACKAGE_NAME = "naksha.model.request"

/**
 * The [PlatformType] of [ResultFilter].
 * @since 3.0
 */
@JvmField
@JsStatic
val ResultFilter_TYPE = forKClass(ResultFilter::class).initialize()
