package com.here.naksha.lib.auth

import com.here.naksha.lib.auth.attribute.ResourceAttributes
import com.here.naksha.lib.base.P_List
import kotlin.js.JsExport

@JsExport
open class AccessAttributesList : P_List<ResourceAttributes>(ResourceAttributes::class)