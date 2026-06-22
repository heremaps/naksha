@file:OptIn(ExperimentalJsExport::class)

package naksha.model.request.ops

import naksha.base.ListProxy
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@JsExport
class SpTransformationList : ListProxy<SpTransformation>(SpTransformation::class)
