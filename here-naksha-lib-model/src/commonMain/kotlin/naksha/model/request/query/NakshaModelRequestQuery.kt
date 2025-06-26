// This will be exposed
// - in JavaScript at the namespace: naksha.model.request.query.{name}
// - jn Java at the class naksha.model.request.query.NakshaModelRequestQueryKt.{name}
@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.query

import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformType
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * The package name `naksha.model.request.query`.
 * @since 3.0
 */
const val PACKAGE_NAME = "naksha.model.request.query"

/**
 * The [PlatformType] of [IMetaQuery].
 * @since 3.0
 */
@JvmField
@JsStatic
val IMetaQuery_TYPE = forKClass(IMetaQuery::class)

/**
 * The [PlatformType] of [IPropertyQuery].
 * @since 3.0
 */
@JvmField
@JsStatic
val IPropertyQuery_TYPE = forKClass(IPropertyQuery::class)

/**
 * The [PlatformType] of [IQuery].
 * @since 3.0
 */
@JvmField
@JsStatic
val IQuery_TYPE = forKClass(IQuery::class)

/**
 * The [PlatformType] of [ISpatialQuery].
 * @since 3.0
 */
@JvmField
@JsStatic
val ISpatialQuery_TYPE = forKClass(ISpatialQuery::class)

/**
 * The [PlatformType] of [ITagQuery].
 * @since 3.0
 */
@JvmField
@JsStatic
val ITagQuery_TYPE = forKClass(ITagQuery::class)
