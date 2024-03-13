# JBON

## Introduction
JBON is a shortcut for Java Binary Object Notation. In this binary format all values are stored as objects in a tree like structure that can be navigated quickly, but is at the same time very small.

As the format name indicates, this format is object-oriented. All JBON elements are encoded using **units**. All **units** always start with a **header**. The header encodes the type of the unit. There are three general types:

- Scalars: All units that encode a fixed size value (integer, timestamp, ...)
- Strings: A special units that encodes UNICODE code points
- Structs: A unit that has subunits (map, array, ...)

All **scalars** and **strings** can only be read at ones. The **structs** can be entered, therefore their header has an explicit size and can be skipped to enter the structure. It does not make sense to skip the header of any scalar value and therefore should not be possible.

The header of all unites start with the **lead-in** byte, which identifies the type of the unit. If **lead-in** byte signals a structure, then it is followed by an optional unsigned integer encoded in 1, 2 or 4 byte (big-endian), storing the size of the payload of the structure. After the size, an optional variant is encoded as 1, 2 or 4 byte integer (big-endian), which allows up to 4 billion subtypes. Following the variant, the payload of the structure follows. Only structures do have a dedicated header size, all other units have always a header size of 1.

### Index vs Offset
When this document mentions an **index**, it refers to the position in a dictionary. When this document refers to an **offset**, then it refers to the byte-offset in a JBON binary (basically a relative pointer in the JBON).

### Size vs Length
In this document the term `size` refers to an amount of bytes, so a byte-size, while the term `length` refers to a number of subunits. For example the map-length is the amount of map-entries while the map-size is the amount of byte it requires.

The size is normally used to skip over units, while the length is used logically.

## Header
The header of all units is in the following format:

- lead-in byte
- optional: size as 1, 2 or 4 byte unsigned integer
- optional: variant as 1, 2 or 4 byte integer

Generally, all units there have a **unit-size**, which is the total size of the unit and therefore the amount of byte to seek forward, if the unit need to be skipped. Additionally, there is the **payload-size**, which is the amount of byte that store the value of the unit and the **header-size**, which is the lead-in byte, plus the optional size, plus the optional variant. In a nutshell:

- **unitContentSize**: The amount of byte that store the payload (0 to n).
- **unitHeaderSize**: The amount of byte that store the header, so lead-in byte, plus size, plus variant (1 to n).
- **unitSize**: The total outer size of the unit, bytes to seek forward to skip the unit, **unitHeaderSize** plus **unitContentSize**. 

### Lead-in byte
All units start with a **lead-in** byte, which describes the type of the unit. This **lead-in** byte is optionally followed by 1, 2 or 4 bytes to store the unit size, optionally followed by the payload of the unit (some units do not have a payload). The following lead-in bytes are defined:

- `00`: mixed
  - `0000_0000`: **undefined**
  - `0000_0001`: **null**
  - `0000_0010`: Boolean, **false**
  - `0000_0011`: Boolean, **true**
  - `0000_0100`: Integer, **int8** + 1 byte
  - `0000_0101`: Integer, **int16** + 2 byte BE payload
  - `0000_0110`: Integer, **int32** + 4 byte BE payload
  - `0000_0111`: Integer, **int64** + 8 byte BE payload
  - `0000_1000`: Float, **float16** + 2 byte BE payload
  - `0000_1001`: Float, **float32** + 4 byte BE payload
  - `0000_1010`: Float, **float64** + 8 byte BE payload
  - `0000_1011`: Float, **float128** + 16 byte BE payload
  - `0000_1100` = Timestamp + 6 byte BE unsigned integer payload
    - Unix epoch timestamp (UTC) in milliseconds, stored in big-endian encoding as 6-byte value. We choose 48-bit, because a year has 31,536,000,000 milliseconds, therefore 36-bit are enough for only 2 years, 40-bit for only 34 years, but 48-bit are already sufficient for 8925 years.
  - `0000_1101` = reserved1
  - `0000_1110` = reserved2
  - `0000_1111` = reserved3
  - `0001_vvvv`: Tiny Local-Reference, **ref5** (0 to 15)
  - `0010_vvvv`: Tiny Global-Reference, **ref5** (0 to 15)
  - `0011_bgvv`: Reference (biased by 16)
    - vv=0: **null**
    - vv=1: **ref8** + 1 byte payload
    - vv=2: **ref16** + 2 byte BE payload
    - vv=3: **ref32** + 4 byte BE payload
- `01`: tiny-value
  - `0100_vvvv`: Integer, **int5** (0 to 15)
  - `0101_vvvv`: Integer, **int5** (-16 to -1)
  - `0110_vvvv`: Float, **float5** (0.0 to 15.0)
  - `0111_vvvv`: Float, **float5** (-16.0 to -1.0)
- `10`: string
  - `10vv_vvvv`: String (vv_vvvv; size 0-60, 61=uint8, 62=uint16, 63=uint32)
    - If the size is not embedded (61-63), then followed by 1, 2 or 4 byte unsigned biased integer (biased by 60), big-endian encoded.
- `11`: struct
  - `11ss_vvtt` = Struct (ss: 0=empty, 1=uint8, 2=uint16, 3=uint32)
    - Followed by one byte, two byte or four byte unsigned content size, big-endian encoded.
    - If standard structure (vv=0, variant=null)
      - `0`: Array
      - `1`: Map
      - `2`: Dictionary
      - `3`: Reserved
    - If variant structure (vv: 1=byte, 2=short, 3=int)
      - Followed by one byte, two byte or four byte integer storing the variant, big-endian encoded.
      - `0`: Feature
      - `1`: XYZ
      - `2`: Custom
      - `3`: Reserved

JBON values are always copy-on-write, that means, every modification requires to copy the object. Therefore, all JBONs are immutable. Reading in a JBON requires a cursor that can be used to move through JBON tree. As every unit stores it outer size, every unit (including all subunits) can be skipped over or entered, by moving the cursor behind the header. Note that only **strings** or **structs** can be entered, all other values are scalars.

### Test the lead-in byte
The lead-in encodes the unit-type. Testing the lead-in byte is done in three levels:

- Test top most two bit (unsigned shift right 6) to detect the base-type, being **tiny-value** (0), **mixed** (1), **string** (2) or **struct** (3)
- For **mixed** (1), test again the top four bit to detect final type.
  - If top four bits are `0111`, a switch against the full lead-in can be done.
  - Otherwise, reference.

## Scalars and fixed size
As described in the **lead-in** section, scalars and fixed size encodings are simple, no further explanation is given.

## References
There two general types of references, back-references and dictionary-references. All references have one thing in common: The referred unit must not contain further references. This means automatically, that dictionaries them self must not store references.

The first 16 entries in a dictionary can be referred by tiny-references. All other entries in a dictionary are referred always by full references. Therefore, logically, full-references are always biased by 16, so a value of 0 means index 16. A reference has a **lead-in** byte in the format: `0111_bgvv`.

If the `b`-bit is zero, then this is a reference into a dictionary. The lower 3-bits are: `0gvv`

- `g`: If this bit is set, refers to the global dictionary, otherwise to the local dictionary.
- `vv`: Signals the size of the reference:
  - `00b`: null-reference
  - `01b`: 8-bit unsigned index (+1 byte)
  - `10b`: 16-bit unsigned index (+2 byte)
  - `11b`: 32-bit unsigned index (+4 byte)

If the `b`-bit is set (**currently not supported**), then this is a **back-reference**. The index is turned into a relative or absolute offset: `1avv`

- `a`: If this bit is set, the offset is unsigned and absolute. Otherwise, the offset is relative and signed.
- `vv`: Signals the size of the reference:
  - `00b`: null-reference
  - `01b`: 8-bit unsigned/signed offset (+1 byte)
  - `10b`: 16-bit unsigned/signed offset (+2 byte)
  - `11b`: 32-bit unsigned/signed offset (+4 byte)

**Note**: References must not refer to references!

## Strings
JBON strings are not encoded using UTF-8, but a special encoding that is smaller, and allows dictionary lookups. The **lead-in** for a string is `10vv_vvvv`. The values (vv_vvvv) stores the size of the code-points in byte:

- `0-60`: The embedded size of the string in byte (0-60).
- `61`: The size is stored biased by 61 in the next byte (61 - 316).
- `62`: The size is stored in the next two byte (unsigned short), big-endian encoded.
- `63`: The size is stored in the next four byte (unsigned integer), big-endian encoded.

The code-points are variable encoded. The leading byte of every code-point signal the following:

- `0vvv_vvvv`: The value encodes the code point value. Allows values between 0 and 127 (ASCII).
- `10_vvvvvv`: The value should be AND masks `0011_1111`, then shift-left by 8, the value of the next byte should be added using OR, and finally 128 should be added. Allows values between 128 and 16511 (2^14+128-1).
- `110_vvvvv`: The value should be AND masks `0001_1111`, then shift-left by 16, and eventually the value of the next two byte (read big-endian) should be added using OR. Allows values between 0 and 2097151 (2^21-1).
- `111_ssgvv`: The value is a **string-reference**. The three lower bits (`gvv`) have the same meaning as for a **reference**, but must not refer to anything but a string. The `ss`-bits (bit number 4 and 5) signal if a special character should be added behind the referred string. The following values are defined:
  - `00`: Do not encode any additional character.
  - `01`: Add a space behind the string.
  - `10`: Add an underscore (`_`) behind the string.
  - `11`: Add a colon (`:`) behind the string.

**Note**: The `ss`-bits improve the compression greatly, because the encoder will split strings by default at a space or underscore. Exactly where these splits happen, we do not need to encode the separator characters. The reason to cut at these two characters is that most often street-names or other human text uses the space as separator, while for constants in programming most often the underscore is used as separator (TYPE_A, TYPE_B, ...). Additionally, we have room for one more split characters to be defined by experience in the future.

## Structures
All other special types are structures. The header stores the outer size of the structure, so the bytes following the **header**.

Note that there are two basic kind of structures. Those with a subtype (variant) and those without. The first 8 structure types are without variant, the last 8 are with variant. The variant is encoded as integer directly after the structure header. The variant is used to define subtypes for structures to relax the namespace, because there are actually only 16 generic structure types available.

For this reason the JBON specification defines one custom variant, that is shared by all users of JBON, and should be used to encode arbitrary (application specific) structures. This allows applications to define their own structures and allows to define up to 2 billion own custom structure.

### Array (0)
The array is just a sequence of units encoded.

### Maps (1)
In JBON the keys of a map must be strings. All key-value pairs are sorted by the binary representation of the key. The key must be a string. The reason for this is to guarantee, that two equal maps generate the same hash-code, when their binary representation is hashed. Note that the key must always be a **string-reference**, either into the local or global dictionary. The values are embedded, even while they may be references.

### Dictionary (2)
To compress the data, JBON uses dictionaries. A dictionary is simply a list of units. The first 16 entries in the dictionary can be referred using tiny-references (1 byte encoding), the rest of the entries require full references (2 to 5 byte encoding). Therefore, it is recommended to use the first 16 units for those values that are most often used and maybe are only small, to compress often used, but small objects.

When a dictionary is mapped, it will lazy load entries into an internal cache, as long as only accessed using index. The dictionary can be queried as well by FNV1b hash, in that case it will load all values into the internal cache. Note that the hash causes collisions, therefore a search can find possible multiple entries and in all cases a binary compare must be performed.

A dictionary must not store any references and strings in a dictionary must not store references. The encoding of the dictionary (after the struct-header) is:

- The **id** of the dictionary as **string** or _null_, if this is a local dictionary.
- The content.

After the header, the content follows. The content is simply a sequence of units. From an encoder perspective this is all. However, the decoder will have to load all the objects into memory to index the content for faster access.

### Reserved (3)

### Feature (0+variant)
A JBON feature is a container for any other JBON unit. It is mainly used to link the embedded unit to a dedicated global and local dictionary. The format looks like:

- The **id** of the global dictionary to be used (**string**), can be _null_.
- The **id** of the feature, **string**, **text** or _null_.
- The embedded local dictionary.
- The embedded JBON object (the root object).

A feature can't create references to other features, only into a global dictionaries with unique identifiers. From an encoder perspective this is all.

### Xyz (1+variant)
This type is reserved for XYZ interactions. It is a flat object, optimized to be very small, with the following layout:

- Variant as **integer** (either **int5**, **int8**, **int16** or **int32**).
- ... content dependent on the variant

#### XyzNs (variant:0)
The information that the database manages and what is delivered by the database.

- **createdAt** (timestamp)
- **updatedAt** (timestamp) - _null_, if being the same as **createdAt**
- **txn** (BigInt64)
- **action** (integer), constants for CREATE (0), UPDATE (1) and DELETE (2)
- **version** (integer)
- **author_ts** (timestamp) - _null_, if being the same as **updatedAt**, which can be the same as **createdAt**
- **extend** (double)
- **puuid** (string or _null_)
- **uuid** (string)
- **app_id** (string)
- **author** (string)
- **grid** (string) SELECT ST_GeoHash(ST_Centroid(geo),7);

Notes: Tags are now a dedicated map, but when exposed, they are joined by an equal sign, the _null_ is default and causes the equal sign to disappear. So the tag "foo" becomes "tag=null" and when converting back "tag=null" is converted into "tag". Any other value, not being _null_, will be encoded into the tag. We do not allow equal signs otherwise, so only one equal sign is allowed in a tag. We do this, because we add an GIN index on the tags and allows key-value search at low level.

#### XyzOp (variant:1)
The information that clients should send to the database to write features or collections. This has to be provided together with a new feature.

- **op** (integer) - The requested operation (CREATE, UPDATE, UPSERT, DELETE or PURGE).
- **id** (string) - The feature-id.
- **uuid** (string or _null_) - If not _null_, then the operation is atomic and the state must be this one (only UPDATE, DELETE and PURGE).
- **grid** (string or _null_) - If the geo-reference-id is calculated by the client.

#### XyzTags (variant:2)
The tags, basically just a normal JBON map, but the values must only be **null**, **boolean**, **string** or **float64**. The map is preceded by the **id** of the global dictionary to be used, can be **null**, so actually being:

- **id** (string or _null_) of the global dictionary to use.
- Now the tags follow, split into a key and value part:
  - **string** or **string-reference** - The key or reference to the key to index.
  - **null**, **boolean**, **string**, **string-reference**, **integer** or **float**. If an integer is stored, it must be exposed as floating point number.

Tags do not support integers directly, but as floating pointer numbers support up to 53-bit precision with integer values, a limited amount of integer support is available.

**Note**: Externally _tags_ are only arrays of strings, therefore to convert external to internal representation the equal sign is used to split individual tag-strings. If a colon is set in-front of the equal sign, a value conversion is done, so _"foo=12"_ results in the value being a string "12", while _"foo:=12"_ results in a value being a floating point number _12_. Please read more about tags in the [documentation](../docs/TAGS.md).

#### XyzTxDetails (variant:3, draft)
Details about a transaction:

- **collections** - A map where the key is the collection identifier and the value is an integer bit-mask with what happened.

### Custom-Variant (2+variant)
An undefined type that any application can use for internal binary encodings. It is a flat object, optimized to be very small, with the following layout:

- Variant as **integer** (either **int5**, **int8**, **int16** or **int32**).
- ... content dependent on the variant

### Reserved (3+variant)

## Extended Proposals
Encoding a GeoJSON position. This is a proposal for a complex value that persists out of longitude, latitude and an optional altitude.

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

## Performance insights
For optimal performance we want to reduce the amount of data being transferred to the database, and at the same time, reduce the amount things the database need to do (CPU load). Technically, this section discusses how we can avoid running any server side code in this case. We still want to have the server side code setup, but mainly to prevent that someone, executing changes using an arbitrary SQL client, breaks the storage state.

Therefore, this document describes what need to be done to have a 100% client side implementation, and what parts need to be installed as server side code, to prevent breaking modifications and protect the store.

### startSession / naksha_start_session
There are two ways to access the database, but both require a **NakshaSession**. Using the Java client, a new client-side session can be started via:

```kotlin
val env = JvmPlv8Env.get()
val session = env.startSession(conn, schema, appName, streamId, appId, author)
```

When using an arbitrary SQL client, this session need to be started server side via:

```sql
SELECT naksha_start_session(appName, streamId, appId, author);
```

Both use-cases do have a session now. For the _Java_ implementation it will disable triggers. This can be done by settings the [session_replication_role](https://www.postgresql.org/docs/16/runtime-config-client.html#GUC-SESSION-REPLICATION-ROLE) to `replica` and later back to `origin`, like `SET session_replication_role = replica;`. The server side session relies upon the triggers to ensure history and other things.

From this point on, all further operations should be executed against the session. In the database there will be SQL functions prefixed with `naksha_`, having the same name as the corresponding counterparts in the **NakshaSession**. For example, there is a `naksha_write_collections` in SQL which actually supports exactly the same as the `session.writeCollections` function call in _Java_.

### writeCollections / naksha_write_collections
This function is used to create, update and delete collections fulfilling the standard Naksha _IStorage_ contract.

### writeFeatures / naksha_write_features
This function is used to create, update and delete features fulfilling the standard Naksha _IStorage_ contract.

### bulkWriteFeatures (client-only)
This function is used to perform bulk operations to create, update or delete features. It fulfills the Naksha _IStorage_ contract, with some minor limitation. The method automatically rollback failed operations and is always atomic. It allows to suppress the success results the same way the default `writeFeatures` does, but it does not support mixed mode, so in an error case it will not return a cursor, but fully fails and automatically rollback.

In the success case, it will return and allow to decide between `commit` and `rollback`, but it is highly recommended to make this decision instant, because meanwhile it will keep locks in the database.

The bulk write will implement these steps:

- Fetch details about the collections into which to write
  - This provides information, if partitioning is supported
- Ensure we have a transaction number (`txn`)
- Disable triggers for this transaction via [session_replication_role](https://www.postgresql.org/docs/16/runtime-config-client.html#GUC-SESSION-REPLICATION-ROLE):
  - `SET LOCAL session_replication_role = replica;`
- Autogenerate ids for features not yet having some
- Sort all features by their id
- If we have partitions, group the features by the partition to which they belong
- Update the features so that all column values are set correctly
  - This requires that we have the expected head state to e.g. set action and/or increment version
  - This previous state is stored in the XYZ namespace
  - Therefore, we expect that the client either gives us these values from XYZ namespace
    - or: If this is a new feature, the client tells us, he wants to insert
  - The client should remove the XYZ namespace from the feature
  - The client should remove the geometry from the feature and give it to us as TWKB
- Create batch statements with order:
  - (1) delete feature from del-table
  - (2) insert deleted into del-table
  - (3) upsert feature in del-table
  - (4) update feature in head, set txn_next=txn (move the feature into history)
  - (5) insert deleted into history
  - (6) insert feature into head
- If the feature is created, do:
  - (1) delete feature from del-table (only if del-table enabled)
  - (6) insert feature into head
- If the feature is updated, do:
  - (1) delete feature from del-table (only if del-table enabled)
  - (4) update feature in head, set txn_next=txn (only if history enabled, move the feature into history)
  - (6) insert feature into head
- If the feature is deleted, do:
  - (4) update feature in head, set txn_next=txn (only if history enabled, move the feature into history)
  - create deleted version of feature
  - (3) upsert feature in del-table (only if del-table enabled)
  - (5) insert deleted into history
- Execute all batches in order (1-6)
- Re-enable triggers: `SET LOCAL session_replication_role = origin;`

### Global dictionary training (draft)
Add support for learning global dictionary by providing example data or even do it in the database?

### Links
- https://postgresqlco.nf/doc/en/param/session_replication_role/
- https://www.postgresql.org/docs/16/runtime-config-client.html#GUC-SESSION-REPLICATION-ROLE
- https://foojay.io/today/a-dissection-of-java-jdbc-to-postgresql-connections-part-2-batching/
- https://github.com/PgBulkInsert/PgBulkInsert
