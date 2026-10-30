package naksha.base

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.ser.std.StdSerializer

internal object IdSerializer : StdSerializer<Id>(Id::class.java) {
    override fun serialize(value: Id, gen: JsonGenerator, provider: SerializerProvider) = gen.writeString(value.text)
}
