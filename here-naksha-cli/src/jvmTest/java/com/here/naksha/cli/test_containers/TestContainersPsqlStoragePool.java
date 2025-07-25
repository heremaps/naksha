package com.here.naksha.cli.test_containers;

import java.util.List;

/**
 * A singleton pool containing two instances of {@link TestContainersPsqlStorage}.
 */
public final class TestContainersPsqlStoragePool {
    /**
     * Returns a {@link StorageController} instance for the storage at the specified index.
     *
     * @param index the index of the storage instance (either {@code 0} or {@code 1})
     * @return a new {@link StorageController} instance associated with the specified storage
     */
    public static StorageController getInstance(int index) {
        return PsqlStoragePoolHolder.POOL.get(index).getStorageController();
    }

    private TestContainersPsqlStoragePool() {
    }

    private static final class PsqlStoragePoolHolder {
        private static final List<TestContainersPsqlStorage> POOL = List.of(
                new TestContainersPsqlStorage(),
                new TestContainersPsqlStorage()
        );

        static {
            POOL.stream()
                    .parallel()
                    .forEach(TestContainersPsqlStorage::start);
        }

        static {
            Runtime.getRuntime().addShutdownHook(
                    new Thread(
                            () -> POOL.stream()
                                    .parallel()
                                    .forEach(TestContainersPsqlStorage::stop)
                    )
            );
        }
    }
}
