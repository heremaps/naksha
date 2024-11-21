package naksha.diff.jsonpatch

internal sealed class JsonPatchEntry(val op: String, val path: String){

    internal class Add(path: String, val value: Any?): JsonPatchEntry("add", path)

    internal class Remove(path: String): JsonPatchEntry("remove", path)

    internal class Replace(path: String, val value: Any?): JsonPatchEntry("replace", path)
}
