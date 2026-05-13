# JBON2
This is version 2 of the **JBON** specification. As the first version was never used in any production system, it seamlessly replaces [version 1](./JBON1.md).

## Introduction
**JBON** is a shortcut for Java Binary Object Notation. In this binary format all values are stored as objects in a tree like structure that can be navigated. It is optimized to reduce the size of the binary, while at the same time being readable without having to parse the data structure. Furthermore, it is intended to play nicely with the [Naksha data model].

The goals of **JBON** are:

- Encode data stored in arbitrary storage systems.
- The same object should result in the same hash and logical bytes, no matter how it is encoded.
- Be compatible with _Java_.
- Be compatible with _JavaScript_.
- Deduplicate data as much as possible to reduce the size.
- Keep the binary as small as possible, while allow reading of the data without parsing.
- Allow efficient caching of the data, especially on the JVM heap _(deduplicated)_.
- Transfer data between services, clients, and storage in a binary safe way.
- Support easy storage of data in databases, on disk or other storage systems.
- Support easy calculation of differences, patching, and application of patches or merging.
- Good cooperation with the [Naksha data model].
- **Keep the decoder stable, while allow improving the encoder over time**
  - This is an  important goal, because we want to store data for years to come, we need a data-format that can improve, while guaranteeing that even decades old decoders can still decode new modern encoded data!

As the format name indicates, this format is object-oriented. All **JBON** data is encoded using _**units**_. All _**units**_ always start with a header. The header encodes the type of the _**unit**_. These are the basic _**unit**_ types:

- `Primitive`: All _**units**_ that encode a fixed size value _(null, integer, float, timestamp, references, ...)_
- `String`: A special _**unit**_ that encodes a list of [UNICODE] code points, optionally including [references] to sub-strings. Strings are split using the [UNICODE] word boundary algorithm from [ICU4J].
- `Binary`: A special _**unit**_ that encodes a types binary as byte-array, i.e. [TWKB].
- `Array`: A list of _**units**_.
- `Map`: A list of key-value pairs.
- `ArrayKind`: An array template with default values, plus some optional annotations.
- `MapKind`: A map template with keys and _(optionally)_ default values, plus some optional annotations.
- `TupleNumber`: A special standardized addressing scheme for [Tuple], defined in the [Naksha data model].
- `Tuple`: A special encoding of a _**unit**_ with some metadata to cooperate with the [Naksha data model].
- `Book`: A special container _(basically an array)_ of _**units**_ that can be referred to.
- `Annotation`: A special _**unit**_ that can be attached to some other _**units**_, to add metadata.

The header of all _**units**_ start with the **lead-in** byte, which identifies the type of the _**unit**_ and in some special cases indicates it's value (one-byte encoding). If **lead-in** byte signals anything except a [primitive] or empty _**unit**_, then it is followed by an unsigned integer encoded in 1, 2 or 4 byte (big-endian) storing the total size of structure/string in byte. This can be used to skip over the _**unit**_ by adding the value to the current offset. Therefore, all _**units**_ always have a size, either implicit or explicit. This allows a decoder to navigate the data without parsing, just by remembering the start of the _**unit**_, next to the offset within the _**unit**_, if the _**unit**_ is navigatable. These values can be encoded in a single long, simplifying a navigation stack implementation.

## Index vs Offset
When this document mentions an `index`, it refers to the position in a structure, for example the element index within an array, map, or string. When this document refers to an `offset`, then it refers to the byte-offset in a JBON, so it is a pointer in the JBON.

## Size vs Length
In this document the term `size` refers to an amount of bytes, so a byte-size, while the term `length` refers to a number of _**units**_. For example the array-length is the amount of elements while the array-size is the amount of byte it requires.

The size is normally used to skip over _**units**_, while the length is used to logically navigate _**units**_.

## Sorting
Whenever data is sorted, the following sort order should be used:

- `undefined`
- `null`
- `false`
- `true`
- Timestamps, sorted by their value, so that older timestamps are before newer timestamps.
- Integers, sorted by their value, so negative integers are before positive integers.
- Floating point numbers, sorted by their value, so negative floats are before positive floats.
  - **Note**: The sorting of floating point numbers is a bit tricky, because of the special values like `NaN`, `Infinity`, and `-Infinity`. The sorting order for these values should be:
    - `-Infinity`
    - All negative floats, sorted by their value.
    - `-0.0`
    - `0.0`
    - All positive floats, sorted by their value.
    - `Infinity`
    - `NaN`
  - The above sort order is compatible with JavaScript and Java, as both languages sort their floating point numbers in this way _(see `Double.compareTo`)_.
- Strings, sorted by their UTF-16 code units, so that the sorting is compatible with JavaScript and Java.
  - **Note**: This is not locale-aware alphabetical sorting.
- All other structures, sorted by their hash, and in case of hash collision, the hash-binary _(the bytes generated for the hash)_ is compared.

Beware that [references] can't be sorted, they always behave exactly like the value to which they refer. So, when a reference to a [string] is given, the sorting is based on the value of the [string], not on the reference itself.

## Hashing
Hashing is part of the **JBON** specification. When all participants hash the same way, the binaries can be easier compared against each other. The trick in hashing **JBON** is that two different binaries can basically represent the same data, for example when they just use different order of members or different encodings. This starts to become more true, when global [books] are shared.

- **All clients _(including storages)_ must be able to calculate the same hash for the same data, even while they encode the data differently!**
- **We do not fix the hash algorithm, so clients can use any hashing algorithm!**

To be able to calculate a hash, the _**unit**_ first needs to be converted into [logical bytes].

## Logical Bytes
To compare two **JBON** _**units**_ logically, they need to be converted into a sequence of comparable bytes, called _logical bytes_. As the **JBON** binary is highly compressed, the same logical data can be encoded in many different ways. Two **JBON** binaries can be logically similar, even while they are binary totally different. This happens for many different reasons, different `global` [books] being used, different `storage` [books], or just different encoders. Therefore, to logically compare two **JBON** _**units**_, all _**child-units**_ of the **JBON** have to be logically serialized and hashed, recursively in a streaming way, in fixed order. Therefore, a uniform logical serialization is needed to compare two **JBON** _**units**_ by value. The **JBON** encoder and decoder can generate such a logical bytes.

In other words: Logically `{a:1,b:2}` is equal to `{b:2,a:1}`, therefore both need to result in the same [logical bytes], and therefore in the same hash.

So, each **JBON** _**unit**_ can be serialized into primary and secondary _logical bytes_. The serialized data can be used to compare two _**units**_ by value andto calculate the [hash] to improve the compare speed. The default hash algorithm is [murmur3], which is implemented as part of the `lib-data` implementation.

- The **primary logical bytes** are used to compare _**units**_ logically.
- The **secondary logical bytes** are used as replacement for `hashCode()` and for `equals()` on the heap, and used by encoders to find matchings.

For example for [strings], the **primary logical bytes** are used for normal hashing, because it guarantees that the binary of a string is never the same as any other value, due to the **lead-in** byte added. However, we often need the _Java_ hash of a [string], which is calculated by converting it into UTF-16 encoding, then hashing the individual 16-bit code units like in _Java_, so, via `s[0]*31^(n-1) + s[1]*31^(n-2) + ... + s[n-1]`, where `s` is the UTF-16 encoded string as characters _(`char[]`)_. This means, there is no **lead-in** added, and therefore the binary can be overlapping with other data types. For example a string that has 5 ASCII code-units can have some overlap with certain integers, when they are encoded into **lead-in** plus 4 byte. However, this **secondary** logical byte representation is only used for the `hashCode()` and `equals()` on the JVM heap.

The default hashing algorithm for **JBON** is a 128-bit [murmur3], used in streaming mode, with option to shorten the hash to 64-bit, 32-bit, 16-bit or 8-bit. This is a simple reference implementation for a streaming [murmur3].

## Units
All _**units**_ have a general concept they follow. The first byte is the `lead-in` byte, signaling the type of the _**unit**_. If the size of the _**unit**_ is not explicitly or implicitly encoded in the `lead-in`, then the `lead-in` is followed by the size of the _**unit**_. The size is encoded in 1, 2 or 4 byte, signaled by the `lead-in`. Dependent on the type of the _**unit**_, more header fields may follow.

Therefore, all _**units**_ have a **unit-size**, which is the total size of the _**unit**_ and equals the amount of byte to skip, when seeking beyond the _**unit**_. Additionally, there is the **value-size**, which is the amount of byte that store the value of the _**unit**_, and the **header-size**, which is the lead-in byte, plus the optional header bytes.

In a nutshell, the decoder should expose these values:

- `buffers`: An array of up to five buffers.
  - `0`: The buffer of the `local` [book].
  - `1`: The buffer for the `storage` [book].
  - `2`: The buffer for the `global` [book].
  - `3`: The buffer for the actual **JBON** being processed, normally a [Tuple].
  - `4`: The buffer for the _attachment_.
- \*`currentBuffer`: The `index` in the `buffers` list of the current buffer, a value between `0` and `3` _(we never enter the attachment buffer)_.
- \*`startOffset`: The offset of the **lead-in** byte of the current _**unit**_ in the current buffer, therefore the start of the unit.
- \*`currentPos`: The relative offset in the value of the _**unit**_.
- `unitSize`: The total outer size of the _**unit**_, bytes to add to `startOffset` to skip beyond the _**unit**_, in effect `unitHeaderSize` plus `unitValueSize`.
- `unitHeaderSize`: The amount of byte that store the header, minimally one byte being the `lead-in`.
- `unitValueSize`: The amount of byte that store the actual _**unit**_ value, can be zero or greater. For some _**units**_ the value is encoded or indicated by the `lead-in` byte, for example `true` or tiny integers, therefore `0` is a valid value size.
- `unitHash`: The logical [murmur3] hash of the _**unit**_. The calculation of this value should only be done lazy, so only when explicitly asked for, because it is expensive to calculate, as the _**unit**_ may have to be iterated recursively.
- `currentOffset`: The offset in the current buffer, equals `startOffset` plus `currentPos`.
- `endOffset`: The offset of the first byte that does not belong to the current _**unit**_, equals `startOffset` plus `unitSize`.

The decoder will always read and decode the header at ones, automatically decoding implicit values. One important consequence of the above design is that we can pack the engine state into a single 64-bit integer, so that a stack can be simply be a `long[]`. So, all the above information can be restored when knowing the `currentBuffer`, the `startOffset`, and the `currentPos`. We only need 4-bit for the `currentBuffer` index _(as we never enter the attachment)_, 31-bit for the `startOffset` and 29-bit for the `currentPos`, resulting in a compact 64-bit integer representation. Therefore, the stack point _(SP)_ is a simple integer used as index within the `long[]`.

When "enter" is executed, the current state is compacted into a `long`, then stored at the current _SP_ index, then _SP_ is incremented by one. When "leave" is executed, it will just decrease the _SP_ by one, read the `long` from stack, and restore the engine state by reparsing the **lead-in** byte.

**Note**: The buffers may overlap, so the whole **JBON** can be stored in a single byte-array, each buffer is just a slice of the actual byte-array. This means, it does not matter if the **JBON** is physically encoded in a single byte-array or split into up to 5 byte-arrays.

## JBON Header
All JBON files start with the string `JBON` followed by the version as big-endian short, so `0x0002`. This is the only header of a JBON file, there is no footer or other metadata. The actual data starts immediately after the header. The header is in big-endian encoding equal to the integer `0x4A424F4E`, followed by a short being `0x0002`. The reason for this design is that this is a valid zero-terminates C string to be shown in clear text editors.

## Lead-in byte
All _**units**_ start with a **lead-in** byte, which describes the actual type of the _**unit**_, and sometimes as well the value:

- `00`: mixed
  - `0000_0000`: **null**
  - `0000_0001`: **undefined**
  - `0000_0010`: Boolean, **false**
  - `0000_0011`: Boolean, **true**
  - `0000_01vv`: Integer _(**int**)_
    - `0000_0100`: Integer, + 1 byte signed integer value _(**int8**)_
    - `0000_0101`: Integer, + 2 byte BE signed integer value _(**int16**)_
    - `0000_0110`: Integer, + 4 byte BE signed integer value _(**int32**)_
    - `0000_0111`: Integer, + 8 byte BE signed integer value _(**int64**)_
  - `0000_10vv`: Floating point _(**float**)_
    - `0000_1000`: _**reserved**_ + 2 byte BE [binary16] floating point value _(**float16**, not yet supported)_
    - `0000_1001`: Float, + 4 byte BE [binary32] floating point value _(**float32**)_
    - `0000_1010`: Float, + 8 byte BE [binary64] floating point value _(**float64**)_
    - `0000_1011`: _**reserved**_ + 16 byte BE [binary128] floating point value _(**float128**, not yet supported)_
  - `0000_1100`: [Timestamp] + 7 byte BE unsigned integer value
  - `0000_1101`: [UInt56], unsigned 56-bit integer, + 7 byte BE unsigned integer value _(**uint56**)_
  - `0000_1110`: [UInt24], unsigned 24-bit integer, + 3 byte BE unsigned integer value _(**uint24**)_
  - `0000_1111`: _reserved_
  - `0001_vvvv`: _reserved_ _(16 values)_
  - `0010_vvvv`: _reserved_ _(16 values)_
  - `0011_bbss`: [Reference]
- `01`: tiny-value
  - `0100_vvvv`: Integer, (0 to 15) _(**int5**)_
  - `0101_vvvv`: Integer, (-16 to -1) _(**int5**)_
  - `0110_vvvv`: Float, (0.0 to 15.0) _(**float5**)_
  - `0111_vvvv`: Float, (-16.0 to -1.0) _(**float5**)_
- `10`: [String]
  - `10ss_ssss`: size 0-60, 61=uint8, 62=uint16, 63=uint32
    - If the size is not embedded (61-63), then the size follows the **lead-in**, encoded as 1, 2 or 4 byte biased unsigned integer (biased by 61), BE encoded.
- `11`: structure
  - `11ss_tttt`
    - ss=0: Empty
    - ss=1: Size is **uint8**, 1 byte unsigned integer size
    - ss=2: Size is **uint16**, 2 byte unsigned integer size
    - ss=3: Size is **uint32**, 4 byte unsigned integer size
    - tttt=0: [Binary]
    - tttt=1: [Array]
    - tttt=2: [Map]
    - tttt=3: [ArrayKind]
    - tttt=4: [MapKind]
    - tttt=5: [TupleNumber]
    - tttt=6: [Tuple]
    - tttt=7: [Book]
    - tttt=8: [Annotation]
    - tttt=9-15: _reserved_

Technically, the **lead-in** byte can be decoded using one big switch statement with 256 cases (128 negative, 128 positive). The negatives are [String] and [Structures], while the positives are [primitives]. Beware that [primitives] must not be replaced with [References]. Therefore, because the [reference] is treated as [primitive], it is not possible to have a [reference] to a [reference], which simplifies the implementation and reduces the possibility of circular [references].

JBON values are always copy-on-write, that means, every modification requires to copy the **JBON**. Therefore, all **JBONs** are immutable _(with one exception, the [Tuple], where the `next_version` can be modified, and it is designed like this)_. Reading in a **JBON** requires a cursor that can be used to move through **JBON** tree. As every _**unit**_ stores it outer size, every _**unit**_ (including all subunits) can be skipped over or entered, by moving the cursor behind the header. Note that only **strings** or **structures** can be entered, all other values are scalars.

There is additionally to these **JBON** encoded values a way to defined raw data, so data without **lead-in**. It is being documented using `byte` for a single real byte, `bytes`, for a dynamic amount of byte, and `byte[{size}]` for a specified amount of bytes.

## Primitives
As described in the **lead-in** section, scalars and fixed size encodings are simple. The size of their encoding is implied by the **lead-in** byte, and sometimes even the value. If not, the value follows directly after the **lead-in** byte, and is always encoded in big-endian encoding.

All **lead-in** bytes between `0` and `127` do represent [primitives].

The [hash] of a primitive is calculated simply above the binary representation of the value like:

- `null`, `undefined`, `false` an `true` are hashed by their **lead-in** byte.
- All floating point numbers are hashed with the **lead-in** byte `0000_0111`, followed by the big-endian encoded 8-byte of the [IEEE-754] binary value.
- All integers are hashed with the **lead-in** byte `0000_0111`, followed by the big-endian encoded 8-byte of the integer value.
- The [uint56] is hashed with the **lead-in** byte `0000_0111`, followed by the big-endian encoded 8-byte of the integer value.
- The [uint24] is hashed with the **lead-in** byte `0000_0111`, followed by the big-endian encoded 8-byte of the integer value.
- The [timestamp] is hashed with the **lead-in** byte `0000_1100`, followed by the big-endian encoded 7-byte of the unsigned integer value _(so as is)_.
- All raw bytes _(`byte`, `bytes`, `byte[{size}]`)_ are hashed as is, no **lead-in** is being used.

This means, that all floating point numbers and integers are hashed as if they were 8 byte values, even when they are actually stored in a smaller encoding. This allows to have the same hash for the same value, even when it is encoded differently, for example `int8` with value `1` and `int64` with value `1` will have the same hash.

Adding the **lead-in** means that [hashing] of the boolean _false_ is different from the [hash] of the integer `0`, which is different from `null`. This guarantees that we do get a different [hash] for them, which is helpful, while the integer `1` is always hashed the same, no matter how it is encoded.

## Timestamp
A timestamp, encoded with a **lead-in** byte `0000_1100`. It encodes a unix epoch timestamp (UTC) in milliseconds, stored in big-endian encoding as 7-byte value following the **lead-in**. Therefore, it belongs to the primitives. We choose this encoding, because a year has 31,536,000,000 milliseconds, therefore 36-bit can encode 2 years, 40-bit encode 34 years, 48-bit encode already 8925 years, with 56-bit encoding around 2 million years, more than enough. Reducing the size from full 8 byte to 7 byte, saves one byte per value, but more significant, it allows to read timestamps atomically as a single 64-bit integer, then binary-ANDing with `0x00FF_FFFF_FFFF_FFFF` to get the timestamp in milliseconds.

Writing the timestamp is as simple, because we only binary-AND the timestamp with `0x00FF_FFFF_FFFF_FFFF`, then binary-OR with `0x0C00_0000_0000_0000`, and eventually write the 64-bit integer using big-endian encoding.

The timestamp is hashed with the **lead-in** byte `0000_1100`, followed by the big-endian encoded 7-byte of the unsigned integer value _(so as is)_.

## UInt56
A 56-bit unsigned integer encoded with a **lead-in** byte `0000_1101`.

This encoding was specifically added to improve the [TupleNumber] encoding, where the `version` part does only uses a 56-bit unsigned integer. Such a `long` value, that only uses the lower 56-bit, will be encoded using the [UInt56] encoding. It is basically the same as the [Timestamp], just for arbitrary values.

Therefore, writing the unsigned 56-bit value is as simple, because we only binary-AND the `long` with `0x00FF_FFFF_FFFF_FFFF`, then binary-OR with `0x0D00_0000_0000_0000`, and eventually write the 64-bit integer using big-endian encoding.

Reading is as simple, because we only read the 64-bit integer using big-endian encoding, then binary-AND with `0x00FF_FFFF_FFFF_FFFF` to get the unsigned 56-bit value.

The uint56 is hashed with the **lead-in** byte `0000_0111`, followed by the big-endian encoded 8-byte of the integer value _(so exactly like a 64-bit integer)_.

## UInt24
A 24-bit unsigned integer encoded with a **lead-in** byte `0000_1110`.

This encoding was specifically added to improve the encoding of the size of structures. Even while it wasts potentially one or two byte per structure, it can simplify the encoder a lot, because it reserves a fixed size to late store the actual structure size, when the encoding is done.

Therefore, writing the unsigned 24-bit value is simple, we only binary-AND the `int` with `0x00FF_FFFF`, then binary-OR with `0x0E00_0000`, and eventually write the 32-bit integer using big-endian encoding.

Reading is as simple, because we only read the 32-bit integer using big-endian encoding, then binary-AND with `0x00FF_FFFF` to get the unsigned 24-bit value.

The uint246 is hashed with the **lead-in** byte `0000_0111`, followed by the big-endian encoded 8-byte of the integer value _(so exactly like a 64-bit integer)_.

## Reference
A reference is used to relocate [structures] or [strings] into [books]. From a decoder perspective, it requires an "enter" instruction, and pushes a return-address to the stack for "leave". Actually, that means a reference is transparent _(technically, entering a references jumps into a [structure] the same way entering the [structure] itself would)_. Therefore, even while it is a [primitive], it works like [structures]. A reference redirects to a value stored in one of the four context related [books]. Actually, it encodes the index in the [book].

**Note**: All [strings] and [structures] can be _(optionally)_ relocated using a _reference_!

The **lead-in** byte has the format `0011_bbss`.

The `bb` bits encode the [book] into which the reference directs:
- 0: `local`
- 1: `storage`
- 2: `global`
- 3: `const`

The `ss` bits encode the size of the index.
- 0: **null** reference _(treated as value `null`)_
- 1: + 1 byte unsigned integer _(**ref8**)_
- 2: + 2 byte BE unsigned integer _(**ref16**)_
- 3: + 4 byte BE unsigned integer _(**ref32**)_

The `const` [book] is a virtual book, it contains certain hard-coded values, like for example some MIME types.

**Beware that references must not refer to references, and that each [book] can only reference itself or a [book] of higher order.** In other words, `const` entries can only refer to them self, references in `global` can only refer to `global` or `const`, references in `storage` can refer to `storage`, `global` and `const`, while references in `local` can refer to all [books]. This reduces the possibility of circular references, and makes detection of them easy.

The **hash** of a reference is calculated by hashing the value it points to, so that the reference itself stays transparent.

## String
A string is technically, from the decoder and logical perspective, a single value. Even while it technically persists out of code-points, can include [references], and can be referenced, the decoder should always just represent them as a single Java `String` instance, using caching.

**JBON** strings are **NOT** encoded using UTF-8, but a special encoding that is smaller and support [references]. The **lead-in** for a string is `10vv_vvvv`. The value (`vv_vvvv`) stores the size of the string in byte:

- `0-60`: The embedded size of the string in byte (0-60).
- `61`: The size is stored biased by 61 in the next byte (61 - 316).
- `62`: The size is stored unbiased in the next two byte (unsigned short), big-endian encoded.
- `63`: The size is stored unbiased in the next four byte (unsigned integer), big-endian encoded.

The code-points are variable encoded like in UTF-8, but shorter (they are all just 1 to 3 byte long). The leading byte of every code-point signals the encoding:

- `0vvv_vvvv`: The value encodes the code point value. Allows values between 0 and 127 (ASCII).
- `100_vvvvv`: The value should be bitwise-ANDed with `000_11111`, then shift-left by 8; the value of the next byte should be bitwise-ORed, and finally 128 should be added. This results in values between 128 and 8319 _(2^13-1+128)_.
- `101_vvvvv`: The value should be bitwise-ANDed with `000_11111`, then shift-left by 16; the value of the next two byte (read big-endian) should be bitwise-ORed, and finally 8320 should be added. This results in values between 8320 and 2,105,471 _(2^21-1+8320)_.
- `11aa_bbss`: The value is a [reference] to a sub-string in one of the three books. This [reference] must not refer to anything but a string.

**Note**: This means we can only encode code points between `0` and `2,105,471`, but this twice the value we need, because the biggest allowed code-point value is `0x10FFFF` aka `1,114,111`!

For embedded [references] the lower four bit _(0..3)_ match the meaning of the same bits in a normal [reference], so they can be processed using the same code. However, bit 4 and 5 encode the append-rule (`aa`). It signals if a special character should be added behind the referred string. The following values are defined:
- `00`: Do not encode any additional character.
- `01`: Add a space (` `) behind the string.
- `10`: Add a dot (`.`) behind the string.
- `11`: Add a colon (`:`) behind the string.

When strings are split by the encoder, it uses the `BreakIterator` from the [ICU4J] library to for that purpose. Pseudocode:

```java
package com.here.example;

import com.ibm.icu.text.BreakIterator;
import com.ibm.icu.util.ULocale;

class SplitDemo {
  static boolean isAppendable(char c) {
    return c == ' ' || c == ':' || c == '.';
  }
  
  static int byteLength(String s) {
    int length = 0;
    for (int i = 0; i < s.length();) {
      final char c = s.charAt(i++);
      int codePoint = c;
      if (isHighSurrogate(c) && i < s.length()) {
        // We need to read the low surrogate as well, to get the actual code point value.
        char low = s.charAt(i);
        if (isLowSurrogate(c)) {
          codePoint = toCodePoint(c, low);
          i++;
        }
      }
      if (codePoint <= 127) {
        length += 1;
      } else if (codePoint <= 8319) {
        length += 2;
      } else if (codePoint <= 2105471) {
        length += 3;
      } else {
        throw new IllegalArgumentException("Code point out of range: " + codePoint);
      }
    }
    return length;
  }
  
  static void demo() {
    final String text = "Hello, don't split 中文 badly.";
    final BreakIterator it = BreakIterator.getWordInstance(ULocale.ROOT);
    it.setText(text);
    int start = it.first();
    int end = it.next();
    int status = it.getRuleStatus();
    String part = end != BreakIterator.DONE ? text.substring(start, end) : null;
    while (part != null) {
      // Preload next
      int next_start = end;
      int next_end = it.next();
      int next_status = it.getRuleStatus();
      String next_part = next_end != BreakIterator.DONE ? text.substring(next_start, next_end) : null;
      
      // Process current, can be empty string, if it is a punctation, and the previous operation appended it.
      // So it was only ` `, `.` or `:`.
      if (byteLength(part) >= 6) {
        if (status >= BreakIterator.WORD_NUMBER && status < BreakIterator.WORD_NUMBER_LIMIT) {
          // number, copy into book
          // We can check the next part, if it is punctuation and starts with an appendable character
          // If so, we can remove the appendable from the next part, and set the corresponding bits in the reference
        } else if (status >= BreakIterator.WORD_LETTER && status < BreakIterator.WORD_LETTER_LIMIT) {
          // letter word, copied into book
          // We can check the next part, if it is punctuation and starts with an appendable character
          // If so, we can remove the appendable from the next part, and set the corresponding bits in the reference
        } else if (status >= BreakIterator.WORD_KANA && status < BreakIterator.WORD_KANA_LIMIT) {
          // kana, copied into book
          // We can check the next part, if it is punctuation and starts with an appendable character
          // If so, we can remove the appendable from the next part, and set the corresponding bits in the reference
        } else if (status >= BreakIterator.WORD_IDEO && status < BreakIterator.WORD_IDEO_LIMIT) {
          // ideograph, copied into book
          // We can check the next part, if it is punctuation and starts with an appendable character
          // If so, we can remove the appendable from the next part, and set the corresponding bits in the reference
        } else {
          // punctuation / space / non-word
          // directly encoded into string, we expect that it does not repeat that often.
        }
      } else if (!part.isEmpty()) {
        // directly encoded into string.
      }
      // Load next to current.
      start = next_start;
      end = next_end;
      status = next_status;
      part = next_part;
      // Load next.
      next_start = end;
      next_end = it.next();
      next_status = it.getRuleStatus();
      next_part = next_end != BreakIterator.DONE ? text.substring(next_start, next_end) : null;
    } 
  }
}
```

As a [reference] consume 2, 3 or 5 byte, it does not make sense to use a [reference] for every part. When the data can be encoded at same length or close, we do not want to use [references]. Therefore, we do not encode a [reference] for a string that is not at least 6 byte long in encoded form _(the above code example shows this)_. The encoder can optimize here, so if the referenced value is well known to be encoded in less byte than the reference will need, it can still use a reference.

**Note**: The appendable-bits (`aa`) improve the compression rate, because the encoder will split strings mostly at a spaces, dots, or colons. Exactly where these splits happen, we do not need to encode these separation characters. The reason to cut at these characters is that most often street-names or other human text uses the space as separator. The dot is often used in [JSON] paths, domain names, and human text. Finally, the colon is often used in URL's, URN's and other structured Web data.

#### Primary Logical Bytes
The primary [logical bytes] of a string start with the empty **lead-in** byte `1000_0000`, followed by the UTF-16 encoded _(big-endian)_ code-units. If there are [references] embedded, then the content of the [reference] is added, so that the [references] stay transparent. This means that the [hash] of a string is independent of how it is actually encoded, and only depends on the actual code-units of the string.

#### Secondary Logical Bytes
The secondary [logical bytes] are generated exactly the same, just that the **lead-in** is left away, so that a standard _Java_ hash can be calculated. So, `s[0]*31^(n-1) + s[1]*31^(n-2) + ... + s[n-1]`, where `s` is the UTF-16 encoded string as `char[]`.

## Null
The value `null` is a normal value, encoded as `0000_0000`. In this document all types are annotated with a question mark _(`?`)_ when they are allowed to be `null`. This includes _**unit**_, therefore `[unit]` means any value, except for `null`, while `[unit]?` means any value, including `null`. The same is true for other types like `int32`, which means the integer must be encoded, while `int32?` means that the value can be either an integer with maximum 32-bits, or `null`.

## Undefined
The `undefined` value is a special value, that is either set explicit by encoding the **lead-in** `0000_0001`, or deducted implicitly. For example, when an [array] should have 5 elements, but it has only 1, then the last four elements are implicitly `undefined` _(deducted from context/situation)_. If the first element is explicitly set to `undefined`, then all elements are `undefined`.

The meaning of `undefined` is context dependent, but often used to refer to some default values or states. Generally it is important to understand that `undefined` is a valid value and often has some special handling. It is different from `null` in that the value `null` is always explicit _(encoded as `0000_0000`)_, while the value `undefined` can be explicit _(`0000_0001`)_ or implicit, deducted by not being available or from context/situation.

**Important**

Specifically, and this is by design, when the total size of a [structure] is less than the attributes defined, then all attributes that are not explicitly encoded, are implicitly `undefined`! This allows to truncate all [structures] and does not require to encode attributes not needed or that should be default.

## Structures
The **lead-in** of all structures start with the top most two bit _(bit 7 and 6)_ set (`11`) and the basic format: `11ss_tttt`. The bit 5 and 4 (`ss`) encode the size of the size of the structure:

- `0`: Empty _(this means by definition that the total byte size is exactly 1, because of the lead-in byte)_
- `1`: Size is **uint8**, 1 byte unsigned integer size
- `2`: Size is **uint16**, 2 byte unsigned integer size
- `3`: Size is **uint32**, 4 byte unsigned integer size

If it is not empty, and the lowest four bit (`tttt`) encode the type of the structure.

For the `Type` column in the following structure tables the maximum allowed type is used. The **lead-in** is always a single byte of type `byte`, while all other values are **JBON** flexible encoded types, for example **int32** for size means that the size is encoded as integer with maximal size of 32-bit, so that the value `0` can be actually encoded as `int5`. A question mark _(`?`)_ behind a type means that the value is nullable, so `null` can be stored instead of the actual value. If that is not the case, the value must not be `null`, nor a [reference] to `null` or a `null`-reference are allowed. Beware that all [string] and [structures] can always be replaced with a [reference] to relocate the _**unit**_ into a [book].

### Binary
The binary structure is used to store binary content, actually byte-arrays of custom data. They are used for example to encode [TWKB]. The **lead-in** of a binary is `11ss_0000`; with `ss` encoding the size of the size, as usual. The binary format is like:

| Name        | Type      | Description                                                                                     |
|-------------|-----------|-------------------------------------------------------------------------------------------------|
| lead_in     | `byte`    | The **lead-in** byte, `11ss_0000`.                                                              |
| byte_size   | `int64`   | The total size of the structure, including the **lead-in**, in bytes.                           |
| mime_type   | [string]  | The MIME-Type of the binary, defaults to `application/octet-stream` from `const` book.          |
| parameters  | [map]?    | Additional _(optional)_ paramters, the [map] must use only [strings] as key and value.          |
| compression | [string]? | The compression algorith used; `null` if not compressed.                                        |
| target_size | `int64`?  | If a compression algorithm is used, the amount of decompressed bytes _(for buffer allocation)_. |
| data_size   | `int64`   | The amount of bytes following that represent the binary.                                        |
| data        | `bytes`   | The bytes of the binary.                                                                        |
| tail        | `bytes`   | Arbitrary tail bytes _(padding)_.                                                               |

The [JSON] and [XML] the binary is encoded as a string using the [data URL scheme]. So, in the format `data:[<media-type>][;<parameter]*[;base64],<data>`, like `data:application/twkb;base64,{encoded-data}`. The `mime_type` is encoded as [media-type] of the [data URL]. The `parameters` are added as parameters to the [media-type], the general form is `{type}/{subtype};[parameter=value;]*base64,{data}`. For example, a binary using some parameters, with `mime_type` being `application/foo` can look like: `data:application/foo;content-encoding=GZIP;charset=UTF-8;data,eyJ4IjoxfQ==`. As mostly all the values are part of the [const] book, the [data URL] is only a few bytes extra, compared to the actual binary data.

```javascript
async function dataUrlToUint8Array(dataUrl) {
  const res = await fetch(dataUrl);
  const buf = await res.arrayBuffer();
  return new Uint8Array(buf);
}
var data = dataUrlToUint8Array("data:application/foo;data,eyJ4IjoxfQ==");
var test = new URL("data:application/foo;content-encoding=GZIP;charset=UTF-8;data,eyJ4IjoxfQ==")
```

The [MIME type] parameter is used to identify the type of the binary, normally values from the [IANA media types] are used. If no official MIME type is available, an own one should be used. For example HERE will use `application/twkb` for TWKB binaries, and `application/jbon` for **JBON** binaries. If no MIME type is available, `application/octet-stream` should be expected, resulting in a simple `byte[]`.

There are pre-defined well-known parameters, for which in the [const] book there are pre-defined values:

- `mime_type`: The [mime type] of the data
- `encoding`: The compression algorithm , encoding, and charset.

#### Primary Logical Bytes
The primary [logical bytes] of a binary are calculated by adding the empty **lead-in** `1100_0000`, followed by `parameters`, and finally by the decompressed `data`. This ignores the `compression`, `byte_size`, `data_size`, and `tail`.

#### Secondary Logical Bytes
The secondary [logical bytes] of a binary are calculated by adding the empty **lead-in** `1100_0000`, followed by `mime_type`, `compression`, `encoding`, `charset`, `data`, and `tail`. This actually represents the [logical bytes] of the binary as it is encoded.

### Array
An array of arbitrary other _**units**_, using the **lead-in** byte `11ss_0001`; with `ss` encoding the size of the size, as usual.

| Name      | Type              | Description                                                           |
|-----------|-------------------|-----------------------------------------------------------------------|
| lead_in   | `byte`            | The **lead-in** byte, `11ss_0001`.                                    |
| byte_size | `int64`           | The total size of the structure, including the **lead-in**, in bytes. |
| kind      | [ref]<[Array]>?   | An _(optional)_ reference to another array to be used as template.    |
| values    | [array]?<[unit]?> | The _**units**_ encoding the array values.                            |

If the `byte_size` is zero _(**lead-in** is `1100_0001`)_, the array is empty (`[]`).

If the `kind` of the array is not `undefined` or `null`, the default values of the [ArrayKind] are defining the minimum length of the array. If a value in the `values` is `undefined` _(explicitly or implicit)_, then the default value is used. Beware that the order of the default values of the [kind] matches the `values` order. Therefore, providing an [ArrayKind] will set a minimum lnegth for the array, and potentially already all values.

#### Primary Logical Bytes
The primary [logical bytes] of the array are calculated by adding the empty **lead-in** `1100_0001` _(empty array)_, followed by all `values` in order, if there are any. If any value is `undefined`, the default value from the [ArrayKind] is used, if there is any. So, the default values have to be treated as if they were embedded, so they need to be added to the [logical bytes] the same way that real embedded values are. This ensures that two equal arrays produce the exact same [logical bytes], no matter if they were encoded using an [ArrayKind] or not.

### Map
A map is a key-value store with the **lead-in** byte being `11ss_0010`; with `ss` encoding the size of the size, as usual.

| Name      | Type              | Description                                                                                                              |
|-----------|-------------------|--------------------------------------------------------------------------------------------------------------------------|
| lead_in   | `byte`            | The **lead-in** byte, `11ss_0010`.                                                                                       |
| byte_size | `int64`           | The total size of the structure, including the **lead-in**, in bytes.                                                    |
| kind      | [ref]<[MapKind]>  | A reference to the [MapKind] of the map.                                                                                 |
| values    | [array]?<[unit]?> | An _(optional)_ array with the values of the members, in the same order as the keys and default values of the [MapKind]. |

If the `byte_size` is zero _(**lead-in** is `1100_0010`)_ , this implies an empty map (`{}`).

If the `values` array is `undefined`, or `byte_size` does not leave any room for values, or the array is simply too short compared to the members of the [MapKind], then the missing values are set to the default values of the [MapKind]. This can be used as a trick to save space when encoding, so the encoder moves those members backwards in the member list of the [MapKind], that are mostly kept at their default value. In a nutshell: Members rarely changed should be located at the end of the member list in an [MapKind].

**Notes**
- To stay [JSON] compatible, keys of the map should be [string] only.

#### Primary Logical Bytes
The primary [logical bytes] of the map are created by adding the empty **lead-in** `1100_0010`, followed by all members, in ascending order. Therefore, first an internal key list is created, then [sorted] ascending. Afterward, all keys are iterated, adding the key to the [logical bytes], then the value. If default values of a member from the [MapKind] is used, it has to be treated as if it was embedded; so default values need to be added to the [logical bytes] just as if they would have been a directly encoded as key and value. This is done to ensure that two maps always generate the same [logical bytes], when they contain the same data; independent of the encoding and key order. Note that by sorting the keys, we ensure that the order of the keys do not matter for the [logical bytes], which is important so that `{a:1,b:2}` actually equals `{b:2,a:1}`. This means, effectively the [MapKind] is transparent for the [logical bytes].

#### Secondary Logical Bytes
The secondary [logical bytes] of a map are created by adding all keys, without the values. The keys are [sorted] in ascending order, before being added to the [logical bytes]. This is done to ensure that two maps with the same keys, but different values, generate the same secondary [logical bytes], which is important for encoding, because it allows to find a good fitting [kind] for a map. In a nutshell, the secondary [logical bytes] do only contain the keys, no values.

### ArrayKind
An _ArrayKind_ is a set of default values; the **lead-in** is `11ss_0011`, with `ss` encoding the size of the size, as usual.

| Name        | Type                   | Description                                                           |
|-------------|------------------------|-----------------------------------------------------------------------|
| lead_in     | `byte`                 | The **lead-in** byte, `11ss_0011`.                                    |
| byte_size   | `int64`                | The total size of the structure, including the **lead-in**, in bytes. |
| values      | [array]<[unit]>        | An array of all values of this kind.                                  |
| name        | [string]?              | The _(optional)_ name of the kind.                                    |
| annotations | [array]?<[annotation]> | The _(optional)_ array of annotations for the kind.                   |

This design allows to have multiple kinds with different default values for the same type, which improves compression in certain cases, as some [arrays] do only occur in a handful of variations. Having all of them in a `global` [book] grows the `global` [book], but allows to reduce all their occurrences using just an _ArrayKind_ reference, without encoding individual values _(which often allows compression down to less than 1%)_. A larger `global` [book] is normally not really harmful, because [books] are immutable, and they are used for huge amount of objects, so there are only a few [books] for billions of [arrays], making this highly efficient.

The annotations should only be used in `global` or `storage` [books]. They can consume plenty of space, but may carry information for the application, UI, or others.

**Note**: Remember, a _**kind**_ is **NOT** a type like a class! There can be multiple _**kinds**_ for the exact same type, by intention. The _**kind**_ is more like a template for an object, with the goal to deduplicate data. It is used to save space when encoding, even while it allows adding annotations as hints. Therefore, do not think of a _**kind**_ as a class!

#### Primary Logical Bytes
An _ArrayKind_ is a transparent _**unit**_ the very same way [reference] is, and therefore should never be part of the normal [logical bytes].

However, the _ArrayKind_ has an own primary [logical bytes] representation. The order of the default values is relevant for the [logical bytes] of the _ArrayKind_ itself, they are processed in natural order. First the empty **lead-in** `1100_0011` is added into the [logical bytes], then the default values are added in the given order. If a `name` is given, it is added next, in any case the string is terminated with an ASCII-0. If annotations are given, they are added after the name.

The primary [logical bytes] will ignore the `name` and `annotations` of the kind. This results in [logical bytes] for the _ArrayKind_ where the order is significant, and [logical bytes] for [maps] where the order of the [members] is not significant. It allows to quickly find similar kinds.

#### Secondary Logical Bytes
An _ArrayKind_ is a transparent _**unit**_ the very same way [reference] is, and therefore should never be part of the normal [logical bytes].

However, the _ArrayKind_ has an own primary [logical bytes] representation. The order of the default values is relevant for the [logical bytes] of the _ArrayKind_ itself. They are processed in natural order. First the empty **lead-in** `1100_0011` is added into the [logical bytes], then the default value is added.

The primary [logical bytes] will ignore the `name` and `annotations` of the kind. This results in [logical bytes] for the _ArrayKind_ where the order is significant, and [logical bytes] for [maps] where the order of the [members] is not significant. It allows to quickly find similar kinds.


The secondary [logical bytes] are only supported for [map] kinds, so when all [members] have a `key`. It is calculated by adding the keys of the [members], [sorted] ascending. The values and all other attributes are ignored for the secondary [logical bytes]. The secondary [logical bytes] are only available if all [members] have a `key`, so for [maps]. The secondary [logical bytes] allows to quickly find similar kinds of similar [maps]. So, it finds kinds with the same members, even when the values are different, which is helpful for encoding. It is normally used by the encoder to find a good kind for a [map] to be encoded. Many encoders do first generate plenty of kinds with each having different default values, to later compact them to a reduced set. It is part of encoder optimizations to find the best fitting kind for an object, so that the default values of the [members] are safe as much as possible.

**Note**: The secondary [logical bytes] of a [kind] are the same as the primary [logical bytes] of a [map], this is done by intention, so encoders can find matches.

### MapKind
A kind is a set of [members] with additional metadata like a name and annotations; the **lead-in** is `11ss_0100`, with `ss` encoding the size of the size, as usual.

| Name        | Type                   | Description                                                           |
|-------------|------------------------|-----------------------------------------------------------------------|
| lead_in     | `byte`                 | The **lead-in** byte, `11ss_0100`.                                    |
| byte_size   | `int64`                | The total size of the structure, including the **lead-in**, in bytes. |
| keys        | [array]<[string]>      | An array of all keys of this kind.                                    |
| values      | [array]<[unit]>        | An array of all values of this kind.                                  |
| name        | [string]?              | The _(optional)_ name of the kind.                                    |
| annotations | [array]?<[annotation]> | The _(optional)_ array of annotations for the kind.                   |

This design allows to have multiple kinds with different default values for the same type, which improves compression in certain cases, as some [maps] do only occur in a handful of variations. Having all of them in a `global` [book] grows the `global` [book], but allows to reduce all their occurrences to [maps] with just a [kind] references without encoding individual values _(which often allows compression down to less than 1%)_. A larger `global` [book] is normally not really harmful, because [books] are immutable, and they are used for huge amount of objects, so there are only a few [books] for billions of [maps], making this highly efficient.

The annotations should only be used in `global` or `storage` [books]. They can consume plenty of space, but can carry important information for the application.

**Note**: Remember, a _**kind**_ is **NOT** a type like a class! There can be multiple _**kinds**_ for the exact same [map] or [array], by intention. The _**kind**_ is more like a template for an [map] or [array], with the goal to deduplicate data. It is used to save space when encoding, even while it allows adding annotations as hints. Therefore, do not think of a _**kind**_ as a class!

#### Primary Logical Bytes
A _**kind**_ is a transparent _**unit**_ the very same way [reference] is, and therefore should never be part of any [map] or [array] hash.

However, the **kind** has an own primary [logical bytes] representation. When all [members] of the kind do have a `key`, then first all [members] are [sorted] ascending by `key`. Otherwise _(when this kind is for an [array])_, the order of the [members] is relevant for the [logical bytes], and they are processed in natural order. Eventually the _(optionally [sorted])_ members are iterated. First the empty **lead-in** `1100_0011` is added into the [logical bytes]. If this is a [map] kind, so all [members] have keys, the `key` of the member is added first to the [logical bytes]; otherwise the `key` is ignored. Then the default value of the member is added.

The primary [logical bytes] will ignore the `name` and `annotations` of the kind. This results in [logical bytes] for [arrays] where the order is significant, and [logical bytes] for [maps] where the order of the [members] is not significant. It allows to quickly find similar kinds.

#### Secondary Logical Bytes
The secondary [logical bytes] are only supported for [map] kinds, so when all [members] have a `key`. It is calculated by adding the keys of the [members], [sorted] ascending. The values and all other attributes are ignored for the secondary [logical bytes]. The secondary [logical bytes] are only available if all [members] have a `key`, so for [maps]. The secondary [logical bytes] allows to quickly find similar kinds of similar [maps]. So, it finds kinds with the same members, even when the values are different, which is helpful for encoding. It is normally used by the encoder to find a good kind for a [map] to be encoded. Many encoders do first generate plenty of kinds with each having different default values, to later compact them to a reduced set. It is part of encoder optimizations to find the best fitting kind for an object, so that the default values of the [members] are safe as much as possible.

**Note**: The secondary [logical bytes] of a [kind] are the same as the primary [logical bytes] of a [map], this is done by intention, so encoders can find matches.

### TupleNumber
A tuple is a unique immutable state of some arbitrary feature, uniquely addressed using a tuple-number. The tuple-number encoding can represent a single tuple-number or a list of tuple-numbers. It is a specialized data encoding with shared upfront data; the **lead-in** is `11ss_0101`.

This form of encoding reduces the encoding size of multiple tuple-numbers greatly, while only mildly increases the size of a single tuple-number _(which we rarely every find anywhere)_. However, multiple array numbers are encountered quite often, for example when transferring the result of a database query to a client. Therefore, we want to encode them very efficiently _(as small as possible)_:

| Section           | Type    | Description                                                                                                                         |
|-------------------|---------|-------------------------------------------------------------------------------------------------------------------------------------|
| lead_in           | `byte`  | The **lead-in** byte, `11ss_0101`.                                                                                                  |
| byte_size         | `int64` | The total size of the structure, including the **lead-in**, in bytes.                                                               |
| database_number   | `int64` | Either `null` (one byte) or the shared database number of each [tuple] in the array.                                                |
| catalog_number    | `int32` | Only if `database_number` is not `null`, then either `null` (one byte) or the shared catalog number of each [tuple] in the array.   |
| collection_number | `int32` | Only if `map_number` is not `null`, then either `null` (one byte) or the shared collection number of each [tuple] in the array.     |
| feature_number    | `int64` | Only if `collection_number` is not `null`, then either `null` (one byte) or the shared feature number of each [tuple] in the array. |
| entries           | `bytea` | The actual tuple-numbers encoded as specified in the [Naksha data model Tuple-Number] section, except for the shared components.    |

Therefore:
- If `database_number` is `null`, then each entry is encoded in 32 byte.
- If `catalog_number` is `null`, then each entry is encoded in 24 byte, all of them are sharing the same `database`.
- If `collection_number` is `null`, then each entry is encoded in 20 byte, all of them are sharing the same `database` and `catalog`.
- If `feature_number` is `null`, then each entry is encoded in 16 byte, all of them are sharing the same `database`, `catalog` and `collection`.
- If none of them is `null`, then each entry is encoded in 8 byte, all of them refer to the same _feature_, just in different versions.

So, if `database_number`, `catalog_number`, `collection_number` and `feature_number` are all given, all [Tuple] are of the same _feature_, so they only differ in the `version`. This happens for example when loading all states of a specific _feature_ form the database. This uses the least amount of space per entry, only 8 byte per entry.

The most common encoding will have `database_number`, `catalog_number` and `collection_number` set, but `feature_number` being `null`. This happens as result of a query from a single collection. In this case, each entry need to encode `feature_number`, and `version`, which requires 16 byte per entry.

The second most common encoding will have `database_number` and `catalog_number` set, but `collection_number` and `feature_number` will be `null`, then each entry is 20 byte.

Potentially rarely found are encodings where `catalog_number` or even `database_number` are `null`, as this wildly mixes data from different sources. However, it is not totally impossible!

**Note**: A single tuple-number can be encoded smaller as defined by the [Naksha data model Tuple-Number], because we can encode the `database_number`, `catalog_number`, `collection_number` and `feature_number` in less than 8 byte, if the value is smaller. Therefore, the smallest single tuple-number would encode in **lead-in** _(1 byte)_, `byte_size` _(1 byte)_, `database_number` _(1 byte)_, `catalog_number` _(1 byte)_, `collection_number` _(1 byte)_, `feature_number` _(1 byte)_, and the `version` as single value _(8 byte)_; therefore, resulting in 14 byte in total, while the [Naksha data model Tuple-Number] encoding does always require 32 byte.

A single tuple-number should be encoded in 35 byte, so **lead-in** _(1 byte)_, `byte_size` _(1 byte)_, `database_number` as `null` _(1 byte)_ and the actual tuple-number value as full qualified _(32 byte)_. Optionally, the `byte_size` can be encoded in two byte, which align the rest of the tuple-number to 4 byte, so that it can be faster processed by some CPU architectures. This would result in 36 byte for a single tuple-number.

#### Primary Logical Bytes
The primary [logical bytes] of a tuple-number are generated by adding the empty **lead-in** `1100_0101`, followed by the bytes of each contained tuple-number in full encoding as specified by the [Naksha data model Tuple-Number]. Each tuple-number actually is added as fixed size 32 byte value in big-endian byte-order. This means, that for each tuple-number the `database_number`, `catalog_number`, `collection_number`, and `feature_number` are added to the [logical bytes] simply as 32-bit or 64-bit integers, in big-endian byte-order. The `version` is encoded into one 64-bit integer -(with top 12 bit being always zero)_, and then added to the [logical bytes].

### Tuple
The tuple is a special **JBON** container designed to exchange [GeoJSON] _features_ between services, components, caches, and storages like a database or a file; the **lead-in** is `11ss_0110`.

The tuple is a special encoding linked to the [Naksha data model]. All tuple are encoded in the following basic layout:

| Section        | Type                   | Description                                                                                                                   |
|----------------|------------------------|-------------------------------------------------------------------------------------------------------------------------------|
| lead_in        | `byte`                 | The **lead-in** byte, `11ss_0110`.                                                                                            |
| byte_size      | `int64`                | The total size of the structure, including the **lead-in**, in bytes.                                                         |
| feature        | [map]                  | The _feature_ to be stored, can be an empty map _(so only one byte)_.                                                         |
| local_book     | [Book]?                | The _(optional)_ `local` book.                                                                                                |
| annotations    | [array]?<[annotation]> | An _(optional)_ array of [annotations].                                                                                       |
| attachment     | [Binary]?              | If `null`, the attachment is explicitly not existing, `undefined` means a context related _attachment_ state.                 |
|                |                        |                                                                                                                               |
| next_version   | [uint56]               | The next version of the tuple, if the tuple is in _HEAD_ state, the value will be `9_007_199_254_740_991L`.                   |
| prev_version   | `int64`?               | The previous version of the tuple; `null` _(one byte)_, if this is the first tuple _(in `CREATE` action)_.                    |
| tuple_number   | [TupleNumber]          | The [tuple-number] of this tuple.                                                                                             |
| id             | [string]?              | The identifier of this tuple, if the _feature-number_ is no positive integer; otherwise `null`.                               |
| global_book_tn | [TupleNumber]?         | Either `null` (one byte) when no global book is needed; otherwise the [Tuple-Number] of the `global` [book] needed to decode. |
| storage_book   | [Book]?                | The `storage` book with values from the storage.                                                                              |

The `attachment` is special when encoded as `undefined`. The meaning has to be interpreted by the application considering the context in that case. When the tuple is encoded as an `UPDATE` or `DELETE` action, then an `undefined` attachment means that the attachment is not changed, so the old attachment should be kept. When the tuple is encoded as a `CREATE` action, then `undefined` means that there is no attachment, so it should simply become `null`.

The layout of this entity has a specific reason. The principle is that the application does not generate a tuple, but operates on [GeoJSON] features with optional attachment. The storage converts the _feature_ and attachment into a tuple. However, when reading, the application can directly access the tuple, what avoids converting from tuple into [GeoJSON] feature. So the design is that tuples are easy to read for everyone, but creation is storage individual.

When a _feature_ is given to the storage engine, it will decide which values from the _feature_ [map] should be offloaded into dedicated places, like own columns in the database table or dedicated helper tables. This is done by relocating the values from the actual `feature`, into the `storage` [book], and to let the rest be added like normal into the `local` [book] or `global` [book]. As every storage, even when storing the same data, may have a different storage layout, this means that tuples read from one storage must be re-encoded when being written into another one, except it is a cache. A cache can just store the tuple as is, because it anyway only need to cache the binary and addresses it by the [TupleNumber].

When a replication is done, it depends on the implementation. Technically, when the physical layout of the replica is exactly the same as the origin, then the same tuple can be stored. However, physical replication is rather rare, so the use case is more exotic.

In the reference implementation using PostgresQL or SqLite the storage engine will not accept tuples as input. It will only accept the `feature` as [JSON] map, and the `attachment` as `byte[]` _(with some metadata, so in a wrapper)_. Then it will directly encode the tuple by itself. Finally, it will truncate the tuple before the `attachment`, and store all the truncated values in dedicated database columns. It will split the `storage_book` as well into own dedicated columns, and fill a helper table for the `tags`. The design is intentionally made, so that a database can index values, while a pure cache just takes the tuple as is, and stores it. Every storage can basically restore the `object` and `attachment` from a tuple, and then re-encode the tuple so that it is optimized for the storage. Even while this costs some CPU time, it allows to have a very efficient storage, and makes all replicas very efficient, and optimized. It is actually a form of logical replication.

When reading back a tuple from the storage, the PostgresQL storage-engine re-constructs the full tuple binary from the database table. It will restore the basic tuple form the table, appending the `attachment` again, it as well knows the final byte-size. Then it will restore the `global_book_tn`, `tuple_number`, `next_version`, and `prev_version` from database columns. The `storage_book` is restored from custom columns, while the tags are restored from a helper table. The resulting tuple contains all data again, without that it has to be re-encoded. All of this is just copy values into a fixed size buffer, where the size is already well known.

This concept makes reading of data very efficient and quite simple, while writing is slightly more expensive. The design of [books] is very supportive for this design.

#### Primary Logical Bytes
The primary [logical bytes] of a tuple are calculated by adding the empty **lead-in** `1100_0110`, then the `feature`, `annotations`, and finally the `attachment`.

#### Secondary Logical Bytes
The secondary [logical bytes] of a tuple are calculated by dding the empty **lead-in** `1100_0110`, then the `feature`, ignoring the `annotations`, and the `attachment`.

In some use cases the secondary [logical bytes] may be preferable, for example for [JSON] clients, which do not receive the `attachemnt` or `annotaions`. Inside the database however, and for caches, the `annotations` and `attachment` are significant, so the primary [logical bytes] are more useful.

### Book
A book is a special data container; the **lead-in** is `11ss_0111`.

All **JBON**'s have four books used for encoding and decoding, named `local`, `storage`, `global`, or `const`. Each book has a specific purposes.

| Book      | Description                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             | References                                  |
|-----------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------|
| `local`   | The `local` book encodes all the data that is accessed via references from the encoded **JBON**. Some things require references, like [Map] encoding, therefore a **JBON** always requires a `local` book.                                                                                                                                                                                                                                                                                                              | to `local`, `storage`, `global` or `const`. |
| `storage` | The `storage` book is used by storage-engines to offload data from the `local` or `global` book into the storage. For example, when a **JBON** is stored in a database, and some data should be indexed, then this data need to be stored in a dedicated table column. The storage-engine therefore will move the data into the `storage` book, and remove it from the `local` book. The storage-engine later can add this data back without reading the whole **JBON**, because it has the data in the `storage` book. | to `storage`, `global` or `const`.          |
| `global`  | The `global` book is located outside of the **JBON**, it is shared between multiple **JBONs**.                                                                                                                                                                                                                                                                                                                                                                                                                          | to `global` and `const`.                    |
| `const`   | The `const` book is hardcoded in the **JBON** specification, as the name suggests it is constant.                                                                                                                                                                                                                                                                                                                                                                                                                       | to `const` only.                            |

The content of a book is basically just an array of arbitrary _**units**_, referred to by other _**units**_ using a [reference]. The [reference] does use the index in the book, not the byte-position, to address a _**unit**_ within a book.

The layout of a book is as follows:

| Section     | Type                   | Description                                                           |
|-------------|------------------------|-----------------------------------------------------------------------|
| lead_in     | `byte`                 | The **lead-in** byte, `11ss_0111`.                                    |
| byte_size   | `int64`                | The total size of the structure, including the **lead-in**, in bytes. |
| book_id     | [String]?              | Either _null_ (one byte) or the identifier of this book.              |
| book_tn     | [TupleNumber]?         | Either _null_ (one byte) or the [Tuple-Number] of this book.          |
| entries     | [array]<[unit]>        | An array of arbitrary values.                                         |
| annotations | [array]?<[annotation]> | The _(optional)_ array of [annotations] for the book.                 |

Note that `book_id` and `book_tn` can be both present, only one of them, or none of them, for example `local` books do not have a `book_id` or `book_tn`, while `global` books normally always have at least a `book_tn`.

#### Primary Logical Bytes
A book is transparent and therefore not part of the [logical bytes] of any [map] or [array].

The primary [logical bytes] are calculated by adding the empty **lead-in** `1100_0111`, then the `book_id`, then the `book_tn`, followed by all `entries` in order, eventually adding the `annotations`.

#### Secondary Logical Bytes
A secondary [logical bytes] are calculated by adding the empty **lead-in** `1100_0111`, followed by all `entries` in order, ignoring the `byte_size`, `book_id`, `book_tn`, and the `annotations`. This can be used to find similar books, and is helpful for encoders.

### Annotation
An annotation is a special purpose object; the **lead-in** is `11ss_1000`.

Annotation are planed to be used to transport meta information, like range limits for values or rendering instructions. They are not yet fully specified, but the basic layout is as follows:

| Section   | Type                   | Description                                                                       |
|-----------|------------------------|-----------------------------------------------------------------------------------|
| lead_in   | `byte`                 | The **lead-in** byte, `11ss_1000`.                                                |
| byte_size | `int64`                | The total size of the structure, including the **lead-in**, in bytes.             |
| id        | [String]?              | Either _null_ (one byte) or the identifier of this annotation.                    |
| value     | [unit]?                | The main annotation value, can be anything, even `null`.                          |
| tail      | `bytes`                | Additional space, not yet specified, reserved for version 3 of the specification. |

Therefore, decoders can decode them, except for the unspecified `tail`. However, it is yet not clear, how exactly they are used. It is recommended to not support annotations in encoders until the specification is complete.

#### Logical Bytes
An annotation is transparent and therefore not part of the [logical bytes] of any [map] or [array], it must not be added into them.

The [logical bytes] of an annotation is calculated by adding the empty **lead-in** `1100_1000`, then the `id`, followed by the `value`, and eventually adding the `tail`. As the `tail` is not yet specified, it is currently ignored for the [logical bytes].

**WARNING**: It is not recommended to use annotations, until the specification is complete!

## Why not CBOR
This section explains why [CBOR] was not selected. The formats are similar in many points, when you read the two specification. So, why do something new? The major two difference between them are:

### Size
**JBON** supports de-duplication, especially for strings and objects, which decreases the size of the data. Compared to **CBOR**, which actually increases the size of data and just makes it binary readable. **JBON** not only allows to de-duplicate strings out of the box, it as well allows to de-duplicate map keys in an efficient and easy way.

### Example
**JBON** supports default values using global dictionaries. **CBOR** does not, therefore you need additional knowledge not being integral part of the format. For example, lets look at the following snippet from a MOM (Map Object Model) topology:

```json
{
  "offroadFlags": {
    "isAlley": [
      {
        "range": {
          "endOffset": 1,
          "startOffset": 0
        },
        "value": false
      }
    ],
    "isSkiRun": [
      {
        "range": {
          "endOffset": 1,
          "startOffset": 0
        },
        "value": false
      }
    ],
    "isSkiLift": [
      {
        "range": {
          "endOffset": 1,
          "startOffset": 0
        },
        "value": false
      }
    ],
    "isBmxTrack": [
      {
        "range": {
          "endOffset": 1,
          "startOffset": 0
        },
        "value": false
      }
    ],
    "isDriveway": [
      {
        "range": {
          "endOffset": 1,
          "startOffset": 0
        },
        "value": false
      }
    ],
    "isRaceTrack": [
      {
        "range": {
          "endOffset": 1,
          "startOffset": 0
        },
        "value": false
      }
    ],
    "isHorseTrail": [
      {
        "range": {
          "endOffset": 1,
          "startOffset": 0
        },
        "value": false
      }
    ],
    "isBicyclePath": [
      {
        "range": {
          "endOffset": 1,
          "startOffset": 0
        },
        "value": false
      }
    ],
    "isHikingTrail": [
      {
        "range": {
          "endOffset": 1,
          "startOffset": 0
        },
        "value": false
      }
    ],
    "isWalkingPath": [
      {
        "range": {
          "endOffset": 1,
          "startOffset": 0
        },
        "value": false
      }
    ],
    "isOilFieldRoad": [
      {
        "range": {
          "endOffset": 1,
          "startOffset": 0
        },
        "value": false
      }
    ],
    "isRunningTrack": [
      {
        "range": {
          "endOffset": 1,
          "startOffset": 0
        },
        "value": false
      }
    ],
    "isGolfCourseTrail": [
      {
        "range": {
          "endOffset": 1,
          "startOffset": 0
        },
        "value": false
      }
    ],
    "isMountainBikeTrail": [
      {
        "range": {
          "endOffset": 1,
          "startOffset": 0
        },
        "value": false
      }
    ],
    "isOutdoorActivityRoad": [
      {
        "range": {
          "endOffset": 1,
          "startOffset": 0
        },
        "value": false
      }
    ],
    "isCrossCountrySkiTrail": [
      {
        "range": {
          "endOffset": 1,
          "startOffset": 0
        },
        "value": false
      }
    ],
    "isOutdoorActivityAccess": [
      {
        "range": {
          "endOffset": 1,
          "startOffset": 0
        },
        "value": false
      }
    ],
    "isUndeterminedGeometryType": [
      {
        "range": {
          "endOffset": 1,
          "startOffset": 0
        },
        "value": false
      }
    ],
    "isPrivateRoadForServiceVehicle": [
      {
        "range": {
          "endOffset": 1,
          "startOffset": 0
        },
        "value": false
      }
    ]
  }
}
```

In efficient [JSON] this is:

```json
{"offroadFlags":{"isAlley":[{"range":{"endOffset":1,"startOffset":0},"value":false}],"isSkiRun":[{"range":{"endOffset":1,"startOffset":0},"value":false}],"isSkiLift":[{"range":{"endOffset":1,"startOffset":0},"value":false}],"isBmxTrack":[{"range":{"endOffset":1,"startOffset":0},"value":false}],"isDriveway":[{"range":{"endOffset":1,"startOffset":0},"value":false}],"isRaceTrack":[{"range":{"endOffset":1,"startOffset":0},"value":false}],"isHorseTrail":[{"range":{"endOffset":1,"startOffset":0},"value":false}],"isBicyclePath":[{"range":{"endOffset":1,"startOffset":0},"value":false}],"isHikingTrail":[{"range":{"endOffset":1,"startOffset":0},"value":false}],"isWalkingPath":[{"range":{"endOffset":1,"startOffset":0},"value":false}],"isOilFieldRoad":[{"range":{"endOffset":1,"startOffset":0},"value":false}],"isRunningTrack":[{"range":{"endOffset":1,"startOffset":0},"value":false}],"isGolfCourseTrail":[{"range":{"endOffset":1,"startOffset":0},"value":false}],"isMountainBikeTrail":[{"range":{"endOffset":1,"startOffset":0},"value":false}],"isOutdoorActivityRoad":[{"range":{"endOffset":1,"startOffset":0},"value":false}],"isCrossCountrySkiTrail":[{"range":{"endOffset":1,"startOffset":0},"value":false}],"isOutdoorActivityAccess":[{"range":{"endOffset":1,"startOffset":0},"value":false}],"isUndeterminedGeometryType":[{"range":{"endOffset":1,"startOffset":0},"value":false}],"isPrivateRoadForServiceVehicle":[{"range":{"endOffset":1,"startOffset":0},"value":false}]}}
```

So 1469 UTF-8 encoded bytes. Within that [JSON] we see, over and over again, sub-objects like this:

```json
{
  "isPrivateRoadForServiceVehicle": [
    {
      "range": {
        "endOffset": 1,
        "startOffset": 0
      },
      "value": false
    }
  ]
}
```

### Long encoding
The most efficient [JSON] of such a sub-object, of the above data, is:

```json
{"isPrivateRoadForServiceVehicle":[{"range":{"endOffset":1,"startOffset":0},"value":false}]}
```

This sub-object in **JSON** is 92 byte long _(UTF-8 encoded)_, and not binary readable. In **JBON** the same data has a long and a short form. Long form first:

```
map lead-in (1 byte) <-- root
  size (1 byte)
  kind reference (3 byte)
  array lead-in (1 byte) <-- value of "isPrivateRoadForServiceVehicle"
    size (1 byte)
    map lead-in (1 byte) <-- value of array[0]
      size (1 byte)
      kind reference (3 byte)
      map lead-in (1 byte) <-- value of "range"
        size (1 byte)
        kind reference (3 byte)
        tiny-int (1 byte) <-- value of "endOffset"
        tiny-int (1 byte) <-- value of "startOffset"
      boolean (1 byte) <-- value of "value"
= 1+1+3+1+1+1+1+3+1+1+3+1+1+1
= 20 byte
```

We have 19 of these object, where we can share the kind reference, because they all have the same fields, which will be detected by the encoder. The [kind] encoding, generated by the encoding for the `local` [book] will not contain default values, the kinds will only contain the field names. So, the `local` [book] will look like:

```
book lead-in (1 byte) <-- book root
  size (1 byte)
  book_id (1 byte) <-- null
  book_tn (1 byte) <-- null
  array lead-in (1 byte)
    string lead-in (1 byte)
      "endOffset" (9 byte)
    string lead-in (1 byte)
      "startOffset" (11 byte)
= 1+1+1+1+1+1+9+1+11
= 26 byte
```

So instead of 19 times 92 byte, we have 19 times 20 byte, plus the `local` [book], which is 26 byte, so in total around 406 byte instead of 1748 byte, which is a compression of around 77%.

### Short encoding
Assuming we have an existing `global` [book] for the encoder to help, so a MOM _(Map Object Model)_ [book], things will change. We not only no longer have to encode the [kind], but our [kind] will have default values, so the encoding now looks like:

```
map lead-in (1 byte) <-- root
  size (1 byte)
  kind reference (3 byte)
  array lead-in (1 byte) <-- "isPrivateRoadForServiceVehicle"
    size (1 byte)
    map lead-in (1 byte) <-- {
      size (1 byte)
      kind reference (3 byte)
      map lead-in (1 byte) <-- "range"
        size (1 byte) <-- only header size, which means, use defaults for all values
        kind reference (3 byte)
      boolean (1 byte) <-- "value"
= 1+1+3+1+1+1+1+3+1+1+3+1
= 18 byte
```

So it is reduced to 18 byte, assuming we can use a shared dictionary for MOM data format. This results in a compression of around 80% for this entry, which means for 100 million records of this type we need instead of 9.2 GiB only around 1.8 GiB. This is only one value, notice that there are 19 of such values in the [JSON], therefore we save around 74 GiB of data.

### Compress more using kinds
However, this is not the end, because if there is a MOM [book] that defines a [kind] for the `"offroadFlags"` with defaults of all properties being `[{"range":{"endOffset":1,"startOffset":0},"value":false}]}`, then the whole `"offroadFlags"` can be encoded into:

```
map lead-in (1 byte) <-- root
  size (1 byte)
  kind reference (3 byte)
= 1+1+3
= 5 byte
```

So, as we want all default values only, we can encode the whole `"offroadFlags"` into just ~5 byte, which is a compression of around 0.9967%, compared to the [JSON] with 1469 byte. 

### Default values
Now, in [CBOR] we would have to encode exactly the same thing as in [JSON], it would not be reduces in size, just become binary readable. This means, with **JBON** we can get a compression rate of above 99%, while still being full binary readable, no parsing.

Within [HERE] there is a proposal, specifically for Map Object Model, to add default values for properties to save space when serializing to [JSON]. This can come up with similar compression rates, but has a major disadvantage. It binds the data to the MOM specification and requires the decoder to have knowledge about this specification. In other words, when we encounter such a compressed [JSON], we have potentially no idea for with MOM specification it was generated. Even if we have, we would now need the decoder that supports exactly this version. If our code has long moved on, old data therefore become unaccessible. More critical, when we want to store such data in a database, and index parts of it, the database, or a service infront of it, needs a MOM decoder of exactly the version with which the data was encoded, to be able to read the default values for indexing.

With the **JBON** solution, only the `global` [book] is needed, which can be kept next to the data, as it is as well just data. It will work with all decoders following the specification, no matter how old they are. This as well works with all data, not only MOM. It allows to re-encode old data with new optimizations, and it allows to auto-generate _(derive)_ the [book] from the data, so analyzing the data, then generate the optimal kinds with optimal default values. We can even have multiple versions of the same [kind] with different default values!

The best is, that this allows storages to index the data, as long as they have the `global` [book], because the reader can return the full object. When we use the reader, the object will appear as if it is part of the **JBON**, it is transparent to the user if the data comes from a `global` [book], or it is really encoded in the binary. The application does not need to know details, it only needs access to the `global` [book]. Compression optimization is purely done on the encoder side and can be improved for all our use-cases, not having to make old data invalid or have to re-encode it, nor does it require special decoding knowledge or a special decoder. No matter how efficient the encoder is made, the decoder always stays the same!

Clearly, we could somehow add the dictionaries and text encoding to [CBOR] using [tags](https://www.rfc-editor.org/rfc/rfc8949.html#name-tagging-of-items), but it would be a proprietary extension and therefore anyway force us to do some own implementations. It would eventually make [CBOR] so incompatible to what the rest of the world does, that there seems to be no advantage in this solution, when compared to creating our own binary encoding.

## Const
The `const` book is a special book, which is not encoded in the binary, but is hardcoded in the specification. It contains values that are commonly used, so that they do not have to be encoded in the binary, but can be just referenced by their index in the `const` book. This saves space and makes encoding more efficient.

| Number | Const                      | Value                      | Description                                                                             |
|--------|----------------------------|----------------------------|-----------------------------------------------------------------------------------------|
| `0001` | `NAKSHA`                   | `Naksha`                   |                                                                                         |
| `0002` | `GEO_JSON`                 | `GeoJSON`                  |                                                                                         |
| `0003` | `JSON`                     | `JSON`                     |                                                                                         |
| `0004` | `PROPERTIES`               | `properties`               |                                                                                         |
| `0005` | `GEOMETRY`                 | `geometry`                 |                                                                                         |
| `0006` | `REFERENCE_POINT`          | `referencePoint`           |                                                                                         |
| `0007` | `NS_COM_HERE_XYZ`          | `@ns:com:here:xyz`         | The XYZ namespace key.                                                                  |
|        |                            |                            |                                                                                         |
| `7000` | `APPLICATION_OCTET_STREAM` | `application/octet-stream` | The MIME-type for arbitrary binaries _(default when no specific [MIME type] is given)_. |
| `7001` | `APPLICATION_JBON`         | `application/jbon`         | The custom MIME-type for **JBON** binaries.                                             |
| `7002` | `APPLICATION_JSON`         | `application/json`         | The custom MIME-type for **JSON** strings.                                              |
| `7003` | `APPLICATION_TWKB`         | `application/twkb`         | The custom MIME-type for [TWKB] binaries.                                               |
| `7004` | `APPLICATION_INT8A`        | `application/int8a`        | The custom MIME-type for a Java byte-array (`byte[]` / `Int8Array`)_.                   |
| `7005` | `APPLICATION_INT16A`       | `application/int16a`       | The custom MIME-type for a Java short-array (`short[]` / `Int16Array`)_.                |
| `7006` | `APPLICATION_INT32A`       | `application/int32a`       | The custom MIME-type for a Java int-array (`int[]` / `Int32Array`)_.                    |
| `7007` | `APPLICATION_INT64A`       | `application/int64a`       | The custom MIME-type for a Java long-array (`long[]` / `BigInt64Array`)_.               |
| `7008` | `APPLICATION_FLOAT16A`     | `application/float16a`     | The custom MIME-type for a Java float-array (`short[]` / `Float16Array`)_.              |
| `7009` | `APPLICATION_FLOAT32A`     | `application/float32a`     | The custom MIME-type for a Java float-array (`float[]` / `Float32Array`)_.              |
| `7010` | `APPLICATION_FLOAT64A`     | `application/float64a`     | The custom MIME-type for a Java double-array (`double[]` / `Float64Array`)_.            |
| `7011` | `APPLICATION_FLOAT128A`    | `application/float128a`    | The custom MIME-type for a Java float-array (`byte[]` / `Int8Array`)_.                  |
|        |                            |                            |                                                                                         |
| `7100` | `CONTENT_ENCODING`         | `content-encoding`         | The encoding or compression algorithm being used in a [Binary] _(or other places)_.     |
| `7101` | `GZIP`                     | `GZIP`                     | The binary is [GZIP] compressed.                                                        |
| `7102` | `LZ4`                      | `LZ4`                      | The binary is [LZ4] compressed.                                                         |
|        |                            |                            |                                                                                         |
| `7200` | `CHARSET`                  | `charset`                  | The character-set being used in a [Binary] _(or other places)_.                         |
| `7201` | `ISO_8859_1`               | `ISO-8859-1`               | Legacy Western European.                                                                |
| `7202` | `ISO_8859_2`               | `ISO-8859-2`               | Legacy Central/Eastern European.                                                        |
| `7205` | `ISO_8859_5`               | `ISO-8859-5`               | Legacy Cyrillic.                                                                        |
| `7215` | `ISO_8859_15`              | `ISO-8859-15`              | Legacy Western European, same as `ISO-8859-1`, but includes `€`.                        |
| `7219` | `US_ASCII`                 | `US-ASCII`                 |                                                                                         |
|        |                            |                            |                                                                                         |
| `7220` | `UTF_8`                    | `UTF-8`                    | UTF-8 encoding.                                                                         |
| `7221` | `UTF_16`                   | `UTF-16`                   | UTF-16 in platform encoding.                                                            |
| `7222` | `UTF_16BE`                 | `UTF-16BE`                 | UTF-16 in big-endian byte-order _(network byte order)_.                                 |
| `7223` | `UTF_16LE`                 | `UTF-16LE`                 | UTF-16 in little-endian byte-order.                                                     |
| `7224` | `UTF_32`                   | `UTF-32`                   | UTF-32 in platform encoding.                                                            |
| `7225` | `UTF_32BE`                 | `UTF-32BE`                 | UTF-32 in big-endian byte-order _(network byte order)_.                                 |
| `7226` | `UTF_32LE`                 | `UTF-32LE`                 | UTF-32 in little-endian byte-order.                                                     |
|        |                            |                            |                                                                                         |
| `7230` | `SHIFT_JIS`                | `Shift_JIS`                | Legacy Japanese.                                                                        |
| `7231` | `EUC_JP`                   | `EUC-JP`                   | Legacy Japanese.                                                                        |
| `7232` | `GBK`                      | `GBK`                      | Legacy Common Chinese.                                                                  |
| `7233` | `BIG5`                     | `Big5`                     | Legacy Traditional Chinese.                                                             |
| `7234` | `KOI8_R`                   | `KOI8-R`                   | Legacy Russian.                                                                         |
|        |                            |                            |                                                                                         |
| `7251` | `WINDOWS_1251`             | `Windows-1251`             | Very common legacy Western encoding on Windows.                                         |
| `7252` | `WINDOWS_1252`             | `Windows-1252`             | Cyrillic on Windows.                                                                    |

## Java
This section documents the Java API for **JBON**.

```java
package naksha.data;
public interface JbonLogicalBytes {
  void addByte(byte b);
  void addShortBE(short s);
  void addIntBE(int i);
  void addLongBE(long l);
  void addFloatBE(float f);
  void addDoubleBE(double d);
  /**
   * Add a Unicode code point as UTF-16 code units, big-endian.
   * @param cp The Unicode code point to add, must be a valid code point between 0 and 0x10FFFF.
   */
  void addCodePointBE(int cp);
  /**
   * Add a char-sequence as UTF-16 code units, big-endian. The string is added without a lead-in byte, it is only the UTF-16 encoded code-units.
   * @param chars The string to add, must not be null.
   * @param normalize If {@code true}, then the string is normalized using NFC normalization form before adding, otherwise it is added as is, requiring that it is already normalized. 
   */
  void addText(@NotNull CharSequence chars, boolean normalize);
  byte[] toBytes();
}
```

```java
package naksha.data;
abstract class AbstractJbon implements JbonHash {
  public @Nullable JbonBook localBook; // 0
  public @Nullable JbonBook storageBook; // 1
  public @Nullable JbonBook globalBook; // 2
  public @NotNull Bytes main; // 3
  public @Nullable Bytes attachment; // 4

  private @Nullable JbonBinary binary;
  protected @NotNull JbonBinary binary() {
    var binary = this.binary;
    if (binary == null) {
      binary = new JbonBinary(this);
      this.binary = binary;
    }
    return binary;
  }
  // TODO: Add the same helper and private members for the other structures:
  //       - JbonArray array
  //       - JbonMap map
  //       - JbonKind kind
  //       - JbonMember member
  //       - JbonTupleNumber tupleNumber
  //       Additionally, we need the same for the localBook, storageBook, and stack.
  
  // TODO: For stack, we should be able to provide the needed size as parameter (like stack(10)), and only increase the stack, when there is no space left.
  //       We use `sp` to track the current stack pointer, so where to write the next value, so push to sp++, pop from --sp.
  //       The values we push is encoded as: {book:4}{start:30}:{pos:28}
  //       Note that we never need to access the attachment and we const does not have complex objects, so when encoding, 4-bit are okay for the book-index (local, storage, global, main).
  //       In other words, we never need to return into attachment or const, so we never push this to stack either.
  private static final long[] EMPTY_STACK = new long[0];
  private long @NotNull [] stack = EMPTY_STACK;
  protected int sp;
}
public class Jbon extends AbstractJbon {
  public Jbon(@NotNull Bytes jbon) { this.main = jbon; }
  public Jbon(@NotNull Bytes jbon, @Nullable JbonBook globalBook) { this.main = jbon; this.globalBook = globalBook; }
}
public final class JbonString extends AbstractJbon {
  public JbonString(@NotNull Jbon jbon) { this.jbon = jbon; }
  @NotNull Jbon jbon;
}
public class JbonStructure {
  JbonStructure(@NotNull Jbon jbon) { this.jbon = jbon; }
  @NotNull Jbon jbon;
}
public final class JbonBinary extends JbonStructure {
  public JbonBinary(@NotNull Jbon jbon) { super(jbon); }
}
public final class JbonArray extends JbonStructure {
  public JbonArray(@NotNull Jbon jbon) { super(jbon); }
}
public final class JbonObject extends JbonStructure {
  public JbonObject(@NotNull Jbon jbon) { super(jbon); }
}
public final class JbonKind extends JbonStructure {
  public JbonKind(@NotNull Jbon jbon) { super(jbon); }
}
public final class JbonMember extends JbonStructure {
  public JbonMember(@NotNull Jbon jbon) { super(jbon); }
}
public final class JbonTupleNumber extends JbonStructure implements ITuple {
  public JbonTupleNumber(@NotNull Jbon jbon) { super(jbon); }
}
public final class JbonTuple extends JbonStructure implements ITuple {
  public JbonTuple(@NotNull Jbon jbon) { super(jbon); }
}
public final class JbonBook extends JbonStructure {
  public JbonBook(@NotNull Jbon jbon) { super(jbon); }
}
public final class JbonAnnotation extends JbonStructure implements ITuple {
  public JbonAnnotation(@NotNull Jbon jbon) { super(jbon); }
}

public class MurMur3 implements JbonLogicalBytes {
  // TODO: Add an optional feature, that can be enabled/disabled, which will collect all bytes that are hashed
  //       so that they can be used for value comparison in case of hash collisions. By default it should be turned off.
  private static final long c1 = 0x87c37b91114253d5L;
  private static final long c2 = 0x4cf5ad432745937fL;

  private long seed;
  private long h;
  private int offset;
  private long totalLength;
  private byte d0, d1, d2, d3, d4, d5, d6, d7;

  public MurMur3() {}

  public MurMur3(long seed) {
    this.seed = seed;
    this.h = seed;
  }

  public MurMur3 reset() {
    offset = 0;
    totalLength = 0L;
    h = seed;
    return this;
  }

  public MurMur3 reset(long seed) {
    this.seed = seed;
    offset = 0;
    totalLength = 0L;
    h = seed;
    return this;
  }

  public MurMur3 update(byte[] data) {
    return update(data, 0, data.length);
  }

  public MurMur3 update(byte[] data, int start, int end) {
    byte d0 = this.d0;
    byte d1 = this.d1;
    byte d2 = this.d2;
    byte d3 = this.d3;
    byte d4 = this.d4;
    byte d5 = this.d5;
    byte d6 = this.d6;
    byte d7 = this.d7;
    int offset = this.offset;
    long h = this.h;
    while (start < end) {
      final byte b = data[start++];
      switch (offset) {
        case 0: d0 = b; break;
        case 1: d1 = b; break;
        case 2: d2 = b; break;
        case 3: d3 = b; break;
        case 4: d4 = b; break;
        case 5: d5 = b; break;
        case 6: d6 = b; break;
        case 7: d7 = b; break;
      }
      if (++offset == 8) {
        long k = (d0 & 0xffL)
              | ((d1 & 0xffL) << 8)
              | ((d2 & 0xffL) << 16)
              | ((d3 & 0xffL) << 24)
              | ((d4 & 0xffL) << 32)
              | ((d5 & 0xffL) << 40)
              | ((d6 & 0xffL) << 48)
              | ((d7 & 0xffL) << 56);
        k *= c1;
        k = Long.rotateLeft(k, 31);
        k *= c2;
        h ^= k;
        h = Long.rotateLeft(h, 27);
        h = h * 5 + 0x52dce729L;
        offset = 0;
      }
    }
    this.h = h;
    this.offset = offset;
    this.totalLength += (end - start) + offset; // bytes consumed so far
    switch (offset & 7) {
      case 7: this.d6 = d6;
      case 6: this.d5 = d5;
      case 5: this.d4 = d4;
      case 4: this.d3 = d3;
      case 3: this.d2 = d2;
      case 2: this.d1 = d1;
      case 1: this.d0 = d0;
    }
    return this;
  }

  public long finish() {
    long k = 0L;
    switch (offset) {
      case 7: k ^= (d6 & 0xffL) << 48;
      case 6: k ^= (d5 & 0xffL) << 40;
      case 5: k ^= (d4 & 0xffL) << 32;
      case 4: k ^= (d3 & 0xffL) << 24;
      case 3: k ^= (d2 & 0xffL) << 16;
      case 2: k ^= (d1 & 0xffL) << 8;
      case 1: k ^= (d0 & 0xffL);
        k *= c1;
        k = Long.rotateLeft(k, 31);
        k *= c2;
        h ^= k;
    }
    h ^= totalLength;
    // fmix64
    h ^= (h >>> 33);
    h *= 0xff51afd7ed558ccdL;
    h ^= (h >>> 33);
    h *= 0xc4ceb9fe1a85ec53L;
    h ^= (h >>> 33);
    return h;
  }

  /** Reduce the 64-bit hash to 32-bit by XOR'ing the high and low halves. */
  public static int toInt32(long hash) {
    return Long.hashCode(hash);
  }

  /** Reduce the 64-bit hash to 16-bit by XOR'ing all 16-bit halves. */
  public static short toInt16(long hash) {
    int h32 = toInt32(hash);
    return (short) (h32 ^ (h32 >>> 16));
  }

  /** Reduce the 64-bit hash to 8-bit by XOR'ing all 8-bit halves. */
  public static byte toInt8(long hash) {
    int h16 = toInt16(hash) & 0xffff;
    return (byte) (h16 ^ (h16 >>> 8));
  }
}
```

---

## Changes
The following changes are introduces into version 2 of this specification, compared to version 1:

- Added a specification for how to calculate _logical bytes_ of any _**unit**_, in a way that the same _**unit**_ generates the same _logical bytes_.
  - This is needed to find similar _**units**_ in the storage via hash.
  - It is important to logically compare _**units**_.
  - It as well allows to compare _**units**_ without identifiers to those being already in the storage. It only requires a secondary compare hash, that limits the hash to significant members, which is simply done by removing the values that should not be part of the hash, then calculate the _logical bytes_, hash them and compare the hash and _logical bytes_.
  - Therefore, the _logical bytes_ is a real unique identifier of _**units**_.
- Introduction of `kind` and `member`, which do now allow us short term to really implemented much better data compaction, and simplifies the encoder implementation
- The dictionaries have been renamed into `books`.
  - This is as well strongly linked to the _logical bytes_, without them, it would be rather as useless as the dictionary, which effectively only stored strings.
  - So, books now really store deep structures and allow deduplication of whole objects in a standardized simplified way!
- Improved the way some values are encoded
  - So, add `uint56` and `uint24`, which are useful as they allocate a fixed space, which again simplifies encoders.
- Added the `Tuple` structure, which is a special wrapper for maps, which is used to exchange data between services and storages.
  - This replaces the really weird previous definition of features.
- Added documentation about the mostly implicit value `undefined`, made it explicit, and improved documentation about `undefined` and `null` 

[Logical Bytes]: #logical-bytes
[logical bytes]: #logical-bytes
[Sorting]: #sorting
[sorting]: #sorting
[sorted]: #sorting
[Hashing]: #hashing
[hashing]: #hashing
[hash]: #hashing
[Unit]: #units
[Units]: #units
[unit]: #units
[units]: #units
[Primitives]: #primitives
[primitive]: #primitives
[primitives]: #primitives
[Timestamp]: #timestamp
[timestamp]: #timestamp
[timestamps]: #timestamp
[UInt56]: #uint56
[uint56]: #uint56
[TupleNumber]: #tuplenumber
[Reference]: #reference
[reference]: #reference
[References]: #reference
[references]: #reference
[ref]: #reference
[String]: #string
[string]: #string
[strings]: #string
[Structure]: #structures
[structure]: #structures
[Structures]: #structures
[structures]: #structures
[Binary]: #binary
[binary]: #binary
[binaries]: #binary
[Array]: #array
[array]: #array
[arrays]: #array
[Map]: #map
[map]: #map
[maps]: #map
[Kind]: #kind
[kind]: #kind
[kinds]: #kind
[Member]: #member
[member]: #member
[members]: #member
[Tuple]: #tuple
[tuple]: #tuple
[tuples]: #tuple
[Book]: #book
[book]: #book
[books]: #book
[Annotation]: #annotation
[annotation]: #annotation
[annotations]: #annotation
[Const]: #const
[const]: #const
[Constants]: #const
[constants]: #const
[CBOR]: https://www.rfc-editor.org/rfc/rfc8949
[JSON]: https://www.rfc-editor.org/rfc/rfc8259
[XML]: https://www.w3.org/TR/xml/
[GeoJSON]: https://datatracker.ietf.org/doc/html/rfc7946
[TWKB]: https://github.com/TWKB/Specification/blob/master/twkb.md
[UNICODE]: https://home.unicode.org/
[Naksha data model]: LIB_DATA.md
[Naksha data model Tuple-Number]: LIB_DATA.md#tuple-number
[tuple-number]: LIB_DATA.md#tuple-number
[IEEE-754]: https://en.wikipedia.org/wiki/IEEE_754
[binary16]: https://en.wikipedia.org/wiki/Half-precision_floating-point_format
[binary32]: https://en.wikipedia.org/wiki/Single-precision_floating-point_format
[binary64]: https://en.wikipedia.org/wiki/Double-precision_floating-point_format
[binary128]: https://en.wikipedia.org/wiki/Quadruple-precision_floating-point_format
[ICU4J]: https://mvnrepository.com/artifact/com.ibm.icu/icu4j
[IANA media types]: https://www.iana.org/assignments/media-types/media-types.xhtml
[data URL scheme]: https://www.rfc-editor.org/rfc/rfc2397
[data URL]: https://www.rfc-editor.org/rfc/rfc2397
[data-url]: https://www.rfc-editor.org/rfc/rfc2397
[MIME-Type]: https://developer.mozilla.org/en-US/docs/Web/HTTP/Guides/MIME_types
[MIME Type]: https://developer.mozilla.org/en-US/docs/Web/HTTP/Guides/MIME_types
[MIME type]: https://developer.mozilla.org/en-US/docs/Web/HTTP/Guides/MIME_types
[MIME-type]: https://developer.mozilla.org/en-US/docs/Web/HTTP/Guides/MIME_types
[mime type]: https://developer.mozilla.org/en-US/docs/Web/HTTP/Guides/MIME_types
[mime-type]: https://developer.mozilla.org/en-US/docs/Web/HTTP/Guides/MIME_types
[HERE]: https://www.here.com/
[MurMur3]: https://en.wikipedia.org/wiki/MurmurHash
[murmur3]: https://en.wikipedia.org/wiki/MurmurHash
