@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.condition

import kotlin.js.JsExport
import kotlin.jvm.JvmField

/**
 * A property operation. The storage manages [rows][naksha.model.Row], and every row has fields. Each field has a specific content, and can be queried. Some fields are structured, in that case the path is required.
 * @property field the row field to access.
 * @property op the operation to execute.
 * @property value the parameter value of the operation.
 */
@JsExport
class Query(
    @JvmField var field: Property,
    @JvmField var op: QueryOp,
    @JvmField var value: Any? = null
) : IQuery<Query>
