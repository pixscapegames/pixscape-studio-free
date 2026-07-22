package games.pixscape.studio.service.physics;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.IntIntMap;
import com.badlogic.gdx.utils.IntSet;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import games.pixscape.runtime.loading.SceneMetaRuntime;

/** Strict read-only fixture identity validation for scene files before runtime export. */
public final class StudioFixtureSceneJsonValidator {
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
            for (JsonValue polygon = polygons.child; polygon != null; polygon = polygon.next) {
                JsonValue generated = polygon.get("generatedFixtureIds");
                if (generated == null) continue;
                for (JsonValue id = generated.child; id != null; id = id.next) {
                    claim(scene, fixtureBodies, claims, body, id.asInt(), "authored polygon");
                }
            }
        }
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
}
