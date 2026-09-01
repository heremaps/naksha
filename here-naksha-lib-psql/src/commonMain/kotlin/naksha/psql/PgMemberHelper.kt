@file:Suppress("OPT_IN_USAGE")

package naksha.psql

/**
 * Helpers to map a custom [naksha.model.objects.Member] to its physical Postgres representation.
 *
 * Type mapping, DDL, coercion and read-back are owned by [PgType.ofMemberType], [PgColumn] and
 * [naksha.model.FeatureMemberValues]; this object only resolves the physical column name.
 */
class PgMemberHelper private constructor() {

    companion object PgMemberHelper_C {
        /**
         * Returns the physical Postgres column name for the given member name.
         * The name is used as-is; collision with built-in columns is prevented on member registration.
         */
        fun pgColumnName(memberName: String): String = memberName
    }
}
