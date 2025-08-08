package com.here.naksha.cli.copy;

import com.here.naksha.cli.copy.service.*;
import com.here.naksha.cli.parsers.JsonFileParser;
import com.here.naksha.cli.parsers.JsonFileParserException;
import com.here.naksha.cli.results.ErrorResult;
import com.here.naksha.cli.results.IResult;
import com.here.naksha.cli.results.SuccessResult;
import naksha.model.NakshaContext;
import naksha.model.SessionOptions;
import naksha.model.objects.NakshaStorage;
import org.jetbrains.annotations.Nullable;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "copy",
        mixinStandardHelpOptions = true,
        description = "Copy data between storages.",
        exitCodeListHeading = "Exit Codes:%n",
        exitCodeList = {
                " 0:Successful program execution",
                " 1:Execution exception",
                " 2:Invalid input"
        },
        sortSynopsis = false,
        sortOptions = false
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

    public CopyCommand(
            CopyServiceFactory copyServiceFactory,
            StorageProvider storageProvider
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

        IResult<CopyServiceSuccessResultPayload, CopyServiceException> copyResult = copy(
                srcCopyElement,
                targetCopyElement,
                sessionOptions
        );

        CopyServiceSuccessResultPayload resultPayload = requireSuccessResultAndGetPayload(copyResult);

        PrintWriter commandLineOut = getCommandLineOut();
        String successMessage = buildCopySuccessMessage(resultPayload);
        commandLineOut.println(successMessage);

        return CommandLine.ExitCode.OK;
    }

    private PrintWriter getCommandLineOut() {
        CommandLine commandLine = commandSpec.commandLine();
        return commandLine.getOut();
    }

    private String buildCopySuccessMessage(CopyServiceSuccessResultPayload resultPayload) {
        return "Success! Copied %d features.".formatted(
                resultPayload.numberOfCopiedElements()
        );
    }

    private CopyServiceSuccessResultPayload requireSuccessResultAndGetPayload(
            IResult<CopyServiceSuccessResultPayload, CopyServiceException> copyResult
    ) throws CopyServiceException {
        return switch (copyResult) {
            case ErrorResult(CopyServiceException exception) -> throw exception;
            case SuccessResult(CopyServiceSuccessResultPayload payload) -> payload;
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

    private IResult<CopyServiceSuccessResultPayload, CopyServiceException> copy(
            CopyElement srcCopyElement,
            CopyElement targetCopyElement,
            SessionOptions sessionOptions
    ) {
        CopyService copyService = copyServiceFactory.create(
                storageProvider,
                sessionOptions
        );

        return copyService.copy(
                srcCopyElement,
                targetCopyElement
        );
    }

    private NakshaStorage loadStorage(Path storageConfig) throws JsonFileParserException {
        return jsonFileParser.parse(storageConfig, NakshaStorage.class);
    }
}
