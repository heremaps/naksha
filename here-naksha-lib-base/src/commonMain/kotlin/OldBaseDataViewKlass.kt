@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.base

import kotlin.js.JsExport

@JsExport
abstract class OldBaseDataViewKlass<out T : P> : OldBaseKlass<T>() {
    override fun isAbstract(): Boolean = false

    override fun isArray(): Boolean = false

    override fun getPlatformKlass(): Klass<N_DataView> = dataViewKlass
}