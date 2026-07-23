package games.pixscape.studio.service.physics;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.IntIntMap;
import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.IntSet;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import games.pixscape.runtime.component.physics.FixtureDefData;
import games.pixscape.runtime.loading.SceneMetaRuntime;

/** Strict read-only fixture identity validation for scene files before runtime export. */
public final class StudioFixtureSceneJsonValidator {
    private static final float GEOMETRY_EPSILON = 1e-6f;

    private StudioFixtureSceneJsonValidator() {
    }

    public static void validate(FileHandle sceneFile, SceneMetaRuntime meta, String sceneLabel) {
        String scene = sceneLabel != null ? sceneLabel : sceneFile != null ? sceneFile.path() : "<unnamed>";
        if (sceneFile == null || !sceneFile.exists()) fail(scene, "scene file is missing");
        if (meta == null) fail(scene, "scene metadata is missing");
        if (meta.nextFixtureId <= 0) fail(scene, "nextFixtureId must be strictly positive");

        JsonValue root;
        try {
            root = new JsonReader().parse(sceneFile);
        } catch (RuntimeException ex) {
            throw new IllegalStateException("Invalid fixture identity state in scene=" + scene
                    + ": scene JSON cannot be parsed", ex);
        }
        JsonValue entities = root != null ? root.get("entities") : null;
        if (entities == null) return;

        IntIntMap fixtureBodies = new IntIntMap();
        IntMap<JsonValue> fixtureDefs = new IntMap<>();
        int maxFixtureId = 0;
        for (JsonValue entity = entities.child; entity != null; entity = entity.next) {
            int body = entityId(entity);
            JsonValue components = entity.get("components");
            JsonValue fixtureArray = child(components, "PhysicsFixturesComponent", "fixtures");
            if (fixtureArray == null) continue;
            for (JsonValue fixture = fixtureArray.child; fixture != null; fixture = fixture.next) {
                int fixtureId = fixture.getInt("fixtureId", 0);
                if (fixtureId <= 0) fail(scene, body, fixtureId, "fixtureId must be strictly positive");
                if (fixtureBodies.containsKey(fixtureId)) {
                    fail(scene, body, fixtureId,
                            "duplicate fixtureId; firstBody=" + fixtureBodies.get(fixtureId, -1));
                }
                fixtureBodies.put(fixtureId, body);
                fixtureDefs.put(fixtureId, fixture);
                maxFixtureId = Math.max(maxFixtureId, fixtureId);
            }
        }
        if (meta.nextFixtureId <= maxFixtureId) {
            fail(scene, -1, maxFixtureId,
                    "nextFixtureId=" + meta.nextFixtureId + " must be greater than every fixtureId");
        }

        IntSet claims = new IntSet();
        for (JsonValue entity = entities.child; entity != null; entity = entity.next) {
            int body = entityId(entity);
            JsonValue components = entity.get("components");
            JsonValue blocks = child(components, "SpatialBlocksComponent", "blocks");
            if (blocks != null) {
                for (JsonValue block = blocks.child; block != null; block = block.next) {
                    boolean collision = block.getBoolean("physicsCollision", false);
                    int fixtureId = block.getInt("fixtureId", 0);
                    if (!collision && fixtureId != 0) {
                        fail(scene, body, fixtureId, "non-collision spatial block owns a fixture");
                    }
                    if (collision) claim(scene, fixtureBodies, claims, body, fixtureId, "spatial block");
                }
            }

            JsonValue polygons = child(components, "PhysicsAuthoringComponent", "polygons");
            if (polygons == null) continue;
            int polygonIndex = 0;
            for (JsonValue polygon = polygons.child; polygon != null;
                 polygon = polygon.next, polygonIndex++) {
                long authoringId = polygon.getLong("authoringId", 0L);
                JsonValue generated = polygon.get("generatedFixtureIds");
                JsonValue parts = polygon.get("convexParts");
                int idCount = generated != null ? generated.size : 0;
                int partCount = parts != null ? parts.size : 0;
                if (idCount != partCount) {
                    failPolygon(scene, body, 0, authoringId, polygonIndex, -1,
                            "convexParts/generatedFixtureIds cardinality mismatch; parts="
                                    + partCount + ", ids=" + idCount);
                }
                IntSet polygonClaims = new IntSet();
                for (int partIndex = 0; partIndex < idCount; partIndex++) {
                    int fixtureId = generated.get(partIndex).asInt();
                    if (!polygonClaims.add(fixtureId)) {
                        failPolygon(scene, body, fixtureId, authoringId, polygonIndex, partIndex,
                                "generated fixture reference is duplicated inside the polygon");
                    }
                    claim(scene, fixtureBodies, claims, body, fixtureId,
                            "authored polygon authoringId=" + authoringId
                                    + ", polygonIndex=" + polygonIndex
                                    + ", partIndex=" + partIndex);
                    String mismatch = geometryMismatch(parts.get(partIndex), fixtureDefs.get(fixtureId));
                    if (mismatch != null) {
                        failPolygon(scene, body, fixtureId, authoringId, polygonIndex, partIndex,
                                "fixture geometry does not match convex part at the same index: "
                                        + mismatch);
                    }
                }
            }
        }
    }

    private static String geometryMismatch(JsonValue part, JsonValue fixture) {
        if (part == null) return "convex part is missing";
        if (fixture == null) return "fixture is missing";
        int shapeType = fixture.getInt("shapeType", -1);
        if (shapeType != FixtureDefData.SHAPE_POLYGON) {
            return "fixture shapeType=" + shapeType + " is not polygon";
        }
        int partCount = part.getInt("count", 0);
        int fixtureCount = fixture.getInt("polyCount", 0);
        if (partCount != fixtureCount) {
            return "vertexCount part=" + partCount + ", fixture=" + fixtureCount;
        }
        JsonValue partVerts = part.get("verts");
        JsonValue fixtureVerts = fixture.get("polyVerts");
        int length = Math.max(0, partCount) * 2;
        if (partVerts == null || partVerts.size < length) return "convex part vertex array is incomplete";
        if (fixtureVerts == null || fixtureVerts.size < length) return "fixture vertex array is incomplete";
        for (int i = 0; i < length; i++) {
            float partValue = partVerts.get(i).asFloat();
            float fixtureValue = fixtureVerts.get(i).asFloat();
            if (Math.abs(partValue - fixtureValue) > GEOMETRY_EPSILON) {
                return "coordinate=" + i + ", part=" + partValue + ", fixture=" + fixtureValue;
            }
        }
        return null;
    }

    private static void claim(String scene, IntIntMap fixtureBodies, IntSet claims,
                              int body, int fixtureId, String owner) {
        if (fixtureId <= 0) fail(scene, body, fixtureId, owner + " has an invalid fixture reference");
        if (fixtureBodies.get(fixtureId, -1) != body) {
            fail(scene, body, fixtureId, owner + " fixture is missing from its body");
        }
        if (!claims.add(fixtureId)) {
            fail(scene, body, fixtureId, "fixture is claimed by multiple authored associations");
        }
    }

    private static JsonValue child(JsonValue components, String component, String field) {
        JsonValue value = components != null ? components.get(component) : null;
        return value != null ? value.get(field) : null;
    }

    private static int entityId(JsonValue entity) {
        try {
            return entity.name != null ? Integer.parseInt(entity.name) : -1;
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static void fail(String scene, String reason) {
        throw new IllegalStateException("Invalid fixture identity state in scene=" + scene + ": " + reason);
    }

    private static void fail(String scene, int body, int fixtureId, String reason) {
        throw new IllegalStateException("Invalid fixture identity state in scene=" + scene
                + ", body=" + body + ", fixtureId=" + fixtureId + ": " + reason);
    }

    private static void failPolygon(String scene, int body, int fixtureId,
                                    long authoringId, int polygonIndex, int partIndex,
                                    String reason) {
        fail(scene, body, fixtureId,
                "authoringId=" + authoringId + ", polygonIndex=" + polygonIndex
                        + ", partIndex=" + partIndex + ", " + reason);
    }
}
