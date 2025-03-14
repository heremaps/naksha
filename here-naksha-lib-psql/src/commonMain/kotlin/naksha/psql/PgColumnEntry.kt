package naksha.psql

import naksha.base.AnyList
import naksha.base.Int64

@Suppress("UNCHECKED_CAST")
internal data class PgColumnEntry(
    val index: Int,
    val name: String,
    val type: PgType,
    val values: AnyList = AnyList()
) {
    constructor(column: PgColumn, index: Int = column.i) : this(index, column.name, column.type)

    fun withSize(size: Int): PgColumnEntry {
        values.size = size
        return this
    }
    fun anyValues(): MutableList<Any?> = values
    fun anyArray(): Array<Any?> = values.toArray()
    fun intValues(): MutableList<Int?> = values as MutableList<Int?>
    fun intArray(): Array<Int?> = values.toArray() as Array<Int?>
    fun int64Values(): MutableList<Int64?> = values as MutableList<Int64?>
    fun int64Array(): Array<Int64?> = values.toArray() as Array<Int64?>
    fun doubleValues(): MutableList<Double?> = values as MutableList<Double?>
    fun doubleArray(): Array<Double?> = values.toArray() as Array<Double?>
    fun stringValues(): MutableList<String?> = values as MutableList<String?>
    fun stringArray(): Array<String?> = values.toArray() as Array<String?>
    fun byteArrayValues(): MutableList<ByteArray?> = values as MutableList<ByteArray?>
    fun byteArrayArray(): Array<ByteArray?> = values.toArray() as Array<ByteArray?>
}