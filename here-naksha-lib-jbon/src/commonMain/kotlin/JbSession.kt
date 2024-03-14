@file:OptIn(ExperimentalJsExport::class)

package com.here.naksha.lib.jbon

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.jvm.JvmStatic

/**
 * The native API to be provided by the platform to grant access to native capabilities for this session.
 * @property appName The name of the application starting the session, only for debugging purpose.
 * @property streamId The stream-identifier, to be added to the transaction logs for debugging purpose.
 * @property appId The UPM identifier of the application (for audit).
 * @property author The UPM identifier of the user (for audit).
 * @constructor Create a new session.
 */
@Suppress("unused")
@JsExport
open class JbSession(var appName: String, var streamId: String, var appId: String, var author: String? = null) {
    companion object {
        @JvmStatic
        fun isSame(s: Any): Boolean {
            return this === s
        }

        /**
         * The current thread local session.
         */
        @JvmStatic
        val threadLocal: IThreadLocal = Jb.env.newThreadLocal()

        /**
         * Returns the current thread local session.
         * @return The current thread local session.
         * @throws IllegalStateException If no session is available.
         */
        @JvmStatic
        fun get(): JbSession {
            val session = threadLocal.get()
            check(session != null)
            return session
        }
    }

    /**
     * Reset the session.
     * @param appName The name of the application starting the session, only for debugging purpose.
     * @param streamId The stream-identifier, to be added to the transaction logs for debugging purpose.
     * @param appId The UPM identifier of the application (for audit).
     * @param author The UPM identifier of the user (for audit).
     */
    open fun reset(appName: String, streamId: String, appId: String, author: String?) {
        this.appName = appName
        this.streamId = streamId
        this.appId = appId
        this.author = author
    }

    /**
     * This is more for platform code, to create new byte-arrays the same way as Kotlin does it. For JAVA this
     * means to create a `byte[]`, for JavaScript it means a `Int8Array`. Beware, that technically the `Int8Array`
     * is already a view using a buffer beneath, which actually is the real byte-array.
     * @param size The size of the byte-array.
     * @return The new byte array.
     */
    fun newByteArray(size: Int): ByteArray {
        return ByteArray(size)
    }

    /**
     * Creates a new JBON builder.
     * @param globalDict The global dictionary to use for this builder.
     * @param size The size of the builder buffer.
     * @return a new builder.
     */
    fun newBuilder(globalDict: JbDict? = null, size: Int = 65536): JbBuilder {
        return JbBuilder(newDataView(ByteArray(size)), globalDict)
    }

    /**
     * Creates a view above the given byte-array.
     * @param bytes The byte-array for which to create a view.
     * @param offset The offset into the byte-array to map.
     * @param size The amount of byte to map, if longer than the byte-array, till the end of the byte-array.
     * @return The view to the byte-array.
     */
    fun newDataView(bytes: ByteArray, offset: Int = 0, size: Int = bytes.size): IDataView {
        require(offset in bytes.indices)
        require(size >= 0)
        val length = if (offset + size <= bytes.size) size else bytes.size - offset
        return Jb.env.newDataView(bytes, offset, length)
    }

    /**
     * Clears cached values, but keeps the context, so it's possible to use session for multiple transactions.
     */
    open fun clear() {}
}