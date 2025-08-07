package naksha.model.request

import naksha.base.*
import naksha.base.PlatformUtil.PlatformUtil_C.deepContains
import naksha.base.Platform.Platform_C.gzipInflate
import naksha.jbon.JbFeatureDecoder
import naksha.model.Naksha.Naksha_C.cache
import naksha.model.Naksha.Naksha_C.getStorageByNumber
import naksha.model.featureGzip
import naksha.model.request.query.*

class PropertyFilter(val req: ReadFeatures) : ResultFilter {
    /**
     * Check if the feature matches the query
     * @param featureTuple the tuple containing the feature
     * @return the tuple back if matches, else return null
     */
    override fun filter(featureTuple: FeatureTuple): FeatureTuple? {
        val pSearch = req.query.properties ?: return featureTuple

        val decoder = resolveFeatureAndDecoder(featureTuple) ?: return null

        if (resolvePropsQuery(pSearch, decoder)) {
            return featureTuple
        }
        return null
    }

    private fun resolveFeatureAndDecoder(featureTuple: FeatureTuple): JbFeatureDecoder? {
        val tuple = featureTuple.tuple ?: return null
        val feature = featureTuple.tuple?.feature ?: return null
        val flags = tuple.meta.flags

        var raw = feature
        if (flags.featureGzip()) {
            raw = gzipInflate(feature)
        }

        val sn = tuple.storageNumber
        val dictReader = getStorageByNumber(sn) ?: cache.getDictReader(sn)

        val decoder = JbFeatureDecoder(dictReader)
        decoder.mapBytes(raw)
        return decoder
    }

    private fun resolvePropsQuery(pQuery: IPropertyQuery?, decoder: JbFeatureDecoder): Boolean {
        when (pQuery) {
            null, is PTrue -> return true
            is PFalse -> return false
            is PAnd -> return pQuery.all { resolvePropsQuery(it, decoder) }
            is POr -> return pQuery.any { resolvePropsQuery(it, decoder) }
            is PNot -> return !resolvePropsQuery(pQuery.query, decoder)
            is PQuery -> {
                val propFromFeature = decoder.get(Property.PROPERTIES, *pQuery.property.path.filterNotNull().toTypedArray())
                val op = pQuery.op
                return resolveOp(op, propFromFeature, pQuery.value)
            }
        }
        throw IllegalArgumentException("Unknown query type for: $pQuery")
        //TODO instead of throwing exceptions, implement a call-back handler customizable
        //TODO to, for example, log the instance where an unknown query is used, so as not
        //TODO to disrupt the flow of the request
    }

    private fun resolveOp(op: AnyOp, featureProperty: Any?, queryProperty: Any?) : Boolean {
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
            AnyOp.CONTAINS -> deepContains(featureProperty, queryProperty)
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
}
