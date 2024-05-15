@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.base

import kotlin.js.JsExport
import kotlin.jvm.JvmStatic

/**
 * The XYZ namespace stored in the `@ns:com:here:xyz` property of the [NakFeature].
 */
@JsExport
class NakXyz(vararg args: Any?) : BaseObject(*args) {
    companion object {
        @JvmStatic
        val klass = object : BaseObjectKlass<NakXyz>() {
            override fun isInstance(o: Any?): Boolean = o is NakXyz

            override fun newInstance(vararg args: Any?): NakXyz = NakXyz()

        }

        @JvmStatic
        val ACTION = Base.intern("action")

        @JvmStatic
        val CREATE = Base.intern("CREATE")

        @JvmStatic
        val UPDATE = Base.intern("UPDATE")

        @JvmStatic
        val DELETE = Base.intern("DELETE")

        @JvmStatic
        val TXN_NEXT = Base.intern("txnNext")

        @JvmStatic
        val TXN = Base.intern("txn")

        @JvmStatic
        val PTXN = Base.intern("ptxn")

        @JvmStatic
        val UID = Base.intern("uid")

        @JvmStatic
        val PUID = Base.intern("puid")

        @JvmStatic
        val VERSION = Base.intern("version")

        @JvmStatic
        val GEO_GRID = Base.intern("geoGrid")

        @JvmStatic
        val FLAGS = Base.intern("flags")

        @JvmStatic
        val APP_ID = Base.intern("appId")

        @JvmStatic
        val AUTHOR = Base.intern("author")

        @JvmStatic
        val TAGS = Base.intern("tags")

        @JvmStatic
        val CREATED_AT = Base.intern("createAt")

        @JvmStatic
        val UPDATED_AT = Base.intern("updatedAt")

        @JvmStatic
        val AUTHOR_TS = Base.intern("authorTs")
    }

    override fun klass(): BaseKlass<*> = klass

    fun getAction(): String = getOr(ACTION, Klass.stringKlass, CREATE)
    fun setAction(value: String?) = set(ACTION, value)
    fun getTxnNext(): Int64? = getOrNull(TXN_NEXT, Klass.int64Klass)
    fun setTxnNext(value: Int64?) = set(TXN_NEXT, value)
    fun getPtxn(): Int64? = getOrNull(PTXN, Klass.int64Klass)
    fun setPtxn(value: Int64?) = set(PTXN, value)
    fun getUid(): Int? = getOrNull(UID, Klass.intKlass)
    fun setUid(value: Int?) = set(UID, value)
    fun getPuid(): Int? = getOrNull(PUID, Klass.intKlass)
    fun setPuid(value: Int?) = set(PUID, value)
    fun getVersion(): Int? = getOrNull(VERSION, Klass.intKlass)
    fun setVersion(value: Int?) = set(VERSION, value)
    fun getAppId(): String? = getOrNull(APP_ID, Klass.stringKlass)
    fun setAppId(value: String?) = set(APP_ID, value)
    fun getAuthor(): String? = getOrNull(AUTHOR, Klass.stringKlass)
    fun setAuthor(value: String?) = set(AUTHOR, value)
    fun getTags(): NakTags = getOr(TAGS, NakTags.klass, NakTags.klass.newInstance())
    fun setTags(value: NakTags) = set(TAGS, value)
    fun getCreatedAt(): Int64? = getOrNull(CREATED_AT, Klass.int64Klass)
    fun setCreatedAt(value: Int64) = set(CREATED_AT, value)
    fun getUpdatedAt(): Int64? = getOrNull(UPDATED_AT, Klass.int64Klass)
    fun setUpdatedAt(value: Int64) = set(UPDATED_AT, value)
    fun getAuthorTs(): Int64? = getOrNull(AUTHOR_TS, Klass.int64Klass)
    fun setAuthorTs(value: Int64) = set(AUTHOR_TS, value)
}