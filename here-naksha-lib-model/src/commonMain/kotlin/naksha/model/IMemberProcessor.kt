package naksha.model

import naksha.base.PAnyMap
import naksha.model.objects.Member
import naksha.model.objects.NakshaCollection

/**
 * Optional callback, invoked by the storage after the value of a member has been extracted. This allows some business logic to mutate the value before it is actually persisted.
 */
fun interface IMemberProcessor {
    /**
     * Invoked by the storage before it persists an object.
     *
     * Modification of the arguments is strictly forbidden for this method, doing so anyway will cause _undefined_ behavior and may lead to data loss. The method **must** only read the given arguments and generate the result or just return the given value.
     * @param session The current session as context.
     * @param collection The collection in which the feature is located.
     * @param obj The object being processed _(the member is still in the feature in the original value)_.
     * @param member The member that is processed.
     * @param value The value that is about to be extracted from the feature, and to be persisted in a dedicated storage slot.
     * @return the value that should be stored, can be just the same as the given one or any other acceptable primitive.
     */
    fun processMember(session: ISession, collection: NakshaCollection, obj: PAnyMap, member: Member, value: Any?): Any?
}