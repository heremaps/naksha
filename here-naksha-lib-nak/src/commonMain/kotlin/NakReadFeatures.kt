package com.here.naksha.lib.base

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.jvm.JvmStatic

@OptIn(ExperimentalJsExport::class)
@JsExport
class NakReadFeatures(vararg args: Any?) : NakReadRequest(*args) {

    companion object {
        @JvmStatic
        val klass = object : BaseObjectKlass<NakReadFeatures>() {
            override fun isInstance(o: Any?): Boolean = o is NakReadFeatures

            override fun newInstance(vararg args: Any?): NakReadFeatures = NakReadFeatures(*args)
        }

        @JvmStatic
        val COLLECTION_IDS = Base.intern("collectionIds")
        @JvmStatic
        val QUERY_DELETED = Base.intern("queryDeleted")
        @JvmStatic
        val QUERY_HISTORY = Base.intern("queryHistory")
        @JvmStatic
        val LIMIT_VERSIONS = Base.intern("limitVersions")
        @JvmStatic
        val RETURN_HANDLE = Base.intern("returnHandle")
        @JvmStatic
        val ORDER_BY = Base.intern("orderBy")
    }

    fun isQueryDeleted(): Boolean = toElement(get(QUERY_DELETED), Klass.boolKlass, false)!!

    fun setQueryDeleted(value: Boolean) = set(QUERY_DELETED, value)

    fun isQueryHistory(): Boolean = toElement(get(QUERY_HISTORY), Klass.boolKlass, false)!!

    fun setQueryHistory(value: Boolean) = set(QUERY_HISTORY, value)

    fun getLimitVersions(): Int = toElement(get(LIMIT_VERSIONS), Klass.intKlass, 1)!!

    fun setLimitVersions(value: Int?) = set(LIMIT_VERSIONS, value)

    fun isReturnHandle(): Boolean = toElement(get(RETURN_HANDLE), Klass.boolKlass, false)!!

    fun setReturnHandle(value: Boolean) = set(RETURN_HANDLE, value)

    fun getOrderBy(): String? = toElement(get(ORDER_BY), Klass.stringKlass)

    fun setOrderBy(value: String?) = set(ORDER_BY, value)

    fun setCollectionIds(values: BaseList<String>) = set(COLLECTION_IDS, values)

    @Suppress("UNCHECKED_CAST")
    fun getCollectionIds(): BaseList<String> = toElement(get(COLLECTION_IDS), BaseList.klass, BaseList())!! as BaseList<String>
}