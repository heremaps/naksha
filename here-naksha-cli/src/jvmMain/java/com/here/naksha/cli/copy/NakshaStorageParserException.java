package com.here.naksha.cli.copy;

import java.io.File;

final public class NakshaStorageParserException extends Exception {
    NakshaStorageParserException(String message, File file, Throwable cause) {
        super(message + " file: " + file.getPath(), cause);
    }

    NakshaStorageParserException(String message, File file) {
        super(message + " file: " + file.getPath());
    }
}
