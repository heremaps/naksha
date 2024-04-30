@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.base

import kotlin.js.JsExport

@JsExport
abstract class BaseDataViewKlass<out T : BaseType> : BaseKlass<T>() {
    override fun isAbstract(): Boolean = false

    override fun isArray(): Boolean = false

    override fun getPlatformKlass(): Klass<PDataView> = dataViewKlass
}