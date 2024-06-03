package com.here.naksha.lib.auth

//TODO
interface Authenticable<T: AccessAttributeMap>{
    fun toAttrMap(): T
}