package naksha.base

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider

class CustomSerializer : JsonSerializer<Any>() {
    override fun serialize(value: Any?, gen: JsonGenerator?, serializers: SerializerProvider?) {
        //TODO implement
        gen?.writeStartObject()
        gen?.writeEndObject()
    }
}