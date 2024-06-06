package com.here.naksha.lib.auth

import com.here.naksha.lib.auth.attribute.ResourceAttributes

//TODO
interface Authenticable<T: ResourceAttributes>{
    fun toAttrMap(): T
}