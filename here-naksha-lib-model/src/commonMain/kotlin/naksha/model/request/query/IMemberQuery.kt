@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.query

import kotlin.js.JsExport

/**
 * Marker interface for member queries.
 * @see MemberAnd
 * @see MemberOr
 * @see MemberNot
 * @see MemberQuery
 */
@JsExport
interface IMemberQuery : IQuery
