# JBON2
This is version 2 of the **JBON** specification. It replaces [version 1](./JBON1.md), which was never used in production.

## Introduction
**JBON** is a shortcut for Java Binary Object Notation. In this binary format all values are stored as objects in a tree-like structure that can be navigated. It is optimized to reduce the size of the binary, while at the same time being readable without having to parse the data structure. Furthermore, it is intended to play nicely with the [Naksha data model].

The goals of **JBON** are:

- Encode data stored in arbitrary storage systems.
- The same object should result in the same hash and logical bytes, no matter how it is encoded.
- Be compatible with _Java_.
- Be compatible with _JavaScript_.
- Deduplicate data as much as possible to reduce the size.
- Keep the binary as small as possible, while allowing reading of the data without parsing.
- Allow efficient caching of the data, especially on the JVM heap _(deduplicated)_.
- Transfer data between services, clients, and storage in a binary safe way.
- Support easy storage of data in databases, on disk or other storage systems.
- Support easy calculation of differences, patching, and application of patches or merging.
- Good cooperation with the [Naksha data model].
- **Keep the decoder stable within each version, while allowing improvements of the encoder over time within a version**
  - The decoder, starting with version 2, **must** always be able to read its own and older versions; but never higher versions _(so version 3 decoder must be able to decode version 2)_.
  - A decoder will reject to decode **JBON** binaries with a bigger version than its own version _(so version 2 decoder reject version 3)_.
  - The backwards compatibility is an important goal of version 2, because we want to store data for years to come.
  - We need a data-format for which we can optimize the encoder over time, while even decades old decoders can read the modern encoding.

As the format name indicates, this format is object-oriented. All **JBON** data is encoded using _**units**_. All _**units**_ always start with a header. The header encodes the type of the _**unit**_. These are the basic _**unit**_ types:

- `Primitive`: The following _**units**_ are called _primitives_: `null`, `boolean`, `integer`, `float`, `timestamp`, `string`, and `tuple-number`.
- `String`: A special _primitive_ that encodes a list of [UNICODE] code points, optionally including [references] to sub-strings. Strings are split using the [UNICODE] word boundary algorithm from [ICU4J].
- `Array` _(`List<Any?>`)_: A list of arbitrary _**units**_ with significant order _(changing the order creates a different array)_.
- `Set` _(`List<Primitive>`)_: A list of unique non-null _**primitives**_; the order of the elements is not significant, therefore the encoder will optimize the order by sorting the elements.
- `Object` _(`Map<String, Any?>`)_: A list of key-value pairs with all keys being unique non-null _**strings**_; the values can be any _**unit**_. The order of the entries is not significant, therefore the encoder will optimize by sorting the entries by their keys.
- `Map` _(`Map<Primitive, Any?>`)_: A list of key-value pairs with keys limited to be unique non-null _**primitives**_; values can be any _**unit**_. The order of the entries is not significant, therefore the encoder will optimize by sorting the entries by their keys.
- `Dictionary` _(`Map<String, String>`)_: A list of key-value pairs with keys being unique non-null _**strings**_ and values being non-null _**strings**_. The order of the entries is not significant, therefore the encoder will optimize by sorting the entries by their keys.
- `Tags` _(`Map<String, Primitive?>`)_: A list of key-value pairs with keys being unique non-null _**strings**_, and the values being any _**primitive**_. The order of the entries is not significant, therefore the encoder will optimize by sorting the entries by their keys.
- `Book` _(`List<Any?>`)_: An addressable array of _**units**_ loaded into context.
- `Tuple`: A special encoding of a _feature_ being an `Object` with some metadata to cooperate with the [Naksha data model].
- `TWKB`: An embedded geometry encoded in [Tiny WKB].
- `Binary`: A special _**unit**_ that encodes a typed binary, so a `byte[]` with metadata.

The header of all _**units**_ starts with the **lead-in** byte, which identifies the type of the _**unit**_ and in some special cases indicates its value (one-byte encoding). If the **lead-in** byte signals anything except a [primitive] or empty _**unit**_, then it is followed by an unsigned integer encoded in 1, 2 or 4 byte (big-endian) storing the total size of structure/string in byte. This can be used to skip over the _**unit**_ by adding the value to the current offset. Therefore, all _**units**_ always have a size, either implicit or explicit. This allows a decoder to navigate the data without parsing, just by remembering the start of the _**unit**_, next to the offset within the _**unit**_, if the _**unit**_ is navigable. These values can be encoded in a single long, simplifying a navigation stack implementation.

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
- [Tuple-Number], sorted by `database_number`, `catalog_number`, `collection_number`, `feature_number`, and finally by `version`, in that order. Beware, that all invalid versions, so less than one or more than _MAX_ version _(`9,007,199,254,740,987`)_, are sorted with the virtual value _HEAD_ _(`9,007,199,254,740,991`)_. Therefore, invalid versions are located at the end, when sorting ascending, and in front when sorting descending.
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
- Strings, are converted into their [UTF16 string], then sorted by their UTF-16 code-units, so that the sorting is compatible with JavaScript and Java.
  - This is not a locale-aware alphabetical sorting.
  - If a [UTF16 string] is part of the **JBON**, it is treated exactly like a string, except that no conversion is needed.
- All structures (including [Array], [Map], [Set], [Object], [Tags], [Dictionary], [Book], [TupleNumberArray], [Tuple], [TWKB], and [Binary]) sort after all primitives, by their [logical bytes].

Beware that [references] can't be sorted, they always behave exactly like the value to which they refer. So, when a reference to a [string] is given, the sorting is based on the value of the [string], not on the reference itself.

If while sorting two values equal, they should stay in existing _(given)_ order.

## Hashing
Hashing is part of the **JBON** specification. When all participants hash the same way, the binaries can be compared more easily against each other. The trick in hashing **JBON** is that two different binaries can basically represent the same data, for example when they just use different order of members or different encodings. This starts to become more true, when global [books] are shared.

- **All clients _(including storages)_ must be able to calculate the same hash for the same data, even while they encode the data differently!**
- **We do not fix the hash algorithm, so clients can use any hashing algorithm!**

To be able to calculate a hash, the _**unit**_ first needs to be converted into [logical bytes].

## Logical Bytes
To compare two **JBON** _**units**_, they need to be converted into a sequence of comparable bytes, called _logical bytes_. As the **JBON** binary is highly compressed, the same logical data can be encoded in many different ways. Two **JBON** binaries can be logically similar, even while they are binary totally different. This happens for many reasons, different `global` [books], different `members` [books], or just different encoders. Therefore, to logically compare two **JBON** _**units**_, they need to be encoded in a standard format, including all _**child-units**_, so that they are always the same binary, no matter of which encoder is used. Actually, they are encoded in the least compact way possible, so they are logically serialized.

In other words: Logically `{a:1,b:2}` is equal to `{b:2,a:1}`, therefore both need to result in the same [logical bytes], and therefore in the same hash.

So, each **JBON** _**unit**_ can be serialized into _logical bytes_. Beware that the _logical bytes_ are a valid binary encoding that can technically be read. The serialized _logical byte_ can be used to compare two _**units**_ by value and to calculate a [hash] to improve the compare speed. The default hashing algorithm for **JBON** is a 64-bit [murmur3], it is part of `lib-data`, and used in streaming mode. It supports shortening the hash to 32-bit, 16-bit or 8-bit. This is a simple reference implementation for a streaming [murmur3]. Beware that due to the logical bytes, any other hash algorithm can be used as well.

## Units
All _**units**_ have a general concept they follow. The first byte is the **lead-in** _BYTE_, signaling the type of the _**unit**_. If the size of the _**unit**_ is not explicitly or implicitly encoded in the **lead-in**, then the **lead-in** is followed by the size of the _**unit**_. The size is encoded as a 1-, 2-, or 4-byte big-endian unsigned integer; the byte width is implicit in the **lead-in**. Dependent on the type of the _**unit**_, more header fields may follow.

Therefore, all _**units**_ have a **unit-size**, which is the total size of the _**unit**_ and equals the amount of byte to skip when seeking beyond the _**unit**_. Additionally, there is the **content-size**, which is the amount of byte that stores the content of the _**unit**_, and the **header-size**, which is the **lead-in** _BYTE_, plus the bytes of the optional header fields.

Generally speaking, every _**unit**_ persists out of a _header_ and a _content_.

In a nutshell, the decoder should expose these values:

- `endOffset`: The byte-offset where the current **JBON** ends.
- `unitOffset`: The byte-offset of the _**unit**_ in the **JBON**.
- `unitType`: The type of the _**unit**_, extracted from the _header_, being the `JbonDataType`.
- `unitSize`: The total outer size of the _**unit**_, bytes to add to the _**unit**_ offset to skip beyond the _**unit**_, in effect `unitHeaderSize` plus `unitValueSize`.
- `unitHeaderSize`: The amount of byte that store the **lead-in** and the header fields. Therefore, minimally one byte being the **lead-in**.
- `unitContentSize`: The amount of byte that store the content, can be zero or greater. For some _**units**_ the content is a byte-stream, others have child-units, others no content at all.
- `unitValue`: The value of the unit. If the _**unit**_ represents a single value, either implicitly encoded in the **lead-in** _BYTE_ _(for example a boolean or tiny integer)_ or explicitly encoded in the content _(for example a string or binary)_, the value.
- `unitHash`: The hash of the _**unit**_. The calculation of this value should only be done lazily, so only when explicitly asked for, because it is expensive to calculate. The hash calculation requires to generate the [logical bytes] of the _**unit**_. The hash algorithm should be flexible, it is not required that [murmur3] is used, this is just the default, when nothing else is specified.

The decoder will always read and decode the header at once, automatically decoding all header fields.

One important consequence of the above design is that we can pack the decoder state into a 64-bit integer. This allows clients to implement the stack as simple `long[]`. So, to save the state of the decoder, we encode the current segment in the top most 2-bit, then 30-bit for the end-offset, and eventually 30-bit for the unit-offset, resulting in a compact 64-bit integer representation. Therefore, the stack pointer _(SP)_ is a simple integer used as index within the stack _(`long[]`)_.

If the current _**unit**_ has child-units, the user can jump into the content by invoking `enter()`, which will return the current state compacted into an `long`, to be pushed to the stack the user has to maintain. Therefore, virtually `stack[SP++] = decoder.enter()`. When returning to the parent, the user should call `leave(state)`, providing the previously saved state, so virtually `decoder.leave(stack[--SP])`. To skip the current _**unit**_ a simple call to `next()` is done. For _primitives_ the decoder will offer methods to decode them, like `decodeInt()`, `decodeString()`, ....

## JBON File
All JBON files start with the string `@JB`, followed by the version, i.e. `0x02`. This is the only header of a JBON file, there is no footer or other metadata. The actual data starts immediately after the header. All _**units**_ stored in the file are simply appended behind each other. Therefore, reading the first four byte of a **JBON2** binary should be `0x404A4202` _(big-endian)_, or decimal `1,078,608,386`. This is the only way to detect `JBON2` encoded file. **This header is mandatory for JBON2 files**.

Decoders MUST verify the first three bytes are `@JB`; if the version byte is unknown, decoders MUST reject the file with a clear error. Future versions will use the same `@JB` prefix with an incremented version byte.

All **JBON** files should use the extension `.jbon`.

In [JBON1] and [JBON2] the `@` encodes the integer `0` _(decimal 64 aka `0100_0000`)_, the `J` encodes the integer `10` _(decimal 74 aka `0100_1010`)_, the `B` encodes the integer `2` _(decimal 66 aka `0100_0010`)_, and the ASCII-2 represents in both formats the boolean value false _(`0000_0010`)_. While it is possible that someone has encoded such a file in [JBON1], we treat it as close to impossible to happen in reality, therefore the defined _header_ should be safe to detect [JBON2] encoded files. When viewing a [JBON2] document in an editor, it should result in the first three characters being `@JB`, the rest will be gibberish.

The general structure to store **JBON** [tuple] as files should be like:

`$XDG_DATA_HOME/naksha/{database-number}/{catalog-number}/{collection-number}/{feature-number}.jbon`

All numbers should be encoded as hexadecimals. This follows the [XDG Base Directory Specification]. The default of `$XDG_DATA_HOME`, if not defined, is `$HOME/.local/share`. All versions of a **JBON** should be added into the same file with the latest version being stored towards the end of the file. As each **JBON** _**unit**_ encodes a size, all versions should be readable.

## Lead-in BYTE
All _**units**_ start with a **lead-in** byte, which describes the actual type of the _**unit**_, and sometimes as well the value:

- `00`: mixed
  - `0000_0000`: **undefined**
  - `0000_0001`: **null**
  - `0000_0010`: boolean, **false**
  - `0000_0011`: boolean, **true**
  - `0000_01vv`: integer _(**int**)_
    - `0000_0100`: integer, + 1 byte signed integer value _(**int8**)_
    - `0000_0101`: integer, + 2 byte BE signed integer value _(**int16**)_
    - `0000_0110`: integer, + 4 byte BE signed integer value _(**int32**)_
    - `0000_0111`: integer, + 8 byte BE signed integer value _(**int64**)_
  - `0000_10vv`: floating point _(**float**)_
    - `0000_1000`: float, + 1 byte [binary8] floating point value _(**float8**)_
    - `0000_1001`: float, + 2 byte [binary16] floating point value _(**float16**)_
    - `0000_1010`: float, + 4 byte BE [binary32] floating point value _(**float32**)_
    - `0000_1011`: float, + 8 byte BE [binary64] floating point value _(**float64**)_
  - `0000_1100`: [timestamp] + 7 byte BE unsigned integer value
  - `0000_1101`: [uint56], unsigned 56-bit integer, + 7 byte BE unsigned integer value _(**uint56**)_
  - `0000_1110`: [uint24], unsigned 24-bit integer, + 3 byte BE unsigned integer value _(**uint24**)_
  - `0000_1111`: [TupleNumber], + 32 byte _(**tuple-number**)_.
  - `0001_vvvv`: tiny members-[reference], **mref4** _(0 to 15)_
  - `0010_vvvv`: tiny global-[reference], **gref4** _(0 to 15)_
  - `0011_bbss`: full-[reference] **ref**
    - `0011_bb00`: reference into book `bb`, + 1 byte BE unsigned integer value _(**ref8**)_
    - `0011_bb01`: reference into book `bb`, + 2 byte BE unsigned integer value _(**ref16**)_
    - `0011_bb10`: reference into book `bb`, + 3 byte BE unsigned integer value _(**ref24**)_
    - `0011_bb11`: reference into book `bb`, + 4 byte BE unsigned integer value _(**ref32**)_
- `01`: tiny-value _(encoded as index into an array of pre-defined values)_
  - `0100_vvvv`: integer, (`0 .. 15`) _(**int4**)_, therefore `vvvv` = 0 / `0000` encodes as 0, aso.
  - `0101_vvvv`: integer, (`-16 .. -1`) _(**int4**)_, therefore `vvvv` = 0 / `0000` encodes as -16, aso.
  - `0110_vvvv`: float, (`0.0 .. 15.0`) _(**float4**)_, therefore `vvvv` = 0 / `0000` encodes as 0.0, aso.
  - `0111_vvvv`: float, (`-16.0 .. -1.0`) _(**float4**)_, therefore `vvvv` = 0 / `0000` encodes as -16.0, aso.
- `10`: [string]
  - `10ss_ssss`: size 0-60, 61=uint8, 62=uint16, 63=uint32
    - If the size is not embedded (61-63), then the size follows the **lead-in**, encoded as 1, 2 or 4 byte biased unsigned integer (biased by 61), BE encoded.
- `11`: structure
  - `11ss_tttt`
    - ss=0 / `00`: Empty
    - ss=1 / `01`: Size is **uint8**, 1 byte unsigned integer size
    - ss=2 / `10`: Size is **uint16**, 2 byte unsigned integer size
    - ss=3 / `11`: Size is **uint32**, 4 byte unsigned integer size
    - tttt= 0 / `11ss_0000`: [Array]
    - tttt= 1 / `11ss_0001`: [Map]
    - tttt= 2 / `11ss_0010`: [Set]
    - tttt= 3 / `11ss_0011`: [Object]
    - tttt= 4 / `11ss_0100`: [Tags]
    - tttt= 5 / `11ss_0101`: [Dictionary]
    - tttt= 6 / `11ss_0110`: [Book]
    - tttt= 7 / `11ss_0111`: [TupleNumberArray]
    - tttt= 8 / `11ss_1000`: [Tuple]
    - tttt= 9 / `11ss_1001`: [TWKB]
    - tttt=10 / `11ss_1010`: [ByteArray]
    - tttt=11 / `11ss_1011`: [Binary]
    - tttt=12 / `11ss_1100`: _reserved_
    - tttt=13 / `11ss_1101`: _reserved_
    - tttt=14 / `11ss_1110`: _reserved_
    - tttt=15 / `11ss_1111`: [UTF16 String] _(**Note**: Only ss=`11` is a valid encoding, please read [UTF16 String] section!)_

Technically, the **lead-in** byte can be decoded using one big switch statement with 256 cases (128 negative, 128 positive). The negatives are [String] and [Structures], while the positives are [primitives]. Beware that [primitives] should not be replaced with [references]. Beware that the [String] is a special form of [primitive]. The [string] can only be read at once, therefore, it counts as [primitive], even while technically it consists of multiple subunits being the [UNICODE] code-points.

JBON values are always copy-on-write, that means, every modification requires to copy the **JBON**. Therefore, all **JBONs** are immutable _(with one exception, the [Tuple], where the `next_version` can be modified, and it is designed like this)_. Reading a **JBON** requires a cursor that can be used to move through the **JBON** tree. As every _**unit**_ stores its outer size, every _**unit**_ (including all subunits) can be skipped over or entered by moving the cursor past the header. Note that only **strings** or **structures** can be entered, all other values are scalars.

There is additionally to these **JBON** encoded values a way to define raw data, so data without **lead-in**. It is being documented using `byte` for a single real byte, `byte[]`, for a dynamic amount of byte, and `byte[{size}]` for a specified amount of bytes.

## Primitives
As indicated in the **lead-in** section, scalars and fixed size encodings are primitives. The size of their encoding is implied by the **lead-in** byte, and sometimes even the value. If not, the value follows directly after the **lead-in** byte, and is always encoded in big-endian encoding.

All **lead-in** bytes between `1` _(inclusive)_ and `191` (inclusive)_ represent [primitives]. Therefore, formally this includes [strings], even while they have some special handling.

The [logical bytes] of a primitive are generated directly from the binary representation of the value like:

- `null`, `false` and `true` are encoded as their **lead-in** byte.
- All floating point numbers are encoded with the **lead-in** byte `0000_1011`, followed by the big-endian encoded 8-byte of the [IEEE-754] binary value.
- All integers are encoded with the **lead-in** byte `0000_0111`, followed by the big-endian encoded 8-byte of the integer value.
- The [uint56] is encoded with the **lead-in** byte `0000_0111`, followed by the big-endian encoded 8-byte of the integer value.
- The [uint24] is encoded with the **lead-in** byte `0000_0111`, followed by the big-endian encoded 8-byte of the integer value.
- The [timestamp] is encoded with the **lead-in** byte `0000_1100`, followed by the big-endian encoded 7-byte of the unsigned integer value _(so as is)_.
- The [TupleNumber] is encoded as is, so as **lead-in** byte `0000_1111`, followed by the 32-byte of the tuple-number.
- All raw bytes _(`byte`, `byte[]`, `byte[{size}]`)_ are encoded as is, no **lead-in** is being used.
- For [strings] a UTF-16 encoding is selected, read more at [string] documentation.

This means that all floating point numbers and integers are encoded with full 8 byte, even when they are actually stored in a smaller encoding. This allows to have the same hash for the same value, even when it is encoded differently, for example `int8` with value `1` and `int64` with value `1` will have the same binary _(logical)_ encoding.

The _logical bytes_ can theoretically be parsed as normal **JBON**.

### Integers
All integers referred to as `int` are variable encoded using `int4`, `int8`, `int16`, `int32` or `int64` encoding, whatever is the smallest encoding.

Additionally, there are two special encodings being [uint56] and [uint24] for sizes. They are as well valid replacements for `int`, they offer the advantage of having a fixed byte-size, no matter of what value is encoded. The encoder can choose them to avoid byte-copy operations. Sometimes they are intentionally explicitly hinted to be used. One well-known case is the `next_version` of [tuple], because this value is modified inside the otherwise immutable [tuple]. It is one very special situation where they are really needed.

### Floats
All floating-point numbers referred to as `float` are variable encoded using `float4`, `float8`, `float16`, `float32` or `float64` encoding, whatever is the smallest encoding.

### Primitive-Stringification
In **JBON** all integers and floating point numbers are always 64-bit, no matter how they are actually encoded in the binary. When needed, they can be stringified into [data URLs], with the following basic format: `data:{type},{value}`.

The types and values are:

- `data:naksha/undefined`: For `undefined`, when needed.
- `data:naksha/bool,{true|false}`: The boolean.
- `data:naksha/int,{decimal}`: A 32-bit integer value encoded as decimal, optionally prefixed with a minus.
- `data:naksha/int64,{decimal}`: A 64-bit integer value encoded as decimal, optionally prefixed with a minus.
- `data:naksha/double,{scientific}`: A 64-bit floating point number in standard scientific notation.
- `data:naksha/binary8,??`: 8-bit floating points as hexadecimal binary.
- `data:naksha/binary16,????`: 16-bit floating points as hexadecimal binary.
- `data:naksha/binary32,????????`: 32-bit floating points as hexadecimal binary.
- `data:naksha/binary64,????????????????`: 64-bit floating points as hexadecimal binary.

The value `null` exists in **JSON**. Boolean and numbers are part of the normal [JSON] specification, but at special places, i.e. when needed as keys. Another situation is a _JavaScript_ client, not being able to parse numbers `Number.MAX_SAFE_INTEGER` or `Number.MIN_SAFE_INTEGER` correctly _(so when `bigint` representation is needed)_, then the string encoding can be used _(`naksha/int64`)_.

## Indexable
The following types are indexable:

- boolean
- integer
- float
- timestamp
- string
- byte[]
- tuple-number
- tags

## Timestamp
A timestamp, encoded with a **lead-in** byte `0000_1100`. It encodes a unix epoch timestamp (UTC) in milliseconds, stored in big-endian encoding as 7-byte value following the **lead-in**. Therefore, it belongs to the primitives. We choose this encoding, because a year has 31,536,000,000 milliseconds, therefore 36-bit can encode 2 years, 40-bit encode 34 years, 48-bit encode already 8925 years, with 56-bit encoding around 2 million years, more than enough. Reducing the size from full 8 byte to 7 byte, saves one byte per value, but more significant, it allows to read timestamps atomically as a single 64-bit integer, then binary-ANDing with `0x00FF_FFFF_FFFF_FFFF` to get the timestamp in milliseconds.

Writing the timestamp is as simple, because we only binary-AND the timestamp with `0x00FF_FFFF_FFFF_FFFF`, then binary-OR with `0x0C00_0000_0000_0000`, and eventually write the 64-bit integer using big-endian encoding.

The timestamp is hashed with the **lead-in** byte `0000_1100`, followed by the big-endian encoded 7-byte of the unsigned integer value _(so as is)_.

The [JSON] representation of a timestamp is as [data URL] in the following format: `data:naksha/timestamp,{decimal}` with `{decimal}` being the numeric encoding of the EPOCH milliseconds.

## UInt56
A 56-bit unsigned integer encoded with a **lead-in** byte `0000_1101`.

This encoding was specifically added to improve the [TupleNumber] encoding, where the `version` part only uses a 56-bit unsigned integer. Such a `long` value, that only uses the lower 56-bit, will be encoded using the [UInt56] encoding. It is basically the same as the [Timestamp], just for arbitrary values.

Therefore, writing the unsigned 56-bit value is as simple, because we only binary-AND the `long` with `0x00FF_FFFF_FFFF_FFFF`, then binary-OR with `0x0D00_0000_0000_0000`, and eventually write the 64-bit integer using big-endian encoding.

Reading is as simple, because we only read the 64-bit integer using big-endian encoding, then binary-AND with `0x00FF_FFFF_FFFF_FFFF` to get the unsigned 56-bit value.

The uint56 is stored in [logical bytes] with the **lead-in** byte `0000_0111`, followed by the big-endian encoded 8-byte of the integer value _(so exactly like a 64-bit integer)_.

## UInt24
A 24-bit unsigned integer encoded with a **lead-in** byte `0000_1110`.

This encoding was specifically added to improve the encoding of the size of structures. Even while it wastes potentially one or two byte per structure, it can simplify the encoder a lot, because it reserves a fixed size to later store the actual structure size, when the encoding is done.

Therefore, writing the unsigned 24-bit value is simple, we only binary-AND the `int` with `0x00FF_FFFF`, then binary-OR with `0x0E00_0000`, and eventually write the 32-bit integer using big-endian encoding.

Reading is as simple, because we only read the 32-bit integer using big-endian encoding, then binary-AND with `0x00FF_FFFF` to get the unsigned 24-bit value.

The uint24 is stored in [logical bytes] with the **lead-in** byte `0000_0111`, followed by the big-endian encoded 8-byte of the integer value _(so exactly like a 64-bit integer)_.

## TupleNumber
A tuple is a unique immutable state of some arbitrary _feature_, this state is uniquely addressed using a tuple-number. The tuple-number stores the address of a single tuple as byte-array of the size 32. The **lead-in** is `0000_1111`.

The tuple-number has the following layout:

| Offset | Size | Name              | Type    | Description                                                                                                               |
|--------|------|-------------------|---------|---------------------------------------------------------------------------------------------------------------------------|
| 0      | 1    | lead_in           | `byte`  | The **lead-in** byte, `0000_1111`.                                                                                        |
| 1      | 8    | database_number   | `int64` | A 64-bit integer in big-endian byte-order, storing the database-number of the database in which the tuple is located.     |
| 9      | 4    | catalog_number    | `int32` | A 32-bit integer in big-endian byte-order, storing the catalog-number of the catalog in which the tuple is located.       |
| 13     | 4    | collection_number | `int32` | A 32-bit integer in big-endian byte-order, storing the collection-number of the collection in which the tuple is located. |
| 17     | 8    | feature_number    | `int64` | A 64-bit integer in big-endian byte-order, storing the feature-number of the tuple.                                       |
| 25     | 8    | version           | `int64` | A 64-bit integer in big-endian byte-order, storing the version of the tuple.                                              |

### Logical Bytes
The [logical bytes] of a tuple-number are exactly the same as the normal encoding, so the bytes can just be copied over.

### JSON
A single tuple-number is serialized [URN] string, in the format:
```
urn:naksha:tn:{database-number}:{catalog-number}:{collection-number}:{feature-number}[:{version}]
```

If the version is less than `1` or greater than _MAX_ version _(`9,007,199,254,740,987`)_, then the version is omitted in the stringified tuple-number; All these values actually mean _HEAD_ version _(`9,007,199,254,740,991`)_.

## Reference
References are used to relocate _**units**_ into [books]. Every _**unit**_ can be relocated into a [book] using a [reference]. From a decoder perspective, it requires an "enter" instruction, and pushes a return-address to the stack for "leave". In effect, a reference is transparent. A reference redirects to a _**unit**_ stored in one of the four context related [books]; it encodes the index in the [book].

The **lead-in** byte has the format `0011_bbss`.

The `bb` bits encode the [book] into which the reference directs:
- 0 / `00`: `local`
- 1 / `01`: `members`
- 2 / `10`: `global`
- 3 / `11`: `const`

The `const` [book] does only contain strings, therefore a normal reference into the `const` [book] is not possible. Decoders **MUST**, by definition, resolve all normal references into the `const` [book] to `undefined`. Encoders **MUST NOT** emit normal references into the `const` [book] _(bb=`11`)_.

The `ss` bits encode the size of the index:
- 0 / `00`: + 1 byte unsigned integer _(**ref8**)_
- 1 / `01`: + 2 byte BE unsigned integer _(**ref16**)_
- 2 / `10`: + 3 byte BE unsigned integer _(**ref24**)_
- 3 / `11`: + 4 byte BE unsigned integer _(**ref32**)_

A reference is fully transparent and does not have any logical representation. All references are fully removed from [logical bytes]. They need to be entered in the decoder, which will just jump to the target position _(logically `while (decoder.isReference()) { stack[SP++] = decoder.enter(); }`)_. If there is no plan to ever return, no stack is needed _(pure value lookup)_.

## String
A string is technically, from the decoder and logical perspective, a single [primitive] value. While technically it consists of code-points, and can include [references] to substrings, and can be referenced from other strings, the decoder should always just expose them as a single Java `Literal` instance.

**JBON** strings are **NOT** encoded using UTF-8, but a special encoding that is smaller and supports [references]. The **lead-in** for a string is `10vv_vvvv`. The value (`vv_vvvv`) stores the size of the string in byte:

- `0-60`: The embedded size of the string in byte (0-60).
- `61`: The size is stored biased by 61 in the next byte _(unsigned byte)_, resulting in a value between 61 and 316 _(inclusive)_.
- `62`: The size is stored unbiased in the next two byte _(unsigned short)_ using big-endian byte-order.
- `63`: The size is stored unbiased in the next four byte _(unsigned integer)_ using big-endian byte-order.

Encoders **MUST** choose the smallest width _(i.e. an encoder **MUST NOT** use the two byte size encoding for a size of 60 or less)_.

The code-points are variable encoded like in UTF-8, but shorter _(they are all just 1 to 3 byte long)_. The leading byte of every code-point signals the encoding:

- `0_vvv_vvvv`: The value encodes the code point value. Allows values between 0 and 127 _(inclusive)_, matching [ASCII].
- `100_vvvvv`: The value should be bitwise-ANDed with `000_11111`, then shift-left by 8; the value of the next byte should be bitwise-ORed, and finally 128 should be added. This results in values between 128 and 8319 _(inclusive, aka `2^13-1 + 128`)_.
- `101_vvvvv`: The value should be bitwise-ANDed with `000_11111`, then shift-left by 16; the value of the next two byte, in big-endian byte-order, should be bitwise-ORed, and finally 8320 should be added. This results in values between 8320 and 2,105,471 _(inclusive, `2^21-1 + 8320`)_.
  - We can encode code-points between `0` and `2,105,471`, twice the amount needed, because the biggest specified code-point value is `0x10FFFF` _(1,114,111)_.
- `11_aaabbs`: The value is a [reference] to a sub-string in one of the four books.
  - `s`: The size of the reference.
    - 0: Small reference, an unsigned 8-bit integer follows, with the index in the string-list of the [book].
    - 1: Large reference, an unsigned 24-bit integer in big-endian byte-order follows, with the index in the string-list of the [book]. Decoders can read the value as 32-bit integer in big-endian byte-order, then mask the top 8-bit to get the unsigned index.
  - `bb`: The [book] into which the reference directs:
    - 0 / `00`: `local`
    - 1 / `01`: `members`
    - 2 / `10`: `global`
    - 3 / `11`: `const`

Therefore, string-references are either two or four byte. Bits 3, 4 and 5 encode the append-rule (`aaa`). It signals if a special character should be added behind the referred string. The following values are defined:
- `000`: Do not encode any additional character _(`11_000_bbs`)_.
- `001`: Add a space (` `) behind the string _(`11_001_bbs`)_.
- `010`: Add a dot (`.`) behind the string _(`11_010_bbs`)_.
- `011`: Add a colon (`:`) behind the string _(`11_011_bbs`)_.
- `100`: Add a comma (`,`) behind the string _(`11_100_bbs`)_.
- `101`: Add a semicolon (`;`) behind the string _(`11_101_bbs`)_.
- `110`: Add a minus (`-`) behind the string _(`11_110_bbs`)_.
- `111`: Add an underscore (`_`) behind the string _(`11_111_bbs`)_.

### String-References
String-references use a different encoding than regular [references] because they live inside [string] code-point streams; in particular they support only 1- or 3-byte indices and may target the `const` [book].

As a string-reference consumes 2 or 4 byte. Therefore, it does not make sense to use a [reference] for every tiny substring. When the data can be encoded at same length or close, we do not want to use string-references. Therefore, we do not encode a string-reference for a string that is not at least 5 byte long in encoded form. This means, a [book] never stores strings being shorter than 3 byte, and rarely strings shorter than 5 characters _(reducing three byte to two byte means 33% less data!)_.

The appendable-bits (`aaa`) improve the compression rate, because the encoder will split strings mostly at the defined characters. When this happens, it's a larger reduction, so a 3 byte string plus appendable can potentially be reduced to a 2 byte reference, so compression is 50% instead of just 33%. In that case, even the tiny words can be compressed, for example `he is a good guy` can be compressed from 16 byte to potentially 10 byte _(`he`, `is`, `good`, and `guy` as 2-byte references plus appendable, `a ` as directly encoded characters)_, resulting in a 37.5% reduction.

As strings are kept in a separate section of [books], a string-reference needs to be encoded by first starting a string, then adding a string-reference into it. This means, a string-reference has 2 or 4 byte. As a [book] does not contain strings with string-references, this means all strings by definition have a reference depth of at most one.

### Split
When strings are split by the encoder, it uses the `BreakIterator` from the [ICU4J] library for that purpose. Pseudocode:

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
      
      // Process current, can be empty string, if it is a punctuation, and the previous operation appended it.
      // So it was only ` `, `.` or `:`.
      if (byteLength(part) >= 3) {
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
    } 
  }
}
```

### Logical Bytes
The [logical bytes] of a string is the [UTF16 string].

**Note on normalization**: All strings **must** be in Unicode NFC normalization form before encoding. Two strings that are logically identical but in different normalization forms (e.g. composed vs. decomposed characters) would otherwise produce different [logical bytes] and therefore different hashes, violating the goal that the same object always results in the same hash. Decoders may assume strings are NFC-normalized; encoders are responsible for normalizing before encoding.

## Null
The value `null` is a normal value, encoded as `0000_0001`. In this document all types are annotated with a question mark _(`?`)_ when they are allowed to be `null`. This includes _**unit**_, therefore `[unit]` means any value, except for `null`, while `[unit]?` means any value, including `null`. The same is true for other types like `int32`, which means the integer must be encoded, while `int32?` means that the value can be either an integer with maximum 32-bits, or `null`.

## Undefined
The `undefined` value is a special value, that is either set explicitly by encoding the **lead-in** `0000_0000`, or deduced implicitly. For example, when an [array] should have 5 elements, but it has only 1, then the last four elements are implicitly `undefined` _(deduced from context/situation)_. If the first element is explicitly set to `undefined`, then all elements are `undefined`.

The meaning of `undefined` is context dependent, but often used to refer to some default values or states. Generally it is important to understand that `undefined` is a valid value and often has some special handling. It is different from `null` in that the value `null` is always explicit _(encoded as `0000_0001`)_, while the value `undefined` can be explicit _(`0000_0000`)_ or implicit, deduced from absence or context/situation.

**Important**

Specifically, and this is by design, when the total size of a [structure] is less than the attributes defined, then all attributes that are not explicitly encoded, are implicitly `undefined`! This allows to truncate all [structures] and does not require to encode attributes not needed or that should be default.

## Structures
The **lead-in** of all structures starts with the top most two bit _(bit 7 and 6)_ set (`11`) and the basic format: `11ss_tttt`. The bit 5 and 4 (`ss`) encode the width of the size field of the structure:

- 0 / `00`: Empty _(this means by definition that the total byte size is exactly 1, because of the lead-in byte)_
- 1 / `01`: Size is **uint8**, 1 byte unsigned integer size
- 2 / `10`: Size is **uint16**, 2 byte unsigned integer size
- 3 / `11`: Size is **uint32**, 4 byte unsigned integer size

If it is not empty, then the lowest four bit (`tttt`) encode the type of the structure.

For the `Type` column in the following structure tables the maximum allowed type is used. The **lead-in** is always a single byte of type `byte` with the following size described as `int32`, encoded either as 1 byte, 2 byte or 4 byte unsigned integer in big-endian byte-order.

When `ss=0` _(empty structure)_, the `byte_size` field is omitted; the total size of the structure is exactly 1 byte _(the **lead-in** only)_. All other fields shown in the structure tables below are likewise absent in the empty form. The `ss=00` _(empty form)_ is only meaningful for [Array], [Map], [Set], [Object], [Tags], [Dictionary] and [TupleNumberArray] _(where it represents the empty collection)_. For [Book], [Tuple], [TWKB], [Binary], and [UTF16 String], the `ss=00` form is invalid; decoders **MUST** reject it as malformed, and encoders **MUST NOT** emit it.

All other values are variable encoded, for example `int` means any integer, `float` means any floating-point number. Therefore, an `int` with value `0` can be encoded as `int4`, the float `1.0` as `float4`. A question mark _(`?`)_ behind a type means that the value is nullable, so `null` can be stored instead of the actual value. If that is not the case, the value must not be `null`, nor a [reference] to `null` is allowed. Beware that all _**units**_ can always be replaced with a [reference] to relocate the _**unit**_ into a [book].

Generally, all structures have a _header_ that is normally decoded with the **lead-in**, and then a body with the actual data that can be entered and "iterated". This is indicated by an empty row in the overview table.

Decoders encountering _reserved_ types **MUST** reject the **JBON** as malformed _(within their own version)_; higher-version binaries are already rejected by the file header check.

For all structures we separate the _header_ from the _content_ by adding an empty table row into the layout table. This is helpful for implementors, because all _header_ fields should be decoded at ones, when the _**unit**_ is processed, while the _content_ is different. The _content_ persists out of an arbitrary number of child-units _(then it can be entered)_ or of some binary data.

### Undefined
A finalized structure **must not** contain any `undefined` _**units**_.

The value `undefined` can be used in special situations to indicate default value usage or to revoke keys from a map. The documentation will clarify what is to be done by the decoder, when `undefined` is encountered. In doubt, the decoder must replace all illegally left over `undefined` values with `null`. If that leads to an invalid document, it should throw an exception indicating a broken **JBON** binary.

### The type-property
When any structure does have a property named `@type`, it is escaped by wrapping it into an array, with the real type being the first element and the actual value being the second element. Therefore, assume i.e. the [object] does have a property `@type` set to value `5`, then it would be serialized like this:

```javascript
var object = {
    "@type": ["naksha:object", 5]
}
```

The same for example for a [map] would result in:

```javascript
var object = {
    "@type": ["naksha:map", 5]
}
```

---

### Array (0)
An array of arbitrary other _**units**_, using the **lead-in** byte `11ss_0000`; with `ss` encoding the size of the size, as usual.

| Name      | Type         | Description                                                           |
|-----------|--------------|-----------------------------------------------------------------------|
| lead_in   | `byte`       | The **lead-in** byte, `11ss_0000`.                                    |
| byte_size | `int32`      | The total size of the structure, including the **lead-in**, in bytes. |
|           |              |                                                                       |
| elements  | ([unit]?)... | The values of the array.                                              |

If `ss=00` _(**lead-in** is `1100_0000`)_, the array is empty (`[]`).

#### Logical Bytes
The [logical bytes] of the array are calculated by adding the **lead-in** `1111_0000`, the byte-size as 32-bit BE integer, followed by all `elements` in order, if there are any. The same rules apply while generating the [logical bytes] that apply generally when decoding. So `elements` being [references], have to be treated as if they were embedded, so they need to be added to the [logical bytes] the same way that real embedded values are.

#### JSON
The [JSON] serialization is as normal plain array:

```javascript
var array = []
```

---

### Map (1)
A map is a key-value store with the **lead-in** byte being `11ss_0001`; with `ss` encoding the size of the size, as usual.

| Name      | Type                      | Description                                                           |
|-----------|---------------------------|-----------------------------------------------------------------------|
| lead_in   | `byte`                    | The **lead-in** byte, `11ss_0001`.                                    |
| byte_size | `int32`                   | The total size of the structure, including the **lead-in**, in bytes. |
|           |                           |                                                                       |
| entries   | ([primitive], [unit]?)... | The key-value pairs.                                                  |

If `ss=00` _(**lead-in** is `1100_0001`)_, this implies an empty map _(`{"@type":"naksha:map"}`)_.

The entries are encoded [sorted] by the key. The keys must not be `null`, `undefined` or duplicates.

#### Logical Bytes
The [logical bytes] of the map are created by adding the **lead-in** `1111_0001`, followed by the byte-size as 32-bit BE integer, followed by all entries [sorted] ascending _(note that units are anyway encoded ordered)_.

#### JSON
The [JSON] serialization is as object with special type property:

```javascript
var map = {
  "@type": "naksha:map",
  "key": value
}
```

For the keys, the [primitive-stringification] is used, if needed.

---

### Set (2)
A set is a special [map] that does not store values, therefore it is a key-only map. The **lead-in** byte is `11ss_0010`; with `ss` encoding the size of the size, as usual.

| Name      | Type             | Description                                                           |
|-----------|------------------|-----------------------------------------------------------------------|
| lead_in   | `byte`           | The **lead-in** byte, `11ss_0010`.                                    |
| byte_size | `int32`          | The total size of the structure, including the **lead-in**, in bytes. |
|           |                  |                                                                       |
| entries   | ([primitive])... | The entries of the set.                                               |

If `ss=00` _(**lead-in** is `1100_0010`)_, this implies an empty set _(`{"@type":"naksha:set"}`)_.

The entries in a set are not sorted, the order is significant. The entries must not be `null`, `undefined` or duplicates.

#### Logical Bytes
The [logical bytes] of the set are calculated by adding the **lead-in** `1111_0010`, followed by the byte-size as 32-bit BE integer, followed by all `entries` [sorted] in ascending order. The same rules apply while generating the [logical bytes] that apply generally when encoding [logical bytes]. So, `entries` being [references] have to be treated as if they were embedded, so they need to be added to the [logical bytes] the same way that real embedded values are.

#### JSON
The [JSON] serialization is done as object with values being `null`, and with a special type property:

```javascript
var set = {
  "@type": "naksha:set",
  "entries": []
}
```

In [JSON] we have no better alternative to encode a set. For the entries, the [primitive-stringification] is used, if needed.

### Object (3)
An object is a special [map] that only allows strings as keys. The **lead-in** byte is `11ss_0011`; with `ss` encoding the size of the size, as usual. All keys must be [strings].

| Name      | Type                  | Description                                                           |
|-----------|-----------------------|-----------------------------------------------------------------------|
| lead_in   | `byte`                | The **lead-in** byte, `11ss_0011`.                                    |
| byte_size | `int32`               | The total size of the structure, including the **lead-in**, in bytes. |
|           |                       |                                                                       |
| entries   | ([string],[unit]?)... | The key-value pairs of this object, [sorted] by the key.              |

If `ss=00` _(**lead-in** is `1100_0011`)_, this implies an empty object (`{}`).

The entries need to be [sorted] ascending by key. The decoder should raise an exception, if the key is invalid, so the key is `null`, `undefined`, or a [reference] to some invalid _(not string)_ _**unit**_. If the keys are present but not in sorted order, the decoder must raise an exception — it must not silently re-sort them, as out-of-order keys indicate a malformed binary.

#### Logical Bytes
The [logical bytes] of the object are created by adding the **lead-in** `1111_0011`, followed by the byte-size as 32-bit BE integer, followed by all entries. The sorted keys are iterated, adding the key and value to the [logical bytes]. This is needed to ensure that two objects always generate the same [logical bytes], when they contain the same entries; independent of the encoding and entry order. Note that by sorting the keys, we ensure that the order of the keys does not matter for the [logical bytes], which is important so that `{a:1,b:2}` actually equals `{b:2,a:1}`.

#### JSON
The [JSON] serialization is a simple plain object:

```javascript
var object = {}
```

However, in conflict case the explicit type name is `naksha:object`, needed only when the object has an explicit property named `@type`.

---

### Tags (4)
The tags are a special [map] that allow only strings as keys and [primitives] as values. The **lead-in** byte is `11ss_0100`; with `ss` encoding the size of the size, as usual.

| Name      | Type                        | Description                                                           |
|-----------|-----------------------------|-----------------------------------------------------------------------|
| lead_in   | `byte`                      | The **lead-in** byte, `11ss_0100`.                                    |
| byte_size | `int32`                     | The total size of the structure, including the **lead-in**, in bytes. |
|           |                             |                                                                       |
| entries   | ([string], [primitive]?)... | The key-value pairs.                                                  |

If `ss=00` _(**lead-in** is `1100_0100`)_, this implies empty tags _(`{"@type":"naksha:tags"}`)_.

The entries in the maps are always encoded [sorted] ascending by the key.

#### Logical Bytes
The [logical bytes] of the tags are created by adding the **lead-in** `1111_0100`, followed by the byte-size as 32-bit BE integer, then all entries in order _(therefore, [sorted] by key, ascending)_.

#### JSON
The [JSON] serialization is as object with special type property:

```javascript
var tags = {
  "@type": "naksha:tags",
  "tag_name": tag_value
}
```

---

### Dictionary (5)
A dictionary is a special [map], that only allows strings as keys and values, with the **lead-in** byte being `11ss_0101`; with `ss` encoding the size of the size, as usual.

| Name      | Type                    | Description                                                           |
|-----------|-------------------------|-----------------------------------------------------------------------|
| lead_in   | `byte`                  | The **lead-in** byte, `11ss_0101`.                                    |
| byte_size | `int32`                 | The total size of the structure, including the **lead-in**, in bytes. |
|           |                         |                                                                       |
| entries   | ([string], [string])... | The key-value pairs.                                                  |

If `ss=00` _(**lead-in** is `1100_0101`)_, this implies an empty dictionary _(`{"@type":"naksha:dictionary"}`)_.

The entries in the dictionary are always encoded [sorted] ascending by the key.

#### Logical Bytes
The [logical bytes] of the dictionary is created by adding the **lead-in** `1111_0101`, followed by the byte-size as 32-bit BE integer, then all entries in order _(therefore, [sorted] by key, ascending)_.

#### JSON
The [JSON] serialization is as object with special type property:

```javascript
var object = {
  "@type": "naksha:dictionary",
  "key": "{value}"
}
```

---

### Book (6)
A book is a special purpose container; the **lead-in** is `11ss_0110`.

The layout of a book is as follows:

| Section         | Type               | Description                                                           |
|-----------------|--------------------|-----------------------------------------------------------------------|
| lead_in         | `byte`             | The **lead-in** byte, `11ss_0110`.                                    |
| byte_size       | `int32`            | The total size of the structure, including the **lead-in**, in bytes. |
| book_type       | [int]              | The type of the book; 0=`local`, 1=`members`, 2=`global`.             |
| database_number | [int]?             | The _optional_ database-number of this book.                          |
| feature_number  | [int]?             | The _optional_ feature-number of this book.                           |
| id              | [String]?          | The _optional_ identifier of this book.                               |
| memberNames     | [array]<[string]>? | The _optional_ names of the elements.                                 |
|                 |                    |                                                                       |
| strings         | [array]<[string]>? | All strings, reachable via [string-references].                       |
| elements        | [array]<[unit]?>?  | All elements, reachable via [references].                             |

- A `global` book must have a valid `database_number` and `feature_number`.
- The `id` is optional for all books.
- The explicit or implicit value `undefined` has the same meaning as an explicit `null`; both values signal that the corresponding field is `null`.
- The `memberNames` is a mandatory field for `members` books, it is optional for all other book types.

Encoders must not encoder `global` books without `database_number` or `feature_number`. Decoders should report an error, when they encounter such books.

Encoders must not encode `members` books without `memberNames`, the field is optional for all other books. The array must have the same length as the `elements` array. The member-names are the names of the members stored in the `elements` of the book. Beware that the `members` book normally is generated by the storage.

The content of a book has two sections: `strings` and `elements`. The `strings` are only reachable via [string-references]; `elements` are reachable via [reference]. Both references use the index in the array, **not the offset** _(the byte-position)_. The `strings` array must only contain [strings] without [string-references]; the encoder must deny encoding of [strings] that contain [string-references] into a book, the decoder must reject these encodings as invalid **JBON**!

Beware that the index space is overlapping between [string-references] and normal [references]. So, a normal [reference] to index `0` will read the first _**unit**_ from `elements`, while a [string-reference] to index `0` will read the first string from the `strings`.

All **JBON**'s have four books used for encoding and decoding, named `local`, `members`, `global`, and `const`. Each book has a specific purpose.

| Book      | Description                                                                                                                                                                                                                                                                                                                                                                                                                                                                        | References                                  |
|-----------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------|
| `local`   | The `local` book encodes all the data that is accessed via references from the encoded **JBON**. Some things require references, like [Map] encoding, therefore a **JBON** always requires a `local` book.                                                                                                                                                                                                                                                                         | to `local`, `members`, `global` or `const`. |
| `members` | The `members` book is used by storage-engines to offload data into dedicated places within the storage. For example, when a **JBON** is stored in a database, and some data should be indexed, then this data need to be stored in a dedicated table column. The storage-engine therefore will require the data to be provided in the `members` book. Due to the reference system, that very same data can be embedded in the feature, or not. This is decided by the application. | to `members`, `global` or `const`.          |
| `global`  | The `global` book is located outside of the **JBON**, it is shared between multiple **JBONs**.                                                                                                                                                                                                                                                                                                                                                                                     | to `global` and `const`.                    |
| `const`   | The `const` book is hardcoded in the **JBON** specification, as the name suggests it is constant.                                                                                                                                                                                                                                                                                                                                                                                  | to `const` only.                            |

#### Logical Bytes
A book is transparent and therefore not part of the [logical bytes] of any other [unit]. However, it has [logical bytes] itself, if requested. These are calculated by adding the **lead-in** `1111_0110`, the byte-size as 32-bit BE integer to the [logical bytes], then the `book_type`, `database_number`, `feature_number`, and `id`, followed by the [logical bytes] of the `memberNames` _(can be `null`, but must not be `undefined`)_. Eventually, the [logical bytes] of the `strings` and `elements` are added, in that order. Therefore, the [logical bytes] are very close to the original book, but all child _**units**_ will be unpacked, and the `id` is embedded as [UTF16 string] _(because [logical bytes] always only contain UTF16 strings)_. The [logical bytes] are never truncated, so implicit `undefined` values are encoded as `null`.

#### JSON
The [JSON] serialization is as object with special type property:

```javascript
var book = {
  "@type": "naksha:book",
  "id": "{id}", // Only if available
  "tn": "{tuple-number}", // Only if `db` and `fn` are available
  "memberNames": [], // Only if `memberNames` is not null or undefined.
  "strings": [],
  "elements": []
}
```

**Note**: The tuple-number can be generated, because the catalog- and collection-numbers, and version are the same for all books. The serialization will be done without version, because books all have version `0`. _(TODO: the fixed catalog-number and collection-number values for the `naksha~admin` / `naksha~books` collection are defined in [`lib-data`](LIB_DATA.md) and will be referenced here once that spec stabilizes.)_

---

### TupleNumberArray (7)
A tuple is a unique immutable state of a _feature_, uniquely addressed using a tuple-number. The tuple-number-array is an efficient way to encode multiple tuple-numbers compact. The **lead-in** is `11ss_0111`.

This form of encoding reduces the encoding size of multiple tuple-numbers greatly, while only mildly increasing the size of a single tuple-number _(which we rarely ever find anywhere)_. However, multiple tuple-numbers are encountered quite often, for example when transferring the result of a database query to a client. Therefore, we want to encode them very efficiently _(as small as possible)_:

| Section           | Type     | Description                                                                                                                                                                          |
|-------------------|----------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| lead_in           | `byte`   | The **lead-in** byte, `11ss_0111`.                                                                                                                                                   |
| byte_size         | `int32`  | The total size of the structure, including the **lead-in**, in bytes.                                                                                                                |
| database_number   | [int]    | The shared database number of each [tuple] in the array; explicitly `undefined` if not shared.                                                                                       |
| catalog_number    | [int]    | If `database_number` is `undefined`, this field is implicitly `undefined`; otherwise the shared catalog number of each [tuple] in the array; explicitly `undefined` if not shared.   |
| collection_number | [int]    | If `catalog_number` is `undefined`, this field is implicitly `undefined`; otherwise the shared collection number of each [tuple] in the array; explicitly `undefined` if not shared. |
| feature_number    | [int]    | If `collection_number` is `undefined`, this field is implicitly `undefined`; otherwise the shared feature number of each [tuple] in the array; explicitly `undefined` if not shared. |
|                   |          |                                                                                                                                                                                      |
| entries           | `byte[]` | The actual tuple-numbers encoded as specified in the [Naksha data model Tuple-Number] section, except for the shared components.                                                     |
If `ss=00` _(**lead-in** is `1100_0111`)_, the array is empty.

Therefore:
- If `database_number` is `undefined`, then each entry is encoded in 32 byte _(`database_number`, `catalog_number`, `collection_number`, `feature_number`, and `version`)_.
- If `catalog_number` is `undefined`, then each entry is encoded in 24 byte _(`catalog_number`, `collection_number`, `feature_number`, and `version`)_, sharing the same `database_number`.
- If `collection_number` is `undefined`, then each entry is encoded in 20 byte _(`collection_number`, `feature_number`, and `version`)_, sharing the same `database_number` and `catalog_number`.
- If `feature_number` is `undefined`, then each entry is encoded in 16 byte _(`feature_number`, and `version`)_, sharing the same `database_number`, `catalog_number` and `collection_number`.
- If none of them is `undefined`, then each entry is encoded in 8 byte _(`version`)_, sharing the same `database_number`, `catalog_number`, `collection_number` and `feature_number`.

**Encoding details**:
- The shared `database_number`, `catalog_number`, `collection_number`, and `feature_number` header fields use normal variable JBON integer encoding _(`int4` / `int8` / `int16` / `int32` / `int64`)_, so small shared values can be encoded compactly.
- Inside `entries`, all components use fixed-width big-endian raw bytes _(no JBON lead-ins)_: `database_number` = 8 bytes, `catalog_number` = 4 bytes, `collection_number` = 4 bytes, `feature_number` = 8 bytes, `version` = 8 bytes. The per-entry totals above _(32, 24, 20, 16, 8)_ follow directly from these widths.
- The value `null` in `database_number`, `catalog_number`, `collection_number`, or `feature_number` header fields has the same meaning as an explicit or implicit `undefined`; they both signal that the corresponding value, and all following, are `undefined`.

So, if `database_number`, `catalog_number`, `collection_number` and `feature_number` are all shared, all [Tuple] are of the same _feature_, just in a different `version`. This happens for example when loading all states of a specific _feature_ from the database. This uses the least amount of space per entry, only 8 byte per entry.

The most common encoding will have `database_number`, `catalog_number` and `collection_number` shared, but `feature_number`, therefore encoding `feature_number` and `version` for each entry _(16 byte)_. This represents different _features_ from the same collection; happens i.e. as result of a query from a single collection.

The second most common encoding will have `database_number` and `catalog_number` shared, but `collection_number`, therefore, encoding `collection_number`, `feature_number` and `version` for each entry _(20 byte)_.

Potentially rarely found are encodings where `catalog_number` or even `database_number` are not shared, as this wildly mixes data from different sources. However, it is not totally impossible.

**A single tuple-number should be encoded in 35 byte, so **lead-in** _(1 byte, `1101_0111`)_, `byte_size` _(1 byte)_, `database_number` as explicitly `undefined` _(1 byte)_ and the actual tuple-number value as full qualified _(32 byte)_ value.**

#### Logical Bytes
The [logical bytes] of a tuple-number-array is generated by adding the **lead-in** `1111_0111`, followed by the 32-bit BE integer of the byte-size, then the bytes of each contained tuple-number in full encoding as specified by the [Naksha data model Tuple-Number]. Therefore, each tuple-number actually is added as fixed size 32-byte value in big-endian byte-order. This means, that for each tuple-number the `database_number`, `catalog_number`, `collection_number`, `feature_number` and `version` are added to the [logical bytes] simply as 32-bit or 64-bit integers, in big-endian byte-order. The `version` occupies 8 bytes in [logical bytes]; the upper 8 bits MUST be zero (only the lower 56 bits carry value).

**Notes**

- We do not sort the tuple-numbers, because the order is significant, as they represent a result-set.
- Like [Array], `TupleNumberArray` is order-sensitive; reordering produces different logical bytes and a different hash.

#### JSON
The tuple-number-array is encoded as a [JSON] object with a specific type:

```javascript
var tn = {
  "@type": "naksha:tn",
  "databaseNumber": 123456789, // only if shared
  "catalogNumber": 123456789, // only if shared
  "collectionNumber": 123456789, // only if shared
  "featureNumber": 123456789, // only if shared
  "entries": [
      // each entry contains the parts not shared, between 1 and 5
      // {database-number}, {catalog-number}, {collection-number}, {feature-number}, {version}
      // {catalog-number}, {collection-number}, {feature-number}, {version}
      // {collection-number}, {feature-number}, {version}
      // {feature-number}, {version}
      // {version}
      []
  ]
}
```

The version can't be omitted, therefore, if the version is less than `1` or greater than _MAX_ version _(`9,007,199,254,740,987`)_, it should be encoded as `0`, representing _HEAD_ _(`9,007,199,254,740,991`)_.

**Note**

When encoding for _JavaScript_ clients the 64-bit integers may have to be stringified as [data URL], because _JavaScript_ does not support full 64-bit integers in [JSON], when the standard parser is used. For example the database number `10007199254880991` must be encoded as `"data:naksha/int64,10007199254880991"`.

---

### Tuple (8)
The tuple is a special **JBON** container designed to exchange _features_ in a dedicated state between services, components, caches, and storages like a database or a file; the **lead-in** is `11ss_1000`.

The tuple is a special encoding linked to the [Naksha data model]. The tuple itself is encoded like following:

| Section      | Type     | Description                                                           |
|--------------|----------|-----------------------------------------------------------------------|
| lead_in      | `byte`   | The **lead-in** byte, `11ss_1000`.                                    |
| byte_size    | `int32`  | The total size of the structure, including the **lead-in**, in bytes. |
|              |          |                                                                       |
| feature      | [Object] | The _feature_ to decode _(mandatory, not `null` or `undefined`)_.     |
| local_book   | [Book]?  | The _optional_ `local` [book].                                        |
| members_book | [Book]?  | The _optional_ embedded `members` [book].                             |
| global_book  | [Book]?  | The _optional_ embedded `global` [book].                              |

A tuple can only be decoded with the `global` and the `members` [books] provided as context to the decoder.

The encoder is allowed to append the `members` [book] and the `global` [book] to the tuple. If the `members` [book] is not provided to the decoder context, it should check whether one is appended and use it. If the `global` [book] is not provided to the decoder context, it should check whether there is one appended and use it. If both context and an appended copy are present, the decoder should prefer the context-provided one and ignore the appended copy. The appended [books] are encoded as standalone **JBON** structures: each has its own **lead-in** byte with an explicit size, so the encoder can drop trailing [books] entirely. The decoder can detect this by reaching the end of the declared tuple `byte_size` before finding the **lead-in** of the expected [book].

The encoder may explicitly set both appendable [books] to `null`, and they can be implicitly set to `undefined` by truncating the tuple. Trailing books may only be dropped from the end, i.e. `global_book` MUST be dropped before `members_book`. It is an invalid **JBON** if there is a `global` [book] without a `members` [book] before it. An explicit `null` for `members_book` still counts as 'present' for this rule, so a `global_book` MAY follow a `null` `members_book`, and the `global_book` itself MAY be either appended, explicitly `null`, or truncated.

The `feature_number` of the `global` [book] **MUST** match the `global_book_fn` member.

The `database_number` of the `global` [book] **MUST** match the `database_number` of the tuple, encoded in the `tn` member, storing the full qualified tuple-number of the tuple.

The `members` [book] is per-tuple and travels with the tuple. This means, the storage need to keep the content of the `members` [book] next to the `tuple` and always read them together. It can embed the members into the tuple, or do something else. For example, in `lib-psql` _(the PostgreSQL implementation of `lib-data` storage API)_ the members are stored as own dedicated columns. So, `lib-psql` will store `feature` _(the actual tuple)_, `fn`, `version`, `global_book_fn`, `next_version`, and `id` as dedicated database columns. If more members are defined for a collection, then `lib-psql` will generate more columns in the storage. This is as well the reason why the encoder always needs the collection specification, because the members are not arbitrary, they **MUST** match exactly the specified ones in the collection definition. However, for the decoder this is not important, the decoder has references in the _feature_ that refer to the member slots, therefore, it does not need any knowledge about the collection. It is able to decode the tuple with only the members [book] provided by the storage.

Some `elements` of the `members` [book] have a pre-defined meaning:

| Name             | Path   | Type          | Description                                                                                                                              |
|------------------|--------|---------------|------------------------------------------------------------------------------------------------------------------------------------------|
| `tn`             | `tn`   | [TupleNumber] | The [Tuple-Number] of this tuple.                                                                                                        |
| `global_book_fn` | `gbfn` | [int]?        | The _optional_ feature-number of the `global` [book] needed to decode; `null` if no global book is needed.                               |
| `next_version`   | `nv`   | [uint56]      | The next version of the tuple; if the tuple is in _HEAD_ state the value will be `9_007_199_254_740_991L`.                               |
| `id`             | `id`   | [String]?     | The _optional_ identifier of this tuple; a string when the feature-number is negative; `null` when the feature-number is positive (≥ 0). |
| ...              | ...    | [indexable]?  | All custom members appended starting here, types **MUST** be [indexable].                                                                |

The `next_version` MUST be encoded as [uint56] _(**lead-in** `0000_1101`)_, so it can be patched in place without changing the byte size of the tuple.

A `Path` of `(none)` means the value is metadata stored only in the `members` [book] and never exposed under a path in the [GeoJSON] _feature_.

The `global` [book] is referred only by feature-number, because it **MUST** originate from the same database the tuple is stored in. All `global` [books] are stored in the admin-catalog _(`naksha~admin`)_ of the database, in the special global [book] collection _(`naksha~books`)_. All [books] in this collection are uniquely identifiable by the database-number and feature-number. Beware that [books] are immutable, therefore, the global [books] collection does not have a _HISTORY_ section. The collection stores all [books] of this database, and all cached [books] of other databases. In other words, all [books] always have the same `catalog_number` and `collection_number`.

The `local` [book] is used by the **JBON** encoder to reuse values that occur multiple times in the actual `feature`, i.e. to deduplicate strings or objects. The same way the `global` [book] is used for cross tuple value deduplication. The `members` [book] is used by the application to relocate parts of the `feature` into dedicated storage slots. The `members` [book] **MUST** only contain _**units**_ that are [indexable].

All members need to be specified within the collection definition, including an `index` from 0 to _n_ and a `path`. Deleting a member will just flag it as deleted, and in new encoded `members` [books] the corresponding slot **MUST** be set to `undefined`.

#### Encoding
To convert a [GeoJSON] _feature_ into a tuple, the application has two options.

The most common option is to provide the collection definition, the _feature_, and the `global` [book] to the encoder:

```java
@NotNull JbonTuple encode(
    @NotNull CollectionProxy collection,
    @NotNull FeatureProxy feature,
    @Nullable BookProxy global_book
);
```

The encoder will convert the _feature_ into a tuple and fill the `members` [book] with the relocated values, as specified by the given collection. So, it will automatically relocate properties from the _feature_ into the `members` [book] as specified in the collection definition, and fail with an exception should any of the properties have a value that is not [indexable].

The second option is a rather low-level option, used when ultimate efficiency in storage size is needed. This option is to provide the collection definition, the `feature_id`, `feature_number`, `version`, `next_version`, `global` [book], the `index` in the global [book] of the _feature_ template, and a `members` [book] with the actual values to store. This will encode a tuple where the _feature_ is just a reference into the `global` [book] that **MUST** be located at the given `index` as an [Object], encoding the _feature_. This template feature **MUST** contain pre-defined relocations into the `members` [book]. The given `members` [book] then should contain all the values being referred by the template:

```java
@NotNull JbonTuple encode(
    @NotNull CollectionProxy collection,
    @Nullable String id,
    long feature_number, // If the `id` is given (not null), then the `feature_number` is ignored, because it is calculated from the id
    long version,
    // Note: All invalid values, less than 1 or greater than MAX version (9_007_199_254_740_987L), are encoded as HEAD (9_007_199_254_740_991L) 
    long next_version,
    @NotNull BookProxy global_book,
    int index,
    @NotNull BookProxy members_book
);
```

This means that there is no `local` [book] in the tuple, and the feature is encoded using a 2, 3 or 5 byte [reference]. All other values are located in dedicated storage members.

For efficient storage implementations, like i.e. `lib-psql`, such a low-level optimized tuple _(without identifier)_ is stored with only 26- to 31-byte overhead in the _HEAD_ table. So, `lib-psql` will actually store the tuple split into columns, with `fn` as PostgreSQL `int8`/`bigint` _(8 byte)_, `version` as `int8`/`bigint` _(8 byte)_, `id` as `text` _(0 byte)_, `global_book_fn` as `int8`/`bigint` _(8 byte)_, and `feature` being a [reference] into `global` [book] _(2, 3 or 5 byte)_. All other columns are filled from the `members` [book]. When `lib-psql` reads the feature, it re-creates the _header_ from these columns, and then appends the `feature`. Normal features will have a bigger overhead, because they require a `feature` encoding, and most likely a `local` [book] after the `feature`.

#### Decoding
When reading a tuple, the application can directly access the tuple, avoiding the conversion of the tuple into a [GeoJSON] _feature_. Otherwise, it can ask the decoder to convert the tuple into a [GeoJSON] _feature_. This does not require the collection definition, because the _feature_ will have [reference] into the `members` [book] for those values that have been relocated from the `feature` into the `members` [book] using a path-definition. However, the decoder needs the `global` and `members` [books].

#### Replication
The design allows replicas to re-encode features. Assuming the replica wants to index additional properties of the _feature_, it can convert a tuple into a [GeoJSON] _feature_ and then use the automatic conversion of `lib-data` to convert this _feature_ into another tuple with more _(or fewer)_ `members`. The _feature_ will still have the same hash, but be encoded differently, maybe even using a special `global` [book] to improve compression. The tuple does have the same tuple-number, and it will be logically the same.

#### Logical Bytes
The [logical bytes] of a tuple are calculated by adding the **lead-in** `1111_1000`, then the 32-bit BE integer size in bytes, and then the [logical bytes] of the `feature`. This means the [logical bytes] do not contain any [book] anymore.

#### JSON
Tuples are exposed as normal [GeoJSON] features with the additional properties `tn`, a string encoding the [tuple-number] of the _feature_, and _(optionally)_ the `nv`, storing the next version. This is only done, when the feature comes from _HISTORY_, for _HEAD_ features `nv` is not exposed. Therefore, a feature without `nv` has by definition _HEAD_ as next-version. They do not need any [books], because they are always fully decoded.

**Note**: If the `id` is `null` in the **JBON**, the JSON `id` is derived as the decimal string of the feature-number.

---

### TWKB (9)
The geometry [Tiny WKB] encoded. The **lead-in** of a binary is `11ss_1001`; with `ss` encoding the size of the size, as usual. The binary format is like:

| Name      | Type     | Description                                                           |
|-----------|----------|-----------------------------------------------------------------------|
| lead_in   | `byte`   | The **lead-in** byte, `11ss_1001`.                                    |
| byte_size | `int32`  | The total size of the structure, including the **lead-in**, in bytes. |
|           |          |                                                                       |
| data      | `byte[]` | The [TWKB] bytes.                                                     |

#### Logical Bytes
The [logical bytes] of the [Tiny WKB] is created by adding the **lead-in** `1111_1001`, followed by the byte-size as 32-bit BE integer, then the `data` bytes, being the [TWKB].

#### JSON
In [JSON] the [TWKB] is stringified using the types specified in [GeoJSON] specification.

---

### ByteArray (10)
The binary structure is used to store binary content, actually byte-arrays of custom data. The **lead-in** of a binary is `11ss_1010`; with `ss` encoding the size of the size, as usual. The binary format is like:

| Name        | Type         | Description                                                           |
|-------------|--------------|-----------------------------------------------------------------------|
| lead_in     | `byte`       | The **lead-in** byte, `11ss_1010`.                                    |
| byte_size   | `int32`      | The total size of the structure, including the **lead-in**, in bytes. |
|             |              |                                                                       |
| data        | `byte[]`     | The bytes.                                                            |

The size of the `data` matches the remaining bytes implied by `byte_size` minus the header size.

#### Logical Bytes
The [logical bytes] of a byte-array are calculated by adding the **lead-in** `1111_1010`, then the byte-size as 32-bit BE integer, followed by the `data`.

#### JSON
In [JSON] and [XML] the binary is encoded as a string using the [data URL scheme] with mime-type being `naksha/int8a`. The actual payload is [base64] encoded:

`data:naksha/int8a;base64,<data>`

---

---

### Binary (11)
The binary structure is used to store binary content, actually byte-arrays of custom data. The **lead-in** of a binary is `11ss_1011`; with `ss` encoding the size of the size, as usual. The binary format is like:

| Name        | Type         | Description                                                                                                       |
|-------------|--------------|-------------------------------------------------------------------------------------------------------------------|
| lead_in     | `byte`       | The **lead-in** byte, `11ss_1011`.                                                                                |
| byte_size   | `int32`      | The total size of the structure, including the **lead-in**, in bytes.                                             |
| mime_type   | [string]?    | The MIME-Type of the binary, if `null`, defaults to `application/octet-stream` from the `const` book.             |
| compression | [string]?    | The compression algorithm used; `null` if not compressed.                                                         |
| target_size | [int]?       | If a compression algorithm is used, the amount of decompressed bytes _(for buffer allocation)_; otherwise `null`. |
| parameters  | [Dictionary] | The dictionary with additional parameters _(can be an empty dictionary, so only 1 byte)_.                         |
|             |              |                                                                                                                   |
| data        | `byte[]`     | The bytes of the binary.                                                                                          |

The dedicated `compression` parameter and `target_size` are used to make it easier to extract the binary, when it is compressed. It specifically allows to allocate the target buffer upfront, in the correct size, very helpful for `LZ4` extraction. Encoders should prefer a const-book reference for known compression algorithms (e.g. `GZIP`, `LZ4`). It is allowed to use compression algorithms that are not explicitly proposed, so `zstd`, `snappy`, ... The application has to provide the decoder with a plugin to inflate and deflate binaries for custom algorithms.

The size of the `data` matches the remaining bytes implied by `byte_size` minus the header size.

The dedicated [MIME type] parameter is used to identify the type of the binary, normally values from the [IANA media types] are used. If no official MIME type is available, an own one should be used. For example HERE will use `application/twkb` for TWKB binaries, and `application/jbon` for **JBON** binaries. If no MIME type is available, `application/octet-stream` should be expected, resulting in a simple `byte[]`.

#### Logical Bytes
The [logical bytes] of a binary are calculated by adding the **lead-in** `1111_1011`, then the byte-size as 32-bit BE integer, followed by the `mime_type`, followed by the [logical bytes] of all `parameters`, and finally by the inflated `data`. Therefore, in the [logical bytes] the `compression`, and `target_size` are ignored.

If the `mime_type` is `null` or `undefined`, then the [logical bytes] must encode the UTF16-string `application/octet-stream`.

If `parameters` is `null` or `undefined`, the [logical bytes] encode an empty dictionary _(**lead-in** `1100_0101`, 1 byte)_.

If a custom compression is used, then the encoder and decoder need a plugin to deflate or inflate the data. If that is not provided, an error should be raised by encoders and/or decoders.

#### JSON
In [JSON] and [XML] the binary is encoded as a string using the [data URL scheme]. The general syntax is:

`data:<mime-type>[;<parameter>]*[;base64]+,<data>`

For example `data:application/twkb;base64,{encoded-data}`. The `mime_type` is encoded as [media-type] of the [data URL]. The `parameters` are added as parameters to the [media-type]. For example, a binary using some parameters, with `mime_type` being `application/foo` can look like `data:application/foo;compression=GZIP;target_size=123456;charset=UTF-8;base64,eyJ4IjoxfQ==`. A timestamp is, e.g., encoded as `data:naksha/timestamp,12345678`.

When serializing to a [data URL], the encoder **MUST** output `compression` and `target_size` as URL parameters first, then merge in the `parameters` dictionary entries (sorted).

---

### UTF16 String (15)
The binary structure is used to store a UTF-16 string. The **lead-in** of the UTF16-string is `11ss_1111`; with `ss` encoding the size of the size, the binary format is like:

| Name      | Type     | Description                                                                    |
|-----------|----------|--------------------------------------------------------------------------------|
| lead_in   | `byte`   | The **lead-in** byte, `11ss_1111`.                                             |
| byte_size | `int32`  | The total size of the string, including the **lead-in**, in bytes.             |
| java_hash | `int32`  | The _Java_ hash of the string as raw 32-bit integer in big-endian byte-order.  |
|           |          |                                                                                |
| chars     | `char[]` | The UTF16 big-endian encoded code-points of the string.                        |

**Important**: The only valid UTF16 **lead-in** is `1111_1111` _(-1)_. The alternatives, `1100_1111`, `1101_1111`, and `1110_1111` **MUST** be treated as invalid encoding by encoders and decoders.

This means, the only way to encode an empty UTF16 string is using a full header, so with a 4-byte size, a `java_hash`, just with truncated `chars`. The empty string therefore is encoded in 9-byte _(**lead-in**, `byte_size`, and `java_hash`)_.

The UTF16-string should only be used within [logical bytes], it is a very inflated string representation. If an encoder nevertheless emits a UTF16 string outside of [logical bytes], its semantic value when encountered is equivalent to a regular **JBON** [string] containing the same code points; the `java_hash` field is mandatory and decoders **MUST** verify it, when encountered.

The `java_hash` is calculated over the UTF-16 characters via `s[0]*31^(n-1) + s[1]*31^(n-2) + ... + s[n-1]`, where `s` is the UTF-16 encoded string as `char[]`. Therefore, the hash is calculated over the UTF-16 code-points.

Beware, we chose UTF-16 encoding instead of UTF-8 to be compatible with the default _Java_ string representation. This allows us to ask the encoder to generate the [logical bytes] and we get back a _Java_ compatible string, even having the same hash. 

#### Logical Bytes
In the [logical bytes] the UTF16 string is added as is, its whole purpose is to be added into [logical bytes]. Therefore, the **JBON** bytes of the UTF16 string exactly match the [logical bytes].

#### JSON
In [JSON] the string is represented as normal [JSON] string; nothing special about it.

---

## Why not CBOR
This section explains why [CBOR] was not selected. The formats are similar in many points, when you read the two specifications. So, why do something new? There are two major differences between them.

### Size
**JBON** supports deduplication, especially for strings and objects, which decreases the size of the data. Compared to **CBOR**, which actually increases the size of data compared to [JSON] and just makes it binary readable. **JBON** not only allows to deduplicate strings out of the box, it as well allows to deduplicate map keys, sub-structures, and even whole _features_ via [books] in an efficient and easy way.

### Defaults via books
**JBON** supports default values using `global` [books]. **CBOR** does not, therefore you need additional knowledge not being integral part of the format. The following sub-sections walk through a realistic example, showing how the same data can be encoded at three different compression levels.

### Example: two similar features
Consider two [GeoJSON] _features_ sharing the same structure, as is common when storing road segments in a [Naksha data model] collection:

```json
[
  {"id":"f1","type":"Feature","properties":{"highway":"residential","maxSpeed":50,"oneway":false}},
  {"id":"f2","type":"Feature","properties":{"highway":"residential","maxSpeed":50,"oneway":false}}
]
```

A single feature is 96 bytes in efficient [JSON]:

```
{"id":"f1","type":"Feature","properties":{"highway":"residential","maxSpeed":50,"oneway":false}}
```

Two features as an array (`[` + 96 + `,` + 96 + `]`) sum to **195 bytes**.

### Low compression: no shared book
Without any [book], a [CBOR] encoder and a naive **JBON** encoder produce roughly the same output: every key and every string is written for every _feature_. A **JBON** encoding of one feature without any shared data needs:

```
object lead-in        (1 byte)   <-- root {…}
  byte_size           (1 byte)
  string "id"         (3 byte)   <-- 1 lead-in + 2 chars
  string "f1"         (3 byte)
  string "type"       (5 byte)   <-- 1 lead-in + 4 chars
  string "Feature"    (8 byte)   <-- 1 lead-in + 7 chars
  string "properties" (11 byte)  <-- 1 lead-in + 10 chars
  object lead-in      (1 byte)   <-- properties {…}
    byte_size         (1 byte)
    string "highway"      (8 byte)
    string "residential"  (12 byte)
    string "maxSpeed"     (9 byte)
    tiny-int 50           (2 byte)  <-- int8 lead-in + 1 byte value
    string "oneway"       (7 byte)
    boolean false         (1 byte)
= 1+1+3+3+5+8+11+1+1+8+12+9+2+7+1
= 73 byte
```

So 73 byte per feature, two features ≈ **146 byte**, plus an array wrapper (lead-in + size = 2 byte) ≈ **148 byte**. Compared to 195 byte [JSON], a saving of around **24%**, which is what you would also expect from [CBOR]. Not impressive — the binary is just more compact than text.

### Medium compression: shared `local` book
A **JBON** encoder can detect that both features share the same keys and values, and lift them into a `local` [book]. The book is embedded next to the features:

```
book lead-in          (1 byte)   <-- local book (db, fn, id all truncated via byte_size)
  byte_size           (1 byte)
  strings array lead-in (1 byte)
    byte_size           (1 byte)
    "id"                (3 byte)
    "type"              (5 byte)
    "Feature"           (8 byte)
    "properties"        (11 byte)
    "highway"           (8 byte)
    "residential"       (12 byte)
    "maxSpeed"          (9 byte)
    "oneway"            (7 byte)
= 1+1+1+1+3+5+8+11+8+12+9+7
= 67 byte
```

Each feature can now reference the shared strings instead of embedding them. Using tiny [references] _(`mref4`, 1 byte each)_ where possible and `ref8` _(2 byte)_ otherwise:

```
object lead-in        (1 byte)   <-- root {…}
  byte_size           (1 byte)
  ref "id"            (1 byte)   <-- mref4 to index 0
  string "f1"         (3 byte)   <-- per-feature, not shared
  ref "type"          (1 byte)   <-- mref4
  ref "Feature"       (1 byte)   <-- mref4
  ref "properties"    (1 byte)   <-- mref4
  object lead-in      (1 byte)   <-- properties {…}
    byte_size         (1 byte)
    ref "highway"     (1 byte)   <-- mref4
    ref "residential" (1 byte)   <-- mref4
    ref "maxSpeed"    (1 byte)   <-- mref4
    tiny-int 50       (2 byte)
    ref "oneway"      (1 byte)   <-- mref4
    boolean false     (1 byte)
= 1+1+1+3+1+1+1+1+1+1+1+1+2+1+1
= 18 byte
```

So per feature 18 byte. Two features (36 byte) plus the array wrapper (2 byte) plus the `local` [book] (67 byte) ≈ **105 byte**. That is **46% smaller** than [JSON] (195 byte), and crucially the saving grows with the number of features: at 100 features the book amortises over 100 × 18 + 67 ≈ 1867 byte, vs. ~9700 byte of [JSON] — an **81% reduction**.

### Large compression: shared `global` book with template
If a `global` [book] is provided that defines a template _feature_ — i.e. an [Object] with `type:"Feature"`, `properties.highway:"residential"`, `properties.maxSpeed:50`, `properties.oneway:false` as default values — then a feature that matches the template only needs to encode its own `id`:

```
object lead-in        (1 byte)   <-- root {…}
  byte_size           (1 byte)
  ref to template     (2 byte)   <-- ref8 into global book
  ref "id"            (1 byte)   <-- mref4
  string "f1"         (3 byte)
= 1+1+2+1+3
= 8 byte
```

So per feature 8 byte. Two features (16 byte) plus array wrapper (2 byte) ≈ **18 byte**, while the `global` [book] is **not** part of the file (it is shared across features, possibly across whole collections). This is a **91% reduction** vs. [JSON] (195 byte) — and the ratio improves further as more features share the same template.

At 1,000,000 such features, [JSON] would be roughly 96 MB, while **JBON** with a shared `global` [book] would be roughly **8 MB**, regardless of where the [book] is stored. The `global` [book] itself is small _(a few hundred bytes for this example)_ and is loaded once into the decoder.

### Comparing the three levels

| Encoding                   | Per feature | 2 features total | 1,000,000 features | Reduction (2 features) | Reduction (1M features) |
|----------------------------|------------:|-----------------:|-------------------:|-----------------------:|------------------------:|
| [JSON]                     |     96 byte |         195 byte |             ~96 MB |                     0% |                      0% |
| **JBON** _(no book)_       |     73 byte |         148 byte |             ~73 MB |                    24% |                     24% |
| **JBON** _(`local` book)_  |     18 byte |         105 byte |             ~18 MB |                    46% |                     81% |
| **JBON** _(`global` book)_ |      8 byte |          18 byte |              ~8 MB |                    91% |                     92% |

The decisive observation is that none of these levels require a different decoder or a different format version; they all use the same **JBON** binary format. The encoder picks the level it wants, the decoder is oblivious.

### Default values
In [CBOR] we would have to encode exactly the same data as in [JSON] — the binary would not be reduced in size, just become binary-readable. With **JBON** we can reach compression rates above 90% while remaining fully binary readable, no parsing required.

Within [HERE] there is a proposal, specifically for the Map Object Model, to add default values for properties to save space when serializing to [JSON]. This can achieve similar compression rates, but has a major disadvantage: it binds the data to the MOM specification and requires the decoder to know that specification. In other words, when we encounter such a compressed [JSON], we have potentially no idea for which MOM version it was generated. Even if we have, we would now need a decoder that supports exactly that version. If our code has long moved on, old data therefore becomes unreadable. More critical, when we want to store such data in a database and index parts of it, the database — or a service in front of it — needs a MOM decoder of exactly the version with which the data was encoded, to be able to read the default values for indexing.

With the **JBON** solution, only the `global` [book] is needed, which can be kept next to the data, as it is itself just data. It will work with all decoders following this specification, no matter how old they are. This also works with all data, not only MOM. It allows to re-encode old data with new optimizations, and it allows to auto-generate _(derive)_ the [book] from the data, so analyzing the data, then generate the optimal templates with optimal default values. We can even have multiple versions of the same template with different default values!

The best is, that this allows storages to index the data, as long as they have the `global` [book], because the reader can return the full object. When we use the reader, the object will appear as if it is part of the **JBON**, it is transparent to the user whether the data comes from a `global` [book], or it is really encoded in the binary. The application does not need to know details, it only needs access to the `global` [book]. Compression optimization is purely done on the encoder side and can be improved for all our use-cases, without having to invalidate old data or re-encode it, nor does it require special decoding knowledge or a special decoder. No matter how efficient the encoder is made, the decoder always stays the same!

Clearly, we could somehow add dictionaries and text encoding to [CBOR] using [tags](https://www.rfc-editor.org/rfc/rfc8949.html#name-tagging-of-items), but it would be a proprietary extension and therefore anyway force us to do our own implementations. It would ultimately make [CBOR] so incompatible with what the rest of the world does, that there seems to be no advantage in this solution, when compared to creating our own binary encoding.

## Const
The `const` [book] is a special [book] which is not encoded in binary form. It is a hardcoded array of strings, being part of the specification. It contains only strings that are commonly used, so that they do not have to be encoded into the binary or other [books], but can be just referenced by their index in the `const` book. This saves space and makes encoding more efficient.

New constants can only be added in new **JBON** versions, to avoid breaking existing decoders.

All numbers in the `Number` column are **decimal** indices into the `const` book. Entries are grouped by ranges; gaps are _reserved_ for future additions; decoders encountering _reserved_ references should report an error.

| Number | Const                      | Value                      | Description                                                                                                                 |
|--------|----------------------------|----------------------------|-----------------------------------------------------------------------------------------------------------------------------|
| `0`    | `US_ASCII`                 | `US-ASCII`                 |                                                                                                                             |
| `1`    | `ISO_8859_1`               | `ISO-8859-1`               | Legacy Western European.                                                                                                    |
| `2`    | `ISO_8859_2`               | `ISO-8859-2`               | Legacy Central/Eastern European.                                                                                            |
| `5`    | `ISO_8859_5`               | `ISO-8859-5`               | Legacy Cyrillic.                                                                                                            |
| `6`    | `SHIFT_JIS`                | `Shift_JIS`                | Legacy Japanese.                                                                                                            |
| `7`    | `EUC_JP`                   | `EUC-JP`                   | Legacy Japanese.                                                                                                            |
| `8`    | `GBK`                      | `GBK`                      | Legacy Common Chinese.                                                                                                      |
| `9`    | `BIG5`                     | `Big5`                     | Legacy Traditional Chinese.                                                                                                 |
| `10`   | `KOI8_R`                   | `KOI8-R`                   | Legacy Russian.                                                                                                             |
| `15`   | `ISO_8859_15`              | `ISO-8859-15`              | Legacy Western European, same as `ISO-8859-1`, but includes `€`.                                                            |
| `16`   | `WINDOWS_1251`             | `Windows-1251`             | Cyrillic on Windows.                                                                                                        |
| `17`   | `WINDOWS_1252`             | `Windows-1252`             | Very common legacy Western encoding on Windows.                                                                             |
| `20`   | `UTF_8`                    | `UTF-8`                    | UTF-8 encoding.                                                                                                             |
| `21`   | `UTF_16`                   | `UTF-16`                   | UTF-16 in platform encoding.                                                                                                |
| `22`   | `UTF_16BE`                 | `UTF-16BE`                 | UTF-16 in big-endian byte-order _(network byte order)_.                                                                     |
| `23`   | `UTF_16LE`                 | `UTF-16LE`                 | UTF-16 in little-endian byte-order.                                                                                         |
| `24`   | `UTF_32`                   | `UTF-32`                   | UTF-32 in platform encoding.                                                                                                |
| `25`   | `UTF_32BE`                 | `UTF-32BE`                 | UTF-32 in big-endian byte-order _(network byte order)_.                                                                     |
| `26`   | `UTF_32LE`                 | `UTF-32LE`                 | UTF-32 in little-endian byte-order.                                                                                         |
| `29`   | `CHARSET`                  | `charset`                  | The character-set being used in a [Binary] _(or other places)_.                                                             |
|        |                            |                            |                                                                                                                             |
| `30`   | `NAKSHA`                   | `Naksha`                   |                                                                                                                             |
| `31`   | `GEO_JSON`                 | `GeoJSON`                  |                                                                                                                             |
| `32`   | `JSON`                     | `JSON`                     |                                                                                                                             |
| `33`   | `PROPERTIES`               | `properties`               |                                                                                                                             |
| `34`   | `GEOMETRY`                 | `geometry`                 |                                                                                                                             |
| `35`   | `REFERENCE_POINT`          | `referencePoint`           |                                                                                                                             |
| `36`   | `NS_COM_HERE_XYZ`          | `@ns:com:here:xyz`         | The XYZ namespace key.                                                                                                      |
|        |                            |                            |                                                                                                                             |
| `40`   | `APPLICATION_OCTET_STREAM` | `application/octet-stream` | The MIME-type for arbitrary binaries _(`byte[]` / `Int8Array`)_.                                                            |
| `41`   | `APPLICATION_JBON`         | `application/jbon`         | The custom MIME-type for **JBON** binaries _(`byte[]` / `Int8Array`)_.                                                      |
| `42`   | `APPLICATION_TWKB`         | `application/twkb`         | The custom MIME-type for [Tiny WKB] binaries _(`byte[]` / `Int8Array`)_.                                                    |
| `43`   | `NAKSHA_TIMESTAMP`         | `naksha/timestamp`         | The custom MIME-type for a EPOCH timestamp in milliseconds _(`Timestamp` / `Date`)_.                                        |
| `44`   | `NAKSHA_BOOLEAN`           | `naksha/bool`              | The custom MIME-type for a boolean value in JSON compatible encoding _(`boolean` / `Boolean`)_.                             |
|        |                            |                            |                                                                                                                             |
| `50`   | `APPLICATION_TEXT`         | `application/text`         | The MIME-type for arbitrary text _(`String` / `String`)_.                                                                   |
| `51`   | `APPLICATION_JSON`         | `application/json`         | The custom MIME-type for **JSON** strings _(`String` / `String`)_.                                                          |
| `52`   | `APPLICATION_JSON64`       | `application/json64`       | The custom MIME-type for **JSON** with support for 64-bit integer _(`String` / `String`)_.                                  |
| `53`   | `APPLICATION_JSONC`        | `application/jsonc`        | The custom MIME-type for **JSON** with comments _(`String` / `String`)_.                                                    |
| `54`   | `APPLICATION_JSONC64`      | `application/jsonc64`      | The custom MIME-type for **JSON** with comments, and 64-bit integers _(`String` / `String`)_.                               |
| `55`   | `APPLICATION_JSONX`        | `application/jsonx`        | The custom MIME-type for **JSON** with comments, 64-bit integers, sets, maps, and primitive-arrays _(`String` / `String`)_. |
|        |                            |                            |                                                                                                                             |
| `60`   | `NAKSHA_INT8`              | `naksha/int8`              | The custom MIME-type for a 8-bit integer in JSON compatible encoding _(`byte` / `Number`)_.                                 |
| `61`   | `NAKSHA_INT16`             | `naksha/int16`             | The custom MIME-type for a 16-bit integer in JSON compatible encoding _(`short` / `Number`)_.                               |
| `62`   | `NAKSHA_INT32`             | `naksha/int32`             | The custom MIME-type for a 32-bit integer in JSON compatible encoding _(`int` / `Number`)_.                                 |
| `63`   | `NAKSHA_INT64`             | `naksha/int64`             | The custom MIME-type for a 64-bit integer in JSON compatible encoding _(`long` / `BigInt`)_.                                |
| `64`   | `NAKSHA_INT128`            | `naksha/int128`            | The custom MIME-type for a 128-bit integer.                                                                                 |
|        |                            |                            |                                                                                                                             |
| `70`   | `NAKSHA_INT8A`             | `naksha/int8a`             | The custom MIME-type for a 8-bit integer-array _(`byte[]` / `Int8Array`)_.                                                  |
| `71`   | `NAKSHA_INT16A`            | `naksha/int16a`            | The custom MIME-type for a 16-bit integer-array _(`short[]` / `Int16Array`)_.                                               |
| `72`   | `NAKSHA_INT32A`            | `naksha/int32a`            | The custom MIME-type for a 32-bit integer-array _(`int[]` / `Int32Array`)_.                                                 |
| `73`   | `NAKSHA_INT64A`            | `naksha/int64a`            | The custom MIME-type for a 64-bit integer-array _(`long[]` / `BigInt64Array`)_.                                             |
| `74`   | `NAKSHA_INT128A`           | `naksha/int128a`           | The custom MIME-type for a 128-bit integer-array.                                                                           |
|        |                            |                            |                                                                                                                             |
| `80`   | `NAKSHA_FLOAT8`            | `naksha/float8`            | The custom MIME-type for a 8-bit floating-point number in JSON compatible encoding _(`double` / `Number`)_.                 |
| `81`   | `NAKSHA_FLOAT16`           | `naksha/float16`           | The custom MIME-type for a 16-bit floating-point number in JSON compatible encoding _(`double` / `Number`)_.                |
| `82`   | `NAKSHA_FLOAT32`           | `naksha/float32`           | The custom MIME-type for a 32-bit floating-point number in JSON compatible encoding _(`double` / `Number`)_.                |
| `83`   | `NAKSHA_FLOAT64`           | `naksha/float64`           | The custom MIME-type for a 64-bit floating-point number in JSON compatible encoding _(`double` / `Number`)_.                |
| `84`   | `NAKSHA_FLOAT128`          | `naksha/float128`          | The custom MIME-type for a 128-bit floating-point number.                                                                   |
|        |                            |                            |                                                                                                                             |
| `90`   | `APPLICATION_FLOAT8A`      | `application/float8a`      | The custom MIME-type for a 8-bit floating-point-array.                                                                      |
| `91`   | `APPLICATION_FLOAT16A`     | `application/float16a`     | The custom MIME-type for a 16-bit floating-point-array _(N/A / `Float16Array`)_.                                            |
| `92`   | `APPLICATION_FLOAT32A`     | `application/float32a`     | The custom MIME-type for a 32-bit floating-point-array _(`float[]` / `Float32Array`)_.                                      |
| `93`   | `APPLICATION_FLOAT64A`     | `application/float64a`     | The custom MIME-type for a 64-bit floating-point-array _(`double[]` / `Float64Array`)_.                                     |
| `94`   | `APPLICATION_FLOAT128A`    | `application/float128a`    | The custom MIME-type for a 128-bit floating-point-array.                                                                    |
|        |                            |                            |                                                                                                                             |
| `100`  | `CONTENT_ENCODING`         | `content-encoding`         | The encoding or compression algorithm being used in a [Binary] _(or other places)_.                                         |
| `101`  | `GZIP`                     | `GZIP`                     | The binary is [GZIP] compressed.                                                                                            |
| `102`  | `LZ4`                      | `LZ4`                      | The binary is [LZ4] compressed.                                                                                             |

Indices `103` through `255` are _reserved_ for future additions; decoders encountering them MUST report an error. The maximum index addressable via `ref32` is `2^32-1`, but the `const` [book] is not expected to grow significantly; reserving 8-bit indices for the foreseeable future keeps `ref8`/`ref16` references small.

## Java
This section documents the Java API for **JBON**.

```java
package naksha.data;

public enum JbonUnitType {
  EOF,
  NULL,
  UNDEFINED,
  BOOLEAN,
  INTEGER,
  FLOAT,
  STRING,
  ARRAY,
  OBJECT,
  BOOK,
  TUPLE_NUMBER,
  TUPLE,
  SET,
  MAP,
  DICTIONARY,
  TAGS,
  TWKB,
  BINARY
}
public enum JbonBookType {
  LOCAL, // 0
  MEMBERS, // 1
  GLOBAL, // 2
  CONST // 3
}

// The thread-safe JBON wrapper, supports allocation free decoding.
public class Jbon {
  public Jbon(
    byte @NotNull [] bytes,
    int offset,
    byte @Nullable [] global_bytes,
    int global_offset,
    byte @Nullable [] members_bytes,
    int members_offset
  ) {
    this.bytes=bytes;
    this.offset=offset;
    this.members_bytes=members_bytes;
    this.members_offset=members_offset;
    this.global_bytes=global_bytes;
    this.global_offset=global_offset;
    this.address = 0xc000_0000 | offset;
  }

  private static final int LOCAL = 0;
  private static final int MEMBERS = 1;
  private static final int GLOBAL = 2;
  // Shared with const book, but const is a virtual book that only stores primitives.
  // Because of this
  private static final int MAIN = 3;
  // books
  private byte[][] books;
  public final byte[] members_bytes;
  public final int members_offset;
  // book #2
  public final byte[] global_bytes;
  public final int global_offset;
  // book #3 - const, hardcoded
  // The main JBON we decode.
  public final byte[] bytes;
  public final int offset;
  // book #0, must be embedded into the bytes, so we just need to the offset.
  public final int local_offset;

  // The offset of the unit
  private int book;
  private int unitStart;
  private int cursor;
  public int address() { return address; }
  public int address(int book, int offset) {
    int old = this.address;
    this.address = ((book & 3) << 30) | (offset & 0x3fff_ffff);
    return old;
  }
  public int book() { return address >>> 30; }
  public int offset() { return address & 0x3fff_ffff; }
  // The type of the current unit, reads offset.
  public JbonUnitType type() { /* ... */ }
  public boolean isReference() { /* ... */ }
  public boolean isPrimitive() { /* ... */ }
  public boolean isBoolean() { /* ... */ }
  public boolean isInteger() { /* ... */ }
  public boolean isFloat() { /* ... */ }
  public boolean isString() { /* ... */ }
  public boolean isStructure() { /* ... */ }
  public boolean canEnter() { /* ... */ }
  // Tests if the end of a structure has been reached.
  public boolean endOfStructure() { /* ... */ }
  public @Nullable Boolean decodeBoolean() { /* ... */ }
  public long decodeInteger() { /* ... */ }
  public double decodeFloat() { /* ... */ }
  public @Nullable Literal decodeString() { /* ... */ }
  // Skip over the current unit, returns true if there is yet another unit.
  public boolean skip() { /* ... */ }
  // Enters a structure or follows a reference. Returns the current address, before entering.
  public int enter() { /* ... */ }
  // Leaves a structure, jumps back to a previous address.
  public void leave(int return_address) { /* ... */ }
}

// A thread-local JBON decoder that can support IArray, IObject, ISet, IMap, ITuple and ITupleNumber.
public class JbonDecoder {
  public JbonDecoder(@NotNull Jbon jbon) { this.jbon = jbon; }
  
  // The immutable JBON 
  public final @NotNull Jbon jbon;
  @Nullable JbonBook localBook;
  @Nullable JbonBook storageBook;
  @Nullable JbonBook membersBook;

  // A shared unit to decode primitives and strings.
  final @NotNull JbonUnit unit = new JbonUnit(this);

  public @Nullable JbonBook getGlobalBook() {}
  public @Nullable JbonBook setGlobalBook(@Nullable JbonBook globalBook) {}
  public @NotNull Jbon withGlobalBook(@Nullable JbonBook globalBook) {
    this.globalBook = globalBook;
    return this;
  }

  // As long as there is any user for a JBON, we keep the reference to all parsed units.
  // The moment the application does not need access to the parsed values, the GC will throw them away.
  @Nullable WeakReference<JbonUnit> root;
  public @NotNull JbonUnit root() {
    // TODO: Return the existing root or create the root unit, invoke decode(), add into "root"; return the new "root" unit.
  }
}

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
  int size(); // The byte-length.
  byte get(int i);
  byte[] toBytes();
  byte[] toBytes(int from, int to);
}

// The basic node, which is called unit within JBON.
public final class JbonUnit {
  // Create the shared unit.
  JbonUnit(@NotNull Jbon jbon) {
    this.jbon = jbon;
    this.parent = null;
  }
  // Create the root unit at the given offset.
  JbonUnit(@NotNull Jbon jbon, int offset) {
    this.jbon = jbon;
    this.parent = null;
    decode(offset);
  }
  // Create a child-unit at the given offset.
  JbonUnit(@NotNull JbonUnit parent, int offset) {
    this.jbon = parent.jbon;
    this.parent = parent;
    decode(offset);
  }
  // Create a child-unit from the decoded shared unit.
  JbonUnit(@NotNull JbonUnit parent, @NotNull JbonUnit unit) {
    this.jbon = parent.jbon;
    this.parent = parent;
    // TODO: Copy all values from unit into this (copy references).
    this.offset = unit.offset;
    // ...
  }
  
  // Decode the unit
  // Normally always invoked directly after creation, but 
  @NotNull JbonUnit decode(int offset) {
    this.offset = offset;
    // TODO: Decode the unit from the given offset!
  }

  final @NotNull Jbon jbon; // The JBON to which the unit belongs.
  final @Nullable JbonUnit parent; // If this is not the root, the reference to the parent. 
  @Nullable JbonUnit next; // Next sibling, if there is any, controlled by parent!
  @Nullable JbonUnit firstChild; // The first child-unit, controlled by this unit including all siblings of the child _(next)_.
  
  // Information always available.
  @NotNull DataType type; // The data type of the unit, represents as well true and false.
  int offset = -1; // The index in the JBON where the unit is located.
  byte lead_in; // The lead-in byte read.
  int size; // The total size of the unit in byte.
  int length; // The length of the unit; -1 if not known or available.
  int header_offset; // The index of the first header field; -1 if the unit does not have any header units.
  // TODO: Add all possible header fields.
  int content_offset; // The index in the JBON where the content starts; -1 if the unit does not have any content.
  
  // Decoded values.
  long int_value;
  double float_value;
  @Nullable Literal string_value;
  @Nullable JbonStruct struct_value;
  
  private int offset() {
    if (offset < 0) throw new DataError("The JBON unit was not yet decoded");
    return offset;
  }
  public boolean isStruct() { return struct_value != null; }
  public @Nullable JbonStruct struct() { return struct_value; }
}

public interface IJbonStruct { 
  // Returns the unit to which this structure belongs; the unit refers back to this via `struct_value`.
  @NotNull JbonUnit unit();
}

public final class JbonBinary implements IJbonStruct {
  public JbonBinary(@NotNull JbonUnit unit) { this.unit = unit; }
  private final @NotNull JbonUnit unit;
  @Override public @NotNull JbonUnit unit() { return unit; }
}

public abstract class JbonStruct extends Proxyable implements IJbonStruct, IStruct, IProxyable {
  public JbonStruct(@NotNull JbonUnit unit) { this.unit = unit; }
  private final @NotNull JbonUnit unit;
  @Override public @NotNull JbonUnit unit() { return unit; }
  // TODO: Add implementation of `copy()`
  //       Implement `mut()`, throwing an exception when copy argument is false, otherwise just returning `this.copy(true)`.
}

public final class JbonArray extends JbonStruct implements IArray {
  public JbonArray(@NotNull JbonUnit unit) { super(unit); }
}

public final class JbonSet extends JbonStruct implements ISet {
  public JbonStruct(@NotNull JbonUnit unit) { super(unit); }
}

public final class JbonMap extends JbonStruct implements IMap {
  public JbonMap(@NotNull JbonUnit unit) { super(unit); }
}

public final class JbonObject extends JbonStruct implements IObject {
  public JbonMap(@NotNull JbonUnit unit) { super(unit); }
}

public final class JbonTupleNumber implements IJbonStruct, ITuple {
  public JbonTupleNumber(@NotNull JbonUnit unit) { this.unit = unit; }
  private final @NotNull JbonUnit unit;
  @Override public @NotNull JbonUnit unit() { return unit; }
}

public final class JbonTuple implements IJbonStruct, ITuple {
  public JbonTuple(@NotNull JbonUnit unit) { this.unit = unit; }
  private final @NotNull JbonUnit unit;
  @Override public @NotNull JbonUnit unit() { return unit; }

  /** Convert this tuple into a GeoJSON feature, using the storage column mapping as specified in the given collection configuration. */
  public @NotNull JsonFeature toGeoJson(@Nullable Map<String, String> columnMap);
  // TODO: Add methods to read the JbonTupleNumber and other encoded values.
}

public final class JbonBook implements IJbonStruct {
  public JbonBook(@NotNull JbonUnit unit) { this.unit = unit; }
  private final @NotNull JbonUnit unit;
  @Override public @NotNull JbonUnit unit() { return unit; }
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
    final int length = end - start;
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
    this.totalLength += length;
    switch (offset) {
      case 7: this.d6 = d6; // fall-through
      case 6: this.d5 = d5; // fall-through
      case 5: this.d4 = d4; // fall-through
      case 4: this.d3 = d3; // fall-through
      case 3: this.d2 = d2; // fall-through
      case 2: this.d1 = d1; // fall-through
      case 1: this.d0 = d0; // fall-through
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
The following changes are introduced in version 2 of this specification, compared to version 1:

- Added header to detect **JBON2**.
- Added `UInt56` and `UInt24` encodings.
- Added a specification for how to calculate _logical bytes_ of any _**unit**_, in a way that the same _**unit**_ generates the same _logical bytes_.
  - This is needed to find similar _**units**_ in the storage via hash.
  - It is important to logically compare _**units**_.
  - It as well allows to compare _**units**_ without identifiers to those being already in the storage. It only requires a secondary compare hash, that limits the hash to significant members, which is simply done by removing the values that should not be part of the hash, then calculate the _logical bytes_, hash them and compare the hash and _logical bytes_.
  - Therefore, the _logical bytes_ is a real unique identifier of _**units**_.
- The dictionaries have been renamed into `books`.
  - This is as well strongly linked to the _logical bytes_, without them, it would be rather as useless as the dictionary, which effectively only stored strings.
  - So, books now really store deep structures and allow deduplication of whole objects in a standardized simplified way!
- Improved the way some values are encoded
  - So, add `uint56` and `uint24`, which are useful as they allocate a fixed space, which again simplifies encoders.
- Added the `Tuple` structure, which is a special wrapper for an `Object`, which is used to exchange data between services and storages.
  - This replaces the really weird previous definition of features.
- Added the `TupleNumber` structure to address single tuples or arrays of tuples efficiently with shared header components.
- Added the `Binary` structure for arbitrary byte arrays.
- Added the `TWKB` structure for compact geometry encoding.
- Added the `const` book with a curated set of well-known values _(charsets, MIME types, etc.)_ to avoid encoding them repeatedly.
- Added the `@type` escape rule for objects that have a member literally named `@type`.
- Added documentation about the mostly implicit value `undefined`, made it explicit, and improved documentation about `undefined` and `null`.
- Added [NFC] Unicode normalization requirement for [logical bytes] of strings.
- Added the `int4`, `float4`, `mref4`, and `gref4` _tiny_ encodings packed into the lead-in byte.
- Added a forward-compatibility policy for the JBON file header.

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
[Integers]: #integers
[Integer]: #integers
[int]: #integers
[Floats]: #floats
[Float]: #floats
[float]: #floats
[floats]: #floats
[Primitive-Stringification]: #primitive-stringification
[primitive-stringification]: #primitive-stringification
[primitive stringification]: #primitive-stringification
[Indexable]: #indexable
[indexable]: #indexable
[Timestamp]: #timestamp
[timestamp]: #timestamp
[timestamps]: #timestamp
[UInt56]: #uint56
[uint56]: #uint56
[UInt24]: #uint24
[uint24]: #uint24
[TupleNumber]: #tuplenumber
[Tuple-Number]: #tuplenumber
[tuple number]: #tuplenumber
[tuple-number]: #tuplenumber
[Reference]: #reference
[reference]: #reference
[References]: #reference
[references]: #reference
[ref]: #reference
[String]: #string
[string]: #string
[strings]: #string
[string-references]: #string-references
[string-reference]: #string-references
[Structure]: #structures
[structure]: #structures
[Structures]: #structures
[structures]: #structures
[Binary]: #binary-10
[binary]: #binary-10
[binaries]: #binary-10
[Array]: #array-0
[array]: #array-0
[arrays]: #array-0
[Map]: #map-1
[map]: #map-1
[maps]: #map-1
[Set]: #set-2
[set]: #set-2
[sets]: #set-2
[Object]: #object-3
[object]: #object-3
[objects]: #object-3
[Tags]: #tags-4
[tags]: #tags-4
[Dictionary]: #dictionary-5
[dictionary]: #dictionary-5
[dict]: #dictionary-5
[Book]: #book-6
[book]: #book-6
[books]: #book-6
[TupleNumberArray]: #tuplenumberarray-7
[tuple number array]: #tuplenumberarray-7
[tuple-number array]: #tuplenumberarray-7
[tuple-number-array]: #tuplenumberarray-7
[Tuple]: #tuple-8
[tuple]: #tuple-8
[tuples]: #tuple-8
[TWKB]: #twkb-9
[twkb]: #twkb-9
[UTF16-String]: #utf16-string-15
[UTF16 String]: #utf16-string-15
[UTF-16 String]: #utf16-string-15
[UTF16 string]: #utf16-string-15
[UTF-16 string]: #utf16-string-15
[UTF16 strings]: #utf16-string-15
[UTF-16 strings]: #utf16-string-15
[utf16 string]: #utf16-string-15
[utf-16 string]: #utf16-string-15
[utf16 strings]: #utf16-string-15
[utf-16 strings]: #utf16-string-15
[Const]: #const
[const]: #const
[Constants]: #const
[constants]: #const
[CBOR]: https://www.rfc-editor.org/rfc/rfc8949
[JSON]: https://www.rfc-editor.org/rfc/rfc8259
[XML]: https://www.w3.org/TR/xml/
[GeoJSON]: https://datatracker.ietf.org/doc/html/rfc7946
[Tiny WKB]: https://github.com/TWKB/Specification/blob/master/twkb.md
[UNICODE]: https://home.unicode.org/
[Naksha data model]: LIB_DATA.md
[Naksha data model Tuple-Number]: LIB_DATA.md#tuple-number
[IEEE-754]: https://en.wikipedia.org/wiki/IEEE_754
[binary8]: https://en.wikipedia.org/wiki/Minifloat
[binary16]: https://en.wikipedia.org/wiki/Half-precision_floating-point_format
[binary32]: https://en.wikipedia.org/wiki/Single-precision_floating-point_format
[binary64]: https://en.wikipedia.org/wiki/Double-precision_floating-point_format
[binary128]: https://en.wikipedia.org/wiki/Quadruple-precision_floating-point_format
[ICU4J]: https://mvnrepository.com/artifact/com.ibm.icu/icu4j
[IANA media types]: https://www.iana.org/assignments/media-types/media-types.xhtml
[data URL scheme]: https://www.rfc-editor.org/rfc/rfc2397
[data URL]: https://www.rfc-editor.org/rfc/rfc2397
[data URLs]: https://www.rfc-editor.org/rfc/rfc2397
[data-url]: https://www.rfc-editor.org/rfc/rfc2397
[media-type]: https://www.rfc-editor.org/rfc/rfc2046
[URN]: https://www.rfc-editor.org/rfc/rfc8141
[GZIP]: https://www.rfc-editor.org/rfc/rfc1952
[LZ4]: https://lz4.org/
[MIME-Type]: https://developer.mozilla.org/en-US/docs/Web/HTTP/Guides/MIME_types
[MIME Type]: https://developer.mozilla.org/en-US/docs/Web/HTTP/Guides/MIME_types
[MIME type]: https://developer.mozilla.org/en-US/docs/Web/HTTP/Guides/MIME_types
[MIME-type]: https://developer.mozilla.org/en-US/docs/Web/HTTP/Guides/MIME_types
[mime type]: https://developer.mozilla.org/en-US/docs/Web/HTTP/Guides/MIME_types
[mime-type]: https://developer.mozilla.org/en-US/docs/Web/HTTP/Guides/MIME_types
[HERE]: https://www.here.com/
[MurMur3]: https://en.wikipedia.org/wiki/MurmurHash
[murmur3]: https://en.wikipedia.org/wiki/MurmurHash
[JBON1]: ./JBON1.md
[JBON2]: ./JBON2.md
[ASCII]: https://www.ascii-code.com/de
[XDG Base Directory Specification]: https://specifications.freedesktop.org/basedir/latest/
[NFC]: https://unicode.org/reports/tr15/
