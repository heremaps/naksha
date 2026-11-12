package com.here.naksha.cli.stream.providers

import com.here.naksha.cli.stream.NakshaStorageV2
import naksha.model.IStorage
import naksha.model.IStorageConfigBuilder
import naksha.model.IStorageProvider

class V2StorageProvider : IStorageProvider {
    override val name = "v2"
    override val description = "Reads streams from Naksha V2 PostgreSQL."

    override fun newConfigBuilder(): IStorageConfigBuilder =
        V2ConfigBuilder()

    override fun getStorage(config: String): IStorage =
        StreamOnlyStorage.use(name, NakshaStorageV2::class.java, config)
}