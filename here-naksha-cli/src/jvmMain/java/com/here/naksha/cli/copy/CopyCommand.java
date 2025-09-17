package com.here.naksha.cli.copy;

import com.here.naksha.cli.VersionInfo;
import com.here.naksha.cli.copy.service.*;
import com.here.naksha.cli.copy.service.factory.CopyServiceFactory;
import com.here.naksha.cli.copy.service.factory.CopyServiceFactory.WriteMode;
import com.here.naksha.cli.loggers.LoggingMixin;
import com.here.naksha.cli.parsers.JsonFileParser;
import com.here.naksha.cli.parsers.JsonFileParserException;
import com.here.naksha.cli.results.CommandFailure;
import com.here.naksha.cli.results.CommandResult;
import com.here.naksha.cli.results.CommandSuccess;
import naksha.model.NakshaContext;
import naksha.model.SessionOptions;
import naksha.model.objects.NakshaStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "copy",
        mixinStandardHelpOptions = true,
        description = "Copy features between storages.",
        exitCodeListHeading = "Exit Codes:%n",
        exitCodeList = {
                " 0:Successful program execution",
                " 1:Execution exception",
                " 2:Invalid input"
        },
        sortSynopsis = false,
        sortOptions = false,
        versionProvider = VersionInfo.class,
        footerHeading = "Examples",
        showDefaultValues = true,
        footer = {
                """
                            ./naksha-cli copy \\
                              --srcStorageConfig gen.json \\
                              --srcMapId "srcmapid" \\
                              --srcCollectionId "srccolid" \\
                              --targetStorageConfig psql.json \\
                              --targetMapId "targetmapid" \\
                              --targetCollectionId "targetcolid" \\
                              --autoCreateTarget
                        
                          Basic PsqlStorage's config:
                              {
                                "id": "psql_storage",
                                "type": "Storage",
                                "create": true,
                                "upgrade": true,
                                "className": "naksha.psql.PsqlStorage",
                                "master": {
                                  "host": "0.0.0.0",
                                  "database": "postgres",
                                  "port": "5432",
                                  "user": "postgres",
                                  "password": "password",
                                  "readOnly": false
                                }
                              }
                        
                          Basic GeneratingStorage's config:
                              {
                                "id": "test_generating_storage",
                                "className": "com.here.naksha.cli.storages.GeneratingStorage",
                                "properties": {
                                  "featureTemplateFile": "./sample_topology_feature.json",
                                  "count": 40000,
                                  "tileIdsCsvFile": "./tile_ids.csv",
                                  "idsPrefix": "gen"
                                }
                              }
                        """
        }
)
public final class CopyCommand implements Callable<Integer> {
    private final CopyServiceFactory copyServiceFactory;
    private final JsonFileParser jsonFileParser;
    private final StorageProvider storageProvider;

    @CommandLine.Spec
    private CommandLine.Model.CommandSpec commandSpec;

    @CommandLine.Option(
            names = {"--srcStorageConfig"},
            description = "Path to file with source storage config.",
            required = true
    )
    private Path srcStorageConfig;

    @CommandLine.Option(
            names = {"--srcMapId"},
            description = "Id of source map."
    )
    private @Nullable String srcMapId;

    @CommandLine.Option(
            names = {"--srcCollectionId"},
            description = "Id of source collection."
    )
    private @Nullable String srcCollectionId;

    @CommandLine.Option(
            names = {"--targetStorageConfig"},
            description = "Path to file with target storage config.",
            required = true
    )
    private Path targetStorageConfig;

    @CommandLine.Option(
            names = {"--targetMapId"},
            description = "Id of target map."
    )
    private @Nullable String targetMapId;

    @CommandLine.Option(
            names = {"--targetCollectionId"},
            description = "Id of target collection."
    )
    private @Nullable String targetCollectionId;

    @CommandLine.Option(
            names = {"--autoCreateTarget"},
            description = "Auto create target's map and collection."
    )
    private boolean autoCreateTarget = false;

    @CommandLine.Option(
            names = {"--featuresWriteExecutor"},
            description = {
                    "Valid values:",
                    "${COMPLETION-CANDIDATES}"
            }
    )
    private WriteMode featuresWriteExecutor = WriteMode.PARALLEL;

    @CommandLine.Option(
            names = {"--threads"},
            description = {
                    "Positive integer.",
                    "Number of threads in the pool."
            }
    )
    private void setThreads(Integer threads) {
        requirePositiveIntegerOrNull(threads, "--threads");
        this.threads = threads;
    }

    private @Nullable Integer threads;

    @CommandLine.Option(
            names = {"--queueMulti"},
            description = {
                    "Positive integer.",
                    "Sets the multiplier used to calculate the size of the executor's task queue.",
                    "The queue size is computed as: threads * queueMulti."
            }
    )
    private void setQueueMulti(Integer queueMulti) {
        requirePositiveIntegerOrNull(queueMulti, "--queueMulti");
        this.queueMulti = queueMulti;
    }

    private @Nullable Integer queueMulti;

    @CommandLine.Option(
            names = {"--maxBatchSize"},
            description = {
                    "Positive integer.",
                    "Max number of features in the batch."
            }
    )
    private void setMaxBatchSize(Integer maxBatchSize) {
        requirePositiveIntegerOrNull(maxBatchSize, "--maxBatchSize");
        this.maxBatchSize = maxBatchSize;
    }

    private @Nullable Integer maxBatchSize;

    @CommandLine.Mixin
    private LoggingMixin loggingMixin;

    public CopyCommand(
            @NotNull CopyServiceFactory copyServiceFactory,
            @NotNull StorageProvider storageProvider
    ) {
        this.copyServiceFactory = copyServiceFactory;
        this.jsonFileParser = new JsonFileParser();
        this.storageProvider = storageProvider;
    }

    @Override
    public Integer call() throws JsonFileParserException, CopyServiceException {
        CopyElement srcCopyElement = buildSrcCopyElement();
        CopyElement targetCopyElement = buildTargetCopyElement();
        NakshaContext.currentContext().withAppId("nakshacli");
        SessionOptions sessionOptions = SessionOptions.from(NakshaContext.currentContext());
        CommandResult<CopyServiceSuccessResultPayload, CopyServiceException> copyResult = copy(
                srcCopyElement,
                targetCopyElement,
                sessionOptions
        );
        CopyServiceSuccessResultPayload resultPayload = requireSuccessResultAndGetPayload(copyResult);

        PrintWriter commandLineOut = getCommandLineOut();
        String successMessage = buildCopySuccessMessage(srcCopyElement, targetCopyElement, resultPayload);
        commandLineOut.println(successMessage);

        return CommandLine.ExitCode.OK;
    }

    private PrintWriter getCommandLineOut() {
        CommandLine commandLine = commandSpec.commandLine();
        return commandLine.getOut();
    }

    private String buildCopySuccessMessage(
            CopyElement src,
            CopyElement target,
            CopyServiceSuccessResultPayload resultPayload
    ) {
        return "Success! Copied %d features from %s to %s.".formatted(
                resultPayload.numberOfCopiedElements(),
                src,
                target
        );
    }

    private CopyServiceSuccessResultPayload requireSuccessResultAndGetPayload(
            CommandResult<CopyServiceSuccessResultPayload, CopyServiceException> copyResult
    ) throws CopyServiceException {
        return switch (copyResult) {
            case CommandFailure(CopyServiceException exception) -> throw exception;
            case CommandSuccess(CopyServiceSuccessResultPayload payload) -> payload;
        };
    }

    private CopyElement buildSrcCopyElement() throws JsonFileParserException {
        NakshaStorage srcNakshaStorage = loadStorage(srcStorageConfig);
        return new CopyElement.Builder(srcNakshaStorage)
                .setMapId(srcMapId)
                .setCollectionId(srcCollectionId)
                .build();
    }

    private CopyElement buildTargetCopyElement() throws JsonFileParserException {
        NakshaStorage targetNakshaStorage = loadStorage(targetStorageConfig);
        return new CopyElement.Builder(targetNakshaStorage)
                .setMapId(targetMapId)
                .setCollectionId(targetCollectionId)
                .build();
    }

    private CommandResult<CopyServiceSuccessResultPayload, CopyServiceException> copy(
            CopyElement srcCopyElement,
            CopyElement targetCopyElement,
            SessionOptions sessionOptions
    ) {
        CopyService copyService = copyServiceFactory.create(
                storageProvider,
                sessionOptions,
                featuresWriteExecutor,
                threads,
                queueMulti,
                maxBatchSize
        );

        return copyService.copy(
                srcCopyElement,
                targetCopyElement,
                autoCreateTarget
        );
    }

    private NakshaStorage loadStorage(Path storageConfig) throws JsonFileParserException {
        return jsonFileParser.parse(storageConfig, NakshaStorage.class);
    }

    private void requirePositiveIntegerOrNull(
            Integer value,
            String optionName
    ) {
        if (value == null) {
            return;
        }
        if (value <= 0) {
            throw new CommandLine.ParameterException(
                    commandSpec.commandLine(),
                    "Invalid value '%s' for option '%s': value should be a positive integer".formatted(value, optionName)
            );
        }
    }
}
