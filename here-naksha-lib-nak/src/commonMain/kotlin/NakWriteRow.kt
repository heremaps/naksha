package com.here.naksha.lib.base

import com.here.naksha.lib.nak.Flags
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.jvm.JvmStatic

@OptIn(ExperimentalJsExport::class)
@JsExport
class NakWriteRow(vararg args: Any?) : BaseObject(*args) {

    companion object {
        @JvmStatic
        val klass = object : BaseObjectKlass<NakWriteRow>() {
            override fun isInstance(o: Any?): Boolean = o is NakWriteRow

            override fun newInstance(vararg args: Any?): NakWriteRow = NakWriteRow(*args)
        }

        @JvmStatic
        val OP = Base.intern("op")

        @JvmStatic
        val ID = Base.intern("id")

        @JvmStatic
        val UUID = Base.intern("uuid")

        @JvmStatic
        val FEATURE = Base.intern("feature")

        @JvmStatic
        val ROW = Base.intern("row")

        @JvmStatic
        val FLAGS = Base.intern("flags")

        @JvmStatic
        val GRID = Base.intern("grid")


        fun fromFeature(op: Int, feature: NakFeature): NakWriteRow {
            val nakWriteRow = NakWriteRow()
            nakWriteRow.setOp(op)
            nakWriteRow.setFeature(feature)
            return nakWriteRow
        }

        fun fromRow(op: Int, row: NakRow): NakWriteRow {
            val nakWriteRow = NakWriteRow()
            nakWriteRow.setOp(op)
            nakWriteRow.setRow(row)
            return nakWriteRow
        }
    }

    fun setOp(value: Int) = set(OP, value)

    fun getOp(): Int = toElement(get(OP), Klass.intKlass)!!

    fun setId(value: String?) = set(ID, value)

    fun getId(): String? = toElement(get(ID), Klass.stringKlass)

    fun setUuid(value: String?) = set(UUID, value)

    fun getUuid(): String? = toElement(get(UUID), Klass.stringKlass)

    fun setFeature(value: NakFeature?) = set(FEATURE, value)

    fun getFeature(): NakFeature? = toElement(get(FEATURE), NakFeature.klass)

    fun setRow(value: NakRow?) = set(ROW, value)

    fun getRow(): NakRow? = toElement(get(ROW), NakRow.klass)

    fun getFlags(): Int? = toElement(get(FLAGS), Klass.intKlass)

    fun setFlags(value: Int?) = set(FLAGS, value)

    fun getFlagsObject(): Flags = Flags(getFlags())

    fun setGrid(value: Int?) = set(GRID, value)

    fun getGrid(): Int? = toElement(get(GRID), Klass.intKlass)
}