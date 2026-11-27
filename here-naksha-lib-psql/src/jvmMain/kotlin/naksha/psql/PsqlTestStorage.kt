package naksha.psql

/**
 * The class to be used as `className` when running local tests.
 *
 * The class just automatically enables [runDocker] when [initStorage] is called, so that `NAKSHA_TEST_PSQL_DB_URL`, then `NAKSHA_TEST_DB_URL`, is used, and a docker container is started if neither is set.
 * @since 3.0
 */
@Deprecated(
    message = "This class does only exist for downward compatibility, it is superseded using test member",
    replaceWith = ReplaceWith("test = true")
)
class PsqlTestStorage : PsqlStorage() {
    override fun initStorage(config: PgConfig, create: Boolean?, upgrade: Boolean?) {
        this.runDocker = true
        super.initStorage(config, create, upgrade)
    }
}