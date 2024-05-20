@file:Suppress("OPT_IN_USAGE")
package com.here.naksha.lib.base

import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.jvm.JvmStatic

@JsExport
class NakTransaction(vararg args: Any?) : NakFeature(*args) {

    @JsName("NakTransactionById")
    constructor(id: String) : this() {
        setId(id)
    }

    companion object {
        @JvmStatic
        val klass = object : BaseObjectKlass<NakTransaction>() {
            override fun isInstance(o: Any?): Boolean = o is NakTransaction

            override fun newInstance(vararg args: Any?): NakTransaction = NakTransaction(*args)
        }

        @JvmStatic
        val MODIFIED_FEATURE_COUNT = Base.intern("modifiedFeatureCount")

        @JvmStatic
        val COLLECTION_COUNTERS = Base.intern("collectionCounters")

        @JvmStatic
        val SEQ_NUMBER = Base.intern("seqNumber")
    }


    fun getModifiedFeatureCount(): Int = getOrCreate(MODIFIED_FEATURE_COUNT, Klass.intKlass)

    fun setModifiedFeatureCount(value: Int) = set(MODIFIED_FEATURE_COUNT, value)

    fun addModifiedCount(count: Int) {
        setModifiedFeatureCount(getModifiedFeatureCount() + count)
    }

    fun getCollectionCounters(): CollectionCounters = getOrCreate(COLLECTION_COUNTERS, CollectionCounters.klass)
    fun setCollectionCounters(value: CollectionCounters) = set(COLLECTION_COUNTERS, value)

    fun addCollectionCounts(collection: String, count: Int) {
        val collectionCounters = getCollectionCounters()
        if (!collectionCounters.containsKey(collection)) {
            collectionCounters.put(collection, count)
        } else {
            val oldCount: Int = collectionCounters[collection]!!
            collectionCounters.put(collection, oldCount + count)
        }
        setCollectionCounters(collectionCounters)
    }
}