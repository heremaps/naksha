# JBON

## Introduction
JBON is a shortcut for Java Binary Object Notation. In this binary format all values are stored as objects in a tree structure.

All JBON entities are derived from the JBON-Any type. This type (and therefore all others too) always starts with a type-byte. It identifies the type of the object and therefore allows to identify the binary size and other attributes. If the sign-bit of the type-byte is set (the highest bit), then the three bit following the sign-bit are the type-identifier and the type is called a **value-type**. In this case, the lower 4-bit are a parameter value of the type. If the sign-bit is cleared, then all other 7 bit are the type-identifier.

Therefore, the type-byte allows eight **value-types** with a 4-bit parameter, and 128 **standard-types**.

JBON values are always copy-on-write, that means, every modification requires to copy the object. Therefore, all JBONs are immutable. Reading in a JBON requires a cursor that can be used to move forward, backward, down and up in the JBON tree.

## Size vs Length
In this document the term `size` refers to an amount of bytes, so a byte-size, while the term `length` refers to an entity count, for example the string-length is the amount of code-points while the string-size is the amount of byte it requires.

## Value-Types (3-bit)

### (0) int4
The lower 4-bit hold the biased (signed) value. The value is stored minus 8, so `-8..7`.

### (1) float4
The lower 4-bit hold the biased (signed) value. The value is stored minus 8, so `-8..7` (must be cast to a float or double).

### (2) size32
A flexible unsigned 32-bit integer. The parameter value means the following:
 
- `0000b`: 0 
- `0001b`: 1
- `0010b`: 2
- `0011b`: 3
- `0100b`: 4
- `0101b`: 5
- `0110b`: 6
- `0111b`: 7
- `1000b`: 8
- `1001b`: 9
- `1010b`: 10
- `1011b`: 11
- `1100b`: (12) The value is encoded biased (-12) in the next byte (means 12 to 267).
- `1101b`: (13) The value is encoded big-endian in the next two byte (0 to 65535).
- `1110b`: (14) The value is encoded big-endian in the next three byte (0 to 16777215).
- `1111b`: (15) The value is encoded big-endian in the next four byte (0 to 2^32-1).

### (3) reference
If the highest bits of the parameter is set, then this refers to the dictionary, otherwise it is a local offset. The lower three bit encode the following meaning:

- `0`: null-reference
- `1`: 8-bit index/offset (+1 byte)
- `2`: 16-bit index/offset (+2 byte)
- `3`: 24-bit index/offset (+3 byte)
- `4`: 32-bit index/offset (+4 byte)
- `5-7`: Reserved.

Note that a dictionary does have a leading index table, which encodes the 32-bit unsigned offset in the dictionary where the corresponding object is located. This means, that the dictionary index is the offset divided by 4 and subtracted by the header of the dictionary. Locally the offset is encoded, so direct absolute index where the referred target is stored.

**Note**: References must not refer to references!

### (4) string
A UNICODE string using a special encoding that is smaller than UTF-8 and additionally allows to reuse sub-strings or include them from dictionaries. Generally the encoder will always break down all strings by the space character and encode them in chunks. The lower 4-bit are the size indicator. A value between 0 and 10 represent the size of the (so between 0 and 10). The values 11 to 15 signal:

- 11: The next byte stores the biased size, so size - 11 (11 to 266).
- 12: The next two byte store the unsigned size, big-endian (0 to 65535).
- 13: The next three byte store the unsigned size, big-endian (0 to 16777215).
- 14: The next four byte store the unsigned size, big-endian (0 to 2^32-1).
- 15: Reserved.

The encoding of the string is not UTF-8, but with a special encoding that is not only smaller, but especially allows dictionary lookups. The leading bytes of every byte signal the following:

- `0vvv_vvvv`: The value encodes the code point value. Allows values between 0 and 255 (ASCII).
- `10vv_vvvv`: The value should be AND masks `0011_1111`, then shift-left by 8, the value of the next byte should be added using OR, and finally 256 should be added. Allows values between 256 and 16640 (2^14+256).
- `110v_vvvv`: The value should be AND masks `0001_1111`, then shift-left by 16, and eventually the value of the next two byte (read big-endian) should be added using OR. Allows values between 0 and 2097152.
- `111s_vvvv`: The value should be AND masks `0000_1111`, it represents a **string-reference**. The lower four bit have the same meaning as for a **reference**, but must not refer to anything but a string, not even to a text. The `s`-bit (bit number 4) signals if a space should be added behind the string. This allows to encode a space (which mostly always follows, because text is split by spaces) into one otherwise not used bit.

### (5) map, array, map-entry or array-entry
A map, array, map-entry or array-entry. The two high bits of the value parameter are used to select the type, the lower 2-bit are always the size indicator of the byte-size of the collection or entry. This indicates the amount of byte to skip, to jump over the map or entry.

The types are:

- `00b`: Map.
- `01b`: Map-Entry.
- `10b`: Array.
- `11b`: Array-Entry.

The size meanings is:

- `00b`: Empty map/array or null-entry (size and length are implicit zero).
- `01b`: The size is stored in one byte (0 to 255).
- `10b`: The size is stored in two byte, big-endian (0 to 65535).
- `11b`: The size is stored in four byte, big-endian (0 to 2^32-1).

After the lead-in the size is encoded, except being empty or null.

For a map and array, after the size an **usize** is encoded, storing the length of the map, so the number of entries.

If being a map-entry, the size is directly followed by the key and value or just the value.

### (6) position (unsupported)
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

### (7) reserved

## Standard-Types (7-bit)

### (0) null
### (1) undefined (unsupported)
If set as key in a map, the key-value pair is removed. In such a case no value will follow.
### (2) bool1 {true}
### (3) bool1 {false}
### (4) float32
The next four byte store the value, big-endian.
### (5) float64
The next eight byte store the value, big-endian.
### (6) dictionary
A dictionary is a special container used to compress JBON documents. A dictionary must not store any references.

After the lead-in byte of the dictionary two values follow. The first is encoded in the next three-byte and encodes the unsigned length of the index table, so the amount of entries in it, a value between 0 and 16777215. The next value is a 4 byte integer that stores the size of the dictionary in bytes. This aligns the index-table to the 8-byte boundary. The index table is stored next and is an array of 32-bit int values (big-endian), holding the offset of the corresponding object in the dictionary, where zero represents null.

After this header the first value encoded in the dictionary is a **string** with the unique identifier of the dictionary.
### (7) document
A JBON document is a container for JBON objects. It holds a reference to a single dictionary and itself is a buffer encoding objects. It is not allowed to create references from one document into another document. To share information only dictionaries may be used.

After the lead-in byte of the document two values are following. First the **size32** with the size of document and then the **reference** to the root object that represents the document.

The encoder may add arbitrary data after this header or directly store the root object. This leaves room to add additional header information later, without breaking the downward compatibility.
### (8-15) int{8,16,24,32,40,48,56,64}
The next 1..8 byte stores the signed integer value.
### (16-31) bool{8,16,24,32,40,48,56,64} (unsupported)
The next 1..8 byte stores a boolean bit-map.
### (32-47) id{8,16,24,32,40,48,56,64} (unsupported)
The next 1..8 byte stores the unsigned identifier.
### (48) Point (unsupported)
A GeoJSON Point, followed by a single position.
### (49) MultiPoint (unsupported)
A GeoJSON MultiPoint, followed by an array of positions (position[]).
### (50) LineString (unsupported)
A GeoJSON LineString, followed by an array of positions (position[]).
### (50) MultiLineString (unsupported)
A GeoJSON MultiLineString, followed by an array of position arrays (position[][]).
### (51) Polygon (unsupported)
A GeoJSON Polygon, following by an array of position array arrays (position[][][]).
### (52-127) Reserved
