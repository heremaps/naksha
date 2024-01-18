//package com.here.naksha.lib.jbon;

class JBON {
    init {
        println("Hello World!")
    }
}
/*

Topology {

}
Clinet Serialize
-> GZip
-> send to naksha
-> unzip (-> we get 2tb of json!)
-> parse using jackson
-> use codec to serialize to json
-> send to database (uncompressed, 2tb)
-> database deserialize to jsonb
-> database modifies it
-> compress and write to disk

Codec -> byte[] (utf-8 byte)

WHERE jbon_get_text(col, array['properties', 'name']) = 'foo';
WHERE col->'properties'->>'name' = 'foo';

-> send to naksha
-> modify it
-> compress and write to disk

*/