package com.here.naksha.lib.nak

class JvmPSymbol internal constructor(private val key: String = "") : PSymbol {

    @Override
    override fun equals(other: Any?) : Boolean = this === other

    @Override
    override fun hashCode() : Int = key.hashCode()

    @Override
    override fun toString() : String = key
}