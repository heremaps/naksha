@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.query

import kotlin.js.JsExport

/**
 * Marker interface for metadata queries.
 * @see IQuery
 * @see IMetaQuery
 * @see MetaQuery
 * @see MetaAnd
 * @see MetaOr
 * @see MetaNot
 */
@JsExport
interface IMetaQuery : IQuery
