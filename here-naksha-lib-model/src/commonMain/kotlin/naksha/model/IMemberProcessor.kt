package naksha.model

import naksha.model.objects.Member
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaFeature

/**
 * Optional callback, invoked by the storage after the value of a member has been extracted. This allowing some business logic to mutate the value before it is actually persisted.
 */
fun interface IMemberProcessor {
    /**
     * @param session The current session as context.
     * @param collection The collection in which the feature is located.
     * @param feature The feature being processed.
     * @param member The name of the member that is processed.
     * @param value The value that is about to be extracted from the feature, and to be persisted in a dedicated storage slot.
     * @return the value that should be stored, can be just the same as the given one or any other acceptable primitive.
     */
    fun processMember(session: ISession, collection: NakshaCollection, feature: NakshaFeature, member: Member, value: Any?): Any?
}