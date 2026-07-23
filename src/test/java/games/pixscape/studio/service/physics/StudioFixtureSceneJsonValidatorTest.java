package games.pixscape.studio.service.physics;

import com.badlogic.gdx.files.FileHandle;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class StudioFixtureSceneJsonValidatorTest {
    @Test
    public void acceptsIndexedAuthoredPartFixtureMapping() throws Exception {
        validate(scene("[7]", "[{\"count\":3,\"verts\":[0,0,1,0,0,1]}]", "[0,0,1,0,0,1]"));
    }

    @Test
    public void rejectsAuthoredPartFixtureCardinalityMismatchWithContext() throws Exception {
        assertInvalid(scene("[7]", "[]", "[0,0,1,0,0,1]"), "partIndex=-1");
        assertInvalid(scene("[7]", "[]", "[0,0,1,0,0,1]"), "authoringId=42");
    }

    @Test
    public void rejectsInsufficientGeneratedFixtureIds() throws Exception {
        String parts = "[{\"count\":3,\"verts\":[0,0,1,0,0,1]},"
                + "{\"count\":3,\"verts\":[2,0,3,0,2,1]}]";
        assertInvalid(twoFixtureScene("[7]", parts, false), "cardinality mismatch");
    }

    @Test
    public void rejectsExcessGeneratedFixtureIds() throws Exception {
        assertInvalid(twoFixtureScene("[7,8]",
                "[{\"count\":3,\"verts\":[0,0,1,0,0,1]}]", false),
                "cardinality mismatch");
    }

    @Test
    public void rejectsDuplicateGeneratedFixtureInsidePolygon() throws Exception {
        String parts = "[{\"count\":3,\"verts\":[0,0,1,0,0,1]},"
                + "{\"count\":3,\"verts\":[0,0,1,0,0,1]}]";
        assertInvalid(twoFixtureScene("[7,7]", parts, false),
                "duplicated inside the polygon");
    }

    @Test
    public void rejectsAuthoredPartFixtureGeometryMismatchWithIndex() throws Exception {
        assertInvalid(scene("[7]", "[{\"count\":3,\"verts\":[0,0,2,0,0,1]}]",
                "[0,0,1,0,0,1]"), "partIndex=0");
        assertInvalid(scene("[7]", "[{\"count\":3,\"verts\":[0,0,2,0,0,1]}]",
                "[0,0,1,0,0,1]"), "geometry does not match");
    }

    @Test
    public void rejectsMissingFixtureAndWrongPartOrder() throws Exception {
        String parts = "[{\"count\":3,\"verts\":[0,0,1,0,0,1]},"
                + "{\"count\":3,\"verts\":[2,0,3,0,2,1]}]";
        assertInvalid(scene("[7,8]", parts, "[0,0,1,0,0,1]"),
                "missing from its body");
        assertInvalid(twoFixtureScene("[8,7]", parts, false), "same index");
    }

    @Test
    public void rejectsFixtureClaimedByAuthoredPolygonAndSpatialBlock() throws Exception {
        assertInvalid(twoFixtureScene("[7]",
                "[{\"count\":3,\"verts\":[0,0,1,0,0,1]}]", true),
                "claimed by multiple");
    }

    private static String scene(String generatedIds, String parts, String fixtureVerts) {
        return "{\"entities\":{\"3\":{\"components\":{"
                + "\"PhysicsFixturesComponent\":{\"fixtures\":[{\"fixtureId\":7,"
                + "\"shapeType\":2,\"polyCount\":3,\"polyVerts\":" + fixtureVerts + "}]},"
                + "\"PhysicsAuthoringComponent\":{\"polygons\":[{\"authoringId\":42,"
                + "\"generatedFixtureIds\":" + generatedIds + ",\"convexParts\":" + parts
                + "}]}}}}}";
    }

    private static String twoFixtureScene(String generatedIds, String parts,
                                          boolean blockClaimsFirst) {
        String blocks = blockClaimsFirst
                ? ",\"SpatialBlocksComponent\":{\"blocks\":[{\"id\":3,"
                + "\"physicsCollision\":true,\"fixtureId\":7}]}"
                : "";
        return "{\"entities\":{\"3\":{\"components\":{"
                + "\"PhysicsFixturesComponent\":{\"fixtures\":["
                + "{\"fixtureId\":7,\"shapeType\":2,\"polyCount\":3,"
                + "\"polyVerts\":[0,0,1,0,0,1]},"
                + "{\"fixtureId\":8,\"shapeType\":2,\"polyCount\":3,"
                + "\"polyVerts\":[2,0,3,0,2,1]}]},"
                + "\"PhysicsAuthoringComponent\":{\"polygons\":[{\"authoringId\":42,"
                + "\"generatedFixtureIds\":" + generatedIds + ",\"convexParts\":" + parts
                + "}]}" + blocks + "}}}}";
    }

    private static void validate(String json) throws Exception {
        Path path = Files.createTempFile("studio-fixture-json-validator", ".json");
        Files.writeString(path, json, StandardCharsets.UTF_8);
        SceneMetaRuntime meta = new SceneMetaRuntime();
        meta.nextFixtureId = 100;
        StudioFixtureSceneJsonValidator.validate(new FileHandle(path.toFile()), meta, "json-scene");
    }

    private static void assertInvalid(String json, String expected) throws Exception {
        try {
            validate(json);
            Assert.fail("Expected Studio JSON fixture validation to fail");
        } catch (IllegalStateException failure) {
            Assert.assertTrue(failure.getMessage(), failure.getMessage().contains(expected));
        }
    }
}
