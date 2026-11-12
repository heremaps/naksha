package com.here.naksha.cli.stream.providers

import naksha.base.AnyObject
import naksha.base.Platform
import naksha.base.illegalArg
import naksha.base.illegalState
import naksha.model.IStorageConfigBuilder
import naksha.psql.PgInstanceConfig

class V2ConfigBuilder : IStorageConfigBuilder {
    private enum class Field(val prompt: String, val fallback: String?) {
        HOST("PostgreSQL host", "localhost"),
        DB("Database", "naksha"),
        PORT("Port", "5432"),
        USER("User", "postgres"),
        PASSWORD("Password", null)
    }

    private var index = 0
    private val master = PgInstanceConfig().withReadOnly(false)

    private val current: Field
        get() = Field.entries.getOrNull(index)
            ?: throw illegalState("All configuration questions are answered")

    override val isDone: Boolean
        get() = index == Field.entries.size

    override val description: String
        get() = ""

    override val question: String
        get() = current.prompt

    override val options: Array<String>?
        get() = null

    override val defaultAnswer: String?
        get() {
            val field = current
            return System.getenv("NAKSHA_STREAM_V2_${field.name}")
                ?: field.fallback
        }

    override val isSecret: Boolean
        get() = current == Field.PASSWORD

    override fun answer(answer: String) {
        val field = current
        val raw = answer.ifEmpty {
            defaultAnswer ?: throw illegalArg("An answer is required")
        }
        val value = if (field == Field.PASSWORD) raw else raw.trim()

        if (field != Field.PASSWORD && value.isEmpty()) {
            throw illegalArg("${field.prompt} must not be blank")
        }

        when (field) {
            Field.HOST -> master.host = value
            Field.DB -> master.db = value
            Field.PORT -> {
                val port = value.toIntOrNull()
                    ?: throw illegalArg("Port must be an integer between 1 and 65535")
                if (port !in 1..65535) {
                    throw illegalArg("Port must be between 1 and 65535")
                }
                master.port = port
            }
            Field.USER -> master.user = value
            Field.PASSWORD -> master.password = value
        }

        index++
    }

    override fun build(): String {
        if (!isDone) {
            throw illegalState("Answer all configuration questions before building")
        }

        return Platform.toJSON(
            AnyObject().apply {
                put("master", master)
            }
        )
    }
}