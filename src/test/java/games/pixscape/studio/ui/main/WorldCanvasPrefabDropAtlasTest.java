package games.pixscape.studio.ui.main;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import games.pixscape.runtime.component.AnimationComponent;
import games.pixscape.runtime.component.AssetRefComponent;
import games.pixscape.studio.history.initializer.GenericEntityInitializer;
import games.pixscape.studio.service.entitygraph.EntityGraph;
import games.pixscape.studio.service.entitygraph.EntityGraphEntry;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class WorldCanvasPrefabDropAtlasTest {

    @Test
    public void ensurePrefabRenderAssetsInSceneAtlas_collectsDistinctValidAssetIds_andReportsChange() {
        World world = new World(new WorldConfiguration());

        List<EntityGraphEntry> entries = new ArrayList<>();
        entries.add(entryWithAsset(world, 100, 11));
        entries.add(entryWithAsset(world, 101, 11)); // duplicate
        entries.add(entryWithAsset(world, 102, 17));
        entries.add(entryWithAsset(world, 103, -1)); // invalid
        entries.add(entryWithoutAsset(world, 104));

        EntityGraph graph = new EntityGraph(entries);

        Set<Integer> ensured = new HashSet<>();
        boolean changed = WorldCanvas.ensurePrefabRenderAssetsInSceneAtlas(graph, assetId -> {
            ensured.add(assetId);
            return assetId == 17;
        });

        Assert.assertTrue(changed);
        Assert.assertEquals(Set.of(11, 17), ensured);
    }

    @Test
    public void ensurePrefabRenderAssetsInSceneAtlas_returnsFalse_whenNothingValid() {
        World world = new World(new WorldConfiguration());
        EntityGraph graph = new EntityGraph(List.of(entryWithAsset(world, 200, 0), entryWithoutAsset(world, 201)));

        boolean changed = WorldCanvas.ensurePrefabRenderAssetsInSceneAtlas(graph, assetId -> true);

        Assert.assertFalse(changed);
    }

    @Test
    public void ensurePrefabRenderAssetsInSceneAtlas_includesEveryAttachedAnimationOnce() {
        World world = new World(new WorldConfiguration());
        EntityGraph graph = new EntityGraph(List.of(
                entryWithAnimations(world, 300, 11, 11, 17, 23, 17, 0, -9)
        ));
        Map<Integer, Integer> callsByAssetId = new HashMap<>();

        boolean changed = WorldCanvas.ensurePrefabRenderAssetsInSceneAtlas(graph, assetId -> {
            callsByAssetId.merge(assetId, 1, Integer::sum);
            return assetId == 23;
        });

        Assert.assertTrue(changed);
        Assert.assertEquals(Set.of(11, 17, 23), callsByAssetId.keySet());
        Assert.assertEquals(Integer.valueOf(1), callsByAssetId.get(11));
        Assert.assertEquals(Integer.valueOf(1), callsByAssetId.get(17));
        Assert.assertEquals(Integer.valueOf(1), callsByAssetId.get(23));
    }

    @Test
    public void ensurePrefabRenderAssetsInSceneAtlas_reportsNoChangeWhenAllEnsuresAreUnchanged() {
        World world = new World(new WorldConfiguration());
        EntityGraph graph = new EntityGraph(List.of(
                entryWithAnimations(world, 400, 11, 11, 17, 23)
        ));

        boolean changed = WorldCanvas.ensurePrefabRenderAssetsInSceneAtlas(
                graph, assetId -> false);

        Assert.assertFalse(changed);
    }

    private static EntityGraphEntry entryWithAsset(World world, int sourceEntityId, int assetId) {
        GenericEntityInitializer init = new GenericEntityInitializer(world)
                .configureStandaloneSprite(assetId, "main", 32, 32, 0f, 0f, 16f, 16f,
                        0, 0, 0, "x", 0);
        return new EntityGraphEntry(sourceEntityId, init);
    }

    private static EntityGraphEntry entryWithoutAsset(World world, int sourceEntityId) {
        GenericEntityInitializer init = new GenericEntityInitializer(world);
        return new EntityGraphEntry(sourceEntityId, init);
    }

    private static EntityGraphEntry entryWithAnimations(World world,
                                                        int sourceEntityId,
                                                        int activeAssetId,
                                                        int... animationAssetIds) {
        int entityId = world.create();
        AssetRefComponent assetRef = world.getMapper(AssetRefComponent.class).create(entityId);
        assetRef.assetId = activeAssetId;
        AnimationComponent animation = world.getMapper(AnimationComponent.class).create(entityId);
        animation.animationAssetIds.addAll(animationAssetIds);
        animation.currentClip = "idle";
        animation.fps = 12f;
        animation.playing = true;
        animation.loop = true;

        GenericEntityInitializer init = new GenericEntityInitializer(world);
        init.syncFrom(entityId);
        return new EntityGraphEntry(sourceEntityId, init);
    }
}
