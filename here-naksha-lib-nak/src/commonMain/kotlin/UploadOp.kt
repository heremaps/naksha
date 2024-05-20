package com.here.naksha.lib.base

import com.here.naksha.lib.nak.Flags

interface UploadOp {
    fun getFlags(): Flags
    fun getGrid(): Int?
}