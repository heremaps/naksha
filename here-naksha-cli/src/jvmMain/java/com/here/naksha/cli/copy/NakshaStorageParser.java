package com.here.naksha.cli.copy;

import naksha.base.Platform;
import naksha.base.PlatformObject;
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
            Object rawConfig = Platform.fromJson(json);
            assert rawConfig instanceof PlatformObject;
            return NakshaStorage.TYPE.proxy((PlatformObject) rawConfig);
        } catch (Exception e) {
            throw new NakshaStorageParserException("Problem with json parsing!", file, e);
        }
    }
}
