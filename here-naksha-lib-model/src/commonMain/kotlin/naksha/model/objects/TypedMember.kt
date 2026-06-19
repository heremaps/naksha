package naksha.model.objects

/**
 * Marker class of typed members.
 * @since 3.0
 */
abstract class TypedMember<T : TypedMember<T>> : Member() {
    /**
     * Tests if the underlying map has the correct type.
     * @return this.
     * @throws naksha.model.NakshaException with [ILLEGAL_STATE][naksha.model.NakshaError.NakshaErrorCompanion.ILLEGAL_STATE], if the type was illegally cast.
     */
    abstract fun verify(): T
}
