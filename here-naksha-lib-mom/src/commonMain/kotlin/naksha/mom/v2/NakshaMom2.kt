// This will be exposed
// - in JavaScript at the namespace: naksha.mom.v2.{name}
// - jn Java at the class naksha.mom.v2.NakshaMom2Kt.{name}
package naksha.mom.v2

import naksha.base.AtomicBool
import naksha.base.Platform.Platform_C.forKClass

/**
 * The package name `naksha.mom.v2`.
 * @since 3.0
 */
const val PACKAGE_NAME = "naksha.mom.v2"

private val isInitialied = AtomicBool(false)
fun initialize() {
    if (isInitialied.compareAndSet(expect = false, update = true)) {
        forKClass(MomDeltaNs::class).initialize()
        forKClass(MomMetaNs::class).initialize()
        forKClass(MomChangeState::class).initialize()
        forKClass(MomReviewState::class).initialize()
        forKClass(MomReference::class).initialize()
        forKClass(MomReferenceList::class).initialize()

        forKClass(MomFeature::class).initialize()
        forKClass(MomProperties::class).initialize()
    }
}