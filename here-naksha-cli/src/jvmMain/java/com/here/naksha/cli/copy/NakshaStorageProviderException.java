package com.here.naksha.cli.copy;

import java.io.File;

final public class NakshaStorageProviderException extends Exception {
    NakshaStorageProviderException(String message, File file, Throwable cause) {
        super(message + " file: " + file.getPath(), cause);
    }

    NakshaStorageProviderException(String message, File file) {
        super(message + " file: " + file.getPath());
    }
}
