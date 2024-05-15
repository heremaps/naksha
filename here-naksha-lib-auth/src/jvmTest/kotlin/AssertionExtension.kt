import org.junit.jupiter.api.Assertions

fun Boolean.assertTrue() =
    assert(true)

fun Boolean.assert(other: Boolean) =
    Assertions.assertEquals(other, this)
