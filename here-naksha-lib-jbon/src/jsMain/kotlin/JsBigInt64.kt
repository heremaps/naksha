package com.here.naksha.lib.jbon

/**
 * Helper to create the prototype and patch the native BigInt.
 */
class JsBigInt64 : BigInt64 {
    override fun hashCode(): Int = js("BigInt.hashCode(this)")
    override fun equals(other:Any?): Boolean = other is BigInt64 && eq(other)
}