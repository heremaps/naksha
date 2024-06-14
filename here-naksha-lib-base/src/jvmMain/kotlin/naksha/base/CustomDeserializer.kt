package naksha.base

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonNode

internal class CustomDeserializer() : JsonDeserializer<Any>() {

    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Any? {
        val value = p.codec.readTree<JsonNode>(p)
        if (value.isLong) {
            return JvmInt64(value.longValue())
        }
        else if (value.isInt || value.isShort) {
            return value.intValue()
        }
        else if (value.isFloat || value.isDouble) {
            return value.doubleValue()
        }
        else if (value.isTextual) {
            val asText = value.asText()
            if (asText.startsWith("data:bigint;") && Platform.fromJsonOptions.get().parseDataUrl) {
                val data = asText.split(";")[1]
                val parts = data.split(",")
                val encoding = parts[0]
                val number = parts[1]
                when (encoding) {
                    "hex" -> return JvmInt64(number.removePrefix("0x").toLong(16))
                    "dec" -> return JvmInt64(number.toLong())
                    "oct" -> return JvmInt64(number.removePrefix("0").toLong(8))
                    "bin" -> return JvmInt64(number.removePrefix("0b").toLong(2))
                }
            }
            return asText
        }
        return null
    }
}
