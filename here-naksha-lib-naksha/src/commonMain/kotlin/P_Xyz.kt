@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.base

import kotlin.js.JsExport
import kotlin.jvm.JvmStatic

/**
 * The XYZ namespace stored in the `@ns:com:here:xyz` property of the [P_NakshaFeature].
 */
@JsExport
class P_Xyz(vararg args: Any?) : P_Object(*args) {
    companion object {
        @JvmStatic
        val ACTION = Platform.intern("action")

        @JvmStatic
        val UUID_NEXT = Platform.intern("uuidNext")

        @JvmStatic
        val UUID = Platform.intern("uuid")

        @JvmStatic
        val PUUID = Platform.intern("puuid")

        @JvmStatic
        val FNVA1 = Platform.intern("fnva1")

        @JvmStatic
        val VERSION = Platform.intern("version")

        @JvmStatic
        val GEO_GRID = Platform.intern("geoGrid")

        @JvmStatic
        val ORIGIN = Platform.intern("origin")

        @JvmStatic
        val APP_ID = Platform.intern("appId")

        @JvmStatic
        val AUTHOR = Platform.intern("author")

        @JvmStatic
        val TAGS = Platform.intern("tags")

        @JvmStatic
        val CREATED_AT = Platform.intern("createAt")

        @JvmStatic
        val UPDATED_AT = Platform.intern("updatedAt")

        @JvmStatic
        val AUTHOR_TS = Platform.intern("authorTs")
    }

    /**
     * FIXME not sure if we need it here since we keep it on flags
     */
    fun getAction(): String = getAs(ACTION, Platform.stringKlass, XYZ_EXEC_CREATED)
    fun setAction(value: String?) = set(ACTION, value)

    fun getUuid(): String? = getOrNull(UUID, Platform.stringKlass)
    fun setUuid(value: String) = set(UUID, value)

    fun getUuidNext(): String? = getOrNull(UUID_NEXT, Platform.stringKlass)
    fun setUuidNext(value: String) = set(UUID_NEXT, value)

    fun getPuuid(): String? = getOrNull(PUUID, Platform.stringKlass)
    fun setPuuid(value: String) = set(PUUID, value)

    fun getVersion(): Int? = getOrNull(VERSION, Platform.intKlass)
    fun setVersion(value: Int?) = set(VERSION, value)

    fun getAppId(): String? = getOrNull(APP_ID, Platform.stringKlass)
    fun setAppId(value: String?) = set(APP_ID, value)

    fun getAuthor(): String? = getOrNull(AUTHOR, Platform.stringKlass)
    fun setAuthor(value: String?) = set(AUTHOR, value)

    fun getFnva1(): String? = getOrNull(FNVA1, Platform.stringKlass)
    fun setFnva1(value: String?) = set(FNVA1, value)

    fun getOrigin(): String? = getOrNull(ORIGIN, Platform.stringKlass)
    fun setOrigin(value: String?) = set(ORIGIN, value)

    fun getTags(): P_Tags = getAs(TAGS, P_Tags::class, P_Tags())
    fun setTags(value: P_Tags) = set(TAGS, value)

    fun getCreatedAt(): Int64? = getOrNull(CREATED_AT, Platform.int64Klass)
    fun setCreatedAt(value: Int64) = set(CREATED_AT, value)

    fun getUpdatedAt(): Int64? = getOrNull(UPDATED_AT, Platform.int64Klass)
    fun setUpdatedAt(value: Int64) = set(UPDATED_AT, value)

    fun getAuthorTs(): Int64? = getOrNull(AUTHOR_TS, Platform.int64Klass)
    fun setAuthorTs(value: Int64) = set(AUTHOR_TS, value)

    fun getGeoGrid(): Int? = getOrNull(GEO_GRID, Platform.intKlass)
    fun setGeoGrid(value: Int) = set(GEO_GRID, value)
}