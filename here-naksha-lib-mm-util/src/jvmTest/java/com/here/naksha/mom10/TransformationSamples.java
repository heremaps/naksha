package com.here.naksha.mom10;

import static java.nio.file.Files.readAllBytes;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import naksha.base.JvmBoxingUtil;
import naksha.base.Platform;
import naksha.model.objects.NakshaFeature;

public class TransformationSamples {

  private TransformationSamples() {
  }

  public static final class TransformationSample {
    private final String sourceDir;
    private final NakshaFeature mom10;
    private final NakshaFeature nakshaInternal;

    public TransformationSample(
        String sourceDir,
        NakshaFeature mom10,
        NakshaFeature nakshaInternal
    ) {
      this.sourceDir = sourceDir;
      this.mom10 = mom10;
      this.nakshaInternal = nakshaInternal;
    }

    public String getSourceDir() {
      return sourceDir;
    }

    public NakshaFeature getMom10() {
      return mom10;
    }

    public NakshaFeature getNakshaInternal() {
      return nakshaInternal;
    }

    @Override
    public String toString() {
      return "Samples from: '" + sourceDir + "'";
    }
  }

  public static Stream<TransformationSample> streamSamples() {
    return getSamples().stream();
  }

  private static List<TransformationSample> getSamples() {
    // we copy loaded samples to avoid mutations
    List<TransformationSample> samples = new ArrayList<>(SamplesLoader.LOADED_SAMPLES.size());
    for (TransformationSample base : SamplesLoader.LOADED_SAMPLES) {
      samples.add(copy(base));
    }
    return samples;
  }

  private static TransformationSample copy(TransformationSample base) {
    return new TransformationSample(
        base.sourceDir,
        base.mom10.copy(true),
        base.nakshaInternal.copy(true)
    );
  }

  private static class SamplesLoader {

    private static final String TEST_DIR = "/transformation_samples";
    private static final String MOM_10_JSON = "mom_10.json";
    private static final String NAKSHA_INTERNAL_JSON = "naksha_internal.json";

    // initialization on demand
    private static final List<TransformationSample> LOADED_SAMPLES;

    static {
      List<Path> dirs = samplesDirs();
      LOADED_SAMPLES = new ArrayList<>(dirs.size());
      for (Path dir : dirs) {
        LOADED_SAMPLES.add(loadSampleFrom(dir));
      }
    }

    private static List<Path> samplesDirs() {
      try {
        Path testDir = Paths.get(TransformationSamples.class.getResource(TEST_DIR).toURI());
        try (Stream<Path> subdirs = Files.list(testDir)) {
          return subdirs.filter(Files::isDirectory).collect(Collectors.toList()); // not reusing underlying stream as it will get closed
        }
      } catch (URISyntaxException | IOException e) {
        throw new RuntimeException(e);
      }
    }

    private static TransformationSample loadSampleFrom(Path dir) {
      try {
        byte[] rawBefore = readAllBytes(dir.resolve(MOM_10_JSON));
        byte[] rawAfter = readAllBytes(dir.resolve(NAKSHA_INTERNAL_JSON));
        return new TransformationSample(
            dir.getFileName().toString(),
            JvmBoxingUtil.box(Platform.fromJSON(rawBefore), NakshaFeature.class),
            JvmBoxingUtil.box(Platform.fromJSON(rawAfter), NakshaFeature.class)
        );
      } catch (IOException e) {
        throw new RuntimeException("Unable to load sample from: " + dir, e);
      }
    }
  }
}
