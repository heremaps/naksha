package naksha.base

class JvmSymbol internal constructor(private val description: String = "") : Symbol {

    @Override
    override fun equals(other: Any?) : Boolean = this === other

    @Override
    override fun hashCode() : Int = description.hashCode()

    @Override
    override fun toString() : String = description
}