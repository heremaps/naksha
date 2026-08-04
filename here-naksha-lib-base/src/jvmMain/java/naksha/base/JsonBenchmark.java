package naksha.base;

import com.cedarsoftware.io.JsonIo;
import com.cedarsoftware.io.ReadOptions;
import com.cedarsoftware.io.ReadOptionsBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class JsonBenchmark {

  private static final Runtime runtime = Runtime.getRuntime();
  private static final double MIB = 1024.0 * 1024.0;
  private static final double NANOS_TO_SECONDS = 1000.0d * 1000.0d * 1000.0d;
  public interface IParse {
    @Nullable Object parse(byte[] utf8_json) throws Exception;
  }

  public JsonBenchmark(@NotNull String dirName, double memoryQuota) throws IOException {
    final Path dir = Paths.get(dirName); // <-- change this
    final var jsonFiles = new ArrayList<@NotNull Path>();
    try (var files = Files.list(dir)) {
      files.filter(p -> p.toString().endsWith(".json"))
           .sorted(Comparator.comparing(p -> p.getFileName().toString()))
           .forEach(jsonFiles::add);
    }
    json_files = jsonFiles.toArray(Path[]::new);
    json_bytes = new byte[json_files.length][];
    json_object = new Object[json_files.length];
    json_nanos = new long[json_files.length];
    json_size = new long[json_files.length];
    parsers = new HashMap<>();
    parsers.put("naksha_jackson", this::naksha_jackson);
    parsers.put("jackson", this::jackson_parse);
    parsers.put("gson", this::gson_parse);
    parsers.put("jsonio", this::jsonio_parse);
    parsers.put("naksha", this::naksha_parse);
    parsers.put("fastjson", this::fastjson_parse);
    parsers.put("simdjson", this::simdjson_parse);

    // Ensure that
    final long maxMem = runtime.maxMemory();
    long consumedMem = 0L;
    long totalFileSize = 0L;
    int i = 0;
    System.out.println("Load all files into memory (unless memory limit reached)");
    while (i < json_files.length && consumedMem < maxMem) {
      final Path path = json_files[i];
      final var data = Files.readAllBytes(path);
      System.out.printf("\tRead file #%d: %s - %.2f MiB\n", i, path.getFileName(), data.length/MIB);
      json_bytes[i] = data;
      json_size[i] = data.length;
      totalFileSize += data.length;
      consumedMem += Math.round(data.length * memoryQuota);
      i++;
    }
    // Fetch the size of the other files, but not the bytes.
    for (int j=i; j < json_files.length; j++) {
      json_size[j] = Files.size(json_files[j]);
      json_bytes[j] = null;
    }
    this.totalFileSize = totalFileSize;
    this.maxFiles = i;
  }

  public final @NotNull Path @NotNull[] json_files;
  public final byte @NotNull [] @Nullable [] json_bytes;
  public final @Nullable Object @NotNull[] json_object;
  public final long @NotNull[] json_nanos;
  public final long @NotNull[] json_size;
  private final Map<@NotNull String, @NotNull IParse> parsers;
  // The maximum files to use due to memory constraints.
  private final int maxFiles;
  // The total byte-size of the JSON to process.
  private final long totalFileSize;

  @Nullable Object jackson_parse(byte @NotNull [] utf8_json) throws IOException {
    final var jacksonMapper = new ObjectMapper();
    return jacksonMapper.readValue(utf8_json, Map.class);
  }

  @Nullable Object naksha_jackson(byte @NotNull [] utf8_json) throws IOException {
    return Base.objectMapper.get().readValue(utf8_json, Map.class);
  }

  @Nullable Object gson_parse(byte @NotNull [] utf8_json) throws IOException {
    final var gson = new Gson();
    try (final var input = new InputStreamReader(new ByteArrayInputStream(utf8_json), StandardCharsets.UTF_8)) {
      return gson.fromJson(input, Map.class);
    }
  }

  @Nullable Object naksha_parse(byte @NotNull [] utf8_json) throws IOException {
    final var naksha = JsonParser.threadLocal();
    return naksha.parse(utf8_json);
  }

  @Nullable Object jsonio_parse(byte @NotNull [] utf8_json) throws IOException {
    final ReadOptions readOptions = new ReadOptionsBuilder().returnAsJsonObjects().build();
    try (final var input = new ByteArrayInputStream(utf8_json)) {
      return JsonIo.toJava(input, readOptions).asClass(Map.class);
    }
  }

  @Nullable Object fastjson_parse(byte @NotNull [] utf8_json) throws IOException {
    return com.alibaba.fastjson.JSON.parse(utf8_json);
  }

  @Nullable Object simdjson_parse(byte @NotNull [] utf8_json) throws IOException {
    throw new UnsupportedOperationException("As long as we're stuck with JDK 11, this is no option");
    // final var parser = new org.simdjson.SimdJsonParser();
    // return parser.parse(utf8_json, utf8_json.length);
  }

  /// Returns amount of byte currently being allocated.
  private void gc_now() {
    final var weak = new WeakReference<>(new Object());
    System.gc();
    while (weak.get() != null) {
      System.gc();
      try {
        Thread.sleep(100);
      } catch (InterruptedException ignored) {}
    }
  }
  private long gc() {
    // To be really sure, force two GC.
    gc_now();
    gc_now();
    return runtime.totalMemory() -  runtime.freeMemory();
  }

  private void processFile(int i, @NotNull IParse parser) {
    final var path = json_files[i];
    final var data = json_bytes[i];
    try {
      assert data != null;
      System.out.printf("\tProcess file #%d: %s - %.2f MiB\n", i, path.getFileName(), data.length/MIB);
      final long startNanos = System.nanoTime();
      final var object = parser.parse(data);
      final long endNanos = System.nanoTime();
      json_object[i] = object;
      json_nanos[i] = endNanos - startNanos;
    } catch (Exception e) {
      System.err.println("Error reading/parsing " + path + ": " + e.getMessage());
      e.printStackTrace(System.err);
    }
  }

  private static final String SYNTAX = "Usage: JsonBenchmark {dirName=path} {parserName=jackson|naksha_jackson|gson|naksha|fastjson|simdjson} [rounds=10] [memQuota=12.0]";

  public static void main(String[] args) throws Exception {
    final var dirName = args.length > 0 ? args[0] : null;
    final var parserName = args.length > 1 ? args[1] : null;
    final var rounds = args.length > 2 ? args[2] : null;
    final var memQuota = args.length > 3 ? args[3] : null;
    if (dirName == null || parserName == null) {
      System.err.println(SYNTAX);
      return;
    }
    final var dirPath = Paths.get(dirName).toAbsolutePath();
    final var dirFile = dirPath.toFile();
    if (!dirFile.exists()) {
      System.err.println(SYNTAX);
      System.err.println("\tNo directory found: " + dirName);
      System.err.println("\tResolves to absolute path: " + dirPath);
      return;
    }
    int ROUNDS = 10;
    if (rounds != null && !rounds.isEmpty()) {
      try {
        ROUNDS = Integer.parseInt(rounds, 10);
        if (ROUNDS <= 0 || ROUNDS > 100) {
          System.err.println(SYNTAX);
          System.err.println("\tInvalid rounds: " + rounds+", expect a value between 1 and 100");
          return;
        }
      } catch (NumberFormatException e) {
        System.err.println(SYNTAX);
        System.err.println("\tInvalid rounds: " + rounds+", expect a value between 1 and 100");
        e.printStackTrace(System.err);
        return;
      }
    }
    double MEMORY_QUOTA = 12.0;
    if (memQuota != null && !memQuota.isEmpty()) {
      try {
        MEMORY_QUOTA = Double.parseDouble(memQuota);
        if (MEMORY_QUOTA <= 1.0 || MEMORY_QUOTA > 20.0) {
          System.err.println(SYNTAX);
          System.err.println("\tInvalid memQuota: " + memQuota+", expect a value between 1.0 and 20.0");
          return;
        }
      } catch (NumberFormatException e) {
        System.err.println(SYNTAX);
        System.err.println("\tInvalid memQuota: " + memQuota+", expect a value between 1.0 and 20.0");
        e.printStackTrace(System.err);
        return;
      }
    }

    final var benchmark = new JsonBenchmark(dirName, MEMORY_QUOTA);
    final var parser = benchmark.parsers.get(parserName);
    if (parser == null) {
      System.err.println(SYNTAX);
      System.err.println("\tNo parser found for " + parserName);
      return;
    }

    System.out.printf("\n\nProcess %.2f MiB of JSON with memory quota of %.2f\n", benchmark.totalFileSize/MIB, MEMORY_QUOTA);
    final int MAX = benchmark.maxFiles;
    if (MAX < benchmark.json_files.length) {
      System.out.printf("\tNOTE: We only process %d of %d files due to low memory, use -Xmx100g to process all files\n", MAX, benchmark.json_files.length);
    }

    final int WARMUPS = 10;
    System.out.printf("Warmup parser parsing the first file %d times\n", WARMUPS);
    for (int warmup=0; warmup < WARMUPS; warmup++) {
      System.out.printf("Warmup #%d ...\n", warmup);
      benchmark.processFile(0, parser);
    }
    System.out.println("Warmup done, run GC");
    benchmark.json_object[0] = null;
    benchmark.json_nanos[0] = 0L;
    benchmark.gc();
    System.out.println("GC done, run test rounds");

    final long startMem = benchmark.gc();
    System.out.printf("\n\nMemory usage before parsing: %.2f MiB, start %d rounds for each file, count only the last ... \n", startMem/MIB, ROUNDS);
    for (int it=0; it < ROUNDS; it++) {
      System.out.println("Round "+it);
      for (int i = 0; i < MAX; i++) {
        benchmark.processFile(i, parser);
      }
      System.out.println("\n-----------------------------------\n");
    }
    System.out.println("Done, clear JSON bytes, run GC, then measure current HEAP usage");
    Arrays.fill(benchmark.json_bytes, null);
    final long endMem = benchmark.gc();
    long totalNanos = 0;
    for (int i=0; i < benchmark.json_nanos.length; i++) {
      totalNanos += benchmark.json_nanos[i];
    }
    final double seconds = totalNanos/NANOS_TO_SECONDS;
    final double totalMib = benchmark.totalFileSize / MIB;
    final double mibPerSecond = (benchmark.totalFileSize / seconds) / MIB;
    final double sourceToHeapRation = (double)endMem / (double)benchmark.totalFileSize;
    System.out.printf("%s: %.2f seconds, json-size: %.2f MiB, heap-usage: %.2f MiB, %.2f MiB/second, source-to-heap ratio: %.2f\n",
                       parserName, seconds, totalMib, endMem/MIB, mibPerSecond, sourceToHeapRation);
  }
}