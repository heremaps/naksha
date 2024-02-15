# Tags

Tags are very special. They are exposed as strings, but internally are indexed as flat maps. Their values must be **null** (default), **boolean**, **string** or **number**. The number is always a 64-bit floating point number, just encoded differently, so value optimal.

Tags are indexed using a [GIN index](https://pganalyze.com/blog/gin-index). These indices have some draw-backs, like they are always unordered and do not allow index-only scans, they are slow in updates (therefore should not be used in large quantities), but they have one really important features. As they store multiple key-value pairs, we can for example add references into the index to later find all features that refer another feature. Assume we need to find all road relations of a certain road, we can add tags to which roads a relation relates to and then query in road relations for all relations that relate to road XYZ. As they support an arbitrary amount of such values, we can tag features a lot and as long as the strings are values are limited, they do not even consume too much memory.

Therefore, they should be used carefully, but are absolutely important for many use-cases.
