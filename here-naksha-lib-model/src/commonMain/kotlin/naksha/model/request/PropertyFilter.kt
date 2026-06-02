package naksha.model.request

import naksha.base.AnyList
import naksha.base.AnyObject
import naksha.base.Platform
import naksha.base.Platform.PlatformCompanion.gzipInflate
import naksha.base.PlatformUtil
import naksha.base.Proxy
import naksha.jbon.JbFeatureDecoder
import naksha.model.DataEncoding
import naksha.model.Naksha
import naksha.model.Naksha.NakshaCompanion.cache
import naksha.model.Naksha.NakshaCompanion.getStorageByNumber
import naksha.model.objects.NakshaFeature
import naksha.model.request.query.*

class PropertyFilter(val req: ReadFeatures) : ResultFilter {

    /**
     * Check if the feature matches the query
     * @param featureTuple the tuple containing the feature
     * @return the tuple back if matches, else return null
     */
    override fun filter(featureTuple: FeatureTuple): FeatureTuple? {
        val pSearch = req.query.properties ?: return featureTuple

        // For JBON2/JBON2_GZIP, decode to NakshaFeature and navigate properties directly.
        val tuple = featureTuple.tuple ?: return null
        val encoding = tuple.meta.dataEncoding
        if (encoding == DataEncoding.JBON2 || encoding == DataEncoding.JBON2_GZIP) {
            val feature = Naksha.decodeFeature(tuple.feature, encoding, null) ?: return null
            return if (resolvePropsQueryOnFeature(pSearch, feature)) featureTuple else null
        }

        val decoder = resolveFeatureAndDecoder(featureTuple) ?: return null

        if (resolvePropsQuery(pSearch, decoder)) {
            return featureTuple
        }
        return null
    }

    private fun resolveFeatureAndDecoder(featureTuple: FeatureTuple): JbFeatureDecoder? {
        val tuple = featureTuple.tuple ?: return null
        val feature = featureTuple.tuple?.feature ?: return null
        val encoding = tuple.meta.dataEncoding

        val raw = if (encoding.gzip) gzipInflate(feature) else feature

        val sn = tuple.storageNumber
        val dictReader = getStorageByNumber(sn) ?: cache.getDictReader(sn)

        val decoder = JbFeatureDecoder(dictReader)
        decoder.mapBytes(raw)
        return decoder
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
                val path = pQuery.property.path.filterNotNull()
                val propValue = walkPath(feature, path)
                return resolveEachOp(pQuery.op, propValue, pQuery.value)
            }
        }
        throw IllegalArgumentException("Unknown query type for: $pQuery")
    }

    /**
     * Walk a property path on an object. Returns [Platform.UNDEFINED] if the path does not exist.
     */
    private fun walkPath(root: Any?, path: List<String>): Any? {
        var current: Any? = root
        for (key in path) {
            current = when (current) {
                is AnyList -> {
                    val index = key.toIntOrNull() ?: return Platform.UNDEFINED
                    if (index < 0 || index >= current.size) return Platform.UNDEFINED
                    current[index]
                }
                is AnyObject -> if (current.containsKey(key)) current[key] else return Platform.UNDEFINED
                is NakshaFeature -> {
                    val raw = current.getRaw(key)
                    if (raw === Platform.UNDEFINED) return Platform.UNDEFINED else raw
                }
                else -> return Platform.UNDEFINED
            }
        }
        return current
    }

    private fun resolvePropsQuery(pQuery: IPropertyQuery?, decoder: JbFeatureDecoder): Boolean {
        when (pQuery) {
            null -> return true
            is PAnd -> return pQuery.all { resolvePropsQuery(it, decoder) }
            is POr -> return pQuery.any { resolvePropsQuery(it, decoder) }
            is PNot -> return !resolvePropsQuery(pQuery.query, decoder)
            is PQuery -> {
                val propertyArray = pQuery.property.path.filterNotNull().toTypedArray()
                val propFromFeature = decoder.get(*propertyArray)
                val op = pQuery.op
                return resolveEachOp(op,propFromFeature,pQuery.value)
            }
        }
        throw IllegalArgumentException("Unknown query type for: $pQuery")
        //TODO instead of throwing exceptions, implement a call-back handler customizable
        //TODO to, for example, log the instance where an unknown query is used, so as not
        //TODO to disrupt the flow of the request
    }

    private fun resolveEachOp(op: AnyOp, featureProperty: Any?, queryProperty: Any?) : Boolean {
        return when (op) {
            AnyOp.EXISTS -> featureProperty != Platform.UNDEFINED
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
        if (Platform.isScalar(featureProperty)) {
            return featureProperty.toString() == queryProperty.toString()
        }
        val parsedQuery = if (queryProperty is String) parseJsonString(queryProperty) else queryProperty

        when (featureProperty) {
            is AnyList -> {
                return when (parsedQuery) {
                    is AnyList -> parsedQuery.all { queryItem -> featureProperty.any { featureItem -> isMatch(featureItem, queryItem) } }
                    is AnyObject -> featureProperty.any { it is AnyObject && it.containsAllProperties(parsedQuery) }
                    else -> featureProperty.any { featureItem -> PlatformUtil.deepEquals(featureItem, parsedQuery) }
                }
            }
            is AnyObject -> {
                if (parsedQuery is AnyObject) {
                    return featureProperty.containsAllProperties(parsedQuery)
                }
            }
        }
        return false
    }

    private fun AnyObject.containsAllProperties(queryObject: AnyObject): Boolean {
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
            is AnyObject -> featureItem is AnyObject && featureItem.containsAllProperties(queryItem)
            else -> PlatformUtil.deepEquals(featureItem, queryItem)
        }
    }


    private fun parseJsonString(json: String?): Any? {
        val trimmed = json?.trim() ?: return null
        if (!((trimmed.startsWith("{") && trimmed.endsWith("}")) || (trimmed.startsWith("[") && trimmed.endsWith("]")))) {
            return json
        }
        return try {
            Proxy.box(Platform.fromJSON(json), Any::class)
        } catch (e: Exception) {
            Platform.PlatformCompanion.logger.warn("JSON parsing failed for string that appeared to be JSON: $json")
            json
        }
    }
}