// JBON2 binary format constants.
//
// This file defines the lead-in byte scheme for the JBON **version 2** binary format,
// as specified in `docs/latest/JBON2.md`. It is intentionally kept separate from the
// legacy JBON1 constants in `LibJbon.kt`, so the two encoders/decoders can coexist:
// JBON1 stays responsible for reading legacy data, JBON2 for everything newly produced.
//
// All multibyte integers in JBON2 are big-endian unless stated otherwise.
package naksha.jbon

// ---------------------------------------------------------------------------
// JBON2 file header
// ---------------------------------------------------------------------------

/**
 * The mandatory JBON2 file header bytes: the ASCII string `@JB` followed by the
 * version byte `0x02`. Reading the first four bytes big-endian yields `0x404A4202`.
 */
val JB2_MAGIC = byteArrayOf('@'.code.toByte(), 'J'.code.toByte(), 'B'.code.toByte(), 0x02)

/** The JBON2 version byte. */
const val JB2_VERSION = 0x02

// ---------------------------------------------------------------------------
// Top-2-bit class mask (`xx??_????`)
// ---------------------------------------------------------------------------

internal const val JB2_CLASS_MASK = 0b1100_0000
internal const val JB2_CLASS_MIXED = 0b0000_0000 // 00 - scalars, references, special
internal const val JB2_CLASS_TINY = 0b0100_0000 // 01 - tiny int / tiny float
internal const val JB2_CLASS_STRING = 0b1000_0000 // 10 - string
internal const val JB2_CLASS_STRUCT = 0b1100_0000 // 11 - structures

// ---------------------------------------------------------------------------
// Tiny encodings (`01vv_vvvv`)
//
// `010v_vvvv` tiny int  : value in -16..15, encoded as ((leadIn shl 27) shr 27)
// `011v_vvvv` tiny float: same 5-bit signed value, interpreted as whole number
// ---------------------------------------------------------------------------

internal const val JB2_TINY_MASK = 0b0010_0000
internal const val JB2_TINY_INT = 0b0000_0000
internal const val JB2_TINY_FLOAT = 0b0010_0000
internal const val JB2_TINY_VALUE_MASK = 0b0001_1111

// ---------------------------------------------------------------------------
// Mixed scalars and specials (`0000_xxxx`)
// ---------------------------------------------------------------------------

internal const val JB2_UNDEFINED = 0b0000_0000
internal const val JB2_NULL = 0b0000_0001
internal const val JB2_FALSE = 0b0000_0010
internal const val JB2_TRUE = 0b0000_0011

// `0000_01vv` integers, +1/+2/+4/+8 byte BE signed value
internal const val JB2_INT8 = 0b0000_0100
internal const val JB2_INT16 = 0b0000_0101
internal const val JB2_INT32 = 0b0000_0110
internal const val JB2_INT64 = 0b0000_0111

// `0000_10vv` floats, +1/+2/+4/+8 byte value
internal const val JB2_FLOAT8 = 0b0000_1000
internal const val JB2_FLOAT16 = 0b0000_1001
internal const val JB2_FLOAT32 = 0b0000_1010
internal const val JB2_FLOAT64 = 0b0000_1011

// `0000_11vv` special fixed-width encodings
internal const val JB2_TIMESTAMP = 0b0000_1100 // +7 byte BE unsigned, UTC epoch ms (56-bit)
internal const val JB2_UINT56 = 0b0000_1101 // +7 byte BE unsigned 56-bit
internal const val JB2_UINT24 = 0b0000_1110 // +3 byte BE unsigned 24-bit
internal const val JB2_TUPLE_NUMBER = 0b0000_1111 // +32 byte tuple-number value

// ---------------------------------------------------------------------------
// Reference (`0011_bbss`)
//
// References relocate a unit into one of the four books, addressed by index.
// ---------------------------------------------------------------------------

internal const val JB2_REF = 0b0011_0000
internal const val JB2_REF_PREFIX_MASK = 0b1111_0000

// `bb` - the target book
internal const val JB2_REF_BOOK_MASK = 0b0000_1100
internal const val JB2_REF_BOOK_LOCAL = 0b0000_0000 // 00
internal const val JB2_REF_BOOK_MEMBERS = 0b0000_0100 // 01
internal const val JB2_REF_BOOK_GLOBAL = 0b0000_1000 // 10
internal const val JB2_REF_BOOK_CONST = 0b0000_1100 // 11 (never a normal ref; resolves to undefined)

// `ss` - the index width
internal const val JB2_REF_SIZE_MASK = 0b0000_0011
internal const val JB2_REF_SIZE8 = 0b0000_0000 // +1 byte
internal const val JB2_REF_SIZE16 = 0b0000_0001 // +2 byte
internal const val JB2_REF_SIZE24 = 0b0000_0010 // +3 byte
internal const val JB2_REF_SIZE32 = 0b0000_0011 // +4 byte

// ---------------------------------------------------------------------------
// String (`10vv_vvvv`)
//
// The 6-bit value encodes the size of the string in bytes:
//   0..60 - the embedded size
//   61    - size biased by 61 in next byte (61..316)
//   62    - size unbiased in next 2 bytes
//   63    - size unbiased in next 4 bytes
// ---------------------------------------------------------------------------

internal const val JB2_STRING_SIZE_MASK = 0b0011_1111
internal const val JB2_STRING_SIZE_BYTE = 61
internal const val JB2_STRING_SIZE_SHORT = 62
internal const val JB2_STRING_SIZE_INT = 63
internal const val JB2_STRING_SIZE_BIAS = 61

// ---------------------------------------------------------------------------
// Code-point lead bytes inside a string body
//   0_vvv_vvvv  - 0..127 (ASCII)
//   100_vvvvv   - 128..8319    (13-bit, biased by 128)
//   101_vvvvv   - 8320..2105471 (21-bit, biased by 8320)
//   11_aaa_bbs  - string-reference (see below)
// ---------------------------------------------------------------------------

internal const val JB2_CP_ASCII_MASK = 0b1000_0000 // 0_xxxxxxx
internal const val JB2_CP_2BYTE = 0b1000_0000 // 100_xxxxx
internal const val JB2_CP_3BYTE = 0b1010_0000 // 101_xxxxx
internal const val JB2_CP_2BYTE_MASK = 0b1110_0000
internal const val JB2_CP_2BYTE_BIAS = 128
internal const val JB2_CP_3BYTE_BIAS = 8320

// ---------------------------------------------------------------------------
// String-reference (`11_aaa_bbs`)
//
// Lives inside a string code-point stream. Either 2 byte (s=0, +1 byte index)
// or 4 byte (s=1, +3 byte BE index).
//   aaa - append a special character behind the referenced string (see JB2_ADD_*)
//   bb  - the target book (same encoding as JB2_REF_BOOK_*)
//   s   - 0 small (8-bit index), 1 large (24-bit index)
// ---------------------------------------------------------------------------

internal const val JB2_SREF = 0b1100_0000
internal const val JB2_SREF_PREFIX_MASK = 0b1100_0000
internal const val JB2_SREF_ADD_MASK = 0b0011_1000
internal const val JB2_SREF_ADD_SHIFT = 3
internal const val JB2_SREF_BOOK_MASK = 0b0000_0110
internal const val JB2_SREF_BOOK_SHIFT = 1
internal const val JB2_SREF_SIZE_MASK = 0b0000_0001
internal const val JB2_SREF_SIZE_SMALL = 0b0000_0000 // +1 byte index
internal const val JB2_SREF_SIZE_LARGE = 0b0000_0001 // +3 byte index

// The 7 appendable characters, indexed by the `aaa` field.
internal const val JB2_ADD_NOTHING = 0b000
internal const val JB2_ADD_SPACE = 0b001 // ' '
internal const val JB2_ADD_DOT = 0b010 // '.'
internal const val JB2_ADD_COLON = 0b011 // ':'
internal const val JB2_ADD_COMMA = 0b100 // ','
internal const val JB2_ADD_SEMICOLON = 0b101 // ';'
internal const val JB2_ADD_MINUS = 0b110 // '-'
internal const val JB2_ADD_UNDERSCORE = 0b111 // '_'

/** The appendable character for each `aaa` value, or 0 for [JB2_ADD_NOTHING]. */
internal val JB2_ADD_CHAR = intArrayOf(
    0, ' '.code, '.'.code, ':'.code, ','.code, ';'.code, '-'.code, '_'.code
)

// ---------------------------------------------------------------------------
// Structures (`11ss_tttt`)
//
// `ss` - the width of the size field that follows the lead-in:
//   00 empty (no size field, total size is exactly 1 byte)
//   01 uint8 size
//   10 uint16 size
//   11 uint32 size
// `tttt` - the structure type.
// ---------------------------------------------------------------------------

internal const val JB2_STRUCT_SIZE_MASK = 0b0011_0000
internal const val JB2_STRUCT_SIZE0 = 0b0000_0000
internal const val JB2_STRUCT_SIZE8 = 0b0001_0000
internal const val JB2_STRUCT_SIZE16 = 0b0010_0000
internal const val JB2_STRUCT_SIZE32 = 0b0011_0000

internal const val JB2_STRUCT_TYPE_MASK = 0b0000_1111
internal const val JB2_STRUCT_ARRAY = 0 // 0000
internal const val JB2_STRUCT_MAP = 1 // 0001
internal const val JB2_STRUCT_SET = 2 // 0010
internal const val JB2_STRUCT_OBJECT = 3 // 0011
internal const val JB2_STRUCT_TAGS = 4 // 0100
internal const val JB2_STRUCT_DICTIONARY = 5 // 0101
internal const val JB2_STRUCT_BOOK = 6 // 0110
internal const val JB2_STRUCT_TUPLE_NUMBER_ARRAY = 7 // 0111
internal const val JB2_STRUCT_TUPLE = 8 // 1000
internal const val JB2_STRUCT_TWKB = 9 // 1001
internal const val JB2_STRUCT_BINARY = 10 // 1010
// 11..14 reserved
internal const val JB2_STRUCT_UTF16 = 15 // 1111

// ---------------------------------------------------------------------------
// Book types (the `book_type` header field of a Book structure)
// ---------------------------------------------------------------------------

internal const val JB2_BOOK_LOCAL = 0
internal const val JB2_BOOK_MEMBERS = 1
internal const val JB2_BOOK_GLOBAL = 2
internal const val JB2_BOOK_CONST = 3

// ---------------------------------------------------------------------------
// Tuple predefined member slots (indices into the `members` book)
// ---------------------------------------------------------------------------

internal const val JB2_TUPLE_MEMBER_TN = 0
internal const val JB2_TUPLE_MEMBER_GLOBAL_BOOK_FN = 1
internal const val JB2_TUPLE_MEMBER_NEXT_VERSION = 2
internal const val JB2_TUPLE_MEMBER_ID = 3
internal const val JB2_TUPLE_MEMBER_CUSTOM_START = 4

// ---------------------------------------------------------------------------
// Special version markers (see JBON2.md tuple-number / next_version rules)
// ---------------------------------------------------------------------------

/** The HEAD version sentinel (`9_007_199_254_740_991`, JavaScript `Number.MAX_SAFE_INTEGER`). */
const val JB2_VERSION_HEAD = 9_007_199_254_740_991L

/** The maximum user version (`9_007_199_254_740_987`). */
const val JB2_VERSION_MAX = 9_007_199_254_740_987L

// Masks for the 56-bit / 24-bit special encodings.
internal const val JB2_MASK_56_LOW = 0x00FF_FFFF_FFFF_FFFFL
internal const val JB2_MASK_24_LOW = 0x00FF_FFFF
