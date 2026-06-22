@file:OptIn(ExperimentalJsExport::class)

package naksha.model.request.ops

import naksha.base.ListProxy
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@JsExport
class OpList : ListProxy<Op>(Op::class)