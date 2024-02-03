@file:OptIn(ExperimentalJsExport::class)

package com.here.naksha.lib.plv8;

import com.here.naksha.lib.jbon.JbSession
import com.here.naksha.lib.jbon.JsEnv

/**
 * Special JS session that is optimized for PLV8 (Postgres extension).
 */
@Suppress("unused", "UNUSED_VALUE")
@JsExport
class Plv8Env : JsEnv() {

    companion object {
        /**
         * Returns the current environment, if it is not yet initialized, initializes it.
         * @return The environment.
         */
        fun get() : Plv8Env {
            var env = JsEnv.get()
            if (env !is Plv8Env) {
                env = Plv8Env()
                JbSession.env = env
                JbSession.log = Plv8Log()
            }
            return env
        }
    }

    override fun lz4Deflate(raw: ByteArray, offset: Int, size: Int): ByteArray {
        val end = endOf(raw, offset, size)
        val bytes: ByteArray
        if (offset == 0 && end == raw.size) {
            bytes = raw
        } else {
            bytes = ByteArray(end - offset)
            raw.copyInto(bytes, 0, offset, end)
        }
        return js("lz4.compress(bytes)") as ByteArray
    }

    override fun lz4Inflate(compressed: ByteArray, bufferSize: Int, offset: Int, size: Int): ByteArray {
        return js("lz4.decompress(compressed)") as ByteArray
    }
}