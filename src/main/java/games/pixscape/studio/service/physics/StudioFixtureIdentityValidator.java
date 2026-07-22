package games.pixscape.studio.service.physics;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.World;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.utils.IntIntMap;
import com.badlogic.gdx.utils.IntSet;
import games.pixscape.runtime.component.SpatialBlockData;
import games.pixscape.runtime.component.SpatialBlocksComponent;
import games.pixscape.runtime.component.physics.FixtureDefData;
import games.pixscape.runtime.component.physics.PhysicsFixturesComponent;
import games.pixscape.runtime.loading.FixtureIdentityValidator;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.studio.component.physics.AuthoredPolygonData;
import games.pixscape.studio.component.physics.PhysicsAuthoringComponent;

/** Validates fixture ownership references that only exist in Studio authoring state. */
public final class StudioFixtureIdentityValidator {
    private StudioFixtureIdentityValidator() {
    }

    public static void validate(World world, SceneMetaRuntime meta, String sceneLabel) {
        FixtureIdentityValidator.validate(world, meta, sceneLabel);
        String scene = sceneLabel != null ? sceneLabel
                : meta != null && meta.name != null ? meta.name : "<unnamed>";

        IntIntMap fixtureBodies = collectFixtureBodies(world);
        IntSet claims = collectSpatialClaims(world);
        ComponentMapper<PhysicsAuthoringComponent> mAuthoring =
                world.getMapper(PhysicsAuthoringComponent.class);
        IntBag entities = world.getAspectSubscriptionManager()
                .get(Aspect.all(PhysicsAuthoringComponent.class)).getEntities();
        int[] data = entities.getData();
        for (int i = 0; i < entities.size(); i++) {
            int body = data[i];
            PhysicsAuthoringComponent authoring = mAuthoring.get(body);
            if (authoring == null || authoring.polygons == null) continue;
            for (int polygonIndex = 0; polygonIndex < authoring.polygons.size; polygonIndex++) {
                AuthoredPolygonData polygon = authoring.polygons.get(polygonIndex);
                if (polygon == null || polygon.generatedFixtureIds == null) continue;
                for (int fixtureId : polygon.generatedFixtureIds) {
                    if (fixtureId <= 0) {
                        fail(scene, body, fixtureId,
                                "authored polygon has an invalid generated fixture reference; authoringId="
                                        + polygon.authoringId);
                    }
                    if (fixtureBodies.get(fixtureId, -1) != body) {
                        fail(scene, body, fixtureId,
                                "authored polygon fixture is missing from its body; authoringId="
                                        + polygon.authoringId);
                    }
                    if (!claims.add(fixtureId)) {
                        fail(scene, body, fixtureId,
                                "fixture is claimed by multiple authored associations; authoringId="
                                        + polygon.authoringId);
                    }
                }
            }
        }
    }

    private static IntIntMap collectFixtureBodies(World world) {
        IntIntMap fixtureBodies = new IntIntMap();
        ComponentMapper<PhysicsFixturesComponent> mapper =
                world.getMapper(PhysicsFixturesComponent.class);
        IntBag entities = world.getAspectSubscriptionManager()
                .get(Aspect.all(PhysicsFixturesComponent.class)).getEntities();
        int[] data = entities.getData();
        for (int i = 0; i < entities.size(); i++) {
            int body = data[i];
            PhysicsFixturesComponent fixtures = mapper.get(body);
            if (fixtures == null || fixtures.fixtures == null) continue;
            for (FixtureDefData fixture : fixtures.fixtures) {
                if (fixture != null) fixtureBodies.put(fixture.fixtureId, body);
            }
        }
        return fixtureBodies;
    }

    private static IntSet collectSpatialClaims(World world) {
        IntSet claims = new IntSet();
        ComponentMapper<SpatialBlocksComponent> mapper = world.getMapper(SpatialBlocksComponent.class);
        IntBag entities = world.getAspectSubscriptionManager()
                .get(Aspect.all(SpatialBlocksComponent.class)).getEntities();
        int[] data = entities.getData();
        for (int i = 0; i < entities.size(); i++) {
            SpatialBlocksComponent blocks = mapper.get(data[i]);
            if (blocks == null || blocks.blocks == null) continue;
            for (SpatialBlockData block : blocks.blocks) {
                if (block != null && block.physicsCollision) claims.add(block.fixtureId);
            }
        }
        return claims;
    }

    private static void fail(String scene, int body, int fixtureId, String reason) {
        throw new IllegalStateException(
                "Invalid Studio fixture identity state: scene=" + scene + ", body=" + body
                        + ", fixtureId=" + fixtureId + ", reason=" + reason);
    }
}
