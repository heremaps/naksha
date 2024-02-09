package com.here.naksha.handler.activitylog;

import static com.here.naksha.handler.activitylog.ActivityHistoryRequestTransformUtilTest.TranslationExpectation.Builder.query;
import static com.here.naksha.lib.core.models.storage.POp.and;
import static com.here.naksha.lib.core.models.storage.POp.eq;
import static com.here.naksha.lib.core.models.storage.POp.or;
import static com.here.naksha.lib.core.models.storage.PRef.activityLogId;
import static com.here.naksha.lib.core.models.storage.PRef.author;
import static com.here.naksha.lib.core.models.storage.PRef.id;
import static com.here.naksha.lib.core.models.storage.PRef.txn;
import static com.here.naksha.lib.core.models.storage.PRef.uuid;
import static java.util.function.Function.identity;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Named.named;

import com.here.naksha.lib.core.models.storage.POp;
import com.here.naksha.lib.core.models.storage.PRef;
import com.here.naksha.lib.core.models.storage.ReadFeatures;
import java.util.function.Function;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class ActivityHistoryRequestTransformUtilTest {

  @ParameterizedTest
  @MethodSource("translationSamples")
  void shouldTranslatePropertyOperation(TranslationExpectation translationExpectation) {
    // Given:
    ReadFeatures propertyBasedQuery = new ReadFeatures().withPropertyOp(translationExpectation.originalQuery);

    // When
    ActivityHistoryRequestTransformUtil.translateIdPropertyToFeatureUuid(propertyBasedQuery);

    // Then
    assertEquals(translationExpectation.queryAfterTranslation, propertyBasedQuery.getPropertyOp());
  }

  private static Stream<Named<TranslationExpectation>> translationSamples() {
    return Stream.of(
        generateSamples(id(), uuid()),
        generateSamples(activityLogId(), id()),
        generateMixedSamples(
            id(), uuid(),
            activityLogId(), id()
        )
    ).flatMap(identity());
  }

  private static Stream<Named<TranslationExpectation>> generateSamples(PRef sourceProperty, PRef targetProperty) {
    String sourcePath = pathString(sourceProperty);
    String targetPath = pathString(targetProperty);
    return Stream.of(
        named("Single query:'%s=sample_id' => '%s=sample_id'".formatted(sourcePath, targetPath),
            query(eq(sourceProperty, "sample_id"))
                .shouldBeTranslatedTo(eq(targetProperty, "sample_id"))),
        named("Multiple query: '%s=id_1,%s=id_2' => '%s=id_1,%s=id_2'".formatted(sourcePath, sourcePath, targetPath, targetPath),
            query(or(eq(sourceProperty, "id_1"), eq(sourceProperty, "id_2")))
                .shouldBeTranslatedTo(or(eq(targetProperty, "id_1"), eq(targetProperty, "id_2")))),
        named(
            "Single query with additional property: '%s=sample_id&props.ns.author=john_doe' => '%s=sample_id&props.ns.author=john_doe')".formatted(
                sourcePath, targetProperty),
            query(and(eq(sourceProperty, "sample_id"), eq(author(), "john_doe")))
                .shouldBeTranslatedTo(or(eq(targetProperty, "id_1"), eq(author(), "john_doe")))),
        named(
            "Multiple query with additional property: '%s=id_1,%s=id_2&props.ns.txn=txn)' => '%s=id_1,%s=id_2&props.ns.txn=txn'".formatted(
                sourcePath, sourcePath, targetPath, targetProperty),
            query(and(or(eq(sourceProperty, "id_1"), eq(sourceProperty, "id_2")), eq(txn(), "txn_1")))
                .shouldBeTranslatedTo(and(or(eq(targetProperty, "id_1"), eq(targetProperty, "id_2")), eq(txn(), "txn_1")))
        )
    );
  }

  private static Stream<Named<TranslationExpectation>> generateMixedSamples(
      PRef firstSourceProperty, PRef firstTargetProperty,
      PRef secondSourceProperty, PRef secondTargetProperty
  ) {
    return Stream.of(
        named("Mixed OR query: '%s=foo OR %s=bar' => '%s=foo OR %s=bar'".formatted(
                pathString(firstSourceProperty), pathString(secondSourceProperty),
                pathString(firstTargetProperty), pathString(secondTargetProperty)
            ),
            query(or(eq(firstSourceProperty, "foo"), eq(secondSourceProperty, "bar")))
                .shouldBeTranslatedTo(or(eq(firstTargetProperty, "foo"), eq(secondTargetProperty, "bar")))
        ),
        named("Mixed AND query: '%s=foo AND %s=bar' => '%s=foo AND %s=bar'".formatted(
                pathString(firstSourceProperty), pathString(secondSourceProperty),
                pathString(firstTargetProperty), pathString(secondTargetProperty)
            ),
            query(and(eq(firstSourceProperty, "foo"), eq(secondSourceProperty, "bar")))
                .shouldBeTranslatedTo(and(eq(firstTargetProperty, "foo"), eq(secondTargetProperty, "bar")))
        )
    );
  }

  private static String pathString(PRef pRef) {
    return String.join(".", pRef.getPath());
  }

  record TranslationExpectation(POp originalQuery, POp queryAfterTranslation) {

    static class Builder {

      POp originalQuery;

      static Builder query(POp originalQuery) {
        Builder builder = new Builder();
        builder.originalQuery = originalQuery;
        return builder;
      }

      TranslationExpectation shouldBeTranslatedTo(@NotNull POp queryAfterTranslation) {
        assert originalQuery != null : "Original query is null, use 'query' method before";
        return new TranslationExpectation(originalQuery, queryAfterTranslation);
      }
    }
  }
}