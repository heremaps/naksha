@file:OptIn(ExperimentalJsExport::class)

package naksha.model.request.ops

import naksha.base.PTypedArray
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@JsExport
class SpTransformationList : PTypedArray<SpTransformation>(SpTransformation::class)
