package naksha.model

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

// FIXME TODO move it to proper library

@OptIn(ExperimentalJsExport::class)
@JsExport
class NakshaContext(
    val su: Boolean,
    val appId: String,
    val author: String?
//    val urm:UserRequestMatrix // FIXME
) {

}