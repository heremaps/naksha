package naksha.model.request

import naksha.base.AnyObject
import naksha.base.Platform
import naksha.jbon.JbFeatureDecoder
import naksha.model.request.query.*

class PropertyFilter(val req: ReadFeatures) : ResultFilter {

    companion object {
        const val PROPERTIES = "properties"
    }

    override fun filter(resultTuple: ResultTuple): ResultTuple? {
        val pSearch = req.query.properties ?: return resultTuple
        if (resultTuple.tuple == null) return null
        val feature = resultTuple.tuple?.feature ?: return null
        val decoder = JbFeatureDecoder()
        decoder.mapBytes(feature)
        if (resolvePropsQuery(pSearch, decoder)) return resultTuple
        return null
    }

    private fun resolvePropsQuery(pQuery: IPropertyQuery?, decoder: JbFeatureDecoder) : Boolean {
        when (pQuery) {
            null -> return true
            is PAnd -> return pQuery.all { resolvePropsQuery(it, decoder) }
            is POr -> return pQuery.any { resolvePropsQuery(it, decoder) }
            is PNot -> return !resolvePropsQuery(pQuery.query, decoder)
            is PQuery -> {
                val propFromFeature = decoder.get(PROPERTIES,*pQuery.property.path.filterNotNull().toTypedArray())
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

    private fun resolveContains(featureProperty: Any?, queryProperty: Any?) : Boolean {
        if (featureProperty == null) return queryProperty == null
        if (Platform.isScalar(featureProperty)) return featureProperty.toString() == queryProperty.toString()
        when (featureProperty) {
            is Array<*> -> {
                if (queryProperty is Array<*>) return featureProperty.intersect(queryProperty.toSet()).size == queryProperty.size
                if (queryProperty is List<*>) return featureProperty.intersect(queryProperty).size == queryProperty.size
                return false
            }
            is AnyObject -> {
                if (queryProperty !is AnyObject) return false
                return featureProperty.contentDeepEquals(queryProperty)
            }
        }
        return false
    }
}