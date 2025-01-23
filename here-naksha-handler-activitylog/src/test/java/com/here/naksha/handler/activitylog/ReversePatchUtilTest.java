package com.here.naksha.handler.activitylog;

import static com.here.naksha.handler.activitylog.ReversePatch.PatchOp.ADD;
import static com.here.naksha.handler.activitylog.ReversePatch.PatchOp.REMOVE;
import static com.here.naksha.handler.activitylog.ReversePatch.PatchOp.REPLACE;
import static com.here.naksha.handler.activitylog.assertions.ReversePatchAssertions.assertThat;

import com.here.naksha.handler.activitylog.ReversePatch.PatchOp;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import naksha.geo.LineStringCoord;
import naksha.geo.PointCoord;
import naksha.geo.SpLineString;
import naksha.model.XyzNs;
import naksha.model.objects.NakshaFeature;
import naksha.model.objects.NakshaProperties;
import net.bytebuddy.utility.RandomString;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ReversePatchUtilTest {

  private final Random random = new Random();

  // TODO: parameterized test against task samples

  @Test
  void shouldConvertDifferenceToReversePatch() {
    // Given: feature representing John some time ago
    NakshaFeature before = xyzFeature(Map.of(
        "op", "John",
        "age", 23,
        "address", Map.of(
            "city", "Funkytown",
            "street", "Sesame Street",
            "number", 79
        ),
        "studentDetails", Map.of(
            "university", "Some fancy school",
            "studentId", 1234
        ),
        "contactDetails", List.of(
            Map.of(
                "type", "phone",
                "value", "123456789"
            ),
            Map.of(
                "type", "email",
                "value", "john@email.com"
            )
        )
    ));

    // And: feature representing John year later (he moved a couple of blocks away, finished studies, found a job and changed his contact details)
    NakshaFeature after = xyzFeature(Map.of(
        "op", "John",
        "age", 24,
        "address", Map.of(
            "city", "Funkytown",
            "street", "Sesame Street",
            "number", 87
        ),
        "occupation", "teacher",
        "contactDetails", List.of(
            Map.of(
                "type", "phone",
                "value", "987654321"
            )
        )
    ));

    // When: reverse patch is calculated (answers: "what needs to be done to get back in time?")
    ReversePatch reversePatch = ReversePatchUtil.reversePatch(before, after);

    // Then: calculated reverse patch instructs what needs to be done to get to previopus state
    assertThat(reversePatch)
        .hasRemoveOpsCount(1) // we added 'occupation'
        .hasUpdateOpsCount(3) // we changed 'age', 'address/number' and phone number ('contactDetails[0]/value')
        .hasAddOpsCount(2) // we removed 'studentDetails' and email ('contactDetails[1]')
        .hasReverseOps(
            new PatchOp(REPLACE, "/properties/age", 23), // previous age was 30
            new PatchOp(REPLACE, "/properties/address/number", 79), // previous address/number was 79
            new PatchOp(REMOVE, "/properties/occupation", null), // previously there was no occupation
            new PatchOp(ADD, "/properties/studentDetails", Map.of( // previously there was some student data
                "university", "Some fancy school",
                "studentId", 1234
            )),
            new PatchOp(REPLACE, "/properties/contactDetails/0/value", "123456789"), // previous number was 123456789
            new PatchOp(ADD, "/properties/contactDetails/1", Map.of( // previously there was an email
                "type", "email",
                "value", "john@email.com"
            ))
        );
  }

  @Test
  void shouldIgnoreChangedId() {
    // Given: feature A
    NakshaFeature featureA = new NakshaFeature("id_a");

    // And: feature B
    NakshaFeature featureB = new NakshaFeature("id_b");

    // When: applying reverse patch calculation on these two
    ReversePatch reversePatch = ReversePatchUtil.reversePatch(featureA, featureB);

    // Then: resulting patch is null
    Assertions.assertNull(reversePatch);
  }

  @Test
  void shouldIgnoreXyzNamespaceButNotTags() {
    // Given: feature with tags
    XyzNs oldXyzNamespace = generateRandomXyzNamespace()
        .addTags(List.of("one", "two", "three"), true);
    NakshaFeature oldFeature = featureWithXyzNamespace(oldXyzNamespace);

    // And: feature with different XyzNamespace (including different tags)
    XyzNs newXyzNamespace = generateRandomXyzNamespace()
        .addTags(List.of("two", "three", "four", "five"), true);
    NakshaFeature newFeature = featureWithXyzNamespace(newXyzNamespace);

    // When: applying reverse patch calculation on these two
    ReversePatch reversePatch = ReversePatchUtil.reversePatch(oldFeature, newFeature);

    // Then: resulting patch has only 'tags' related changes (even though other XyzNamespace properties changed)
    assertThat(reversePatch)
        .hasRemoveOpsCount(1) // added 4th element to `tags`
        .hasAddOpsCount(0) // we did not remove anything ('tags' list got bigger)
        .hasUpdateOpsCount(3) // 'tags' at given ind changed: 'one' => 'two', 'two' => 'three', 'three' => 'four'
        .hasReverseOps(
            new PatchOp(REPLACE, "/properties/@ns:com:here:xyz/tags/0", "one"),
            new PatchOp(REPLACE, "/properties/@ns:com:here:xyz/tags/1", "two"),
            new PatchOp(REPLACE, "/properties/@ns:com:here:xyz/tags/2", "three"),
            new PatchOp(REMOVE, "/properties/@ns:com:here:xyz/tags/3", null)
        );
  }

  @Test
  void shouldCorrectlyParseLineGeometryChange(){
    // Given
    NakshaFeature oldFeature = new NakshaFeature();
    LineStringCoord oldCoordinates = new LineStringCoord();
    oldCoordinates.add(new PointCoord(4.0d, 5.0));
    oldCoordinates.add(new PointCoord(4.0d, 6.0));
    oldCoordinates.add(new PointCoord(4.0d, 7.0));
    oldCoordinates.add(new PointCoord(4.0d, 8.0));
    oldFeature.setGeometry(new SpLineString().withCoordinates(oldCoordinates));

    // And:
    NakshaFeature newFeature = new NakshaFeature();
    LineStringCoord newCoordinates = new LineStringCoord();
    newCoordinates.add(new PointCoord(4.0d, 5.0));
    newCoordinates.add(new PointCoord(4.0d, 8.0));
    newCoordinates.add(new PointCoord(4.0d, 7.0));
    newFeature.setGeometry(new SpLineString().withCoordinates(newCoordinates));

    // When: applying reverse patch calculation on these two
    ReversePatch reversePatch = ReversePatchUtil.reversePatch(oldFeature, newFeature);

    // Then:
    assertThat(reversePatch)
        .hasRemoveOpsCount(0)
        .hasAddOpsCount(1) // we did remove last coordinate
        .hasUpdateOpsCount(1) // we did update second coordinate
        .hasReverseOps(
            new PatchOp(REPLACE, "/geometry/coordinates/1/1", 6.0),
            new PatchOp(ADD, "/geometry/coordinates/3", new PointCoord(4.0d, 8.0d))
        );
  }

  private NakshaFeature featureWithXyzNamespace(XyzNs xyzNamespace) {
    NakshaFeature feature = new NakshaFeature();
    NakshaProperties properties = new NakshaProperties();
    properties.setXyz(xyzNamespace);
    feature.setProperties(properties);
    return feature;
  }

  private XyzNs generateRandomXyzNamespace() {
    final XyzNs xyzNamespace = new XyzNs();
    xyzNamespace.put("appId",RandomString.make(10));
    xyzNamespace.put("author",RandomString.make(10));
    xyzNamespace.put("txn",random.nextLong());
    xyzNamespace.put("uuid",UUID.randomUUID().toString());
    xyzNamespace.put("puuid",UUID.randomUUID().toString());
        return xyzNamespace;
  }

  private NakshaFeature xyzFeature(Map<String, Object> props) {
    NakshaFeature feature = new NakshaFeature();
    NakshaProperties properties = new NakshaProperties();
    properties.putAll(props);
    feature.setProperties(properties);
    return feature;
  }
}