package naksha.model

import naksha.base.AnyObject
import naksha.model.objects.Member
import naksha.model.objects.MemberList

/**
 * Optional callback, invoked by the storage after the value of a member has been extracted. This allows some business logic to mutate the value before it is actually persisted.
 *
 * When implemented as lambda, the lambda will receive the following arguments:
 * - **session**: If the encoding happens in the context of a session, the session _(optional)_.
 * - **collection**: The collection in which the feature will be stored.
 * - **feature**: The object being processed _(the member is still in the feature in the original value)_.
 * - **member**: The member that is processed.
 * - **value**: The value that is about to be extracted from the feature, and to be persisted in a dedicated storage slot.
 * - **returns** the value that should be stored, can be just the same as the given one or any other acceptable primitive.
 */
fun interface IMemberProcessor {
    /**
     * Invoked by the storage before it persists a feature.
     *
     * Beware that the `session`, `collection` and `feature` **must** be treated as read-only and **must not** be modified, even while it is technically possible. Modification of the objects is strictly forbidden for this method, doing so anyway will cause undefined behavior and may lead to data loss. The method **must** only read the given arguments and generate the result or just return the given value.
     * @param session If the encoding happens in the context of a session, the session _(optional)_.
     * @param memberList The members of the collection in which the feature should be stored.
     * @param feature The feature being processed _(the member is still in the feature in the original value)_.
     * @param member The member that is processed.
     * @param value The value that is about to be extracted from the feature, and to be persisted in a dedicated storage slot.
     * @return the value that should be stored, can be just the same as the given one or any other acceptable primitive.
     */
    fun processMember(session: IWriteSession?, memberList: MemberList, feature: AnyObject, member: Member, value: Any?): Any?
}