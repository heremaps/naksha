package naksha.psql;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.LongSummaryStatistics;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import naksha.model.NakshaException;
import naksha.model.TupleNumber;
import naksha.model.TupleNumberVariant;
import naksha.model.Version;
import naksha.model.objects.NakshaCollection;
import naksha.model.objects.NakshaFeature;
import naksha.model.objects.NakshaFeatureList;
import naksha.model.objects.NakshaStorage;
import naksha.model.request.ErrorResponse;
import naksha.model.request.ReadFeatures;
import naksha.model.request.Response;
import naksha.model.request.SuccessResponse;
import naksha.model.request.Write;
import naksha.model.request.WriteRequest;
import naksha.model.request.query.AnyOp;
import naksha.model.request.query.MetaColumn;
import naksha.model.request.query.MetaQuery;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

//@EnabledIf("shouldRunLoadTest")
class HistoryLoadTest extends PgTestBase {

  private static final String TEST_FLAG = "RUN_HISTORY_LOAD_TEST";
  private static final String COLLECTION_ID = "um-mod-dev:activity_history_test_collection_regular";
  private static final String MAP_ID = "naksha_data_schema";

  private static final int FEATURE_COUNT = 6_000;
  private static final int VERSIONS_PER_FEATURE = 10;
  private static final int WRITE_OPS_PER_REQUEST = 500;
  private static final int HISTORY_ENTRIES_PER_READ_REQUEST = 100;

  HistoryLoadTest() {
    super(new NakshaCollection(COLLECTION_ID, MAP_ID), MAP_ID);
  }

  static boolean shouldRunLoadTest() {
    String flagValue = System.getenv(TEST_FLAG);
    return flagValue != null && flagValue.equalsIgnoreCase("true");
  }

  @Test
  void testHistoryLoad() {
    // Given: DB with {FEATURE_COUNT} features in HEAD and {FEATURE_COUNT * (VERSIONS_PER_FEATURE - 1)} features in HISTORY
    Map<String, TupleNumber> midTnsById = populate();

//    // When
//    LongSummaryStatistics stats = measureHistoryReads(1, midTnsById);
//
//    // Then
//    System.out.printf("Count: %d%n", stats.getCount());
//    System.out.printf("Avg:   %.3f ms%n", stats.getAverage() / 1_000_000);
//    System.out.printf("Min:   %.3f ms%n", stats.getMin() / 1_000_000.0);
//    System.out.printf("Max:   %.3f ms%n", stats.getMax() / 1_000_000.0);
//    System.out.printf("Total: %.3f ms%n", stats.getSum() / 1_000_000.0);
  }

  private LongSummaryStatistics measureHistoryReads(int entriesAtOnce, Map<String, TupleNumber> midTnsById) {
    ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    List<Entry<String, TupleNumber>> tns = midTnsById.entrySet().stream().collect(Collectors.toList());
    Map<String, String> fidsToTnB64s = tns.stream().collect(Collectors.toMap(
        e -> e.getKey(),
        e -> Base64.getEncoder().encodeToString(e.getValue().toByteArray(TupleNumberVariant.B96))
    ));
    List<Callable<Long>> tasks = new ArrayList<>();
    long requestCount = FEATURE_COUNT / entriesAtOnce;
    for (int i = 0; i < requestCount; i++) {
      int firstIncl = i * entriesAtOnce;
      int lastExcl = firstIncl + entriesAtOnce;
      ReadFeatures request = requestPredecessorsOf(tns.subList(firstIncl, lastExcl));
      tasks.add(() -> {
        long start = System.nanoTime();
        Response readResp = executeRead(request);
        long execTime = System.nanoTime() - start;
        // check if what happened is legit
        if(readResp instanceof SuccessResponse) {
          NakshaFeatureList features = ((SuccessResponse) readResp).getFeatures();
          Assertions.assertFalse(features.isEmpty());
          features.forEach(fetchedPredecessor -> {
            Version predecessorVer = fetchedPredecessor.getTupleNumber().version;
            Version successorVer = midTnsById.get(fetchedPredecessor.getId()).version;
            System.out.println("Predecessor: " + predecessorVer.txn.toLong() + ", successor: " + successorVer.txn.toLong());
            Assertions.assertTrue(predecessorVer.txn.toLong() < successorVer.txn.toLong());
          });
        }

        // return data point for stats
        return execTime;
      });
    }

    LongSummaryStatistics stats = null;
    try {
      List<Future<Long>> futures = executor.invokeAll(tasks);
      stats = futures.stream()
          .map(f -> {
            try {
              return f.get();
            } catch (Exception e) {
              throw new RuntimeException(e);
            }
          })
          .collect(Collectors.summarizingLong(Long::longValue));
    } catch (InterruptedException e) {
      e.printStackTrace();
    } finally {
      executor.shutdown();
    }
    return stats;
  }

  /*
   see: com.here.naksha.handler.activitylog.ActivityLogHandler#requestPredecessorsOf(java.util.List)
   */
  private ReadFeatures requestPredecessorsOf(List<Entry<String, TupleNumber>> tupleNumbers) {
    // we will compare against `next_tn` which is encodded with 96-bit encoding
    byte[][] b96tns = new byte[tupleNumbers.size()][];
    for (int i = 0; i < tupleNumbers.size(); i++) {
      b96tns[i] = tupleNumbers.get(i).getValue().toByteArray(TupleNumberVariant.B96);
    }
    MetaQuery nuidQuery = new MetaQuery(MetaColumn.nextVersion(), AnyOp.IS_ANY_OF, b96tns);
    ReadFeatures requestPredecessors = new ReadFeatures();
    requestPredecessors.setMapId(getCollection().getMapId());
    requestPredecessors.addCollectionIds(getCollection().getId());
    requestPredecessors.setQueryHistory(true);
    requestPredecessors.getQuery().setMetadata(nuidQuery);
    return requestPredecessors;
  }

  private Map<String, TupleNumber> populate() {
    int workersCount = FEATURE_COUNT / WRITE_OPS_PER_REQUEST;
    List<FeaturePopulator> populators = new ArrayList<>(workersCount);
    ConcurrentHashMap<String, TupleNumber> lastTnsById = new ConcurrentHashMap<>(FEATURE_COUNT);
    for (int i = 0; i < workersCount; i++) {
      populators.add(new FeaturePopulator(i * WRITE_OPS_PER_REQUEST, lastTnsById));
    }
    ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    try {
      List<Future<Response>> futures = executor.invokeAll(populators);
      for (Future<Response> future : futures) {
        Response resp = future.get();
        if (resp instanceof ErrorResponse) {
          throw new NakshaException(((ErrorResponse) resp).getError());
        }
      }
    } catch (InterruptedException | ExecutionException e) {
      // handle exceptions
    } finally {
      executor.shutdown();
    }
    return lastTnsById;
  }

  class FeaturePopulator implements Callable<Response> {

    private final int firstIdNum;
    private final Map<String, TupleNumber> midTnsById;

    public FeaturePopulator(int firstIdNum, Map<String, TupleNumber> lastTnsById) {
      this.firstIdNum = firstIdNum;
      this.midTnsById = lastTnsById;
    }

    public Map<String, TupleNumber> getMidTnsById() {
      return midTnsById;
    }

    @Override
    public Response call() throws Exception {
      Response createResp = create();
      if (createResp instanceof ErrorResponse) {
        return createResp;
      }
      Response updateResp = null;
      // there are {versions-1} updates, so start from 1
      for (int updateAttempt = 1; updateAttempt < VERSIONS_PER_FEATURE; updateAttempt++) {
        updateResp = update(updateAttempt);
        if (updateResp instanceof ErrorResponse) {
          return updateResp;
        }
        // last tn == first version, attempts start from 1
        if (updateAttempt == VERSIONS_PER_FEATURE / 2 && updateResp instanceof SuccessResponse) {
          updateMidTns((SuccessResponse) updateResp);
        }
      }
      return updateResp;
    }

    private void updateMidTns(SuccessResponse sr) {
      sr.getFeatures().forEach(updatedFeature -> {
        midTnsById.put(updatedFeature.getId(), updatedFeature.getTupleNumber());
      });
    }

    private Response create() {
      WriteRequest wr = new WriteRequest();
      for (int i = 0; i < WRITE_OPS_PER_REQUEST; i++) {
        String featureId = "feature_lt_" + (firstIdNum + i);
        NakshaFeature feature = new NakshaFeature(featureId);
        feature.setTitle("Initial state of feature: " + featureId);
        wr.add(new Write().createFeature(getCollection(), feature));
      }
      return simpleExecute(wr);
    }

    private Response update(int updateIteration) {
      WriteRequest wr = new WriteRequest();
      for (int i = 0; i < WRITE_OPS_PER_REQUEST; i++) {
        String featureId = "feature_lt_" + (firstIdNum + i);
        NakshaFeature feature = new NakshaFeature(featureId);
        feature.setTitle("Updated state of feature: " + featureId + ", update count: " + updateIteration);
        wr.add(new Write().upsertFeature(getCollection(), feature));
      }
      return simpleExecute(wr);
    }
  }

  private Response simpleExecute(WriteRequest wr) {
    return storage.useWriteSession(newSessionOptions(), writer -> {
      Response resp = writer.execute(wr);
      if (resp instanceof ErrorResponse) {
        writer.rollback();
      } else {
        writer.commit();
      }
      return resp;
    });
  }
}
