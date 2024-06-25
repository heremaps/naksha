package naksha.base

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider

object CustomSerializer : JsonSerializer<Any>() {
    override fun serialize(value: Any?, gen: JsonGenerator?, serializers: SerializerProvider?) {
        // TODO
        // - If the value is JsEnum, serialize using valueOf()
        gen?.writeStartObject()
        gen?.writeEndObject()
    }
}