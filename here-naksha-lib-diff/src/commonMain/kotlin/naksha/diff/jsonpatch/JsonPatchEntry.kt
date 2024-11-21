package naksha.diff.jsonpatch

sealed class JsonPatchEntry(val op: String, val path: String){

    class Add(path: String, val value: Any?): JsonPatchEntry("add", path)

    class Remove(path: String): JsonPatchEntry("remove", path)

    class Replace(path: String, val value: Any?): JsonPatchEntry("replace", path)
}
