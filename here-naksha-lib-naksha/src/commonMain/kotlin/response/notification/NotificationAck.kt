package com.here.naksha.lib.base.response

import com.here.naksha.lib.base.BaseMap
import com.here.naksha.lib.base.Int64
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
class NotificationAck(
    val seqNumber: Int64,
    val uid: Int,
    val handlerStates: BaseMap<Any?>
) : Response(SUCCESS_TYPE) {
    override fun size(): Int = 0
}