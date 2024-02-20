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

## Features (JbFeature)
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
### (6) time48 - Unix Timestamp in Milliseconds
Unix epoch timestamp (UTC) in milliseconds, stored in big-endian encoding as 6-byte value. We choose 48-bit, because a year has 31,536,000,000 milliseconds, therefore 36-bit are enough for only 2 years, 40-bit for only 34 years, but 48-bit are already sufficient for 8925 years. I do not believe this format will still exist in that year, so we choose 48-bit.
### (7) reserved
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
- The **id** of the global dictionary to be used (**string**), can be _null_.
- The **id** of the feature, **string**, **text** or _null_.
- The embedded local dictionary.
- The embedded JBON object (the root object).

A feature can't create references to other features, only into a global dictionaries with unique identifiers. From an encoder perspective this is all.
### (19) XYZ-Specials
This type is reserved for XYZ interactions. It is a flat object, optimized to be very small, with the following layout:

- Lead-In byte.
- variant as **integer** (either **uint4**, **int8**, **int16** or **int32**).
- ... content dependent on the variant

#### (0) XyzNs
The information that the database manages and what is delivered by the database.

- **createdAt** (timestamp)
- **updatedAt** (timestamp, _null_, if being the same as **createdAt**)
- **txn** (BigInt64)
- **action** (integer), constants for CREATE (0), UPDATE (1) and DELETE (2)
- **version** (integer)
- **author_ts** (timestamp)
- **extend** (double)
- **puuid** (string or _null_)
- **uuid** (string)
- **app_id** (string)
- **author** (string)
- **grid** (string) SELECT ST_GeoHash(ST_Centroid(geo),7);

Notes: Tags are now a dedicated map, but when exposed, they are joined by an equal sign, the _null_ is default and causes the equal sign to disappear. So the tag "foo" becomes "tag=null" and when converting back "tag=null" is converted into "tag". Any other value, not being _null_, will be encoded into the tag. We do not allow equal signs otherwise, so only one equal sign is allowed in a tag. We do this, because we add an GIN index on the tags and allows key-value search at low level.

#### (1) XyzOp
The information that clients should send to the database to write features or collections. This has to be provided together with a new feature.

- **op** (integer) - The requested operation (CREATE, UPDATE, UPSERT, DELETE or PURGE).
- **id** (string) - The feature-id.
- **uuid** (string or _null_) - If not _null_, then the operation is atomic and the state must be this one (only UPDATE, DELETE and PURGE).
- **crid** (string or _null_) - If a custom-reference-id should be used.

#### (2) XyzTags
The tags, basically just a normal JBON map, but the values must only be **null**, **boolean**, **string** or **float64**. The map is preceded by the **id** of the global dictionary to be used, can be **null**, so actually being:

- **id** (string or _null_) of the global dictionary to use.
- Now the tags follow, split into a key and value part:
  - **string** or **string-reference** - The key or reference to the key to index.
  - **null**, **boolean**, **string**, **string-reference**, **integer** or **float**. If an integer is stored, it must be exposed as floating point number.

Tags do not support integers directly, but as floating pointer numbers support up to 53-bit precision with integer values, a limited amount of integer support is available.

**Note**: Externally _tags_ are only arrays of strings, therefore to convert external to internal representation the equal sign is used to split individual tag-strings. If a colon is set in-front of the equal sign, a value conversion is done, so _"foo=12"_ results in the value being a string "12", while _"foo:=12"_ results in a value being a floating point number _12_. Please read more about tags in the [documentation](../docs/TAGS.md).

#### (3) XyzTxDetails (TDB)
Details about a transaction:

- **collections** - A map where the key is the collection identifier and the value is an integer bit-mask with what happened. 

### (20) JbLz4Compressed
Some LZ4 compressed payload, requires decompression. The format is:

- Lead-In byte.
- Compressed size as **integer** (either **uint4**, **int8**, **int16** or **int32**).
- Decompressed size as **integer** (either **uint4**, **int8**, **int16** or **int32**).
- The compressed bytes. The **compressed-size** amount of bytes, should inflate to **compressed-size**.

### (21) Reserved.
### (22) Reserved.
### (23) Point (draft-only)
A GeoJSON Point, followed by a single position.
### (24) MultiPoint (draft-only)
A GeoJSON MultiPoint, followed by an array of positions (position[]).
### (25) LineString (draft-only)
A GeoJSON LineString, followed by an array of positions (position[]).
### (26) MultiLineString (draft-only)
A GeoJSON MultiLineString, followed by an array of position arrays (position[][]).
### (27) Polygon (draft-only)
A GeoJSON Polygon, following by an array of position arrays (position[][]).
### (28) MultiPolygon (draft-only)
A GeoJSON Multi-Polygon, following by an array of position array arrays (position[][][]).
### (29-31) Reserved
Reserved for further objects.

## Why not CBOR
This section explains why [CBOR](https://www.rfc-editor.org/rfc/rfc8949.html) was not selected. The formats are similar in many points, when you read the two specification. So, why do something new? The major two difference between them are:

### Size
**JBON** supports de-duplication, especially for strings, which decreases the size of the data. Compared to **CBOR**, which actually increases the size of data and just makes it binary readable, when compared to a JSON. **JBON** not only allows to de-duplicate strings out of the box, it as well allows to de-duplicate complete objects.

Specifically the **text** type does actually allow to de-duplicate parts of a string and is very beneficial (for example in compressing **URN**s), where encoders may simply use the common prefix from a global dictionary. For example the string `urn:here::here:Topology:58626681` can be encoded (using a good encoder) as:

- text lead-in (1 byte)
- text size (1 byte)
- string-reference into global dictionary for `urn:here::here:Topology:` (2-3 byte)
- the value `58626681` (8 byte)

This result in a total of 12 to 13 byte for a 32 character UTF-8 encoded string (compression around 60%). In other words, it saves 19 byte per feature, which means for 100 million features of this type we need instead of 2.98gb of storage only 1.21gb. This is only one value.

Generally, every byte we save, decreases the topology data size by around 95mb, while at the same time allows to use the data without any need to decompress or decode them, we can just seek into the data.

### Default Values
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

We see, that over and over again this sub-object is encoded:

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

In **JBON** we can add this array including the object into the global dictionary as default value. Additionally, we put the _isPrivateRoadForServiceVehicle_ into the global dictionary and then can encode this as map entry:

- key **string-reference** (2-3 byte)
- object **reference** (2-3 byte)

So effectively reducing the size of this whole entry to 4 to 6 byte. Compared to when JSON does out of it:

`{"isPrivateRoadForServiceVehicle":[{"range":{"endOffset":1,"startOffset":0},"value":false}]}`

Now, in **CBOR** we would have to encode exactly the same thing, it would not be reduces in size, just become binary readable. This means, with **JBON** we encode 6 byte vs 96 byte of JSON or around the same for CBOR. This alone, for just one single of these properties, will save 8583mb (or 8.5gb) for the 100 million topologies we have. As there are (see above) many of these objects in the data, this must be multiplied by 10 or more, so we easily save up to 100gb of data, not losing any information!

However, the best is, that we can ask the reader for this property, and it will return it. When we use the reader, the object will appear as if it is part of the **JBON**. The application does not need to know details, it only needs access to the global dictionaries. Compression optimization is purely done on the encoder side and can be improved for all our use-cases, not having to make old data invalid or have to re-encode it.

Clearly, we could somehow add the dictionaries and text encoding to **CBOR** using [tags](https://www.rfc-editor.org/rfc/rfc8949.html#name-tagging-of-items), but it would be a proprietary extension and therefore anyway force us to do some own implementations. It would eventually make CBOR so incompatible to what the rest of the world does in this format, that there seems to be no advantage in this solution, when compared to creating our own binary encoding.
