# JBON

## Introduction
JBON is a shortcut for Java Binary Object Notation. In this binary format all values are stored as objects in a tree like structure that can be navigated quickly, but is at the same time very small.

As the format name indicates, this format is object-oriented. All JBON elements are encoded using **units**. All **units** always starts with a type-byte. It identifies the type of the unit and therefore allows to identify the binary size and other attributes. Each unit can contain subunits, for example, a string contains unicode code-points, a map contains map-entries aso..

The top most two bit of the type-byte selects the type-encoding itself:

- `1ttt_vvvv`: One of the eight primary parameter types with a 4-bit value parameter.
- `01tt_vvvv`: One of the four extended parameter types with a 4-bit value parameter.
- `001?_????`: Reserved (32 types).
- `0001_tttt`: One of the 32 standard types without value-parameter. The first 16 are scalars, the last 16 are complex objects.

Therefore, the type-byte allows 12 **parameter-types** with a 4-bit parameter, 32 **standard-types**, and reserves up to 32 types for future extension.

JBON values are always copy-on-write, that means, every modification requires to copy the object. Therefore, all JBONs are immutable. Reading in a JBON requires a cursor that can be used to move through JBON tree.

## Index vs Offset
When this document mentions an **index**, it refers to the position in a dictionary. When this document refers to an **offset**, then it refers to the byte-offset in a JBON binary (basically a relative pointer in the JBON).

## Size vs Length
In this document the term `size` refers to an amount of bytes, so a byte-size, while the term `length` refers to a number of units. For example the map-length is the amount of map-entries while the map-size is the amount of byte it requires.

The size is normally used to skip over units, while the length is used logically. For this purpose, the size does not store the size of itself nor the size of the previously located lead-in byte, therefore to skip over the unit the size plus lead-in, plus the size of the size field itself has to be used.

In a nutshell, the size stores the amount of bytes needed to skip a unit, after reading its size and when the offset is located behind the size field.

## Dictionaries (JbDict)
To compress the data, JBON uses dictionaries. A dictionary is simply a list of units. The first 16 entries in the dictionary can be referred using tiny-references (1 byte encoding), the rest of the entries require full references (2 to 5 byte encoding). Therefore, it is recommended to use the first 16 units for those values that are most often used and maybe are only small, to compress often used, but small objects.

When a dictionary is mapped, it will lazy load entries into an internal cache, as long as only accessed using index. The dictionary can be queried as well by FNV1b hash, in that case it will load all values into the internal cache. Note that the hash causes collisions, therefore a search can find possible multiple entries and in all cases a binary compare must be performed.

## Maps (JbMap)
In JBON the keys of a map must be strings, note that text is not allowed for keys. All key-value pairs are sorted by the binary representation of the key. The reason for this is to guarantee, that two equal maps generate the same hash-code, when their binary representation is hashed.

## Features (JbDocument)
A JBON feature is basically any unit, but with a local dictionary and an optional identifier of a global dictionary needed to decode it.

## Strings (JbString, JbText)
JBON strings (and texts) are not encoded using UTF-8, but a special encoding that is smaller, and allows dictionary lookups. The leading bytes of every byte in a JBON string/text signal the following:

- `0vvv_vvvv`: The value encodes the code point value. Allows values between 0 and 127 (ASCII).
- `10_vvvvvv`: The value should be AND masks `0011_1111`, then shift-left by 8, the value of the next byte should be added using OR, and finally 128 should be added. Allows values between 128 and 16511 (2^14+128-1).
- `110_vvvvv`: The value should be AND masks `0001_1111`, then shift-left by 16, and eventually the value of the next two byte (read big-endian) should be added using OR. Allows values between 0 and 2097151 (2^21-1).
- `111_ssgvv`: The value is a **string-reference**. The three lower bits (`gvv`) have the same meaning as for a **reference**, but must not refer to anything but a string, not even to a text. The `ss`-bits (bit number 4 and 5) signal if a special character should be added behind the referred string. The following values are defined:
  - `00`: Do not encode any additional character.
  - `01`: Add a space behind the string.
  - `10`: Add an underscore (`_`) behind the string.
  - `11`: Add a colon (`:`) behind the string.

**Note**: The `ss`-bits improve the compression greatly, because the encoder will split strings by default at a space or underscore. Exactly where these splits happen, we do not need to encode the separator characters. The reason to cut at these two characters is that most often street-names or other human text uses the space as separator, while for constants in programming most often the underscore is used as separator (TYPE_A, TYPE_B, ...). Additionally, we have room for one more split characters to be defined by experience in the future.

## Primary Parameter-Types (3-bit)

### (0) uint4
The lower 4-bit hold the positive (unsigned) value between `0..15`.

### (1) sint4
The lower 4-bit hold the negative (signed) value between `-1..-16`.

### (2) float4
The lower 4-bit hold the biased (signed) value. The value is stored minus 8, so `0..15` represents `-8..7`.

### (3) reference
The full reference encoding. All indices being bigger than 15 are encoded as full references with a bias of 16, so that a value of 0 means index 16. Note that null-references are an exception, as they do not have any value. The value is used as a bit-field with the syntax `bgvv`:

If the `b`-bit is zero, then this is a reference into a global or local dictionary. The lower 3-bits are: `0gvv`

- `g`: If this bit is set, refers to the global dictionary, otherwise to the local dictionary.
- `vv`: Signals the size of the reference:
  - `00b`: null-reference
  - `01b`: 8-bit unsigned index (+1 byte)
  - `10b`: 16-bit unsigned index (+2 byte)
  - `11b`: 32-bit unsigned index (+4 byte)
 
If the `b`-bit is set, then this is a **back-reference**. The index is turned into a relative or absolute offset: `1avv`

- `a`: If this bit is set, the offset is unsigned and absolute. Otherwise, the offset is relative and signed.
- `vv`: Signals the size of the reference:
  - `00b`: null-reference
  - `01b`: 8-bit unsigned/signed offset (+1 byte)
  - `10b`: 16-bit unsigned/signed offset (+2 byte)
  - `11b`: 32-bit signed offset (+4 byte)

**Note**: References must not refer to references!

### (4) string - JbString
A string that must not contain any references. The lower 4-bit are the size indicator. A value between 0 and 12 represent the size of the string. The values 13 to 15 signal:

- `13`: The next byte stores the unsigned size, (0 to 255).
- `14`: The next two byte store the unsigned size, big-endian (0 to 65535).
- `15`: The next four byte store the unsigned size, big-endian (0 to 2^32-1).

### (5) container - JbMap, JbArray or JbText
A map, array, map-entry or array-entry. The two high bits of the value parameter are used to select the type, the lower 2-bit are always the size indicator of the byte-size of the collection or entry. This indicates the amount of byte to skip, to jump over the map or entry.

The types are:

- `00b`: JbMap
- `01b`: JbArray
- `10b`: Reserved
- `11b`: JbText (string that may contain references)

The size meanings is:

- `00b`: Empty map/array/text (size and length are implicit zero).
- `01b`: The size is stored in one byte (0 to 255).
- `10b`: The size is stored in two byte, big-endian (0 to 65535).
- `11b`: The size is stored in four byte, big-endian (0 to 2^32-1).

After the lead-in the size is encoded, except being empty. Eventually the content follows, ((key,value),...) for maps, (value,...) for arrays and (uni-code, ...) for strings using the corresponding encoding.

**Note**: The keys for a map are always references to strings. The values for map and array may only hold scalar values or references, therefore it is not allowed to embed strings.

### (6) tiny-local-reference
### (7) tiny-global-reference
A local or global reference with embedded index. The lower 4-bit encode the index. This allows to encode the first 16 entries in the dictionary with the strings used most often and therefore compress them the most (they are only referred by a single byte).

## Extended Parameter-Types (2-bit)

### (0) JbPosition (draft-only)
A GeoJSON position. This is a complex value that persists out of longitude, latitude and an optional altitude.

This is a binary encoding of a GeoJSON position and can be an absolute or a relative position. A relative position is only allowed in a list of positions.

Generally the longitude and latitude values are converted from decimal degree into an integer format for better processing and to be able to store relative positions. The longitude and latitude are first converted into 1/100'th milliseconds, so the decimal degree is multiplied with **360,000,000** (`60 x 60 x 1000 x 100`). This number is converted into a 64-bit integer and will be a number between -64,800,000,000 and +64,800,000,000. Therefore, the number can be stored in a 36-bit integer for longitude and a 35-bit integer for latitude.

For an earth circumference of 40,075km, we get a precision minimum of 0.31 millimeter. In other words, the earth circumference is around 40,075,000,000 millimeter (`40,075 x 1,000 x 100 x 10`), divided by 129,600,000,000 (two times 64,800,000,000) resulting in 0.31 millimeter per unit.

This allows to store every position absolutely in 71-bit, using one additional bit to signal if an altitude is present or not. Therefore, an absolute position is encoded in 9 to 12 byte.

A position can be stored relative, in that case only the difference to the previous position is stored, instead of storing the full absolute value. Assume two coordinates are 0.5 seconds next to each other, this means 50,000 milliseconds difference, which can be encoded in 32-bit (16-bit for each axis), therefore consuming 50% less space.

The lower 4-bit of the position encode if the position is absolute or relative and if it is relative, how many byte are used to encode longitude, latitude and optionally altitude:

- 0: Relative, 8-bit longitude, 8-bit latitude, no altitude (2-byte).
- 1: Relative, 16-bit longitude, 16-bit latitude, no altitude (4-byte).
- 2: Relative, 24-bit longitude, 24-bit latitude, no altitude (6-byte).
- 3: Relative, 16-bit longitude, 8-bit latitude, no altitude (3-byte).
- 4: Relative, 24-bit longitude, 8-bit latitude, no altitude (4-byte).
- 5: Relative, 8-bit longitude, 16-bit latitude, no altitude (3-byte).
- 6: Relative, 8-bit longitude, 24-bit latitude, no altitude (4-byte).
- 7: Relative, 8-bit longitude, 8-bit latitude, 8-bit altitude (3-byte).
- 8: Relative, 16-bit longitude, 16-bit latitude, 8-bit altitude (5-byte).
- 9: Relative, 24-bit longitude, 24-bit latitude, 8-bit altitude (7-byte).
- 10: Relative, 16-bit longitude, 16-bit latitude, 16-bit altitude (6-byte).
- 11: Relative, 24-bit longitude, 24-bit latitude, 24-bit altitude (9-byte).
- 12: The position is absolute without altitude (9-byte).
- 13: The position is absolute with 8-bit altitude (10-byte).
- 14: The position is absolute with 16-bit altitude (11-byte).
- 15: The position is absolute with 24-bit altitude (12-byte).

### (1) Reserved
### (2) Reserved
### (3) Reserved

## Standard-Types (6-bit)
The first 16 standard types are scalars, the others are objects.

### (0) null
### (1) undefined (unsupported)
If set as key in a map, the key-value pair is removed. In such a case no value will follow.
### (2) bool1 {true}
### (3) bool1 {false}
### (4) float32
The next four byte store the value, big-endian.
### (5) float64
The next eight byte store the value, big-endian.
### (6) reserved
Maybe _float128_.
### (7) reserved
Maybe _int128_.
### (8) int8
### (9) int16
### (10) int32
### (11) int64
The next 1 to 8 byte store the signed integer value.
### (12-15) reserved
Reserved to support other variants, maybe int128 or uint8 to uint64.
### (16) global dictionary - JbDict
A global dictionary is a special container used to compress JBON features. A dictionary must not store any references. The encoding of the dictionary is:

- Lead-In byte.
- The size as **integer** (either **uint4**, **int8**, **int16** or **int32**).
- The **id** of the document as **string**.
- The content, a sequence of **string**s.

After the header, the content follows. The content is simply a sequence of **string**s. From an encoder perspective this is all. However, the decoder will have to load all the objects into memory to index the content for faster access.
### (17) local dictionary - JbDict
A local dictionary is exactly the same as the global dictionary, except that it does not come with an **id**, so:
 
- Lead-In byte.
- The size as **integer** (either **uint4**, **int8**, **int16** or **int32**).
- The content, a sequence of **string**s.

### (18) JbFeature
A JBON feature is a container for a JBON object of any type. The format looks like:

- Lead-In byte.
- The size as **integer** (either **uint4**, **int8**, **int16** or **int32**).
- The **id** of the feature as **string**, can be **null**.
- The **id** of the global dictionary to be used, can be **null**.
- The embedded local dictionary.
- The embedded JBON object (the root object).

A feature can't create references to other features, only into a global dictionaries with unique identifiers. From an encoder perspective this is all.
### (19) JbLz4
Some LZ4 compressed payload, requires decompression. The format is:

- Lead-In byte.
- Compressed size as **integer** (either **uint4**, **int8**, **int16** or **int32**).
- Decompressed size as **integer** (either **uint4**, **int8**, **int16** or **int32**).
- The compressed bytes. The **compressed-size** amount of bytes, should inflate to **compressed-size**.

### (20) Point (draft-only)
A GeoJSON Point, followed by a single position.
### (21) MultiPoint (draft-only)
A GeoJSON MultiPoint, followed by an array of positions (position[]).
### (22) LineString (draft-only)
A GeoJSON LineString, followed by an array of positions (position[]).
### (23) MultiLineString (draft-only)
A GeoJSON MultiLineString, followed by an array of position arrays (position[][]).
### (24) Polygon (draft-only)
A GeoJSON Polygon, following by an array of position arrays (position[][]).
### (25) MultiPolygon (draft-only)
A GeoJSON Multi-Polygon, following by an array of position array arrays (position[][][]).
### (26-31) Reserved
Reserved for further objects.

## PLV8 Exports

### Supported types
- `bool`: **boolean**
- `int`: **int4**
- `float32`: **float**
- `float64`: **double**
- `string`: **text**
- `map`: **bytea**
- `array`: **bytea**

### Map operations
- `jb_map_get_{type}(jbon bytea, path text, alternative {type}) : {type}`
- `jb_map_get_type(jbon bytea, path text) : string`
- `jb_map_contains_key(jbon bytea, path text) : boolean`

### Array operations
- `jb_array_get_{type}(jbon bytea, index int4, alternative {type}) : {type}`
- `jb_map_get_type(jbon bytea, path text) : string`

### Support operations
- `jb_to_jsonb(bytea) : jsonb`
- `jb_from_jsonb(jsonb) : bytea`

- Global dictionary support
  - Prepare SQL connection support for JUnit
  - JbPlatform
    - prepare()
    - execute()
    - ...
- Session init (user, author, ...)
- Code to create new transaction numbers
  - GUID decoder and encoder
- WriteFeatures
- WriteCollections (CREATE, UPDATE, DELETE, ...)
- Before and After Triggers (fiddle-out)
- Support access to last error (errNo, errMsg)
- Need the storage-id somewhere (in Platform)
