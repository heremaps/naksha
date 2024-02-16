@file:OptIn(ExperimentalJsExport::class)

package com.here.naksha.lib.plv8;

import com.here.naksha.lib.jbon.Jb
import com.here.naksha.lib.jbon.JbSession
import com.here.naksha.lib.jbon.JsEnv

/**
 * Special JS session that is optimized for PLV8 (Postgres extension).
 */
@Suppress("unused", "UNUSED_VALUE", "MemberVisibilityCanBePrivate")
@JsExport
class Plv8Env : JsEnv() {

    companion object {
        private lateinit var env : Plv8Env
        private lateinit var log : Plv8Log

        fun initialize() {
            if (!Jb.isInitialized()) JsEnv.initialize()
            if (!this::env.isInitialized) {
                env = Plv8Env()
                Jb.env = env
            }
            if (!this::log.isInitialized) {
                log = Plv8Log()
                Jb.log = log
            }
        }

        /**
         * Returns the current environment, if it is not yet initialized, initializes it.
         * @return The environment.
         */
        fun get() : Plv8Env {
            initialize()
            return Jb.env as Plv8Env
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