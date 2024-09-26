package naksha.model.request

import naksha.base.Platform
import naksha.jbon.JbFeatureDecoder
import naksha.model.request.query.*

class PropertyFilter(val req: ReadFeatures) : ResultFilter {

    companion object {
        const val PROPERTIES = "properties"
    }

    override fun call(a1: ResultTuple): ResultTuple? {
        val pSearch = req.query.properties ?: return a1
        if (a1.tuple == null) return null
        if (a1.tuple!!.feature == null) return null
        val decoder = JbFeatureDecoder()
        decoder.mapBytes(a1.tuple!!.feature!!)
        if (resolvePropsQuery(pSearch, decoder)) return a1
        return null
    }

    private fun resolvePropsQuery(pQuery: IPropertyQuery?, decoder: JbFeatureDecoder) : Boolean {
        when (pQuery) {
            null -> return true
            is PAnd -> {
                pQuery.forEach {
                    if (!resolvePropsQuery(it, decoder)) return false
                }
                return true
            }
            is POr -> {
                pQuery.forEach {
                    if (resolvePropsQuery(it, decoder)) return true
                }
                return false
            }
            is PNot -> return !resolvePropsQuery(pQuery, decoder)
            is PQuery -> {
                val propFromFeature = decoder[PROPERTIES,pQuery.property.path.first()!!]
                val op = pQuery.op
                return resolveEachOp(op,propFromFeature,pQuery.value)
            }
        }
        return false
    }

    private fun resolveEachOp(op: AnyOp, featureProperty: Any?, queryProperty: Any?) : Boolean {
        when (op) {
            AnyOp.EXISTS -> return featureProperty != Platform.UNDEFINED
            AnyOp.IS_NULL -> return featureProperty == null
            AnyOp.IS_NOT_NULL -> return featureProperty != null
            AnyOp.IS_TRUE -> return featureProperty == true
            AnyOp.IS_FALSE -> return featureProperty == false
            AnyOp.IS_ANY_OF -> {
                if (queryProperty !is Array<*>) return false
                return queryProperty.contains(featureProperty)
            }
            AnyOp.CONTAINS -> {}
            StringOp.EQUALS -> return (featureProperty is String) && (queryProperty is String) && (featureProperty.toString() == queryProperty.toString())
            StringOp.STARTS_WITH -> return (featureProperty is String) && (queryProperty is String) && (featureProperty.startsWith(queryProperty.toString()))
            DoubleOp.EQ -> return (featureProperty is Number) && (queryProperty is Number) && (featureProperty.toDouble().equals(queryProperty.toDouble()))
            DoubleOp.GT -> return (featureProperty is Number) && (queryProperty is Number) && (featureProperty.toDouble() > queryProperty.toDouble())
            DoubleOp.LT -> return (featureProperty is Number) && (queryProperty is Number) && (featureProperty.toDouble() < queryProperty.toDouble())
            DoubleOp.GTE -> return (featureProperty is Number) && (queryProperty is Number) && (featureProperty.toDouble() >= queryProperty.toDouble())
            DoubleOp.LTE -> return (featureProperty is Number) && (queryProperty is Number) && (featureProperty.toDouble() <= queryProperty.toDouble())
        }
        return false
    }
}