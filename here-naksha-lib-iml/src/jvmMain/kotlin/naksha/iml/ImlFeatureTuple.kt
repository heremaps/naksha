package naksha.iml

import naksha.base.TupleNumber
import naksha.model.Tuple
import naksha.model.objects.NakshaFeature
import naksha.model.request.FeatureTuple

/**
 * Keeps IML's source XYZ metadata when the shared cache attaches a tuple directly during lazy loading.
 * @since 3.0
 */
internal class ImlFeatureTuple(number: TupleNumber) : FeatureTuple(number, null) {
  private var decodedTuple: Tuple? = null
  private var decodedFeature: NakshaFeature? = null

  override var feature: NakshaFeature?
    get() {
      val current = tuple
      if (current !== decodedTuple) {
        decodedFeature = current?.decodeFeature(null)
        decodedTuple = current
      }
      return decodedFeature
    }
    set(value) {
      decodedFeature = value
      decodedTuple = tuple
    }

  override fun newFeature(): NakshaFeature? = tuple?.decodeFeature(null)
}
