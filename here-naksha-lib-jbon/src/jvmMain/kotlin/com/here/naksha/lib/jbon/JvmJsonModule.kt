//package com.here.naksha.lib.jbon
//
//import com.fasterxml.jackson.core.JsonGenerator
//import com.fasterxml.jackson.core.JsonParser
//import com.fasterxml.jackson.core.TreeNode
//import com.fasterxml.jackson.databind.DeserializationContext
//import com.fasterxml.jackson.databind.JsonDeserializer
//import com.fasterxml.jackson.databind.JsonSerializer
//import com.fasterxml.jackson.databind.SerializerProvider
//import com.fasterxml.jackson.databind.module.SimpleModule
//import com.fasterxml.jackson.databind.node.ArrayNode
//import com.fasterxml.jackson.databind.node.BooleanNode
//import com.fasterxml.jackson.databind.node.DoubleNode
//import com.fasterxml.jackson.databind.node.FloatNode
//import com.fasterxml.jackson.databind.node.IntNode
//import com.fasterxml.jackson.databind.node.LongNode
//import com.fasterxml.jackson.databind.node.NullNode
//import com.fasterxml.jackson.databind.node.ObjectNode
//import com.fasterxml.jackson.databind.node.ShortNode
//import com.fasterxml.jackson.databind.node.TextNode
//import kotlin.math.floor
//
//class JvmJsonModule : SimpleModule() {
//    class JvmDeserializer : JsonDeserializer<Any?>() {
//        override fun deserialize(p: JsonParser?, ctxt: DeserializationContext?): Any? {
//            check(p != null)
//            return when (val node = p.readValueAsTree<TreeNode>()) {
//                is ArrayNode -> p.codec.treeToValue(node, Array::class.java)
//                is ObjectNode -> p.codec.treeToValue(node, JvmMap::class.java)
//                is TextNode -> node.textValue()
//                is ShortNode -> node.intValue()
//                is IntNode -> node.intValue()
//                is LongNode -> node.longValue()
//                is FloatNode -> node.doubleValue()
//                is DoubleNode -> node.doubleValue()
//                is BooleanNode -> node.booleanValue()
//                is NullNode -> null
//                else -> null
//            }
//        }
//    }
//
//    class JvmDoubleSerializer : JsonSerializer<Double>() {
//        override fun serialize(value: Double?, gen: JsonGenerator?, serializers: SerializerProvider?) {
//            check(gen != null)
//            if (value == null) {
//                gen.writeNull()
//            } else {
//                val r = floor(value)
//                if (r != value) {
//                    gen.writeNumber(value)
//                } else {
//                    gen.writeNumber(r.toLong())
//                }
//            }
//        }
//    }
//
//    init {
//        addSerializer(Double::class.java, JvmDoubleSerializer())
//        addSerializer(Double::class.javaObjectType, JvmDoubleSerializer())
//        addDeserializer(Any::class.java, JvmDeserializer())
//    }
//}