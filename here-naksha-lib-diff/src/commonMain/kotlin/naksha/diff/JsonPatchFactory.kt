package naksha.diff

import naksha.base.AnyList
import naksha.base.Platform
import naksha.base.illegalArg

class JsonPatchFactory private constructor() {

    companion object JsonPatchBuilder_C {

        private const val ROOT_PATH = "";
        private const val PATH_DELIMITER = "/";
        private const val EMPTY_JSON_ARRAY = "[]"

        /**
         * Converts [difference] (which can be a complex [naksha.diff.MapDiff] or [naksha.diff.ListDiff]) to JSON that complies to
         * JsonPatch standard: [RFC 6092](https://datatracker.ietf.org/doc/html/rfc6902/)
         *
         * In short, [RFC 6092](https://datatracker.ietf.org/doc/html/rfc6902/) specifies that the JsonPatch is just an array of entries.
         * Each entry specifies operation, path and potential value.
         *
         * @param difference [Difference] to be converted
         * @return RFC 6092 - compliant JSON
         */
        fun jsonPatch(difference: Difference?): String {
            if (difference == null) return EMPTY_JSON_ARRAY
            val accumulator = AnyList()
            accumulateJsonPatchEntries(difference, ROOT_PATH, accumulator)
            return Platform.toJSON(accumulator)
        }


        private fun accumulateJsonPatchEntries(
            difference: Difference?,
            currentPath: String,
            accumulator: AnyList
        ) {
            when (difference) {
                is PrimitiveDiff -> accumulator.add(jsonPathEntry(difference, currentPath))
                is MapDiff -> flattenMapDiff(difference, currentPath, accumulator)
                is ListDiff -> flattenListDiff(difference, currentPath, accumulator)
            }
        }

        private fun jsonPathEntry(difference: PrimitiveDiff, path: String): JsonPatchEntry {
            return when (difference) {
                is InsertDiff -> JsonPatchAdd(path, difference.newValue)
                is RemoveDiff -> JsonPatchRemove(path)
                is UpdateDiff -> JsonPatchReplace(path, difference.newValue)
                else -> throw illegalArg("Unknown PrimitiveDiff type: ${difference.platformType().name}")
            }
        }

        private fun flattenMapDiff(
            mapDiff: MapDiff,
            currentPath: String,
            accumulator: AnyList
        ) {
            mapDiff.differences.forEach { (key, diff) ->
                accumulateJsonPatchEntries(
                    difference = diff,
                    currentPath = currentPath + PATH_DELIMITER + key,
                    accumulator = accumulator
                )
            }
        }

        private fun flattenListDiff(
            listDiff: ListDiff,
            currentPath: String,
            accumulator: AnyList
        ) {
            listDiff.differences.forEachIndexed { index, diff ->
                accumulateJsonPatchEntries(
                    difference = diff,
                    currentPath = currentPath + PATH_DELIMITER + index,
                    accumulator = accumulator
                )
            }
        }
    }
}