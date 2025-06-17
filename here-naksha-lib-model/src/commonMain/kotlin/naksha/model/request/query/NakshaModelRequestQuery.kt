// This will be exposed
// - in JavaScript at the namespace: naksha.model.request.query.{name}
// - jn Java at the class naksha.model.request.query.NakshaModelRequestQueryKt.{name}
package naksha.model.request.query

import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformType

/**
 * The package name `naksha.model.request.query`.
 * @since 3.0
 */
const val PACKAGE_NAME = "naksha.model.request.query"

/**
 * The [PlatformType] of [IMetaQuery].
 * @since 3.0
 */
val IMetaQuery_TYPE = forKClass(IMetaQuery::class)

/**
 * The [PlatformType] of [IPropertyQuery].
 * @since 3.0
 */
val IPropertyQuery_TYPE = forKClass(IPropertyQuery::class)

/**
 * The [PlatformType] of [IQuery].
 * @since 3.0
 */
val IQuery_TYPE = forKClass(IQuery::class)

/**
 * The [PlatformType] of [ISpatialQuery].
 * @since 3.0
 */
val ISpatialQuery_TYPE = forKClass(ISpatialQuery::class)

/**
 * The [PlatformType] of [ITagQuery].
 * @since 3.0
 */
val ITagQuery_TYPE = forKClass(ITagQuery::class)
