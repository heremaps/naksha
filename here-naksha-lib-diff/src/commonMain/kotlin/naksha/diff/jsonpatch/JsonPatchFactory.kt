package naksha.diff.jsonpatch

import naksha.diff.*

class JsonPatchFactory private constructor() {

    companion object JsonPatchBuilder_C {

        private const val ROOT_PATH = "";
        private const val PATH_DELIMITER = "/";

        /**
         * Converts [difference] (which can be a complex [naksha.diff.MapDiff] or [naksha.diff.ListDiff]) to list of [JsonPatchEntry].
         * The result after serialization complies with JsonPatch standard: [RFC 6092](https://datatracker.ietf.org/doc/html/rfc6902/)
         *
         * In short, [RFC 6092](https://datatracker.ietf.org/doc/html/rfc6902/) specifies that the JsonPatch is just an array of entries.
         * Each entry specifies operation, path and potential value.
         *
         * TODO: sample
         */
        fun jsonPatch(difference: Difference?): List<JsonPatchEntry> {
            if (difference == null) return emptyList()
            val accumulator = mutableListOf<JsonPatchEntry>()
            accumulateJsonPatchEntries(difference, ROOT_PATH, accumulator)
            return accumulator
        }


        private fun accumulateJsonPatchEntries(
            difference: Difference?,
            currentPath: String,
            accumulator: MutableList<JsonPatchEntry>
        ) {
            when (difference) {
                is PrimitiveDiff -> accumulator.add(jsonPathEntry(difference, currentPath))
                is MapDiff -> flattenMapDiff(difference, currentPath, accumulator)
                is ListDiff -> flattenListDiff(difference, currentPath, accumulator)
            }
        }

        private fun jsonPathEntry(difference: PrimitiveDiff, path: String): JsonPatchEntry {
            return when (difference) {
                is InsertOp -> JsonPatchEntry.Add(path, difference.newValue)
                is RemoveOp -> JsonPatchEntry.Remove(path)
                is UpdateOp -> JsonPatchEntry.Replace(path, difference.newValue)
                else -> throw IllegalArgumentException("Unknown PrimitiveDiff type: ${difference::class.simpleName}")
            }
        }

        private fun flattenMapDiff(
            mapDiff: MapDiff,
            currentPath: String,
            accumulator: MutableList<JsonPatchEntry>
        ) {
            mapDiff.forEach { (key, diff) ->
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
            accumulator: MutableList<JsonPatchEntry>
        ) {
            listDiff.forEachIndexed { index, diff ->
                accumulateJsonPatchEntries(
                    difference = diff,
                    currentPath = currentPath + PATH_DELIMITER + index,
                    accumulator = accumulator
                )
            }
        }
    }
}