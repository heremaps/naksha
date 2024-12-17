@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.query

import kotlin.js.JsExport

/**
 * Marker interface for metadata queries.
 * @see MetaAnd
 * @see MetaOr
 * @see MetaNot
 * @see MetaQuery
 */
@JsExport
interface IMetaQuery : IQuery