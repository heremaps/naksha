import com.here.naksha.lib.jbon.JbBuilder
import com.here.naksha.lib.jbon.JbSession
import java.lang.UnsupportedOperationException

object JbJsonConverter {
    private val env = JbSession.env!!

    fun jsonToJbonByteArray(json: String): ByteArray {
        val jsonObject = env.parse(json)
        val builder = JbSession.get().newBuilder()
        when (jsonObject) {
            is Map<*, *> -> writeMap(jsonObject, builder)
            is Collection<*> -> writeCollection(jsonObject, builder)
            else -> throw RuntimeException("not a json")
        }
        return builder.buildFeature(null)
    }

    fun writeMap(jsonMap: Map<*, *>, jbBuilder: JbBuilder) {
        val start = jbBuilder.startMap()
        for (entry in jsonMap.entries) {
            jbBuilder.writeKey(entry.key as String)
            writeValue(entry.value, jbBuilder)
        }
        jbBuilder.endMap(start)
    }

    fun writeCollection(collection: Collection<*>, jbBuilder: JbBuilder) {
        val start = jbBuilder.startArray()
        for (entry in collection) {
            writeValue(entry, jbBuilder)
        }
        jbBuilder.endArray(start)
    }

    private fun writeValue(value: Any?, jbBuilder: JbBuilder) {
        when (value) {
            is String -> jbBuilder.writeString(value)
            is Map<*, *> -> writeMap(value, jbBuilder)
            is Boolean -> jbBuilder.writeBool(value)
            is Int -> jbBuilder.writeInt32(value)
            is Long -> jbBuilder.writeInt64(value)
            is Double ->
                if (value <= Float.MAX_VALUE && value >= Float.MIN_VALUE) {
                    jbBuilder.writeFloat32(value.toFloat())
                } else {
                    jbBuilder.writeFloat64(value)
                }

            is Collection<*> -> writeCollection(value, jbBuilder)
            null -> jbBuilder.writeNull()
            else -> {
                throw UnsupportedOperationException()
            }
        }
    }
}