package com.here.naksha.lib.plv8

class NakshaException(
        val errNo: String,
        val errMsg: String,
        val op: String,
        val id: String,
        val xyz: ByteArray?,
        val tags: ByteArray?,
        val feature: ByteArray? = null,
        val geo: Any? = null
) : RuntimeException(errMsg)
