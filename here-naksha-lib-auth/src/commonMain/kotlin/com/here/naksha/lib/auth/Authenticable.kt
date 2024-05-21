package com.here.naksha.lib.auth

interface Authenticable<T: AccessAttributeMap>{
    fun toAttrMap(): T
}