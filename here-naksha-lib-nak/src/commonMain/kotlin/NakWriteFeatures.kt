package com.here.naksha.lib.base

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.jvm.JvmStatic

@OptIn(ExperimentalJsExport::class)
@JsExport
class NakWriteFeatures(vararg args: Any?) : NakWriteRequest(*args) {

    @JsName("NakReadFeaturesForCollection")
    constructor(collectionId: String) : this() {
        setCollectionId(collectionId)
    }

    companion object {
        @JvmStatic
        val klass = object : BaseObjectKlass<NakWriteFeatures>() {
            override fun isInstance(o: Any?): Boolean = o is NakWriteFeatures

            override fun newInstance(vararg args: Any?): NakWriteFeatures = NakWriteFeatures(*args)
        }

        @JvmStatic
        val COLLECTION_ID = Base.intern("collectionId")
    }

    fun setCollectionId(value: String) = set(COLLECTION_ID, value)

    fun getCollectionId(): String = toElement(get(COLLECTION_ID), Klass.stringKlass)!!
}