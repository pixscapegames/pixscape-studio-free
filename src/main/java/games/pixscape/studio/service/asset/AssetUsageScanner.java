package games.pixscape.studio.service.asset;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.World;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntSet;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import games.pixscape.runtime.component.AssetRefComponent;
import games.pixscape.runtime.component.ParticleEmitterComponent;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.tiled.TileChunk;
import games.pixscape.studio.asset.AssetMeta;
import games.pixscape.studio.asset.AssetMetaDatabase;
import games.pixscape.studio.asset.TileAssetMeta;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.configuration.SceneMeta;
import games.pixscape.studio.io.StudioFs;

import java.io.File;
import java.util.Objects;

public final class AssetUsageScanner {

    private final World world;
    private final ProjectConfig projectConfig;
    private final AssetMetaDatabase assetMetaDatabase;

    private final ComponentMapper<AssetRefComponent> mAssetRef;
    private final ComponentMapper<ParticleEmitterComponent> mParticle;
    private final ComponentMapper<TiledLayerComponent> mTiled;

    public AssetUsageScanner(World world,
                             ProjectConfig projectConfig,
                             AssetMetaDatabase assetMetaDatabase) {
        this.world = Objects.requireNonNull(world, "world");
        this.projectConfig = Objects.requireNonNull(projectConfig, "projectConfig");
        this.assetMetaDatabase = Objects.requireNonNull(assetMetaDatabase, "assetMetaDatabase");

        this.mAssetRef = world.getMapper(AssetRefComponent.class);
        this.mParticle = world.getMapper(ParticleEmitterComponent.class);
        this.mTiled = world.getMapper(TiledLayerComponent.class);
    }

    public AssetUsageReport scanAsset(int assetId) {
        if (assetId <= 0) {
            return AssetUsageReport.empty();
        }

        AssetMeta assetMeta = assetMetaDatabase.findById(assetId);
        if (assetMeta == null) {
            return AssetUsageReport.empty();
        }

        UsageAccumulator acc = new UsageAccumulator();

        scanCurrentLoadedScene(assetMeta, acc);
        scanOtherProjectSceneFiles(assetMeta, acc);

        return acc.toReport();
    }

    public AssetUsageReport scanTileset(int tilesetId) {
        if (tilesetId <= 0) {
            return AssetUsageReport.empty();
        }

        IntSet tileAssetIds = collectTileAssetIdsForTileset(tilesetId);
        if (tileAssetIds.size == 0) {
            return AssetUsageReport.empty();
        }

        UsageAccumulator acc = new UsageAccumulator();

        scanCurrentLoadedSceneTiles(tileAssetIds, acc);
        scanOtherProjectSceneFilesTiles(tileAssetIds, acc);

        return acc.toReport();
    }

    private void scanCurrentLoadedScene(AssetMeta assetMeta, UsageAccumulator acc) {
        String currentSceneName = projectConfig.getCurrentSceneName();
        if (currentSceneName == null || currentSceneName.isBlank()) {
            return;
        }

        boolean found = switch (assetMeta.type) {
            case IMAGE, ANIMATION -> scanCurrentWorldByAssetId(assetMeta.id, acc, currentSceneName);
            case TILE -> scanCurrentWorldTile(assetMeta.id, acc, currentSceneName);
            case PARTICLE -> scanCurrentWorldParticle(assetMeta, acc, currentSceneName);
            case TILESET -> false;
        };

        if (found) {
            acc.referencedInCurrentLoadedScene = true;
        }
    }

    private void scanCurrentLoadedSceneTiles(IntSet tileAssetIds, UsageAccumulator acc) {
        String currentSceneName = projectConfig.getCurrentSceneName();
        if (currentSceneName == null || currentSceneName.isBlank()) {
            return;
        }

        boolean found = scanCurrentWorldTiles(tileAssetIds, acc, currentSceneName);
        if (found) {
            acc.referencedInCurrentLoadedScene = true;
        }
    }

    private void scanOtherProjectSceneFiles(AssetMeta assetMeta, UsageAccumulator acc) {
        FileHandle scenesDir = resolveScenesDir();
        if (scenesDir == null || !scenesDir.exists()) {
            return;
        }

        String currentSceneName = projectConfig.getCurrentSceneName();

        for (String sceneName : projectConfig.getSceneNames()) {
            if (sceneName == null || sceneName.isBlank()) continue;
            if (sceneName.equals(currentSceneName)) continue;

            SceneMeta sceneMeta = projectConfig.getSceneMeta(sceneName);
            if (sceneMeta == null || sceneMeta.getFile() == null || sceneMeta.getFile().isBlank()) {
                continue;
            }

            FileHandle sceneFile = scenesDir.child(sceneMeta.getFile());
            if (!sceneFile.exists() || sceneFile.isDirectory()) {
                continue;
            }

            switch (assetMeta.type) {
                case IMAGE, ANIMATION -> scanSceneFileByAssetId(sceneFile, sceneName, assetMeta.id, acc);
                case TILE -> scanSceneFileTile(sceneFile, sceneName, assetMeta.id, acc);
                case PARTICLE -> scanSceneFileParticle(sceneFile, sceneName, assetMeta, acc);
                case TILESET -> {
                    // no-op
                }
            }
        }
    }

    private void scanOtherProjectSceneFilesTiles(IntSet tileAssetIds, UsageAccumulator acc) {
        FileHandle scenesDir = resolveScenesDir();
        if (scenesDir == null || !scenesDir.exists()) {
            return;
        }

        String currentSceneName = projectConfig.getCurrentSceneName();

        for (String sceneName : projectConfig.getSceneNames()) {
            if (sceneName == null || sceneName.isBlank()) continue;
            if (sceneName.equals(currentSceneName)) continue;

            SceneMeta sceneMeta = projectConfig.getSceneMeta(sceneName);
            if (sceneMeta == null || sceneMeta.getFile() == null || sceneMeta.getFile().isBlank()) {
                continue;
            }

            FileHandle sceneFile = scenesDir.child(sceneMeta.getFile());
            if (!sceneFile.exists() || sceneFile.isDirectory()) {
                continue;
            }

            scanSceneFileTiles(sceneFile, sceneName, tileAssetIds, acc);
        }
    }

    private boolean scanCurrentWorldByAssetId(int assetId,
                                              UsageAccumulator acc,
                                              String sceneName) {
        boolean found = false;

        IntBag bag = world.getAspectSubscriptionManager()
                .get(Aspect.all(AssetRefComponent.class))
                .getEntities();

        int[] data = bag.getData();
        for (int i = 0; i < bag.size(); i++) {
            int e = data[i];
            AssetRefComponent ref = mAssetRef.getSafe(e, null);
            if (ref == null) continue;
            if (ref.assetId != assetId) continue;

            acc.occurrenceCount++;
            found = true;
        }

        if (found) {
            acc.addScene(sceneName);
        }

        return found;
    }

    private boolean scanCurrentWorldParticle(AssetMeta assetMeta,
                                             UsageAccumulator acc,
                                             String sceneName) {
        if (assetMeta.sourceRelPath == null || assetMeta.sourceRelPath.isBlank()) {
            return false;
        }

        boolean found = false;

        IntBag bag = world.getAspectSubscriptionManager()
                .get(Aspect.all(ParticleEmitterComponent.class))
                .getEntities();

        int[] data = bag.getData();
        for (int i = 0; i < bag.size(); i++) {
            int e = data[i];
            ParticleEmitterComponent particle = mParticle.getSafe(e, null);
            if (particle == null) continue;
            if (!matchesParticleEffectPath(assetMeta, particle.effectPath)) continue;

            acc.occurrenceCount++;
            found = true;
        }

        if (found) {
            acc.addScene(sceneName);
        }

        return found;
    }

    private boolean scanCurrentWorldTiles(IntSet tileAssetIds,
                                          UsageAccumulator acc,
                                          String sceneName) {
        boolean found = false;

        IntBag bag = world.getAspectSubscriptionManager()
                .get(Aspect.all(TiledLayerComponent.class))
                .getEntities();

        int[] data = bag.getData();
        for (int i = 0; i < bag.size(); i++) {
            int e = data[i];
            TiledLayerComponent tiled = mTiled.getSafe(e, null);
            if (tiled == null) continue;

            if (tiled.tileAssetIds != null) {
                for (int j = 0; j < tiled.tileAssetIds.size; j++) {
                    int assetId = tiled.tileAssetIds.get(j);
                    if (assetId <= 0) continue;
                    if (!tileAssetIds.contains(assetId)) continue;

                    acc.occurrenceCount++;
                    found = true;
                }
            }

            if (tiled.data == null) continue;

            var chunks = tiled.data.getChunks();
            while (chunks.hasNext()) {
                TileChunk chunk = chunks.next();

                for (int ly = 0; ly < chunk.chunkHeight; ly++) {
                    for (int lx = 0; lx < chunk.chunkWidth; lx++) {
                        int assetId = chunk.get(lx, ly);
                        if (assetId <= 0) continue;
                        if (!tileAssetIds.contains(assetId)) continue;

                        acc.occurrenceCount++;
                        found = true;
                    }
                }
            }
        }

        if (found) {
            acc.addScene(sceneName);
        }

        return found;
    }

    private boolean scanCurrentWorldTile(int tileAssetId,
                                         UsageAccumulator acc,
                                         String sceneName) {
        IntSet ids = new IntSet();
        ids.add(tileAssetId);
        return scanCurrentWorldTiles(ids, acc, sceneName);
    }

    private void scanSceneFileByAssetId(FileHandle sceneFile,
                                        String sceneName,
                                        int assetId,
                                        UsageAccumulator acc) {
        JsonValue root = parseScene(sceneFile);
        if (root == null) return;

        JsonValue entities = root.get("entities");
        if (entities == null || !entities.isObject()) return;

        boolean found = false;

        for (JsonValue ent = entities.child; ent != null; ent = ent.next) {
            JsonValue components = ent.get("components");
            if (components == null || !components.isObject()) continue;

            JsonValue assetRef = components.get("AssetRefComponent");
            if (assetRef == null || !assetRef.isObject()) continue;

            int refId = assetRef.getInt("assetId", -1);
            if (refId != assetId) continue;

            acc.occurrenceCount++;
            found = true;
        }

        if (found) {
            acc.addScene(sceneName);
        }
    }

    private void scanSceneFileParticle(FileHandle sceneFile,
                                       String sceneName,
                                       AssetMeta assetMeta,
                                       UsageAccumulator acc) {
        if (assetMeta.sourceRelPath == null || assetMeta.sourceRelPath.isBlank()) {
            return;
        }

        JsonValue root = parseScene(sceneFile);
        if (root == null) return;

        JsonValue entities = root.get("entities");
        if (entities == null || !entities.isObject()) return;

        boolean found = false;

        for (JsonValue ent = entities.child; ent != null; ent = ent.next) {
            JsonValue components = ent.get("components");
            if (components == null || !components.isObject()) continue;

            JsonValue particle = components.get("ParticleEmitterComponent");
            if (particle == null || !particle.isObject()) continue;

            String effectPath = particle.getString("effectPath", null);
            if (!matchesParticleEffectPath(assetMeta, effectPath)) continue;

            acc.occurrenceCount++;
            found = true;
        }

        if (found) {
            acc.addScene(sceneName);
        }
    }

    private void scanSceneFileTiles(FileHandle sceneFile,
                                    String sceneName,
                                    IntSet tileAssetIds,
                                    UsageAccumulator acc) {
        JsonValue root = parseScene(sceneFile);
        if (root == null) return;

        JsonValue entities = root.get("entities");
        if (entities == null || !entities.isObject()) return;

        boolean found = false;

        for (JsonValue ent = entities.child; ent != null; ent = ent.next) {
            JsonValue components = ent.get("components");
            if (components == null || !components.isObject()) continue;

            JsonValue tiled = components.get("TiledLayerComponent");
            if (tiled == null || !tiled.isObject()) continue;

            JsonValue tileAssetIdsJson = tiled.get("tileAssetIds");
            if (tileAssetIdsJson == null || !tileAssetIdsJson.isArray()) continue;

            for (JsonValue v = tileAssetIdsJson.child; v != null; v = v.next) {
                int assetId = v.asInt();
                if (assetId <= 0) continue;
                if (!tileAssetIds.contains(assetId)) continue;

                acc.occurrenceCount++;
                found = true;
            }
        }

        if (found) {
            acc.addScene(sceneName);
        }
    }

    private void scanSceneFileTile(FileHandle sceneFile,
                                   String sceneName,
                                   int tileAssetId,
                                   UsageAccumulator acc) {
        IntSet ids = new IntSet();
        ids.add(tileAssetId);
        scanSceneFileTiles(sceneFile, sceneName, ids, acc);
    }

    private IntSet collectTileAssetIdsForTileset(int tilesetId) {
        IntSet ids = new IntSet();

        for (AssetMeta meta : assetMetaDatabase.assets) {
            if (!(meta instanceof TileAssetMeta tileMeta)) continue;
            if (tileMeta.tilesetId != tilesetId) continue;
            ids.add(tileMeta.id);
        }

        return ids;
    }

    private FileHandle resolveScenesDir() {
        FileHandle projectDir = resolveProjectDir();
        return projectDir.child(StudioFs.DIR_SCENES);
    }

    private FileHandle resolveProjectDir() {
        if (projectConfig.projectDirectoryPath != null && !projectConfig.projectDirectoryPath.isBlank()) {
            return new FileHandle(new File(projectConfig.projectDirectoryPath));
        }
        return StudioFs.requireStudioProjectDir(projectConfig);
    }

    private JsonValue parseScene(FileHandle sceneFile) {
        try {
            return new JsonReader().parse(sceneFile);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static boolean matchesParticleEffectPath(AssetMeta assetMeta, String effectPath) {
        if (assetMeta == null || assetMeta.sourceRelPath == null || effectPath == null) {
            return false;
        }

        String source = normalizePath(assetMeta.sourceRelPath);
        String effect = normalizePath(effectPath);
        if (source.equals(effect)) {
            return true;
        }

        String prefix = StudioFs.DIR_ORIG_EFFECTS + "/";
        return source.startsWith(prefix)
                && source.substring(prefix.length()).equals(effect);
    }

    private static String normalizePath(String path) {
        return path != null ? path.trim().replace('\\', '/') : "";
    }

    private static final class UsageAccumulator {
        int occurrenceCount = 0;
        boolean referencedInCurrentLoadedScene = false;
        final Array<String> sceneNames = new Array<>();

        void addScene(String sceneName) {
            if (sceneName == null || sceneName.isBlank()) return;
            if (!sceneNames.contains(sceneName, false)) {
                sceneNames.add(sceneName);
            }
        }

        AssetUsageReport toReport() {
            return new AssetUsageReport(
                    occurrenceCount > 0,
                    occurrenceCount,
                    sceneNames,
                    referencedInCurrentLoadedScene
            );
        }
    }

    public record AssetUsageReport(boolean used, int occurrenceCount, Array<String> sceneNames,
                                   boolean referencedInCurrentLoadedScene) {
        public AssetUsageReport(boolean used,
                                int occurrenceCount,
                                Array<String> sceneNames,
                                boolean referencedInCurrentLoadedScene) {
            this.used = used;
            this.occurrenceCount = occurrenceCount;
            this.sceneNames = sceneNames != null ? new Array<>(sceneNames) : new Array<>();
            this.referencedInCurrentLoadedScene = referencedInCurrentLoadedScene;
        }

        public static AssetUsageReport empty() {
            return new AssetUsageReport(false, 0, new Array<>(), false);
        }
    }
}
