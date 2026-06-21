package games.pixscape.studio.ui.asset;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.ui.Stack;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Scaling;
import com.kotcrab.vis.ui.widget.*;
import com.kotcrab.vis.ui.widget.spinner.IntSpinnerModel;
import com.kotcrab.vis.ui.widget.spinner.Spinner;
import games.pixscape.runtime.helper.RuntimeFs;
import games.pixscape.studio.asset.AssetMeta;
import games.pixscape.studio.asset.AssetMetaDatabase;
import games.pixscape.studio.asset.TileAnimationProjectDefData;
import games.pixscape.studio.asset.TileAnimationsMetaDatabase;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.configuration.SceneMeta;
import games.pixscape.studio.io.StudioFs;
import games.pixscape.studio.io.TileAnimationsIO;
import games.pixscape.studio.service.StandaloneTextureCache;
import games.pixscape.studio.service.runtimeavailability.RuntimeAvailabilityCategory;
import games.pixscape.studio.ui.main.StudioApplicationAdapter;

public final class RuntimeAvailabilityThumbsView extends VisTable {

    private static final float TILE_PAD = 4f;

    private final StudioApplicationAdapter app;
    private final VisTable header = new VisTable();
    private final VisTable headerLeft = new VisTable(true);
    private final VisTable headerRight = new VisTable(true);
    private final VisTable content = new VisTable();
    private final VisTable grid = new VisTable();
    private final VisScrollPane scroll;
    private final Array<RuntimeAvailabilityItem> items = new Array<>();
    private final Array<Texture> ownedTextures = new Array<>();

    private RuntimeAvailabilityCategory category = RuntimeAvailabilityCategory.PREFABS;
    private PopupMenu activeContextMenu;
    private float tileSize = 48f;

    public RuntimeAvailabilityThumbsView(StudioApplicationAdapter app) {
        this.app = app;
        top().left().pad(6);

        content.top().left();
        content.add(grid).expandX().fillX().top().left();
        grid.top().left();

        scroll = new VisScrollPane(content);
        scroll.setForceScroll(true, true);
        scroll.setFadeScrollBars(false);

        buildHeader();
        add(header).growX().padBottom(4f).row();
        add(scroll).grow();
    }

    public void showCategory(RuntimeAvailabilityCategory category) {
        this.category = category != null ? category : RuntimeAvailabilityCategory.PREFABS;
        refresh();
    }

    public void refresh() {
        disposeOwnedTextures();
        items.clear();
        grid.clear();

        ProjectConfig cfg = ProjectConfig.getInstance();
        SceneMeta scene = cfg != null ? cfg.getCurrentSceneMeta() : null;

        if (scene == null || app == null || app.getSceneService() == null) {
            showEmptyState();
            return;
        }

        switch (category) {
            case SPRITES -> loadAssetItems(scene, RuntimeAvailabilityCategory.SPRITES);
            case ANIMATIONS -> loadAssetItems(scene, RuntimeAvailabilityCategory.ANIMATIONS);
            case PARTICLES -> loadParticles(cfg, scene);
            case PREFABS -> loadPrefabs(cfg, scene);
            case TILED_TILES -> loadAssetItems(scene, RuntimeAvailabilityCategory.TILED_TILES);
            case TILED_ANIMATIONS -> loadTiledAnimations(scene);
        }

        if (items.size == 0) {
            showEmptyState();
            return;
        }

        for (RuntimeAvailabilityItem item : items) {
            addItem(item);
        }

        grid.invalidateHierarchy();
        content.invalidateHierarchy();
    }

    private void buildHeader() {
        header.clear();
        header.top().left();
        headerLeft.top().left();
        headerRight.top().right();

        header.add(headerLeft).left().expandX().fillX();
        header.add(headerRight).right();

        buildThumbSizeControl();
    }

    private void buildThumbSizeControl() {
        headerRight.clear();

        IntSpinnerModel model = new IntSpinnerModel(48, 32, 128, 8);
        // No reusable studio wrapper exists for this VisUI Spinner yet.
        Spinner sizeSpinner = new Spinner("Thumb size", model);

        sizeSpinner.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                tileSize = Math.max(32f, Math.min(model.getValue(), 192f));
                refresh();
            }
        });

        headerRight.add(sizeSpinner).width(80f).padRight(50f);
    }

    private void loadPrefabs(ProjectConfig cfg, SceneMeta scene) {
        for (String prefabId : app.getSceneService().getRuntimeAvailabilityService().listPrefabIds(scene)) {
            if (prefabId == null || prefabId.isBlank()) continue;
            FileHandle prefabFile = StudioFs.requirePrefabFile(cfg, prefabId);
            if (!prefabFile.exists()) continue;
            FileHandle previewFile = StudioFs.requirePrefabPreviewFile(cfg, prefabId);
            items.add(RuntimeAvailabilityItem.prefab(prefabId, prefabFile, previewFile));
        }
    }

    private void loadTiledAnimations(SceneMeta scene) {
        TileAnimationsMetaDatabase db = loadTileAnimationsMetaDatabase();
        if (db == null) return;

        for (Integer tileAnimationId : app.getSceneService().getRuntimeAvailabilityService().listTiledAnimationIds(scene)) {
            if (tileAnimationId == null || tileAnimationId <= 0) continue;
            TileAnimationProjectDefData def = findTiledAnimationDef(db, tileAnimationId);
            if (def != null) {
                items.add(RuntimeAvailabilityItem.tiledAnimation(def.id, def.name));
            }
        }
    }

    private void addItem(RuntimeAvailabilityItem item) {
        VisTable tile = new VisTable(true);
        tile.setBackground("window-bg");
        tile.pad(4f);
        tile.setUserObject(item);

        Actor preview = switch (item.category) {
            case SPRITES, TILED_TILES -> buildAssetImageThumb(item);
            case ANIMATIONS -> buildAnimationThumb(item);
            case PARTICLES -> buildParticleThumb(item);
            case PREFABS -> buildPrefabThumb(item);
            case TILED_ANIMATIONS -> buildTiledAnimationThumb(item);
        };

        tile.add(preview).size(tileSize, tileSize).top().left();

        tile.addListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                if (button != Input.Buttons.RIGHT) {
                    return false;
                }

                showContextMenu(item, event.getStageX(), event.getStageY());
                event.stop();
                return true;
            }
        });

        Tooltip tip = new Tooltip.Builder(item.label)
                .target(tile)
                .build();
        tip.setAppearDelayTime(0f);

        grid.add(tile).pad(TILE_PAD).top().left();
    }

    private void loadAssetItems(SceneMeta scene, RuntimeAvailabilityCategory category) {
        AssetMetaDatabase assetDb = loadAssetMetaDatabase();
        if (assetDb == null) return;

        Iterable<Integer> ids = switch (category) {
            case SPRITES -> app.getSceneService().getRuntimeAvailabilityService().listSpriteAssetIds(scene);
            case ANIMATIONS -> app.getSceneService().getRuntimeAvailabilityService().listAnimationAssetIds(scene);
            case TILED_TILES -> app.getSceneService().getRuntimeAvailabilityService().listTiledTileAssetIds(scene);
            default -> java.util.List.of();
        };

        for (Integer assetId : ids) {
            if (assetId == null || assetId <= 0) continue;
            AssetMeta meta = assetDb.findById(assetId);
            if (meta == null) continue;
            items.add(RuntimeAvailabilityItem.asset(category, meta.id, assetLabel(category, meta), meta.sourceRelPath));
        }
    }

    private void loadParticles(ProjectConfig cfg, SceneMeta scene) {
        for (String effectPath : app.getSceneService().getRuntimeAvailabilityService().listParticleEffectPaths(scene)) {
            if (effectPath == null || effectPath.isBlank()) continue;
            FileHandle effectFile = StudioFs.requireStudioProjectDir(cfg)
                    .child(StudioFs.DIR_ORIG_EFFECTS)
                    .child(effectPath);
            if (!effectFile.exists()) continue;
            items.add(RuntimeAvailabilityItem.particle(effectPath, effectFile.nameWithoutExtension()));
        }
    }

    private Actor buildPrefabThumb(RuntimeAvailabilityItem item) {
        if (item.previewFile != null && item.previewFile.exists()) {
            Texture texture = new Texture(item.previewFile);
            ownedTextures.add(texture);
            VisImage image = new VisImage(new TextureRegion(texture));
            image.setScaling(Scaling.fit);
            return image;
        }

        VisLabel label = new VisLabel(item.label);
        label.setWrap(true);
        label.setAlignment(Align.center);
        return label;
    }

    private Actor buildAssetImageThumb(RuntimeAvailabilityItem item) {
        Texture texture = item.sourceRelPath != null
                ? StandaloneTextureCache.getOrLoadProjectRelative(item.sourceRelPath)
                : null;
        if (texture == null) {
            return labelThumb(item.label);
        }

        VisImage image = new VisImage(new TextureRegion(texture));
        image.setScaling(Scaling.fit);
        return image;
    }

    private Actor buildAnimationThumb(RuntimeAvailabilityItem item) {
        ProjectConfig cfg = ProjectConfig.getInstance();
        if (cfg == null || item.sourceRelPath == null || item.sourceRelPath.isBlank()) {
            return labelThumb(item.label);
        }

        FileHandle dir = StudioFs.requireStudioProjectDir(cfg).child(item.sourceRelPath);
        if (!dir.exists() || !dir.isDirectory()) {
            return labelThumb(item.label);
        }

        Array<Texture> textures = new Array<>();
        for (FileHandle child : dir.list()) {
            if (child == null || child.isDirectory()) continue;
            if (!"png".equalsIgnoreCase(child.extension())) continue;

            Texture texture = StandaloneTextureCache.getOrLoadProjectRelative(item.sourceRelPath + "/" + child.name());
            if (texture != null) {
                textures.add(texture);
            }
        }

        if (textures.size == 0) {
            return labelThumb(item.label);
        }

        return animatedOrStaticThumb(textures);
    }

    private Actor buildParticleThumb(RuntimeAvailabilityItem item) {
        return labelThumb(item.label);
    }

    private Actor buildTiledAnimationThumb(RuntimeAvailabilityItem item) {
        AssetMetaDatabase assetDb = loadAssetMetaDatabase();
        TileAnimationsMetaDatabase animDb = loadTileAnimationsMetaDatabase();

        if (assetDb == null || animDb == null) {
            return new VisImage();
        }

        TileAnimationProjectDefData def = findTiledAnimationDef(animDb, item.tiledAnimationId);
        if (def == null || def.frameAssetIds == null || def.frameAssetIds.length == 0) {
            return new VisImage();
        }

        Array<Texture> textures = new Array<>();
        for (int frameAssetId : def.frameAssetIds) {
            AssetMeta frameMeta = assetDb.findById(frameAssetId);
            if (frameMeta == null || frameMeta.sourceRelPath == null || frameMeta.sourceRelPath.isBlank()) continue;

            Texture texture = StandaloneTextureCache.getOrLoadProjectRelative(frameMeta.sourceRelPath);
            if (texture != null) {
                textures.add(texture);
            }
        }

        if (textures.size == 0) {
            return new VisImage();
        }

        return animatedOrStaticThumb(textures);
    }

    private Actor animatedOrStaticThumb(Array<Texture> textures) {
        VisImage staticFrame = new VisImage(new TextureRegion(textures.first()));
        staticFrame.setScaling(Scaling.fit);

        Stack stack = new Stack();
        stack.setSize(tileSize, tileSize);
        stack.add(staticFrame);

        if (textures.size > 1) {
            AnimatedThumbnail animated = new AnimatedThumbnail(textures, 12f);
            animated.setSize(tileSize, tileSize);
            animated.setVisible(false);
            stack.add(animated);

            stack.addListener(new InputListener() {
                @Override
                public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor) {
                    if (pointer == -1) {
                        staticFrame.setVisible(false);
                        animated.setVisible(true);
                        animated.startPreview();
                    }
                }

                @Override
                public void exit(InputEvent event, float x, float y, int pointer, Actor toActor) {
                    if (pointer == -1) {
                        animated.stopPreview();
                        animated.setVisible(false);
                        staticFrame.setVisible(true);
                    }
                }
            });
        }

        return stack;
    }

    private Actor labelThumb(String text) {
        VisLabel label = new VisLabel(text);
        label.setWrap(true);
        label.setAlignment(Align.center);
        return label;
    }

    private void showEmptyState() {
        grid.clear();
        VisLabel label = new VisLabel(emptyStateText());
        label.setWrap(true);
        label.setAlignment(Align.center);
        grid.add(label).growX().pad(16f).center();
        grid.invalidateHierarchy();
        content.invalidateHierarchy();
    }

    private String emptyStateText() {
        return switch (category) {
            case SPRITES -> "Drop sprites here to make them available at runtime.";
            case ANIMATIONS -> "Drop animations here to make them available at runtime.";
            case PARTICLES -> "Drop particle effects here to make them available at runtime.";
            case PREFABS -> "Drop prefabs here to make them available at runtime.";
            case TILED_TILES -> "Drop tiles here to make them available at runtime.";
            case TILED_ANIMATIONS -> "Drop tiled animations here to make them available at runtime.";
        };
    }

    private void showContextMenu(RuntimeAvailabilityItem item, float stageX, float stageY) {
        if (getStage() == null) {
            return;
        }

        if (activeContextMenu != null) {
            activeContextMenu.remove();
            activeContextMenu = null;
        }

        PopupMenu menu = new PopupMenu();
        MenuItem remove = new MenuItem("Remove from Runtime Availability");
        remove.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                removeItem(item);
            }
        });
        menu.addItem(remove);
        menu.showMenu(getStage(), stageX, stageY);
        activeContextMenu = menu;
    }

    private void removeItem(RuntimeAvailabilityItem item) {
        if (item == null || app == null || app.getSceneService() == null) return;

        switch (item.category) {
            case SPRITES -> app.getSceneService().removeRuntimeAvailableSprite(item.assetId);
            case ANIMATIONS -> app.getSceneService().removeRuntimeAvailableAnimation(item.assetId);
            case PARTICLES -> app.getSceneService().removeRuntimeAvailableParticle(item.particleEffectPath);
            case PREFABS -> app.getSceneService().removeRuntimeAvailablePrefab(item.prefabId);
            case TILED_ANIMATIONS -> app.getSceneService().removeRuntimeAvailableTiledAnimation(item.tiledAnimationId);
            case TILED_TILES -> app.getSceneService().removeRuntimeAvailableTiledTile(item.assetId);
        }
        refresh();
    }

    private AssetMetaDatabase loadAssetMetaDatabase() {
        ProjectConfig cfg = ProjectConfig.getInstance();
        if (cfg == null) return null;

        FileHandle projectDir = StudioFs.requireStudioProjectDir(cfg);
        FileHandle metaFile = projectDir.child(StudioFs.FILE_ASSETS_JSON);
        if (!metaFile.exists()) return null;

        return AssetMetaDatabase.load(metaFile);
    }

    private TileAnimationsMetaDatabase loadTileAnimationsMetaDatabase() {
        ProjectConfig cfg = ProjectConfig.getInstance();
        if (cfg == null) return null;

        FileHandle projectDir = StudioFs.requireStudioProjectDir(cfg);
        FileHandle file = projectDir.child(RuntimeFs.FILE_TILE_ANIMATIONS_JSON);
        if (!file.exists()) return null;

        return TileAnimationsIO.load(file);
    }

    private TileAnimationProjectDefData findTiledAnimationDef(TileAnimationsMetaDatabase db, int tileAnimationId) {
        if (db == null || db.animations == null) return null;
        for (TileAnimationProjectDefData def : db.animations) {
            if (def != null && def.id == tileAnimationId) {
                return def;
            }
        }
        return null;
    }

    private String assetLabel(RuntimeAvailabilityCategory category, AssetMeta meta) {
        if (meta == null) return "";
        if (category == RuntimeAvailabilityCategory.TILED_TILES) {
            return "id:" + meta.id;
        }
        if (meta.logicalPath != null && !meta.logicalPath.isBlank()) {
            return StudioFs.removeExtension(new FileHandle(meta.logicalPath).name());
        }
        if (meta.sourceRelPath != null && !meta.sourceRelPath.isBlank()) {
            return StudioFs.removeExtension(new FileHandle(meta.sourceRelPath).name());
        }
        return "Asset " + meta.id;
    }

    private void disposeOwnedTextures() {
        for (Texture texture : ownedTextures) {
            if (texture != null) {
                texture.dispose();
            }
        }
        ownedTextures.clear();
    }

    private static final class RuntimeAvailabilityItem {
        private final RuntimeAvailabilityCategory category;
        private final String label;
        private final int assetId;
        private final String sourceRelPath;
        private final String particleEffectPath;
        private final String prefabId;
        private final FileHandle prefabFile;
        private final FileHandle previewFile;
        private final int tiledAnimationId;

        private RuntimeAvailabilityItem(RuntimeAvailabilityCategory category,
                                        String label,
                                        int assetId,
                                        String sourceRelPath,
                                        String particleEffectPath,
                                        String prefabId,
                                        FileHandle prefabFile,
                                        FileHandle previewFile,
                                        int tiledAnimationId) {
            this.category = category;
            this.label = label;
            this.assetId = assetId;
            this.sourceRelPath = sourceRelPath;
            this.particleEffectPath = particleEffectPath;
            this.prefabId = prefabId;
            this.prefabFile = prefabFile;
            this.previewFile = previewFile;
            this.tiledAnimationId = tiledAnimationId;
        }

        private static RuntimeAvailabilityItem asset(RuntimeAvailabilityCategory category,
                                                     int assetId,
                                                     String label,
                                                     String sourceRelPath) {
            return new RuntimeAvailabilityItem(
                    category,
                    label,
                    assetId,
                    sourceRelPath,
                    null,
                    null,
                    null,
                    null,
                    -1
            );
        }

        private static RuntimeAvailabilityItem particle(String effectPath, String label) {
            return new RuntimeAvailabilityItem(
                    RuntimeAvailabilityCategory.PARTICLES,
                    label != null && !label.isBlank() ? label : effectPath,
                    -1,
                    null,
                    effectPath,
                    null,
                    null,
                    null,
                    -1
            );
        }

        private static RuntimeAvailabilityItem prefab(String prefabId,
                                                      FileHandle prefabFile,
                                                      FileHandle previewFile) {
            return new RuntimeAvailabilityItem(
                    RuntimeAvailabilityCategory.PREFABS,
                    prefabId,
                    -1,
                    null,
                    null,
                    prefabId,
                    prefabFile,
                    previewFile,
                    -1
            );
        }

        private static RuntimeAvailabilityItem tiledAnimation(int id, String name) {
            return new RuntimeAvailabilityItem(
                    RuntimeAvailabilityCategory.TILED_ANIMATIONS,
                    name != null && !name.isBlank() ? name : ("Animation " + id),
                    -1,
                    null,
                    null,
                    null,
                    null,
                    null,
                    id
            );
        }
    }
}
