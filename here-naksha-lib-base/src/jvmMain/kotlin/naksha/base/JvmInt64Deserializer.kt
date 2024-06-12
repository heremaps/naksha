package naksha.base

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonNode

internal class CustomNumberDeserializer : JsonDeserializer<Number>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Number? {
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
//        else if (p.valueAsString.startsWith("data:bigint;")) {
//            return value.toString()
//        }
        return null;
    }
}
