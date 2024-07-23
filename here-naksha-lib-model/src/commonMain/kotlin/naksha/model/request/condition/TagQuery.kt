@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.condition

import kotlin.js.JsExport

/**
 * All tag based operations. Tags are split at the equal sign into key-value pairs by the storage, and indexed accordingly.
 * @see TagExists
 * @see TagIsString
 * @see TagMatches
 */
@JsExport
abstract class TagQuery : IQuery<TagQuery>


/*


- Finish query changes:
    - general purpose string ops: eq, startsWith, anyOf
    - spatial
    - tags
        - [not] exists
        - [not] value matches regex
        - [not] value eq/lt/lte/gt/gte value (double)
        - [not] value is true|false
        - [not] value is null
        - [not] allow any AND/OR/NOT combination
        - prevent [not] not
    - author / author_ts
    - appId
    - ids
    - properties
        - these queries are executed client side
        - implement as filter method that is applied before custom filters
    - orderBy (PgIndex, add "supportOrdering")
    - general approach: query ids, reduce cardinality
    - then fetch all rows by index using ordered index
        - only needed when handle and/or ordering requested
- Finish table creation
- Insert test data
- Query test data
- Verify that indices are used


*/
