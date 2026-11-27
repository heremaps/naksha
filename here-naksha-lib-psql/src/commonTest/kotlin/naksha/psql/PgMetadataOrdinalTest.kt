package naksha.psql

import naksha.base.Id
import naksha.base.Platform
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class PgMetadataOrdinalTest {
  @Test
  fun maskedOrdinalsMatchMetadataNumbersAcrossTheFullLogicalRange() {
    val mask = Platform.swapLong(Long.MIN_VALUE or Int.MAX_VALUE.toLong())
    val boundaries = longArrayOf(
      Long.MIN_VALUE, Long.MIN_VALUE + 1, Long.MIN_VALUE + Int.MAX_VALUE,
      Long.MIN_VALUE + (1L shl 31), -1L, 0L, 1L, 127L, 128L, 255L, 256L,
      Int.MAX_VALUE.toLong(), 1L shl 31, 1L shl 32, Long.MAX_VALUE - 1, Long.MAX_VALUE
    )
    val random = Random(33)
    for (i in 0 until boundaries.size + 10000) {
      val logical = if (i < boundaries.size) boundaries[i] else random.nextLong()
      val number = Id.numberToInt(logical)
      val ordinal = Id.featureNumberToOrdinal(logical) and mask
      assertEquals(Id.featureNumberToOrdinal(number.toLong()) and mask, ordinal,
        "Lookup number $number must match logical feature number $logical")
      assertEquals(logical < 0L, number < 0)
      assertEquals(logical and Int.MAX_VALUE.toLong(), (number and Int.MAX_VALUE).toLong())
      for (bit in 0..63) {
        val changed = logical xor (1L shl bit)
        val sameNumber = Id.numberToInt(changed) == number
        assertEquals(bit in 31..62, sameNumber, "Logical bit $bit")
        assertEquals(sameNumber, (Id.featureNumberToOrdinal(changed) and mask) == ordinal,
          "Masked ordinal must preserve exactly the metadata number bits: logical=$logical bit=$bit")
      }
    }
  }

  @Test
  fun everyLookupBitSurvivesSignExtensionAndMasking() {
    val mask = Platform.swapLong(Long.MIN_VALUE or Int.MAX_VALUE.toLong())
    val boundaries = intArrayOf(Int.MIN_VALUE, Int.MIN_VALUE + 1, -1, 0, 1, 127, 128, 255, 256,
      Int.MAX_VALUE - 1, Int.MAX_VALUE)
    val random = Random(34)
    for (i in 0 until boundaries.size + 10000) {
      val number = if (i < boundaries.size) boundaries[i] else random.nextInt()
      val ordinal = Id.featureNumberToOrdinal(number.toLong()) and mask
      assertEquals(number, Id.numberToInt(number.toLong()))
      for (bit in 0..31) {
        val changed = number xor (1 shl bit)
        assertEquals(false, (Id.featureNumberToOrdinal(changed.toLong()) and mask) == ordinal,
          "Lookup bit $bit must remain distinguishable for number $number")
      }
    }
  }
}
