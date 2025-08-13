package com.here.naksha.cli.testcontainers;

import naksha.base.Platform;
import naksha.model.IStorage;
import naksha.model.Naksha;
import naksha.model.objects.NakshaStorage;

import static java.util.Objects.requireNonNull;

/**
 * A singleton pool containing two instances of {@link TestContainersPsqlStorage}.
 */
public final class TestContainersPsqlStoragePool {
    public static final NakshaStorage TEST_STORAGE_1 = requireNonNull(Platform.fromJson("""
    {
      "id": "naksha_cli_test_storage_1",
      "className": "naksha.psql.PsqlTestStorage",
      "port": 15432
}""", NakshaStorage.TYPE));
    public static final NakshaStorage TEST_STORAGE_2 = requireNonNull(Platform.fromJson("""
    {
      "id": "naksha_cli_test_storage_2",
      "className": "naksha.psql.PsqlTestStorage",
      "port": 25432
    }""", NakshaStorage.TYPE));
    public static final IStorage TEST_STORAGE_1_API = Naksha.useStorage(TEST_STORAGE_1);
    public static final IStorage TEST_STORAGE_2_API = Naksha.useStorage(TEST_STORAGE_2);

//    public static TestContainersPsqlStorage getRunningContainer(InstanceIndex instanceIndex) {
//        List<TestContainersPsqlStorage> testContainersPsqlStorages = PsqlStoragePoolHolder.POOL;
//        return testContainersPsqlStorages.get(instanceIndex.getIndex());
//    }
//
//    public enum InstanceIndex {
//        FIRST_INSTANCE(0),
//        SECOND_INSTANCE(1);
//
//        private final int index;
//
//        InstanceIndex(int index) {
//            this.index = index;
//        }
//
//        public int getIndex() {
//            return index;
//        }
//    }
//
//    private TestContainersPsqlStoragePool() {
//    }
//
//    private static final class PsqlStoragePoolHolder {
//        private static final List<TestContainersPsqlStorage> POOL = List.of(
//                new TestContainersPsqlStorage(),
//                new TestContainersPsqlStorage()
//        );
//
//        static {
//            POOL.stream()
//                    .parallel()
//                    .forEach(TestContainersPsqlStorage::start);
//        }
//
//        static {
//            Runtime.getRuntime().addShutdownHook(
//                    new Thread(
//                            () -> POOL.stream()
//                                    .parallel()
//                                    .forEach(TestContainersPsqlStorage::stop)
//                    )
//            );
//        }
//    }
}
