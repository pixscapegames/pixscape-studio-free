package games.pixscape.studio.service.atlas;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.World;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.ParticleEffect;
import com.badlogic.gdx.graphics.g2d.ParticleEmitter;
import com.badlogic.gdx.utils.IntSet;
import games.pixscape.runtime.component.AssetRefComponent;
import games.pixscape.runtime.component.ParticleEmitterComponent;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.tiled.TileChunk;
import games.pixscape.studio.asset.AssetMeta;
import games.pixscape.studio.asset.AssetMetaDatabase;
import games.pixscape.studio.asset.TileAnimationProjectDefData;
import games.pixscape.studio.asset.TileAnimationsMetaDatabase;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.configuration.SceneMeta;
import games.pixscape.studio.configuration.SceneRuntimeAvailabilityData;
import games.pixscape.studio.helper.InternalAssets;
import games.pixscape.studio.history.initializer.GenericEntitySnapshotData;
import games.pixscape.studio.io.StudioFs;
import games.pixscape.studio.service.entitygraph.EntityGraph;
import games.pixscape.studio.service.entitygraph.EntityGraphEntry;
import games.pixscape.studio.service.prefab.PrefabAssetService;

import java.util.HashSet;
import java.util.Set;

public final class SceneAtlasInputService {

    private static final String TAG = "SceneAtlasInputService";
    private static final String INTERNAL_DIR = "__pixscape_internal__";

    public AtlasInputSyncResult syncSceneAtlasInput(ProjectConfig cfg,
                                                    String sceneTag,
                                                    FileHandle projectDir,
                                                    Set<String> requiredProjectRelativePaths) {
        if (cfg == null) throw new IllegalArgumentException("cfg is null");
        if (sceneTag == null || sceneTag.isBlank()) throw new IllegalArgumentException("sceneTag is blank");
        if (projectDir == null) throw new IllegalArgumentException("projectDir is null");

        Set<String> required = requiredProjectRelativePaths != null
                ? requiredProjectRelativePaths
                : Set.of();

        FileHandle inputDir = inputDir(projectDir, sceneTag);
        inputDir.mkdirs();
        ensureInternalWhitePixel(inputDir);
        Set<String> requiredInputFileNames = toRequiredInputFileNames(required);
        int deleted = cleanupUnusedInputFiles(inputDir, requiredInputFileNames);

        int copied = 0;

        for (String relPath : required) {
            if (relPath == null || relPath.isBlank()) continue;

            FileHandle source = projectDir.child(relPath);
            if (!source.exists() || source.isDirectory()) {
                Gdx.app.error(TAG, "Missing atlas input source: " + source.path());
                continue;
            }

            FileHandle dest = inputDir.child(source.name());

            if (copyIfDifferent(source, dest)) {
                copied++;
                Gdx.app.log(TAG, "Copied atlas input: " + dest.path());
            }
        }

        return new AtlasInputSyncResult(
                deleted > 0 || copied > 0,
                copied,
                deleted
        );
    }

    public AtlasInputSyncResult syncSceneAtlasInputForSave(ProjectConfig cfg,
                                                           World world,
                                                           AssetMetaDatabase assetDb,
                                                           TileAnimationsMetaDatabase tileAnimationsDb) {
        if (cfg == null || world == null || assetDb == null) {
            return new AtlasInputSyncResult(false, 0, 0);
        }
        FileHandle projectDir = StudioFs.requireStudioProjectDir(cfg);
        Set<String> required = collectRequiredAtlasInputPathsForCurrentScene(cfg, world, assetDb, tileAnimationsDb);
        return syncSceneAtlasInput(cfg, cfg.canonicalSceneTagCurrent(), projectDir, required);
    }

    public IntSet collectUsedTiledRenderableAssetIds(World world,
                                                     TileAnimationsMetaDatabase tileAnimationsDb) {
        IntSet used = new IntSet();
        if (world == null) return used;
        ComponentMapper<TiledLayerComponent> mTiled = world.getMapper(TiledLayerComponent.class);
        IntBag tiledLayers = world.getAspectSubscriptionManager().get(Aspect.all(TiledLayerComponent.class)).getEntities();
        int[] tiledData = tiledLayers.getData();
        for (int i = 0; i < tiledLayers.size(); i++) {
            TiledLayerComponent tiled = mTiled.get(tiledData[i]);
            if (tiled == null || tiled.data == null) continue;
            for (TileChunk chunk : tiled.data.getChunks()) {
                for (int ly = 0; ly < chunk.chunkHeight; ly++)
                    for (int lx = 0; lx < chunk.chunkWidth; lx++) {
                        int logicalId = chunk.get(lx, ly);
                        if (logicalId <= 0) continue;
                        TileAnimationProjectDefData animDef = findTileAnimationProjectDef(tileAnimationsDb, logicalId);
                        if (animDef != null && animDef.frameAssetIds != null) {
                            for (int frameAssetId : animDef.frameAssetIds) if (frameAssetId > 0) used.add(frameAssetId);
                        } else {
                            used.add(logicalId);
                        }
                    }
            }
        }
        return used;
    }

    private Set<String> collectRequiredAtlasInputPathsForCurrentScene(ProjectConfig cfg,
                                                                      World world,
                                                                      AssetMetaDatabase assetDb,
                                                                      TileAnimationsMetaDatabase tileAnimationsDb) {
        Set<String> required = new HashSet<>();
        ComponentMapper<AssetRefComponent> mAssetRef = world.getMapper(AssetRefComponent.class);
        IntBag assetRefs = world.getAspectSubscriptionManager().get(Aspect.all(AssetRefComponent.class)).getEntities();
        int[] data = assetRefs.getData();
        for (int i = 0; i < assetRefs.size(); i++) {
            AssetRefComponent ref = mAssetRef.getSafe(data[i], null);
            if (ref != null && ref.assetId > 0) addAssetMetaSourcePath(cfg, assetDb, required, ref.assetId);
        }
        IntSet tiledAssetIds = collectUsedTiledRenderableAssetIds(world, tileAnimationsDb);
        IntSet.IntSetIterator it = tiledAssetIds.iterator();
        while (it.hasNext) addAssetMetaSourcePath(cfg, assetDb, required, it.next());
        addRuntimeAvailabilitySourcePaths(cfg, world, assetDb, tileAnimationsDb, required);
        addParticleImageSourcePaths(cfg, world, required);
        return required;
    }

    private void addRuntimeAvailabilitySourcePaths(ProjectConfig cfg,
                                                   World world,
                                                   AssetMetaDatabase assetDb,
                                                   TileAnimationsMetaDatabase tileAnimationsDb,
                                                   Set<String> required) {
        SceneMeta scene = cfg != null ? cfg.getCurrentSceneMeta() : null;
        SceneRuntimeAvailabilityData availability = scene != null ? scene.runtimeAvailability : null;
        if (availability == null || required == null) return;

        if (availability.spriteAssetIds != null) {
            for (Integer assetId : availability.spriteAssetIds) {
                if (assetId != null) {
                    addAssetMetaSourcePath(cfg, assetDb, required, assetId);
                }
            }
        }

        if (availability.animationAssetIds != null) {
            for (Integer assetId : availability.animationAssetIds) {
                if (assetId != null) {
                    addAssetMetaSourcePath(cfg, assetDb, required, assetId);
                }
            }
        }

        if (availability.particleEffectPaths != null) {
            for (String effectPath : availability.particleEffectPaths) {
                addParticleEffectImageSourcePaths(cfg, effectPath, required);
            }
        }

        if (availability.prefabIds != null && !availability.prefabIds.isEmpty()) {
            addRuntimeAvailablePrefabSources(cfg, world, assetDb, availability, required);
        }

        if (availability.tiledTileAssetIds != null) {
            for (Integer assetId : availability.tiledTileAssetIds) {
                if (assetId != null) {
                    addAssetMetaSourcePath(cfg, assetDb, required, assetId);
                }
            }
        }

        if (availability.tiledAnimationIds != null) {
            for (Integer tileAnimationId : availability.tiledAnimationIds) {
                if (tileAnimationId == null || tileAnimationId <= 0) continue;
                TileAnimationProjectDefData def = findTileAnimationProjectDef(tileAnimationsDb, tileAnimationId);
                if (def == null || def.frameAssetIds == null) continue;
                for (int frameAssetId : def.frameAssetIds) {
                    addAssetMetaSourcePath(cfg, assetDb, required, frameAssetId);
                }
            }
        }
    }

    private void addRuntimeAvailablePrefabSources(ProjectConfig cfg,
                                                  World world,
                                                  AssetMetaDatabase assetDb,
                                                  SceneRuntimeAvailabilityData availability,
                                                  Set<String> required) {
        if (cfg == null || world == null || assetDb == null) return;

        PrefabAssetService prefabAssetService = new PrefabAssetService(world);
        for (String prefabId : availability.prefabIds) {
            if (prefabId == null || prefabId.isBlank()) continue;

            FileHandle prefabFile = StudioFs.requirePrefabFile(cfg, prefabId);
            if (!prefabFile.exists()) {
                Gdx.app.error(TAG, "Missing runtime availability prefab: " + prefabFile.path());
                continue;
            }

            EntityGraph graph;
            try {
                graph = prefabAssetService.loadPrefab(prefabFile);
            } catch (RuntimeException ex) {
                Gdx.app.error(TAG, "Failed to collect runtime availability prefab: " + prefabFile.path(), ex);
                continue;
            }

            if (graph == null || graph.isEmpty()) continue;
            for (EntityGraphEntry entry : graph.entries()) {
                if (entry == null || entry.initializer() == null) continue;
                GenericEntitySnapshotData snapshot = entry.initializer().toSnapshotData(entry.sourceEntityId());
                if (snapshot == null || !snapshot.hasAssetRef || snapshot.assetRefAssetId <= 0) continue;
                addAssetMetaSourcePath(cfg, assetDb, required, snapshot.assetRefAssetId);
            }
        }
    }

    private void addAssetMetaSourcePath(ProjectConfig cfg,
                                        AssetMetaDatabase assetDb,
                                        Set<String> required,
                                        int assetId) {
        if (cfg == null || assetDb == null || required == null || assetId <= 0) return;

        AssetMeta meta = assetDb.findById(assetId);
        if (meta == null || meta.sourceRelPath() == null || meta.sourceRelPath().isBlank()) return;

        FileHandle projectDir = StudioFs.requireStudioProjectDir(cfg);
        FileHandle source = projectDir.child(meta.sourceRelPath());

        if (!source.exists()) return;

        if (source.isDirectory()) {
            for (FileHandle child : source.list()) {
                if (child == null || child.isDirectory()) continue;
                if (!"png".equalsIgnoreCase(child.extension())) continue;

                required.add(meta.sourceRelPath() + "/" + child.name());
            }
        } else {
            required.add(meta.sourceRelPath());
        }
    }

    private void addParticleImageSourcePaths(ProjectConfig cfg, World world, Set<String> required) {
        FileHandle projectDir = StudioFs.requireStudioProjectDir(cfg);
        FileHandle effectsRoot = projectDir.child(StudioFs.DIR_ORIG_EFFECTS);
        ComponentMapper<ParticleEmitterComponent> mParticle = world.getMapper(ParticleEmitterComponent.class);
        IntBag particles = world.getAspectSubscriptionManager().get(Aspect.all(ParticleEmitterComponent.class)).getEntities();
        int[] data = particles.getData();
        for (int i = 0; i < particles.size(); i++) {
            ParticleEmitterComponent particle = mParticle.getSafe(data[i], null);
            if (particle == null || particle.effectPath == null || particle.effectPath.isBlank()) continue;
            addParticleEffectImageSourcePaths(projectDir, effectsRoot.child(particle.effectPath), required);
        }
    }

    private void addParticleEffectImageSourcePaths(ProjectConfig cfg, String effectPath, Set<String> required) {
        if (cfg == null || effectPath == null || effectPath.isBlank()) return;
        FileHandle projectDir = StudioFs.requireStudioProjectDir(cfg);
        FileHandle effectFile = projectDir.child(StudioFs.DIR_ORIG_EFFECTS).child(effectPath);
        addParticleEffectImageSourcePaths(projectDir, effectFile, required);
    }

    private void addParticleEffectImageSourcePaths(FileHandle projectDir, FileHandle effectFile, Set<String> required) {
        if (projectDir == null || effectFile == null || required == null || !effectFile.exists()) return;

        ParticleEffect effect = new ParticleEffect();
        try {
            effect.loadEmitters(effectFile);
            for (ParticleEmitter emitter : effect.getEmitters()) {
                if (emitter == null || emitter.getImagePaths() == null) continue;
                for (String rawPath : emitter.getImagePaths()) {
                    String rel = resolveParticleImageProjectRelativePath(projectDir, rawPath);
                    if (rel != null && !rel.isBlank()) required.add(rel);
                }
            }
        } catch (Exception ex) {
            Gdx.app.error(TAG, "Failed to collect particle images for atlas input: " + effectFile.path(), ex);
        }
    }

    private String resolveParticleImageProjectRelativePath(FileHandle projectDir, String rawPath) {
        if (projectDir == null || rawPath == null || rawPath.isBlank()) return null;
        String normalized = rawPath.replace("\\", "/");
        if (normalized.startsWith(StudioFs.DIR_ORIG_IMAGES + "/")) return normalized;
        FileHandle direct = projectDir.child(normalized);
        if (direct.exists() && !direct.isDirectory()) return normalized;
        String fileName = fileNameFromPath(normalized);
        FileHandle inOrigImages = projectDir.child(StudioFs.DIR_ORIG_IMAGES).child(fileName);
        if (inOrigImages.exists() && !inOrigImages.isDirectory())
            return StudioFs.DIR_ORIG_IMAGES + "/" + inOrigImages.name();
        return null;
    }

    private static TileAnimationProjectDefData findTileAnimationProjectDef(TileAnimationsMetaDatabase db, int tileAnimationId) {
        if (db == null || db.animations == null) return null;
        for (TileAnimationProjectDefData def : db.animations) if (def != null && def.id == tileAnimationId) return def;
        return null;
    }

    private static String fileNameFromPath(String path) {
        if (path == null || path.isEmpty()) return "";
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return (slash >= 0 && slash + 1 < path.length()) ? path.substring(slash + 1) : path;
    }

    private static Set<String> toRequiredInputFileNames(Set<String> requiredPaths) {
        Set<String> requiredFileNames = new HashSet<>();
        if (requiredPaths == null) return requiredFileNames;
        for (String relPath : requiredPaths) {
            String fileName = fileNameFromPath(relPath);
            if (!fileName.isBlank()) requiredFileNames.add(fileName);
        }
        return requiredFileNames;
    }


    public boolean ensureImageInInput(ProjectConfig cfg,
                                      String sceneTag,
                                      String projectRelativePath) {
        if (cfg == null) return false;
        if (sceneTag == null || sceneTag.isBlank()) return false;
        if (projectRelativePath == null || projectRelativePath.isBlank()) return false;

        FileHandle projectDir = StudioFs.requireStudioProjectDir(cfg);
        FileHandle source = projectDir.child(projectRelativePath);

        if (!source.exists() || source.isDirectory()) {
            Gdx.app.error(TAG, "Missing image for atlas input: " + source.path());
            return false;
        }

        FileHandle inputDir = inputDir(projectDir, sceneTag);
        inputDir.mkdirs();
        ensureInternalWhitePixel(inputDir);

        FileHandle dest = inputDir.child(source.name());
        return copyIfDifferent(source, dest);
    }

    public boolean ensureAnimationDirInInput(ProjectConfig cfg,
                                             String sceneTag,
                                             String animationProjectRelativeDir) {
        if (cfg == null) return false;
        if (sceneTag == null || sceneTag.isBlank()) return false;
        if (animationProjectRelativeDir == null || animationProjectRelativeDir.isBlank()) return false;

        FileHandle projectDir = StudioFs.requireStudioProjectDir(cfg);
        FileHandle animDir = projectDir.child(animationProjectRelativeDir);

        if (!animDir.exists() || !animDir.isDirectory()) {
            Gdx.app.error(TAG, "Missing animation dir for atlas input: " + animDir.path());
            return false;
        }

        FileHandle inputDir = inputDir(projectDir, sceneTag);
        inputDir.mkdirs();
        ensureInternalWhitePixel(inputDir);

        boolean changed = false;

        for (FileHandle child : animDir.list()) {
            if (child == null || child.isDirectory()) continue;
            if (!"png".equalsIgnoreCase(child.extension())) continue;

            FileHandle dest = inputDir.child(child.name());
            if (copyIfDifferent(child, dest)) {
                changed = true;
                Gdx.app.log(TAG, "Copied animation frame to atlas input: " + dest.path());
            }
        }

        return changed;
    }

    public boolean ensureAssetInInput(ProjectConfig cfg,
                                      String sceneTag,
                                      String assetProjectRelativePath) {
        return ensureImageInInput(cfg, sceneTag, assetProjectRelativePath);
    }

    private static FileHandle inputDir(FileHandle projectDir, String sceneTag) {
        return projectDir
                .child(StudioFs.DIR_ATLASES)
                .child(StudioFs.DIR_INPUT)
                .child(sceneTag);
    }

    private static void ensureInternalWhitePixel(FileHandle inputDir) {
        FileHandle internalDir = inputDir.child(INTERNAL_DIR);
        FileHandle whitePixel = internalDir.child(InternalAssets.WHITE_PIXEL_FILE);

        if (!whitePixel.exists()) {
            InternalAssets.copyWhitePixelTo(whitePixel);
        }
    }

    private static boolean copyIfDifferent(FileHandle source, FileHandle dest) {
        if (source == null || dest == null) return false;
        if (!source.exists() || source.isDirectory()) return false;

        if (dest.exists()
                && !dest.isDirectory()
                && dest.length() == source.length()
                && dest.lastModified() >= source.lastModified()) {
            return false;
        }

        dest.parent().mkdirs();
        source.copyTo(dest);
        return true;
    }

    private static int cleanupUnusedInputFiles(FileHandle inputDir,
                                               Set<String> requiredInputFileNames) {
        if (inputDir == null || !inputDir.exists()) return 0;

        int deleted = 0;

        for (FileHandle child : inputDir.list()) {
            if (child == null) continue;

            if (child.isDirectory()) {
                if (INTERNAL_DIR.equals(child.name())) {
                    continue;
                }

                child.deleteDirectory();
                deleted++;
                Gdx.app.log(TAG, "Deleted unused atlas input dir: " + child.path());
                continue;
            }

            if (!"png".equalsIgnoreCase(child.extension())) {
                continue;
            }

            if (!requiredInputFileNames.contains(child.name())) {
                child.delete();
                deleted++;
                Gdx.app.log(TAG, "Deleted unused atlas input file: " + child.path());
            }
        }

        return deleted;
    }
}
