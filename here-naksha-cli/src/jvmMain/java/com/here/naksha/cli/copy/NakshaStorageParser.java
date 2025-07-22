package com.here.naksha.cli.copy;

import naksha.base.JvmBoxingUtil;
import naksha.base.Platform;
import naksha.model.objects.NakshaStorage;

import java.io.File;
import java.nio.file.Files;

final class NakshaStorageParser {
    NakshaStorage get(File file) throws NakshaStorageParserException {
        if (!file.exists()) {
            throw new NakshaStorageParserException("File does not exist!", file);
        }

        if (!file.isFile()) {
            throw new NakshaStorageParserException("It is not a file!", file);
        }

        String json;

        try {
            json = Files.readString(file.toPath());
        } catch (Exception e) {
            throw new NakshaStorageParserException("Problem with reading!", file);
        }

        try {
            Object rawConfig = Platform.fromJSON(json);
            return JvmBoxingUtil.box(rawConfig, NakshaStorage.class);
        } catch (Exception e) {
            throw new NakshaStorageParserException("Problem with json parsing!", file, e);
        }
    }
}
