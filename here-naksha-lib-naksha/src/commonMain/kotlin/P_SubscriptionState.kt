@file:Suppress("OPT_IN_USAGE")
package com.here.naksha.lib.base

import kotlin.js.JsExport
<<<<<<<< HEAD:here-naksha-lib-base/src/commonMain/kotlin/P_AnyList.kt
|||||||| 717284f7d:here-naksha-lib-base/src/commonMain/kotlin/PArray.kt
import kotlin.js.JsName
========
import kotlin.js.JsName
import kotlin.jvm.JvmStatic
>>>>>>>> origin/v3:here-naksha-lib-naksha/src/commonMain/kotlin/P_SubscriptionState.kt

@JsExport
<<<<<<<< HEAD:here-naksha-lib-base/src/commonMain/kotlin/P_AnyList.kt
open class P_AnyList : P_List<Any>(Any::class)
|||||||| 717284f7d:here-naksha-lib-base/src/commonMain/kotlin/PArray.kt
@JsName("Array")
interface PArray
========
class P_SubscriptionState: P_Object() {
}
>>>>>>>> origin/v3:here-naksha-lib-naksha/src/commonMain/kotlin/P_SubscriptionState.kt
