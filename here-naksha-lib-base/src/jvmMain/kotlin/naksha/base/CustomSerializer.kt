package naksha.base

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import naksha.base.Base.BaseCompanion.MAX_SAFE_INT_AS_LONG
import naksha.base.Base.BaseCompanion.MAX_SAFE_INT_AS_ULONG
import naksha.base.Base.BaseCompanion.MIN_SAFE_INT_AS_LONG

object CustomSerializer : JsonSerializer<Any>() {
    /**
     * @see BaseCompanion.module
     */
    override fun serialize(value: Any?, gen: JsonGenerator, serializers: SerializerProvider?) {
        if (value is Id) {
            gen.writeString(value.text)
            return
        }
        if (value is BaseEnum) {
            var v = value.value
            if (v is Int64) v = v.toLong()
            when (v) {
                is String -> gen.writeString(v)
                is Long -> {
                    if (v !in MIN_SAFE_INT_AS_LONG..MAX_SAFE_INT_AS_LONG) {
                        gen.writeString("data:bigint;dec,$v")
                    } else {
                        gen.writeNumber(v)
                    }
                }

                is ULong -> {
                    if (v > MAX_SAFE_INT_AS_ULONG) {
                        gen.writeString("data:bigint;dec,$v")
                    } else {
                        gen.writeNumber(v.toLong())
                    }
                }

                is UInt -> gen.writeNumber(v.toLong())
                is Int, Short, UShort, Byte, UByte -> gen.writeNumber((v as Number).toInt())
                is Float -> gen.writeNumber(v)
                is Double -> gen.writeNumber(v)
                else -> gen.writeNull()
            }
        }
    }
}