package games.pixscape.studio.service.prefab;

import com.artemis.ComponentMapper;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.artemis.io.JsonArtemisSerializer;
import com.artemis.io.SaveFileFormat;
import com.artemis.managers.WorldSerializationManager;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.IntIntMap;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.component.RenderMaterialComponent;
import games.pixscape.runtime.component.TextureRegionComponent;
import games.pixscape.runtime.component.physics.PhysicsGearJointComponent;
import games.pixscape.runtime.component.physics.PhysicsJointComponent;
import games.pixscape.runtime.prefab.PrefabAsset;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.studio.history.initializer.GenericEntityInitializer;
import games.pixscape.studio.history.initializer.GenericEntitySnapshotData;
import games.pixscape.studio.service.entitygraph.EntityGraph;
import games.pixscape.studio.service.entitygraph.EntityGraphEntry;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

public final class PrefabAssetService {
    private static final String PREFAB_TYPE = "pixscape-prefab";
    private static final int PREFAB_VERSION = 1;

    private final World world;
    private final Json json;
    private final PrefabEntityDataMapper mapper = new PrefabEntityDataMapper();

    public PrefabAssetService(World world) {
        this.world = world;
        this.json = createJson();
    }

    private static Json createJson() {
        Json json = new Json();
        json.setOutputType(JsonWriter.OutputType.json);
        json.setIgnoreUnknownFields(true);

        // Always write default-valued fields such as:
        // type = "pixscape-prefab"
        // version = 1
        json.setUsePrototypes(false);

        return json;
    }

    public void savePrefab(FileHandle file, String name, EntityGraph graph) {
        if (file == null) throw new IllegalArgumentException("Prefab file is required");
        if (graph == null) throw new IllegalArgumentException("EntityGraph is required");
        if (file.parent() != null) file.parent().mkdirs();

        PrefabAsset asset = new PrefabAsset();
        asset.name = name;

        for (EntityGraphEntry entry : graph.entries()) {
            asset.entities.add(mapper.fromGraphEntry(entry));
        }

        file.writeString(json.prettyPrint(asset), false, "UTF-8");

        try {
            FileHandle fragmentFile = file.sibling(file.nameWithoutExtension() + ".pixfragment.json");
            saveRuntimeFragment(fragmentFile, graph);
        } catch (Exception ex) {
            System.err.println("[Prefab] Failed to write runtime fragment: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    public void saveRuntimeFragment(FileHandle file, EntityGraph graph) {
        if (file == null) throw new IllegalArgumentException("Fragment file is required");
        if (graph == null) throw new IllegalArgumentException("EntityGraph is required");
        if (file.parent() != null) file.parent().mkdirs();

        World tempWorld = new World(new WorldConfigurationBuilder()
                .with(new WorldSerializationManager(), new DirtyTrackerSystem(1024))
                .build());

        WorldSerializationManager wsm = tempWorld.getSystem(WorldSerializationManager.class);
        wsm.setSerializer(new JsonArtemisSerializer(tempWorld));

        IntIntMap sourceToTemp = new IntIntMap();
        IntArray created = new IntArray();

        for (EntityGraphEntry entry : graph.entries()) {
            int eid = tempWorld.create();
            sourceToTemp.put(entry.sourceEntityId(), eid);
            created.add(eid);
        }

        for (EntityGraphEntry entry : graph.entries()) {
            int eid = sourceToTemp.get(entry.sourceEntityId(), -1);

            GenericEntitySnapshotData snapshot = entry.initializer().toSnapshotData(entry.sourceEntityId());
            GenericEntityInitializer init = new GenericEntityInitializer(tempWorld).applySnapshotData(snapshot);
            init.init(eid);
            copyResolvedRenderState(world, entry.sourceEntityId(), tempWorld, eid);

            PixscapeIdentityComponent id = tempWorld.getMapper(PixscapeIdentityComponent.class).getSafe(eid, null);

            if (id == null) {
                id = tempWorld.getMapper(PixscapeIdentityComponent.class).create(eid);
            }

            id.stableId = IdentityRegistry.UNASSIGNED_STABLE_ID;

            PhysicsJointComponent joint = tempWorld.getMapper(PhysicsJointComponent.class).getSafe(eid, null);

            if (id.name == null || id.name.isBlank()) {
                id.name = (joint != null) ? "prefab_joint" : "prefab_entity";
            }
        }
        remapRuntimeFragmentJointReferences(tempWorld, created, sourceToTemp);

        tempWorld.process();

        SaveFileFormat request = new SaveFileFormat();
        for (int i = 0; i < created.size; i++) {
            request.entities.add(created.get(i));
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        wsm.save(out, request);

        file.writeBytes(out.toByteArray(), false);
    }

    private static void copyResolvedRenderState(
            World sourceWorld,
            int sourceEid,
            World targetWorld,
            int targetEid
    ) {
        TextureRegionComponent srcTr = sourceWorld.getMapper(TextureRegionComponent.class).getSafe(sourceEid, null);
        RenderMaterialComponent srcMat = sourceWorld.getMapper(RenderMaterialComponent.class).getSafe(sourceEid, null);

        if (srcTr != null) {
            TextureRegionComponent dstTr = targetWorld.getMapper(TextureRegionComponent.class).getSafe(targetEid, null);
            if (dstTr == null) {
                dstTr = targetWorld.getMapper(TextureRegionComponent.class).create(targetEid);
            }

            dstTr.u1 = srcTr.u1;
            dstTr.v1 = srcTr.v1;
            dstTr.u2 = srcTr.u2;
            dstTr.v2 = srcTr.v2;
            dstTr.pixW = srcTr.pixW;
            dstTr.pixH = srcTr.pixH;
            dstTr.valid = srcTr.valid;
        }

        if (srcMat != null) {
            RenderMaterialComponent dstMat =
                    targetWorld.getMapper(RenderMaterialComponent.class).getSafe(targetEid, null);
            if (dstMat == null) {
                dstMat = targetWorld.getMapper(RenderMaterialComponent.class).create(targetEid);
            }

            dstMat.shaderIdx = srcMat.shaderIdx;
            dstMat.blendModeId = srcMat.blendModeId;
            dstMat.textureHandle = srcMat.textureHandle;
            dstMat.debugAtlasTag = srcMat.debugAtlasTag;
        }
    }

    private static void remapRuntimeFragmentJointReferences(
            World world,
            IntArray created,
            IntIntMap sourceToTemp
    ) {
        ComponentMapper<PhysicsJointComponent> mJoint = world.getMapper(PhysicsJointComponent.class);
        ComponentMapper<PhysicsGearJointComponent> mGear = world.getMapper(PhysicsGearJointComponent.class);

        for (int i = 0; i < created.size; i++) {
            int eid = created.get(i);

            PhysicsJointComponent joint = mJoint.getSafe(eid, null);
            if (joint != null) {
                joint.aEid = remapRequired(sourceToTemp, joint.aEid, "joint.aEid");
                joint.bEid = remapRequired(sourceToTemp, joint.bEid, "joint.bEid");
            }

            PhysicsGearJointComponent gear = mGear.getSafe(eid, null);
            if (gear != null) {
                gear.joint1Eid = remapRequired(sourceToTemp, gear.joint1Eid, "gear.joint1Eid");
                gear.joint2Eid = remapRequired(sourceToTemp, gear.joint2Eid, "gear.joint2Eid");
            }
        }
    }

    private static int remapRequired(IntIntMap map, int sourceId, String field) {
        int mapped = map.get(sourceId, -1);
        if (mapped < 0) {
            throw new IllegalStateException("Cannot remap " + field + " source entity " + sourceId);
        }
        return mapped;
    }

    public EntityGraph loadPrefab(FileHandle file) {
        if (file == null) throw new IllegalArgumentException("Prefab file is required");
        if (!file.exists()) throw new IllegalArgumentException("Prefab file does not exist: " + file.path());

        PrefabAsset asset = json.fromJson(PrefabAsset.class, file.readString("UTF-8"));
        validate(asset, file);

        List<EntityGraphEntry> entries = new ArrayList<>();
        for (PrefabAsset.PrefabEntityData data : asset.entities) {
            entries.add(mapper.toGraphEntry(world, data));
        }

        return new EntityGraph(entries);
    }

    private void validate(PrefabAsset asset, FileHandle file) {
        if (asset == null) {
            throw new IllegalArgumentException("Invalid prefab JSON: " + file.path());
        }

        if (!PREFAB_TYPE.equals(asset.type)) {
            throw new IllegalArgumentException("Invalid prefab type: " + asset.type);
        }

        if (asset.version != PREFAB_VERSION) {
            throw new IllegalArgumentException("Unsupported prefab version: " + asset.version);
        }

        if (asset.entities == null) {
            asset.entities = new ArrayList<>();
        }
    }
}