package naksha.psql

import naksha.base.NakshaError.NakshaErrorCompanion.ILLEGAL_ARGUMENT
import naksha.base.NakshaException
import naksha.base.Platform
import naksha.base.PlatformMap
import naksha.base.illegalArg
import naksha.base.illegalState
import naksha.model.IStorage
import naksha.model.IStorageConfigBuilder
import naksha.model.IStorageProvider
import naksha.model.Naksha

class PsqlStorageProvider : IStorageProvider {
    override val name: String
        get() = "v3"

    override val description: String
        get() = "A Naksha v3 PostgresQL storage."

    override fun newConfigBuilder(): IStorageConfigBuilder = PsqlConfigBuilder()

    override fun getStorage(config: String): IStorage {
        val raw = try {
            Platform.fromJSON(config)
        } catch (e: Exception) {
            throw NakshaException(ILLEGAL_ARGUMENT, "Invalid v3 configuration: ${e.message}")
        }
        if (raw !is PlatformMap) throw illegalArg("Invalid v3 configuration: not a JSON object")
        return Naksha.useStorage(raw.proxy(PgConfig::class))
    }
}

internal class PsqlConfigBuilder : IStorageConfigBuilder {
    private class Question(
        val key: String,
        val question: String,
        val description: String,
        val defaultAnswer: String?,
        val secret: Boolean = false,
        val port: Boolean = false,
    )

    private val questions = listOf(
        Question("id", "Storage id", "The id of the storage, as installed in the database.", null),
        Question("host", "Host", "The host of the PostgresQL server.", "localhost"),
        Question("port", "Port", "", PgInstanceConfig.DEFAULT_PORT.toString(), port = true),
        Question("db", "Database", "", PgInstanceConfig.DEFAULT_DB),
        Question("user", "User", "", PgInstanceConfig.DEFAULT_USER),
        Question("password", "Password", "", null, secret = true),
    )
    private val answers = HashMap<String, String>()
    private var index = 0

    private fun current(): Question = if (isDone) throw illegalState("All questions are answered") else questions[index]

    override val isDone: Boolean
        get() = index >= questions.size

    override val description: String
        get() = current().description

    override val question: String
        get() = current().question

    override val options: Array<String>?
        get() {
            current()
            return null
        }

    override val defaultAnswer: String?
        get() = current().defaultAnswer

    override val isSecret: Boolean
        get() = current().secret

    override fun answer(answer: String) {
        val q = current()
        if (answer.isBlank()) throw illegalArg("${q.question} is required")
        if (q.port && answer.toIntOrNull()?.let { it in 1..65535 } != true) throw illegalArg("${q.question} must be a number between 1 and 65535")
        answers[q.key] = answer
        index++
    }

    override fun build(): String {
        if (!isDone) throw illegalState("Not all questions are answered")
        val master = PgInstanceConfig()
            .withHost(answers.getValue("host"))
            .withPort(answers.getValue("port").toInt())
            .withDb(answers.getValue("db"))
            .withUser(answers.getValue("user"))
            .withPassword(answers.getValue("password"))
        return Platform.toJSON(PgConfig(answers.getValue("id")).withMaster(master))
    }
}
