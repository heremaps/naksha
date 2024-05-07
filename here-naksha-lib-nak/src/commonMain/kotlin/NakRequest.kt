package com.here.naksha.lib.base

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.jvm.JvmStatic

/**
 * Base request class.
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
abstract class NakRequest(vararg args: Any?) : BaseObject(*args) {

    companion object {
        @JvmStatic
        val NO_FEATURE = Base.intern("noFeature")
        @JvmStatic
        val NO_GEOMETRY = Base.intern("noGeometry")
        @JvmStatic
        val NO_TAGS = Base.intern("noTags")
        @JvmStatic
        val NO_META = Base.intern("noMeta")
        @JvmStatic
        val RESULT_FILTER = Base.intern("resultFilter")
    }

    open fun isNoFeature(): Boolean = toElement(get(NO_FEATURE), Klass.boolKlass, false)!!

    open fun setNoFeature(value: Boolean) = set(NO_FEATURE, value)

    open fun isNoGeometry(): Boolean = toElement(get(NO_GEOMETRY), Klass.boolKlass, false)!!

    open fun setNoGeometry(value: Boolean) = set(NO_GEOMETRY, value)

    open fun isNoMeta(): Boolean = toElement(get(NO_META), Klass.boolKlass, false)!!

    open fun setNoMeta(value: Boolean) = set(NO_META, value)

    open fun isNoTags(): Boolean = toElement(get(NO_TAGS), Klass.boolKlass, false)!!

    open fun setNoTags(value: Boolean) = set(NO_TAGS, value)
}