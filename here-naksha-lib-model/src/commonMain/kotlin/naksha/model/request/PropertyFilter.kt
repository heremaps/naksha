package naksha.model.request

import naksha.base.PAnyArray
import naksha.base.PAnyMap
import naksha.base.Base
import naksha.base.Base.BaseCompanion.UNDEFINED
import naksha.base.PlatformList
import naksha.base.PlatformListApi.PlatformListApiCompanion.array_get
import naksha.base.PlatformListApi.PlatformListApiCompanion.array_get_length
import naksha.base.PlatformMap
import naksha.base.PlatformMapApi.PlatformMapApiCompanion.map_contains_key
import naksha.base.PlatformMapApi.PlatformMapApiCompanion.map_get
import naksha.base.BaseUtil
import naksha.base.Proxy
import naksha.model.objects.JsonPath
import naksha.model.objects.NakshaCollection

import naksha.model.objects.NakshaFeature
import naksha.model.request.query.*

class PropertyFilter(val req: ReadFeatures) : IObjectFilter {

    override fun filter(collection: NakshaCollection, obj: PAnyMap): PAnyMap? {
        val pSearch = req.query.properties ?: return obj
        return if (resolvePropsQueryOnFeature(pSearch, obj.proxy(NakshaFeature::class))) obj else null
    }

    /**
     * Resolve a property query on a decoded [NakshaFeature] by walking the property path directly.
     */
    private fun resolvePropsQueryOnFeature(pQuery: IPropertyQuery?, feature: NakshaFeature): Boolean {
        when (pQuery) {
            null -> return true
            is PAnd -> return pQuery.all { resolvePropsQueryOnFeature(it, feature) }
            is POr -> return pQuery.any { resolvePropsQueryOnFeature(it, feature) }
            is PNot -> return !resolvePropsQueryOnFeature(pQuery.query, feature)
            is PQuery -> {
                val propValue = walkPath(feature, pQuery.property.path)
                return resolveEachOp(pQuery.op, propValue, pQuery.value)
            }
        }
        throw IllegalArgumentException("Unknown query type for: $pQuery")
    }

    /**
     * Walk a property path on an object or array.
     * @return [BaseCompanion.UNDEFINED] if the path does not exist.
     */
    private fun walkPath(objectOrArray: Any?, path: JsonPath): Any? {
        var current: Any? = objectOrArray
        for (key in path) {
            if (key == null) return UNDEFINED
            current = when (current) {
                is PAnyArray -> {
                    val index: Int = keyToListIndex(key) ?: return UNDEFINED
                    if (index < 0 || index >= current.size) return UNDEFINED
                    current[index]
                }
                is NakshaFeature -> {
                    val raw = current.getRaw(key)
                    if (raw === UNDEFINED) return UNDEFINED else raw
                }
                is PAnyMap -> if (current.containsKey(key)) current[key] else return UNDEFINED
                is PlatformList -> {
                    val index: Int = keyToListIndex(key) ?: return UNDEFINED
                    if (index < 0 || index >= array_get_length(current)) return UNDEFINED
                    array_get(current, index)
                }
                is PlatformMap -> {
                    if (key !is String) return UNDEFINED
                    if (!map_contains_key(current, key)) return UNDEFINED
                    map_get(current, key)
                }
                else -> return UNDEFINED
            }
        }
        if (current is PlatformList) return current.proxy(PAnyArray::class)
        if (current is PlatformMap) return current.proxy(PAnyMap::class)
        return current
    }

    /** A list index from a numeric path segment: a [Number], or a numeric [String] like `"2"`; else `null`. */
    private fun keyToListIndex(key: Any?): Int? = when (key) {
        is Number -> key.toInt()
        is String -> key.toIntOrNull()
        else -> null
    }

    private fun resolveEachOp(op: AnyOp, featureProperty: Any?, queryProperty: Any?) : Boolean {
        return when (op) {
            AnyOp.EXISTS -> featureProperty != UNDEFINED
            AnyOp.IS_NULL -> featureProperty == null
            AnyOp.IS_NOT_NULL -> featureProperty != null
            AnyOp.IS_TRUE -> featureProperty == true
            AnyOp.IS_FALSE -> featureProperty == false
            AnyOp.IS_ANY_OF -> {
                if (queryProperty is Array<*>) return queryProperty.contains(featureProperty)
                if (queryProperty is List<*>) return queryProperty.contains(featureProperty)
                false
            }
            AnyOp.CONTAINS -> resolveContains(featureProperty, queryProperty)
            StringOp.EQUALS -> (featureProperty is String) && (queryProperty is String) && (featureProperty.toString() == queryProperty.toString())
            StringOp.STARTS_WITH -> (featureProperty is String) && (queryProperty is String) && (featureProperty.startsWith(queryProperty.toString()))
            DoubleOp.EQ -> (featureProperty is Number) && (queryProperty is Number) && (featureProperty.toDouble() == queryProperty.toDouble())
            DoubleOp.GT -> (featureProperty is Number) && (queryProperty is Number) && (featureProperty.toDouble() > queryProperty.toDouble())
            DoubleOp.LT -> (featureProperty is Number) && (queryProperty is Number) && (featureProperty.toDouble() < queryProperty.toDouble())
            DoubleOp.GTE -> (featureProperty is Number) && (queryProperty is Number) && (featureProperty.toDouble() >= queryProperty.toDouble())
            DoubleOp.LTE -> (featureProperty is Number) && (queryProperty is Number) && (featureProperty.toDouble() <= queryProperty.toDouble())
            else -> throw IllegalArgumentException("Unknown op type for: $op")
        }
    }
    /**
     * The core logic is based on the v2 implementation, which used
     * PostgreSQL's jsonb containment operator (@>).
     * @see <a href="https://www.postgresql.org/docs/9.5/datatype-json.html#JSON-CONTAINMENT">PostgreSQL jsonb Containment</a>
     */

    private fun resolveContains(featureProperty: Any?, queryProperty: Any?): Boolean {
        if (featureProperty == null) return queryProperty == null
        if (Base.isScalar(featureProperty)) {
            return featureProperty.toString() == queryProperty.toString()
        }
        val parsedQuery = if (queryProperty is String) parseJsonString(queryProperty) else queryProperty

        when (featureProperty) {
            is PAnyArray -> {
                return when (parsedQuery) {
                    is PAnyArray -> parsedQuery.all { queryItem -> featureProperty.any { featureItem -> isMatch(featureItem, queryItem) } }
                    is PAnyMap -> featureProperty.any { it is PAnyMap && it.containsAllProperties(parsedQuery) }
                    else -> featureProperty.any { featureItem -> BaseUtil.deepEquals(featureItem, parsedQuery) }
                }
            }
            is PAnyMap -> {
                if (parsedQuery is PAnyMap) {
                    return featureProperty.containsAllProperties(parsedQuery)
                }
            }
        }
        return false
    }

    private fun PAnyMap.containsAllProperties(queryObject: PAnyMap): Boolean {
        return queryObject.entries.all { (queryKey, queryValue) ->
            if (!this.containsKey(queryKey)) {
                false
            } else {
                val featureValue = this[queryKey]
                resolveContains(featureValue, queryValue)
            }
        }
    }

    private fun isMatch(featureItem: Any?, queryItem: Any?): Boolean {
        return when (queryItem) {
            is PAnyMap -> featureItem is PAnyMap && featureItem.containsAllProperties(queryItem)
            else -> BaseUtil.deepEquals(featureItem, queryItem)
        }
    }


    private fun parseJsonString(json: String?): Any? {
        val trimmed = json?.trim() ?: return null
        if (!((trimmed.startsWith("{") && trimmed.endsWith("}")) || (trimmed.startsWith("[") && trimmed.endsWith("]")))) {
            return json
        }
        return try {
            Proxy.box(Base.fromJSON(json), Any::class)
        } catch (e: Exception) {
            Base.logger.warn("JSON parsing failed for string that appeared to be JSON: $json")
            json
        }
    }
}