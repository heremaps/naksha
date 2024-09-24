package naksha.model.request

import naksha.jbon.JbFeatureDecoder
import naksha.model.request.query.*

class PropertyFilter(val req: ReadFeatures) : ResultFilter {

    override fun call(a1: ResultTuple): ResultTuple? {
        val pSearch = req.query.properties ?: return a1
        // Now, implement in Java the different operations supported
        // In the tuple, you use the JbFeatureDecoder, not decode the feature!
        // The feature decoder allow to map arbitrary bytes, like the feature bytes from the tuple
        // decoder.mapBytes(tuple.feature)
        // decoder.get("properties", "name")
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
                return decoder[pQuery.property.name] == pQuery.value
            }
        }
        return false
    }
}