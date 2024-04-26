@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.nak

import kotlin.js.JsExport

@JsExport
abstract class NakDataViewKlass<out T : NakType> : NakKlass<T>() {
    override fun isAbstract(): Boolean = false

    override fun isArray(): Boolean = false

    override fun getPlatformKlass(): Klass<PDataView> = dataViewKlass
}