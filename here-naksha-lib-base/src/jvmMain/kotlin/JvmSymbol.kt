package com.here.naksha.lib.base

class JvmSymbol internal constructor(private val key: String = "") : Symbol {

    @Override
    override fun equals(other: Any?) : Boolean = this === other

    @Override
    override fun hashCode() : Int = key.hashCode()

    @Override
    override fun toString() : String = key
}