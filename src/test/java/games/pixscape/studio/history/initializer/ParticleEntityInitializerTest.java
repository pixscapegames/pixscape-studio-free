package games.pixscape.studio.history.initializer;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import games.pixscape.runtime.component.AABBComponent;
import games.pixscape.runtime.component.AssetRefComponent;
import games.pixscape.runtime.component.DimensionsComponent;
import games.pixscape.runtime.component.OrientedBoundsComponent;
import games.pixscape.runtime.component.ParticleEmitterComponent;
import games.pixscape.runtime.component.RenderMaterialComponent;
import games.pixscape.runtime.component.TextureRegionComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.studio.component.EntityMetaComponent;
import games.pixscape.studio.model.EntityKind;
import org.junit.Test;

import static org.junit.Assert.*;

public class ParticleEntityInitializerTest {

    @Test
    public void newParticleUsesOnlyPointTransformComposition() {
        World world = new World(new WorldConfiguration());
        int entity = world.create();

        new GenericEntityInitializer(world)
                .configureParticleEmitter("fire.p", "main", 12f, -8f, 3, "Fire")
                .init(entity);

        TransformComponent transform = world.getMapper(TransformComponent.class).get(entity);
        assertEquals(12f, transform.x, 0f);
        assertEquals(-8f, transform.y, 0f);
        assertEquals(0f, transform.originX, 0f);
        assertEquals(0f, transform.originY, 0f);
        assertEquals(0f, transform.rotationRad, 0f);
        assertEquals(1f, transform.scaleX, 0f);
        assertEquals(1f, transform.scaleY, 0f);
        assertTrue(world.getMapper(ParticleEmitterComponent.class).has(entity));
        assertEquals(EntityKind.PARTICLE,
                world.getMapper(EntityMetaComponent.class).get(entity).kind);
        assertProxyFree(world, entity);
    }

    @Test
    public void duplicationDropsLegacyParticleProxiesButDeletionSnapshotRestoresThem() {
        World world = new World(new WorldConfiguration());
        int source = world.create();
        TransformComponent sourceTransform = world.getMapper(TransformComponent.class).create(source);
        sourceTransform.x = 4f;
        sourceTransform.y = 5f;
        world.getMapper(ParticleEmitterComponent.class).create(source).effectPath = "legacy.p";
        world.getMapper(DimensionsComponent.class).create(source);
        world.getMapper(AABBComponent.class).create(source);
        world.getMapper(OrientedBoundsComponent.class).create(source);

        GenericEntityInitializer historicalSnapshot = new GenericEntityInitializer(world);
        historicalSnapshot.syncFrom(source);

        int restored = world.create();
        historicalSnapshot.init(restored);
        assertTrue(world.getMapper(DimensionsComponent.class).has(restored));
        assertTrue(world.getMapper(AABBComponent.class).has(restored));
        assertTrue(world.getMapper(OrientedBoundsComponent.class).has(restored));

        int duplicated = world.create();
        historicalSnapshot.duplicate().init(duplicated);
        assertTrue(world.getMapper(ParticleEmitterComponent.class).has(duplicated));
        assertEquals("legacy.p",
                world.getMapper(ParticleEmitterComponent.class).get(duplicated).effectPath);
        assertProxyFree(world, duplicated);
    }

    private static void assertProxyFree(World world, int entity) {
        assertFalse(world.getMapper(DimensionsComponent.class).has(entity));
        assertFalse(world.getMapper(AABBComponent.class).has(entity));
        assertFalse(world.getMapper(OrientedBoundsComponent.class).has(entity));
        assertFalse(world.getMapper(AssetRefComponent.class).has(entity));
        assertFalse(world.getMapper(TextureRegionComponent.class).has(entity));
        assertFalse(world.getMapper(RenderMaterialComponent.class).has(entity));
    }
}
