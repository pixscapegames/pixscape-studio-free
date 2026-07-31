package games.pixscape.studio.service.atlas;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.tools.texturepacker.TexturePacker;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.helper.InternalAssets;
import games.pixscape.studio.helper.RenderRebindHelper;
import games.pixscape.studio.io.StudioFs;
import games.pixscape.studio.ui.main.WorldCanvas;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class SceneAtlasLoaderService {
    private static final ConcurrentMap<String, Object> SCENE_PACK_LOCKS = new ConcurrentHashMap<>();

    private SceneAtlasLoaderService() {
    }

    /**
     * Packs the scene atlas.
     */
    public static void packSceneAtlas(ProjectConfig cfg,
                                      String canonicalTag,
                                      FileHandle projectDir) {
        FileHandle atlasesRoot = projectDir.child(StudioFs.DIR_ATLASES);
        atlasesRoot.mkdirs();

        packSceneAtlasToDirectory(cfg, canonicalTag, projectDir, atlasesRoot);
    }

    public static void packSceneAtlasToDirectory(ProjectConfig cfg,
                                                 String canonicalTag,
                                                 FileHandle projectDir,
                                                 FileHandle outputDir) {
        if (cfg != null && canonicalTag != null && cfg.getSceneMeta(canonicalTag) != null) {
            String remapped = cfg.canonicalSceneTag(canonicalTag);
            if (remapped != null && !remapped.equals(canonicalTag)) {
                canonicalTag = remapped;
            }
        }

        String lockKey = projectDir.path() + "::" + canonicalTag;
        Object lock = SCENE_PACK_LOCKS.computeIfAbsent(lockKey, ignored -> new Object());

        synchronized (lock) {
            FileHandle atlasesRoot = projectDir.child(StudioFs.DIR_ATLASES);
            FileHandle inputDir = atlasesRoot.child(StudioFs.DIR_INPUT).child(canonicalTag);
            inputDir.mkdirs();
            outputDir.mkdirs();

            FileHandle internalDir = inputDir.child("__pixscape_internal__");
            FileHandle whitePixel = internalDir.child(InternalAssets.WHITE_PIXEL_FILE);
            if (!whitePixel.exists()) {
                InternalAssets.copyWhitePixelTo(whitePixel);
            }

            TexturePacker.Settings settings = new TexturePacker.Settings();
            settings.maxWidth = 2048;
            settings.maxHeight = 2048;
            settings.minWidth = 2048;
            settings.minHeight = 2048;
            settings.duplicatePadding = true;
            settings.edgePadding = true;
            settings.combineSubdirectories = true;

            TexturePacker.process(
                    settings,
                    inputDir.path(),
                    outputDir.path(),
                    canonicalTag
            );
        }
    }

    /**
     * Loads the scene atlas into AtlasService (load-only + rebind).
     * <p>
     * This method does not pack and does not clear the global registry.
     */
    public static void loadSceneAtlas(ProjectConfig cfg,
                                      String canonicalTag,
                                      FileHandle projectDir,
                                      WorldCanvas canvas) {

        FileHandle atlasesDir = projectDir.child(StudioFs.DIR_ATLASES);
        FileHandle atlasFile = atlasesDir.child(StudioFs.withExt(canonicalTag, StudioFs.EXT_ATLAS));

        // 0) Ensures the internal PNG is present in input/<scene>/__pixscape_internal__/
        // (even if not packed yet)
        FileHandle inputDir = atlasesDir.child(StudioFs.DIR_INPUT).child(canonicalTag);
        FileHandle internalDir = inputDir.child("__pixscape_internal__");
        FileHandle whitePixel = internalDir.child(InternalAssets.WHITE_PIXEL_FILE);
        if (!whitePixel.exists()) {
            InternalAssets.copyWhitePixelTo(whitePixel);
        }

        AtlasStudioService atlasRuntimeService = canvas.getAtlasService();

        // 1) Atlas file must be already packed by caller.
        if (!atlasFile.exists()) {
            String msg = "loadSceneAtlas: atlas missing for tag '" + canonicalTag
                    + "' at " + atlasFile.path() + ", skip load.";
            Gdx.app.log("SceneAtlasLoader", msg);
            EventFlow.i().publish(new EventFlow.LogMessage(msg));
            return;
        }

        // 2) Load
        atlasRuntimeService.unload(canonicalTag);
        atlasRuntimeService.load(canonicalTag, atlasFile);

        // 3) Rebind after atlas is ready
        RenderRebindHelper.rebindAfterAtlasChange(
                canvas,
                canonicalTag,
                canvas.getAssetVisualResolver(),
                "scene-atlas-loaded"
        );
    }

}
