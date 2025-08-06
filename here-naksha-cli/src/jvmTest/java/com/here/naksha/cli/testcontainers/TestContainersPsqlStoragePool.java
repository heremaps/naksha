package com.here.naksha.cli.testcontainers;

import java.util.List;

/**
 * A singleton pool containing two instances of {@link TestContainersPsqlStorage}.
 */
public final class TestContainersPsqlStoragePool {
    public static TestContainersPsqlStorage getRunningContainer(InstanceIndex instanceIndex) {
        List<TestContainersPsqlStorage> testContainersPsqlStorages = PsqlStoragePoolHolder.POOL;
        return testContainersPsqlStorages.get(instanceIndex.getIndex());
    }

    public enum InstanceIndex {
        FIRST_INSTANCE(0),
        SECOND_INSTANCE(1);

        private final int index;

        InstanceIndex(int index) {
            this.index = index;
        }

        public int getIndex() {
            return index;
        }
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
