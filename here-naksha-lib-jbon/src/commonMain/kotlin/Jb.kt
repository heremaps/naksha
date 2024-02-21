@file:OptIn(ExperimentalJsExport::class)

package com.here.naksha.lib.jbon

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.jvm.JvmStatic

/**
 * The native APIs, requires that the environment initializes this as first, before doing anything else.
 */
@JsExport
object Jb {
    /**
     * The default size for builders created using the static _create_ method.
     */
    var defaultBuilderSize = 1000

    @JvmStatic
    fun isInitialized(): Boolean {
        return this::env.isInitialized
                && this::map.isInitialized
                && this::int64.isInitialized
                && this::log.isInitialized
    }

    @JvmStatic
    fun initialize(env: IEnv, map: IMapApi, int64: BigInt64Api, log: ILog) {
        this.env = env
        this.map = map
        this.int64 = int64
        this.log = log
    }

    /**
     * The environment (JVM, Browser, PLV8, ...).
     */
    @JvmStatic
    lateinit var env: IEnv

    /**
     * Returns the accessor to native maps.
     * @return The accessor to native maps.
     */
    @JvmStatic
    lateinit var map: IMapApi

    /**
     * Returns the accessor to native 64-bit integers.
     * @return The accessor to native 64-bit integers.
     */
    @JvmStatic
    lateinit var int64: BigInt64Api

    /**
     * Returns the accessor to native logging.
     * @return The accessor to native logging.
     */
    @JvmStatic
    lateinit var log: ILog
}