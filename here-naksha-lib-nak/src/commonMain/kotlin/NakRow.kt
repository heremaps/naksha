package com.here.naksha.lib.base

import com.here.naksha.lib.nak.Flags
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.jvm.JvmStatic

@OptIn(ExperimentalJsExport::class)
@JsExport
class NakRow(vararg args: Any?) : BaseObject(*args) {

    companion object {
        @JvmStatic
        val klass = object : BaseObjectKlass<NakRow>() {
            override fun isInstance(o: Any?): Boolean = o is NakRow

            override fun newInstance(vararg args: Any?): NakRow = NakRow(*args)
        }

        @JvmStatic
        val ID = Base.intern("id")

        @JvmStatic
        val TYPE = Base.intern("type")

        @JvmStatic
        val FLAGS = Base.intern("flags")

        @JvmStatic
        val META = Base.intern("meta")

        @JvmStatic
        val FEATURE = Base.intern("feature")

        @JvmStatic
        val TAGS = Base.intern("tags")

        @JvmStatic
        val GEO = Base.intern("geo")

        @JvmStatic
        val GEO_REF = Base.intern("geoRef")
    }

    fun getId(): String? = toElement(get(ID), Klass.stringKlass)

    fun setId(value: String) = set(ID, value)

    fun getType(): String? = toElement(get(TYPE), Klass.stringKlass)

    fun setType(value: String) = set(TYPE, value)

    fun getFlags(): Int? = toElement(get(FLAGS), Klass.intKlass)

    fun setFlags(value: Int?) = set(FLAGS, value)

    fun getFlagsObject(): Flags = Flags(getFlags())

    fun getMeta(): PDataView? = toElement(get(META), Klass.dataViewKlass)

    fun setMeta(value: PDataView?) = set(META, value)

    @JsName("setMetaBytes")
    fun setMeta(value: ByteArray) = set(META, Klass.dataViewKlass.newInstance(value))

    fun getFeature(): PDataView? = toElement(get(FEATURE), Klass.dataViewKlass)

    fun setFeature(value: PDataView?) = set(FEATURE, value)

    @JsName("setFeatureBytes")
    fun setFeature(value: ByteArray) = set(FEATURE, Klass.dataViewKlass.newInstance(value))

    fun getTags(): PDataView? = toElement(get(TAGS), Klass.dataViewKlass)

    fun setTags(value: PDataView?) = set(TAGS, value)

    @JsName("setTagsBytes")
    fun setTags(value: ByteArray) = set(TAGS, Klass.dataViewKlass.newInstance(value))

    fun getGeo(): PDataView? = toElement(get(GEO), Klass.dataViewKlass)

    fun setGeo(value: PDataView?) = set(GEO, value)

    @JsName("setGeoBytes")
    fun setGeo(value: ByteArray) = set(GEO, Klass.dataViewKlass.newInstance(value))

    fun getGeoRef(): PDataView? = toElement(get(GEO_REF), Klass.dataViewKlass)

    fun setGeoRef(value: PDataView?) = set(GEO_REF, value)

    @JsName("setGeoRefBytes")
    fun setGeoRef(value: ByteArray) = set(GEO_REF, Klass.dataViewKlass.newInstance(value))
}