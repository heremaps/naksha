@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.base

import kotlin.js.JsExport
import kotlin.jvm.JvmStatic

/**
 * The XYZ namespace stored in the `@ns:com:here:xyz` property of the [P_NakshaFeature].
 */
@JsExport
class P_Xyz(vararg args: Any?) : BaseObject(*args) {
    companion object {
        @JvmStatic
        val klass = object : BaseObjectKlass<P_Xyz>() {
            override fun isInstance(o: Any?): Boolean = o is P_Xyz

            override fun newInstance(vararg args: Any?): P_Xyz = P_Xyz()
        }

        @JvmStatic
        val ACTION = Base.intern("action")

        @JvmStatic
        val UUID_NEXT = Base.intern("uuidNext")

        @JvmStatic
        val UUID = Base.intern("uuid")

        @JvmStatic
        val PUUID = Base.intern("puuid")

        @JvmStatic
        val FNVA1 = Base.intern("fnva1")

        @JvmStatic
        val VERSION = Base.intern("version")

        @JvmStatic
        val GEO_GRID = Base.intern("geoGrid")

        @JvmStatic
        val ORIGIN = Base.intern("origin")

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

    /**
     * FIXME not sure if we need it here since we keep it on flags
     */
    fun getAction(): String = getOr(ACTION, Klass.stringKlass, XYZ_EXEC_CREATED)
    fun setAction(value: String?) = set(ACTION, value)

    fun getUuid(): String? = getOrNull(UUID, Klass.stringKlass)
    fun setUuid(value: String) = set(UUID, value)

    fun getUuidNext(): String? = getOrNull(UUID_NEXT, Klass.stringKlass)
    fun setUuidNext(value: String) = set(UUID_NEXT, value)

    fun getPuuid(): String? = getOrNull(PUUID, Klass.stringKlass)
    fun setPuuid(value: String) = set(PUUID, value)

    fun getVersion(): Int? = getOrNull(VERSION, Klass.intKlass)
    fun setVersion(value: Int?) = set(VERSION, value)

    fun getAppId(): String? = getOrNull(APP_ID, Klass.stringKlass)
    fun setAppId(value: String?) = set(APP_ID, value)

    fun getAuthor(): String? = getOrNull(AUTHOR, Klass.stringKlass)
    fun setAuthor(value: String?) = set(AUTHOR, value)

    fun getFnva1(): String? = getOrNull(FNVA1, Klass.stringKlass)
    fun setFnva1(value: String?) = set(FNVA1, value)

    fun getOrigin(): String? = getOrNull(ORIGIN, Klass.stringKlass)
    fun setOrigin(value: String?) = set(ORIGIN, value)

    fun getTags(): P_Tags = getOr(TAGS, P_Tags.klass, P_Tags.klass.newInstance())
    fun setTags(value: P_Tags) = set(TAGS, value)

    fun getCreatedAt(): Int64? = getOrNull(CREATED_AT, Klass.int64Klass)
    fun setCreatedAt(value: Int64) = set(CREATED_AT, value)

    fun getUpdatedAt(): Int64? = getOrNull(UPDATED_AT, Klass.int64Klass)
    fun setUpdatedAt(value: Int64) = set(UPDATED_AT, value)

    fun getAuthorTs(): Int64? = getOrNull(AUTHOR_TS, Klass.int64Klass)
    fun setAuthorTs(value: Int64) = set(AUTHOR_TS, value)

    fun getGeoGrid(): Int? = getOrNull(GEO_GRID, Klass.intKlass)
    fun setGeoGrid(value: Int) = set(GEO_GRID, value)
}