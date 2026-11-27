package com.here.naksha.cli.stream.providers

import naksha.base.AnyObject
import naksha.base.Platform
import naksha.base.illegalArg
import naksha.base.illegalState
import naksha.model.IStorage
import naksha.model.IStorageConfigBuilder
import naksha.model.IStorageProvider

class DataHubStorageProvider : IStorageProvider {
    override val name = "datahub"
    override val description = "Reads spaces from DataHub (XYZ Hub 1.24, branch DHCodeFreeze) via its REST API."

    override fun newConfigBuilder(): IStorageConfigBuilder = DataHubConfigBuilder()

    override fun getStorage(config: String): IStorage =
        StreamOnlyStorage.use(name, DataHubStorage::class.java, config)
}

class DataHubConfigBuilder : IStorageConfigBuilder {
    private var index = 0
    private var url = DataHubConfig.DEFAULT_URL
    private var token: String? = null
    private var pageSize = DataHubConfig.DEFAULT_PAGE_SIZE

    private val questions = arrayOf(
        "DataHub URL",
        "Access token (leave empty to read ${DataHubConfig.TOKEN_ENV} when streaming)",
        "Features per request (1..${DataHubConfig.MAX_PAGE_SIZE})",
    )

    override val isDone: Boolean get() = index == questions.size
    override val description: String get() = ""
    override val question: String get() = questions.getOrNull(index) ?: throw illegalState("All configuration questions are answered")
    override val options: Array<String>? get() = null
    override val defaultAnswer: String?
        get() = when (index) {
            0 -> System.getenv("NAKSHA_STREAM_DATAHUB_URL") ?: DataHubConfig.DEFAULT_URL
            2 -> DataHubConfig.DEFAULT_PAGE_SIZE.toString()
            else -> null
        }
    override val isSecret: Boolean get() = index == 1

    override fun answer(answer: String) {
        val value = answer.trim().ifEmpty { defaultAnswer ?: "" }
        when (index) {
            0 -> {
                if (!value.startsWith("http://") && !value.startsWith("https://")) throw illegalArg("The URL must start with http:// or https://")
                url = value.trimEnd('/')
            }
            1 -> token = value.ifEmpty { null }
            2 -> {
                val n = value.toIntOrNull()
                if (n == null || n !in 1..DataHubConfig.MAX_PAGE_SIZE) throw illegalArg("Must be a number between 1 and ${DataHubConfig.MAX_PAGE_SIZE}")
                pageSize = n
            }
            else -> throw illegalState("All configuration questions are answered")
        }
        index++
    }

    override fun build(): String {
        if (!isDone) throw illegalState("Answer all configuration questions before building")
        return Platform.toJSON(AnyObject().apply {
            put("url", url)
            token?.let { put("token", it) }
            put("pageSize", pageSize)
        })
    }
}
