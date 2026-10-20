package com.here.naksha.cli.streaming

import naksha.base.Id
import naksha.model.NakshaContext
import naksha.model.objects.NakshaStorage
import naksha.model.streaming.StreamRequest

class MainStreamingV2 {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            NakshaContext.defaultAppId.set("naksha-cli-streaming")
            val host = System.getenv("NAKSHA_STREAM_V2_HOST") ?: "localhost"
            val db = System.getenv("NAKSHA_STREAM_V2_DB") ?: "naksha"
            val port = System.getenv("NAKSHA_STREAM_V2_PORT")?.toIntOrNull() ?: 5432
            val user = System.getenv("NAKSHA_STREAM_V2_USER") ?: "postgres"
            val password = System.getenv("NAKSHA_STREAM_V2_PASSWORD") ?: "password"
            val catalog = System.getenv("NAKSHA_STREAM_V2_CATALOG") ?: "public"
            val collection = System.getenv("NAKSHA_STREAM_V2_COLLECTION") ?: "dummy_collection"
            val config = NakshaStorage.fromJSON("""
            {
              "id": "collection_mod_test_storage",
              "type": "Storage",
              "title": "Target for V2 Stream Reader",
              "description": "Storage for V2 stream reader",
              "properties": {
                "master": {
                  "host": "$host",
                  "db": "$db",
                  "port": $port,
                  "user": "$user",
                  "password": "$password",
                  "readOnly": false
                }
              }
            }
        """)
            val storageV2 = NakshaStorageV2(config)
            val streamSession = storageV2.newStreamSession()
            val streamRequest = StreamRequest(
                databaseId = Id(db),
                catalogId = Id(catalog),
                collectionId = Id(collection),
                minVersion = 133039218,  // TODO: make this configurable
                //version = 133040256      // TODO: make this configurable
                chunkSize = 2
            )
            val stream = streamSession.read(streamRequest)
            println(stream.unacknowledgedChunks.size)
        }
    }

}