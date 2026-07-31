package games.pixscape.studio.ui.asset;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Cursor;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.ui.Stack;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.Scaling;
import com.kotcrab.vis.ui.widget.*;
import com.kotcrab.vis.ui.widget.spinner.IntSpinnerModel;
import com.kotcrab.vis.ui.widget.spinner.Spinner;
import games.pixscape.runtime.component.AnimationComponent;
import games.pixscape.runtime.helper.RuntimeFs;
import games.pixscape.studio.asset.*;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.event.GetScrollListener;
import games.pixscape.studio.event.LoseScroolListener;
import games.pixscape.studio.io.StudioFs;
import games.pixscape.studio.io.TileAnimationsIO;
import games.pixscape.studio.service.StandaloneTextureCache;
import games.pixscape.studio.service.asset.AssetUsageScanner;
import games.pixscape.studio.service.prefab.PrefabAssetItem;
import games.pixscape.studio.service.prefab.PrefabBrowserService;
import games.pixscape.studio.service.prefab.PrefabPreviewWriter;
import games.pixscape.studio.ui.asset.dnd.DragContext;
import games.pixscape.studio.ui.asset.dnd.DragCursors;
import games.pixscape.studio.ui.asset.dnd.DragPayload;
import games.pixscape.studio.ui.config.CommonLayout;
import games.pixscape.studio.ui.main.StudioApplicationAdapter;
import games.pixscape.studio.ui.property.entityproperties.AnimationClipsDialog;

import java.util.function.Consumer;

public final class AssetsThumbsView extends VisTable {

    // UI
    private final VisTable content = new VisTable();
    private final VisTable grid = new VisTable();
    private final VisScrollPane scroll;
    private AssetNode selectedNode;
    private final Array<VisTable> tileWidgets = new Array<>();
    private AssetNode currentFolder;

    private final VisTable header = new VisTable();
    private final VisTable headerLeft = new VisTable(true);
    private final VisTable headerRight = new VisTable(true);
    private final Array<Actor> headerLeftActions = new Array<>();
    private final VisTextButton newAnimationBtn;
    private Runnable createTiledAnimationListener;
    private final Array<AssetNode> selectedNodes = new Array<>();
    private int selectionAnchorIndex = -1;

    // state
    private final Array<AssetNode> currentAssets = new Array<>();
    private Consumer<AssetNode> selectionListener;
    private static Cursor currentCursor;

    private float tileSize = 48f;
    private static final float TILE_PAD = 4f;
    private boolean layoutDirty = false;
    private float lastWidth = -1f;

    private final StudioApplicationAdapter app;

    private final ThumbsLayoutStrategy galleryLayoutStrategy = new ResponsiveGalleryLayoutStrategy();
    private ThumbsLayoutStrategy currentLayoutStrategy = galleryLayoutStrategy;

    private PopupMenu activeAssetMenu;
    private final PrefabBrowserService prefabBrowserService = new PrefabBrowserService();

    private TiledAnimationFrameRemoveListener tiledAnimationFrameRemoveListener;
    private TiledAnimationFrameDurationChangeListener tiledAnimationFrameDurationChangeListener;
    private TiledAnimationFrameMoveListener tiledAnimationFrameMoveListener;
    private TiledAnimationDeleteListener tiledAnimationDeleteListener;

    private final Array<Texture> ownedPrefabThumbTextures = new Array<>();

    @FunctionalInterface
    public interface TiledAnimationFrameRemoveListener {
        void accept(int tileAnimationId, int frameIndex);
    }

    @FunctionalInterface
    public interface TiledAnimationFrameDurationChangeListener {
        void accept(int tileAnimationId, int frameIndex, int durationMs);
    }

    @FunctionalInterface
    public interface TiledAnimationFrameMoveListener {
        void accept(int tileAnimationId, int fromIndex, int toIndex);
    }

    @FunctionalInterface
    public interface TiledAnimationDeleteListener {
        void accept(int tileAnimationId, String name);
    }

    public AssetsThumbsView(StudioApplicationAdapter app) {
        this.app = app;
        top().left().pad(6);

        content.top().left();
        content.add(grid).expandX().fillX().top().left();

        grid.top().left();

        scroll = new VisScrollPane(content);
        scroll.setForceScroll(true, true);
        scroll.setFadeScrollBars(false);
        scroll.addListener(new GetScrollListener(scroll));
        scroll.addListener(new LoseScroolListener());

        currentLayoutStrategy.configureScrollPane(scroll);

        newAnimationBtn = new VisTextButton("New tiled animation");
        newAnimationBtn.setColor(CommonLayout.BUTTON_COLOR);

        newAnimationBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (createTiledAnimationListener != null) {
                    createTiledAnimationListener.run();
                }
            }
        });

        buildHeader();
        add(header).growX().padBottom(4f).row();
        add(scroll).grow();

        EventFlow.i().subscribe(EventFlow.PrefabsChanged.class, evt -> {
            if (currentFolder != null && currentFolder.root == AssetNode.Root.PREFABS) {
                showForNode(currentFolder);
            }
        });
    }

    public void setTileSelectionListener(Consumer<AssetNode> listener) {
        this.selectionListener = listener;
    }

    public void setCreateTiledAnimationListener(Runnable listener) {
        this.createTiledAnimationListener = listener;
    }

    public void addHeaderLeftAction(Actor action) {
        if (action == null || headerLeftActions.contains(action, true)) {
            return;
        }

        headerLeftActions.add(action);
        refreshHeaderActions();
    }

    public void setTiledAnimationFrameRemoveListener(TiledAnimationFrameRemoveListener listener) {
        this.tiledAnimationFrameRemoveListener = listener;
    }

    public void setTiledAnimationFrameDurationChangeListener(
            TiledAnimationFrameDurationChangeListener listener
    ) {
        this.tiledAnimationFrameDurationChangeListener = listener;
    }

    public void setTiledAnimationFrameMoveListener(TiledAnimationFrameMoveListener listener) {
        this.tiledAnimationFrameMoveListener = listener;
    }

    public void setTiledAnimationDeleteListener(TiledAnimationDeleteListener listener) {
        this.tiledAnimationDeleteListener = listener;
    }

    private void buildHeader() {
        header.clear();
        header.top().left();

        headerLeft.top().left();
        headerRight.top().right();

        header.add(headerLeft).left().expandX().fillX();
        header.add(headerRight).right();

        buildThumbSizeControl();
        refreshHeaderActions();
    }

    private void buildThumbSizeControl() {
        headerRight.clear();

        IntSpinnerModel model = new IntSpinnerModel(48, 32, 128, 8);
        // No reusable studio wrapper exists for this VisUI Spinner yet.
        Spinner sizeSpinner = new Spinner("Thumb size", model);

        sizeSpinner.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                setTileSize(model.getValue());
            }
        });

        headerRight.add(sizeSpinner).width(80f).padRight(50);
    }

    private void refreshHeaderActions() {
        boolean show =
                currentFolder != null
                        && currentFolder.kind == AssetNode.Kind.TILED_ANIMATIONS_FOLDER;

        headerLeft.clear();
        for (Actor action : headerLeftActions) {
            headerLeft.add(action).left().padRight(4f);
        }
        if (show) {
            headerLeft.add(newAnimationBtn).left();
        }

        headerLeft.invalidateHierarchy();
        header.invalidateHierarchy();
    }

    @Override
    public void layout() {
        super.layout();

        float viewportWidth = scroll.getWidth();
        if (viewportWidth <= 0f) return;

        if (!layoutDirty && Math.abs(viewportWidth - lastWidth) < 1f) {
            return;
        }

        lastWidth = viewportWidth;
        layoutDirty = false;

        rebuildGrid();
    }

    public void clear() {
        selectedNode = null;
        currentFolder = null;
        currentAssets.clear();
        tileWidgets.clear();
        grid.clear();
        disposeOwnedPrefabThumbTextures();

        currentLayoutStrategy = galleryLayoutStrategy;
        currentLayoutStrategy.configureScrollPane(scroll);
        refreshHeaderActions();

        layoutDirty = false;
        lastWidth = -1f;
    }

    public void showForNode(AssetNode folder) {
        currentFolder = folder;
        disposeOwnedPrefabThumbTextures();
        refreshHeaderActions();

        selectedNode = null;
        clearMultiSelection();
        currentAssets.clear();
        tileWidgets.clear();
        grid.clear();

        if (folder == null) {
            currentLayoutStrategy = galleryLayoutStrategy;
            currentLayoutStrategy.configureScrollPane(scroll);
            return;
        }

        if (folder.kind == AssetNode.Kind.TILED_ANIMATIONS_FOLDER) {
            currentLayoutStrategy = galleryLayoutStrategy;
            currentLayoutStrategy.configureScrollPane(scroll);
            loadTiledAnimations();
            layoutDirty = true;
            rebuildGrid();
            return;
        }

        if (folder.kind == AssetNode.Kind.TILED_ANIMATION) {
            currentLayoutStrategy = galleryLayoutStrategy;
            currentLayoutStrategy.configureScrollPane(scroll);
            loadTiledAnimationFrames(folder);
            layoutDirty = true;
            rebuildGrid();
            return;
        }

        if (folder.root == AssetNode.Root.PREFABS) {
            currentLayoutStrategy = galleryLayoutStrategy;
            currentLayoutStrategy.configureScrollPane(scroll);
            loadPrefabs();
            layoutDirty = true;
            rebuildGrid();
            return;
        }

        if (folder.kind != AssetNode.Kind.FOLDER) {
            currentLayoutStrategy = galleryLayoutStrategy;
            currentLayoutStrategy.configureScrollPane(scroll);
            return;
        }

        if (folder.root == AssetNode.Root.TILES &&
                (folder.path == null || folder.path.isBlank())) {
            currentLayoutStrategy = galleryLayoutStrategy;
            currentLayoutStrategy.configureScrollPane(scroll);
            showTilesRootPlaceholder();
            return;
        }

        currentAssets.addAll(AssetsFolderScanner.scan(folder));
        resolveLayoutStrategyAndPrepareAssets();

        layoutDirty = true;
        rebuildGrid();
    }

    private void loadPrefabs() {
        ProjectConfig cfg = ProjectConfig.getInstance();
        Array<PrefabAssetItem> items = prefabBrowserService.scan(cfg);
        for (PrefabAssetItem item : items) {
            AssetNode node = new AssetNode(
                    AssetNode.Kind.PREFAB,
                    AssetNode.Root.PREFABS,
                    item.prefabFile().path(),
                    item.name(),
                    null
            );
            currentAssets.add(node);
        }
    }

    private void loadTiledAnimations() {
        TileAnimationsMetaDatabase db = loadTileAnimationsMetaDatabase();
        if (db == null || db.animations == null || db.animations.size == 0) {
            showEmptyTiledAnimationsPlaceholder();
            return;
        }

        for (TileAnimationProjectDefData def : db.animations) {
            if (def == null) {
                continue;
            }

            AssetNode node = new AssetNode(
                    AssetNode.Kind.TILED_ANIMATION,
                    AssetNode.Root.TILES,
                    AssetNode.TILED_ANIMATIONS_NODE_PATH + "/" + def.id,
                    def.name,
                    null
            );
            node.tileAnimationId = def.id;
            currentAssets.add(node);
        }
    }

    private void loadTiledAnimationFrames(AssetNode animationNode) {
        TileAnimationsMetaDatabase animDb = loadTileAnimationsMetaDatabase();
        AssetMetaDatabase assetDb = loadAssetMetaDatabase();

        if (animDb == null || assetDb == null) {
            return;
        }

        TileAnimationProjectDefData def = findTiledAnimationDef(animDb, animationNode.tileAnimationId);
        if (def == null || def.frameAssetIds == null || def.frameDurationsMs == null) {
            return;
        }

        for (int i = 0; i < def.frameAssetIds.length; i++) {
            int frameAssetId = def.frameAssetIds[i];
            int durationMs = def.frameDurationsMs[i];

            AssetMeta meta = assetDb.findById(frameAssetId);
            if (meta == null) {
                continue;
            }

            AssetNode node = AssetNode.fromAssetMeta(
                    AssetNode.Kind.TILED_ANIMATION_FRAME,
                    AssetNode.Root.TILES,
                    meta.sourceRelPath(),
                    meta
            );
            node.tileAnimationId = animationNode.tileAnimationId;
            node.durationMs = durationMs;
            node.frameIndex = i;

            currentAssets.add(node);
        }
    }

    private TileAnimationsMetaDatabase loadTileAnimationsMetaDatabase() {
        ProjectConfig cfg = ProjectConfig.getInstance();
        if (cfg == null) return null;

        FileHandle projectDir = StudioFs.requireStudioProjectDir(cfg);
        FileHandle file = projectDir.child(RuntimeFs.FILE_TILE_ANIMATIONS_JSON);
        if (!file.exists()) return null;

        return TileAnimationsIO.load(file);
    }

    private void showEmptyTiledAnimationsPlaceholder() {
        grid.clear();
        tileWidgets.clear();

        VisLabel label = new VisLabel(
                "No tiled animations yet.\n\n" +
                        "Create one from Tiles -> Animations."
        );
        label.setWrap(true);
        label.setAlignment(Align.center);

        grid.add(label)
                .growX()
                .pad(16f)
                .center();

        grid.row();
        grid.invalidateHierarchy();
        content.invalidateHierarchy();
    }

    private void showTilesRootPlaceholder() {
        grid.clear();
        tileWidgets.clear();

        VisLabel label = new VisLabel(
                "Select a tileset folder to view its tiles.\n\n" +
                        "Tiles are displayed inside each tileset folder to preserve their original layout."
        );
        label.setWrap(true);
        label.setAlignment(Align.center);

        grid.add(label)
                .growX()
                .pad(16f)
                .center();

        grid.row();
        grid.invalidateHierarchy();
        content.invalidateHierarchy();
    }

    private void rebuildGrid() {
        grid.clear();
        tileWidgets.clear();

        if (currentAssets.size == 0) {
            grid.invalidateHierarchy();
            content.invalidateHierarchy();
            return;
        }

        float availableWidth = scroll.getWidth();
        if (availableWidth <= 0f) return;

        currentLayoutStrategy.rebuildGrid(
                grid,
                content,
                currentAssets,
                availableWidth,
                tileSize,
                TILE_PAD,
                this::addThumb
        );

        grid.invalidateHierarchy();
        content.invalidateHierarchy();
    }

    private void resolveLayoutStrategyAndPrepareAssets() {
        currentLayoutStrategy = galleryLayoutStrategy;
        currentLayoutStrategy.configureScrollPane(scroll);

        if (!isTilesFolder(currentFolder)) {
            return;
        }

        AssetMetaDatabase db = loadAssetMetaDatabase();
        if (db == null) {
            return;
        }

        TilesetAssetMeta tilesetMeta = findTilesetMetaForCurrentFolder(db);
        if (tilesetMeta == null || tilesetMeta.columns <= 0) {
            return;
        }

        Array<AssetNode> filteredTiles = new Array<>();
        for (AssetNode node : currentAssets) {
            TileAssetMeta tileMeta = findTileMetaForNode(db, node);
            if (tileMeta != null && tileMeta.tilesetId == tilesetMeta.id()) {
                filteredTiles.add(node);
            }
        }

        filteredTiles.sort((a, b) -> Integer.compare(
                sheetIndexOf(db, a),
                sheetIndexOf(db, b)
        ));

        currentAssets.clear();
        currentAssets.addAll(filteredTiles);

        if (!shouldPreserveTilesetLayout(tilesetMeta)) {
            return;
        }

        currentLayoutStrategy = new FixedTilesetLayoutStrategy(tilesetMeta.columns);
        currentLayoutStrategy.configureScrollPane(scroll);
    }

    static boolean shouldPreserveTilesetLayout(TilesetAssetMeta tilesetMeta) {
        return tilesetMeta != null
                && tilesetMeta.columns > 0
                && tilesetMeta.sourceRelPath() != null
                && !tilesetMeta.sourceRelPath().isBlank();
    }

    private boolean isTilesFolder(AssetNode folder) {
        return folder != null
                && folder.kind == AssetNode.Kind.FOLDER
                && folder.root == AssetNode.Root.TILES
                && folder.path != null
                && !folder.path.isBlank();
    }

    private AssetMetaDatabase loadAssetMetaDatabase() {
        ProjectConfig cfg = ProjectConfig.getInstance();
        if (cfg == null) return null;

        FileHandle projectDir = StudioFs.requireStudioProjectDir(cfg);
        FileHandle metaFile = projectDir.child(StudioFs.FILE_ASSETS_JSON);
        if (!metaFile.exists()) return null;

        return AssetMetaDatabase.load(metaFile);
    }

    private TilesetAssetMeta findTilesetMetaForCurrentFolder(AssetMetaDatabase db) {
        if (db == null || currentFolder == null || currentFolder.path == null) return null;

        String logicalPath = StudioFs.PREFIX_TILES + normalizeFolderPath(currentFolder.path);
        AssetMeta meta = db.findByLogicalPath(logicalPath);
        return (meta instanceof TilesetAssetMeta tilesetMeta) ? tilesetMeta : null;
    }

    private String normalizeFolderPath(String path) {
        if (path == null) return "";
        String normalized = path.replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    private TileAssetMeta findTileMetaForNode(AssetMetaDatabase db, AssetNode node) {
        AssetMeta meta = findMetaForNode(db, node);
        return (meta instanceof TileAssetMeta tileMeta) ? tileMeta : null;
    }

    private int sheetIndexOf(AssetMetaDatabase db, AssetNode node) {
        TileAssetMeta tileMeta = findTileMetaForNode(db, node);
        return tileMeta != null ? tileMeta.sheetIndex : Integer.MAX_VALUE;
    }

    private AssetMeta findMetaForNode(AssetMetaDatabase db, AssetNode node) {
        if (db == null || node == null) return null;

        String sourceRelPath = buildSourceRelPath(node);
        if (sourceRelPath == null || sourceRelPath.isBlank()) return null;

        AssetType type = assetTypeForNode(node);
        return type != null
                ? db.findUniqueBySourceRelPath(sourceRelPath, type)
                : null;
    }

    private String buildSourceRelPath(AssetNode node) {
        if (node == null || node.path == null || node.path.isBlank()) return null;

        return switch (node.root) {
            case IMAGES -> StudioFs.DIR_ORIG_IMAGES + "/" + node.path;
            case ANIMATIONS -> StudioFs.DIR_ORIG_ANIMATIONS + "/" + node.path;
            case PARTICLES -> StudioFs.DIR_ORIG_EFFECTS + "/" + node.path;
            case TILES -> StudioFs.DIR_ORIG_TILES + "/" + node.path;
            case PREFABS -> null;
        };
    }

    private void addThumb(AssetNode node) {
        VisTable tile = new VisTable(true);
        tile.setBackground("window-bg");
        tile.pad(4);

        tileWidgets.add(tile);
        tile.setUserObject(node);

        tile.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                handleAssetSelectionClick(node);
                event.stop();
            }
        });

        tile.addListener(new ClickListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                if (button != Input.Buttons.RIGHT) return false;

                if (node.kind == AssetNode.Kind.TILED_ANIMATION_FRAME) {
                    showTiledAnimationFrameContextMenu(node, event.getStageX(), event.getStageY());
                    event.stop();
                    return true;
                }

                if (!supportsAssetContextMenu(node)) return false;

                showAssetContextMenu(node, event.getStageX(), event.getStageY());
                event.stop();
                return true;
            }
        });

        Actor contentActor;

        switch (node.kind) {
            case ANIMATION -> {
                ProjectConfig cfg = ProjectConfig.getInstance();
                FileHandle projectDir = StudioFs.requireStudioProjectDir(cfg);

                FileHandle animDir = projectDir
                        .child(StudioFs.DIR_ORIG_ANIMATIONS)
                        .child(node.path);

                Array<FileHandle> frameFiles = new Array<>();

                if (animDir.exists() && animDir.isDirectory()) {
                    for (FileHandle child : animDir.list()) {
                        if (child == null || child.isDirectory()) continue;
                        if ("png".equalsIgnoreCase(child.extension())) {
                            frameFiles.add(child);
                        }
                    }
                }

                if (frameFiles.size > 0) {
                    frameFiles.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));

                    Array<Texture> textures = new Array<>();
                    for (FileHandle fh : frameFiles) {
                        String rel = StudioFs.DIR_ORIG_ANIMATIONS + "/" + node.path + "/" + fh.name();
                        Texture tex = StandaloneTextureCache.getOrLoadProjectRelative(rel);
                        textures.add(tex);
                    }

                    VisImage staticFrame = new VisImage(new TextureRegion(textures.first()));
                    staticFrame.setScaling(Scaling.fit);

                    Stack stack = new Stack();
                    stack.setSize(tileSize, tileSize);
                    stack.add(staticFrame);

                    AnimatedThumbnail animated = new AnimatedThumbnail(textures, 12f);
                    animated.setSize(tileSize, tileSize);
                    animated.setVisible(false);
                    stack.add(animated);

                    tile.addListener(new InputListener() {
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

                    contentActor = stack;
                } else {
                    contentActor = new VisImage();
                }
            }

            case PARTICLE -> {
                VisLabel name = new VisLabel(node.name);
                name.setAlignment(Align.center);
                name.setWrap(true);

                ProjectConfig cfg = ProjectConfig.getInstance();
                FileHandle projectDir = StudioFs.requireStudioProjectDir(cfg);

                FileHandle particleFile = projectDir
                        .child(StudioFs.DIR_ORIG_EFFECTS)
                        .child(node.path);

                FileHandle imagesDir = projectDir
                        .child(StudioFs.DIR_ORIG_IMAGES);

                ParticleThumbnail effectThumb = new ParticleThumbnail(particleFile, imagesDir);
                effectThumb.setSize(tileSize, tileSize);
                effectThumb.setVisible(false);

                Stack stack = new Stack();
                stack.add(name);
                stack.add(effectThumb);
                stack.setSize(tileSize, tileSize);

                tile.addListener(new InputListener() {
                    @Override
                    public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor) {
                        if (pointer == -1) {
                            effectThumb.setVisible(true);
                            effectThumb.startPreview();
                        }
                    }

                    @Override
                    public void exit(InputEvent event, float x, float y, int pointer, Actor toActor) {
                        if (pointer == -1) {
                            effectThumb.stopPreview();
                            effectThumb.setVisible(false);
                        }
                    }
                });

                contentActor = stack;
            }

            case TILED_ANIMATION -> {
                Actor actor = buildTiledAnimationThumb(node);
                contentActor = actor != null ? actor : new VisImage();
            }

            case TILED_ANIMATION_FRAME -> {
                contentActor = buildEditableTiledAnimationFrameThumb(node);
            }

            case PREFAB -> {
                contentActor = buildPrefabThumb(node);
            }

            default -> {
                TextureRegion region = AssetPreviewCache.get(node);
                VisImage img = (region != null) ? new VisImage(region) : new VisImage();
                img.setScaling(Scaling.fit);
                contentActor = img;
            }
        }

        if (node.kind == AssetNode.Kind.TILED_ANIMATION_FRAME) {
            tile.add(contentActor).width(tileSize).top().left();
        } else {
            tile.add(contentActor).size(tileSize, tileSize).top().left();
        }

        Tooltip tip = new Tooltip.Builder(node.tooltipText(), Align.left)
                .target(tile)
                .build();
        tip.setAppearDelayTime(0f);

        grid.add(tile).pad(TILE_PAD).top().left();

        attachDnD(tile, node);
    }

    private void showTiledAnimationFrameContextMenu(AssetNode node, float stageX, float stageY) {
        if (activeAssetMenu != null) {
            activeAssetMenu.remove();
            activeAssetMenu = null;
        }

        PopupMenu menu = new PopupMenu();

        MenuItem remove = new MenuItem("Remove from animation");
        remove.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (tiledAnimationFrameRemoveListener != null) {
                    tiledAnimationFrameRemoveListener.accept(node.tileAnimationId, node.frameIndex);
                }
            }
        });

        MenuItem moveLeft = new MenuItem("Move left");
        moveLeft.setDisabled(node.frameIndex <= 0);
        moveLeft.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (tiledAnimationFrameMoveListener != null && node.frameIndex > 0) {
                    tiledAnimationFrameMoveListener.accept(
                            node.tileAnimationId,
                            node.frameIndex,
                            node.frameIndex - 1
                    );
                }
            }
        });

        MenuItem moveRight = new MenuItem("Move right");
        moveRight.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (tiledAnimationFrameMoveListener != null) {
                    tiledAnimationFrameMoveListener.accept(
                            node.tileAnimationId,
                            node.frameIndex,
                            node.frameIndex + 1
                    );
                }
            }
        });

        menu.addItem(remove);
        menu.addItem(moveLeft);
        menu.addItem(moveRight);
        menu.showMenu(getStage(), stageX, stageY);
        activeAssetMenu = menu;
    }

    private Actor buildTiledAnimationThumb(AssetNode node) {
        if (node == null || node.tileAnimationId <= 0) {
            return new VisImage();
        }

        AssetMetaDatabase assetDb = loadAssetMetaDatabase();
        TileAnimationsMetaDatabase animDb = loadTileAnimationsMetaDatabase();

        if (assetDb == null || animDb == null) {
            return new VisImage();
        }

        TileAnimationProjectDefData def = findTiledAnimationDef(animDb, node.tileAnimationId);
        if (def == null || def.frameAssetIds == null || def.frameAssetIds.length == 0) {
            return new VisImage();
        }

        Array<Texture> textures = new Array<>();

        for (int frameAssetId : def.frameAssetIds) {
            AssetMeta frameMeta = assetDb.findById(frameAssetId);
            if (frameMeta == null || frameMeta.sourceRelPath() == null || frameMeta.sourceRelPath().isBlank()) {
                continue;
            }

            Texture tex = StandaloneTextureCache.getOrLoadProjectRelative(frameMeta.sourceRelPath());
            if (tex != null) {
                textures.add(tex);
            }
        }

        if (textures.size == 0) {
            return new VisImage();
        }

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

    private Actor buildTiledAnimationFrameThumb(AssetNode node) {
        if (node == null || node.assetId <= 0) {
            return new VisImage();
        }

        AssetMetaDatabase assetDb = loadAssetMetaDatabase();
        if (assetDb == null) {
            return new VisImage();
        }

        AssetMeta meta = assetDb.findById(node.assetId);
        if (meta == null || meta.sourceRelPath() == null || meta.sourceRelPath().isBlank()) {
            return new VisImage();
        }

        Texture texture = StandaloneTextureCache.getOrLoadProjectRelative(meta.sourceRelPath());
        if (texture == null) {
            return new VisImage();
        }

        VisImage image = new VisImage(new TextureRegion(texture));
        image.setScaling(Scaling.fit);
        return image;
    }

    private Actor buildEditableTiledAnimationFrameThumb(AssetNode node) {
        VisTable table = new VisTable(true);
        table.top();

        Actor imageActor = buildTiledAnimationFrameThumb(node);
        table.add(imageActor).size(tileSize, tileSize).row();

        VisTextField durationField = new VisTextField(
                node.durationMs > 0 ? Integer.toString(node.durationMs) : "300"
        );
        durationField.setMessageText("ms");
        durationField.setTextFieldFilter(new VisTextField.TextFieldFilter.DigitsOnlyFilter());
        durationField.setAlignment(Align.center);

        durationField.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (durationField.getText() == null || durationField.getText().isBlank()) {
                    return;
                }

                int durationMs;
                try {
                    durationMs = Integer.parseInt(durationField.getText());
                } catch (NumberFormatException ex) {
                    return;
                }

                if (durationMs <= 0) {
                    return;
                }

                if (tiledAnimationFrameDurationChangeListener != null) {
                    tiledAnimationFrameDurationChangeListener.accept(
                            node.tileAnimationId,
                            node.frameIndex,
                            durationMs
                    );
                }
            }
        });

        table.add(durationField).width(tileSize).row();

        return table;
    }

    private TileAnimationProjectDefData findTiledAnimationDef(TileAnimationsMetaDatabase db, int tileAnimationId) {
        if (db == null || db.animations == null) {
            return null;
        }

        for (TileAnimationProjectDefData def : db.animations) {
            if (def != null && def.id == tileAnimationId) {
                return def;
            }
        }
        return null;
    }

    private void handleAssetSelectionClick(AssetNode node) {
        boolean ctrl = Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT)
                || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT);
        boolean shift = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)
                || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT);

        if (!isMultiSelectableAsset(node)) {
            clearMultiSelection();
            setSelectedNode(node);
            return;
        }

        int clickedIndex = indexOfCurrentAsset(node);
        if (clickedIndex < 0 || clickedIndex >= currentAssets.size) {
            clearMultiSelection();
            setSelectedNode(node);
            return;
        }

        if (shift && selectionAnchorIndex >= 0) {
            selectAssetRange(node, clickedIndex);
            return;
        }

        if (ctrl) {
            toggleAssetSelection(node, clickedIndex);
            return;
        }

        clearMultiSelection();
        addToSelection(node);
        selectionAnchorIndex = clickedIndex;
        setSelectedNode(node);
    }

    private void selectAssetRange(AssetNode node, int clickedIndex) {
        int anchorIndex = selectionAnchorIndex;

        if (anchorIndex < 0 || anchorIndex >= currentAssets.size) {
            anchorIndex = clickedIndex;
        }

        clearMultiSelection();

        int min = Math.min(anchorIndex, clickedIndex);
        int max = Math.max(anchorIndex, clickedIndex);

        for (int i = min; i <= max; i++) {
            AssetNode candidate = currentAssets.get(i);
            if (isMultiSelectableAsset(candidate)) {
                addToSelection(candidate);
            }
        }

        selectedNode = node;
        selectionAnchorIndex = anchorIndex;
        refreshSelectionVisuals();

        if (selectionListener != null) {
            selectionListener.accept(node);
        }
    }

    private void toggleAssetSelection(AssetNode node, int clickedIndex) {
        boolean wasSelected = isSelected(node);

        if (wasSelected) {
            removeFromSelection(node);

            if (selectedNode == node) {
                selectedNode = selectedNodes.size > 0
                        ? selectedNodes.peek()
                        : null;
            }
        } else {
            addToSelection(node);
            selectedNode = node;
        }

        selectionAnchorIndex = clickedIndex;
        refreshSelectionVisuals();

        if (selectionListener != null && selectedNode != null) {
            selectionListener.accept(selectedNode);
        }
    }

    private boolean isMultiSelectableAsset(AssetNode node) {
        if (node == null) return false;

        return switch (node.kind) {
            case IMAGE, ANIMATION, PARTICLE, PREFAB -> true;
            default -> false;
        };
    }

    private int indexOfCurrentAsset(AssetNode node) {
        for (int i = 0; i < currentAssets.size; i++) {
            if (currentAssets.get(i) == node) {
                return i;
            }
        }
        return -1;
    }

    private boolean isSelected(AssetNode node) {
        return selectedNodes.contains(node, true);
    }

    private void clearMultiSelection() {
        selectedNodes.clear();
        selectionAnchorIndex = -1;
    }

    private void addToSelection(AssetNode node) {
        if (node != null && !selectedNodes.contains(node, true)) {
            selectedNodes.add(node);
        }
    }

    private void removeFromSelection(AssetNode node) {
        selectedNodes.removeValue(node, true);
    }

    private boolean supportsAssetContextMenu(AssetNode node) {
        if (node == null) return false;

        return switch (node.kind) {
            case IMAGE, ANIMATION, PARTICLE, PREFAB, TILED_ANIMATION -> true;
            default -> false;
        };
    }

    private void showAssetContextMenu(AssetNode node, float stageX, float stageY) {
        if (activeAssetMenu != null) {
            activeAssetMenu.remove();
            activeAssetMenu = null;
        }

        PopupMenu menu = new PopupMenu();

        if (node.kind == AssetNode.Kind.ANIMATION) {
            MenuItem editClips = new MenuItem("Edit clips");
            editClips.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    showEditAnimationClipsDialog(node);
                }
            });
            menu.addItem(editClips);
        }

        Array<AssetNode> deleteTargets = deleteTargetsFor(node);
        String deleteLabel = deleteLabelFor(node, deleteTargets);
        MenuItem delete = new MenuItem(deleteLabel);
        delete.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (node.kind == AssetNode.Kind.PREFAB) {
                    showDeletePrefabDialog(deleteTargets);
                } else if (node.kind == AssetNode.Kind.TILED_ANIMATION) {
                    if (tiledAnimationDeleteListener != null) {
                        tiledAnimationDeleteListener.accept(node.tileAnimationId, node.name);
                    }
                } else {
                    showDeleteAssetDialog(deleteTargets);
                }
            }
        });

        menu.addItem(delete);
        menu.showMenu(getStage(), stageX, stageY);

        activeAssetMenu = menu;
    }

    private Array<AssetNode> deleteTargetsFor(AssetNode node) {
        Array<AssetNode> targets = new Array<>();

        if (isMultiSelectableAsset(node) && isSelected(node) && selectedNodes.size > 1) {
            for (AssetNode selected : selectedNodes) {
                if (isSameAssetSelectionFamily(node, selected)) {
                    targets.add(selected);
                }
            }

            targets.sort((a, b) -> Integer.compare(indexOfCurrentAsset(a), indexOfCurrentAsset(b)));
            return targets;
        }

        if (node != null) {
            targets.add(node);
        }
        return targets;
    }

    private String deleteLabelFor(AssetNode node, Array<AssetNode> targets) {
        if (targets != null && targets.size > 1 && isMultiSelectableAsset(node)) {
            return "Delete " + targets.size + " " + pluralAssetTypeLabel(targets);
        }

        return switch (node.kind) {
            case PREFAB -> "Delete prefab";
            case TILED_ANIMATION -> "Delete";
            default -> "Delete \"" + node.name + "\"";
        };
    }

    private String pluralAssetTypeLabel(Array<AssetNode> targets) {
        if (targets == null || targets.size == 0) {
            return "assets";
        }

        AssetNode first = targets.first();
        for (AssetNode target : targets) {
            if (target.kind != first.kind || target.root != first.root) {
                return "assets";
            }
        }

        return switch (first.kind) {
            case IMAGE -> first.root == AssetNode.Root.TILES ? "tiles" : "images";
            case ANIMATION -> "animations";
            case PARTICLE -> "particle effects";
            case PREFAB -> "prefabs";
            default -> "assets";
        };
    }

    private void showEditAnimationClipsDialog(AssetNode node) {
        if (node == null || node.kind != AssetNode.Kind.ANIMATION) return;

        ProjectConfig cfg = ProjectConfig.getInstance();
        if (cfg == null || app == null || app.getSceneService() == null) return;

        String sourceRelPath = buildSourceRelPath(node);
        if (sourceRelPath == null || sourceRelPath.isBlank()) return;

        int frameCount = countAnimationFrames(node);
        int frameMax = Math.max(0, frameCount - 1);

        AnimationAssetMeta meta = app.getSceneService().findAnimationAssetMetaBySourceRelPath(sourceRelPath);
        if (meta == null) {
            showSimpleErrorDialog("Animation asset metadata not found.");
            return;
        }

        AnimationComponent component = buildAnimationComponentForAsset(node, meta, frameMax);
        AnimationClipsDialog dialog = new AnimationClipsDialog(component, () -> {
            app.getSceneService().saveAnimationAssetClips(
                    sourceRelPath,
                    component,
                    frameCount,
                    component.fps
            );
        }, frameMax);
        dialog.show(getStage());
    }

    private AnimationComponent buildAnimationComponentForAsset(AssetNode node,
                                                               AnimationAssetMeta meta,
                                                               int frameMax) {
        AnimationComponent component = new AnimationComponent();
        component.animation = node.path;
        component.fps = meta.fps > 0f ? meta.fps : 12f;
        component.loop = true;
        component.playing = true;
        component.currentClip = meta.currentClip;

        if (component.clips == null) {
            component.clips = new ObjectMap<>();
        }
        component.clips.clear();
        if (meta.clips != null) {
            for (ObjectMap.Entry<String, AnimationComponent.Clip> entry : meta.clips) {
                if (entry == null || entry.key == null || entry.key.isBlank() || entry.value == null) {
                    continue;
                }

                AnimationComponent.Clip src = entry.value;
                AnimationComponent.Clip copy = new AnimationComponent.Clip(src.start, src.end);
                copy.flipX = src.flipX;
                component.clips.put(entry.key, copy);
            }
        }

        if (component.clips.size == 0) {
            component.currentClip = "default";
            component.clips.put("default", new AnimationComponent.Clip(0, frameMax));
        } else if (component.currentClip == null
                || component.currentClip.isBlank()
                || !component.clips.containsKey(component.currentClip)) {
            component.currentClip = component.clips.keys().next();
        }

        return component;
    }

    private int countAnimationFrames(AssetNode node) {
        if (node == null || node.path == null || node.path.isBlank()) return 0;

        ProjectConfig cfg = ProjectConfig.getInstance();
        if (cfg == null) return 0;

        FileHandle animDir = StudioFs.requireStudioProjectDir(cfg)
                .child(StudioFs.DIR_ORIG_ANIMATIONS)
                .child(node.path);

        if (!animDir.exists() || !animDir.isDirectory()) return 0;

        int count = 0;
        for (FileHandle child : animDir.list()) {
            if (child != null
                    && !child.isDirectory()
                    && "png".equalsIgnoreCase(child.extension())) {
                count++;
            }
        }
        return count;
    }

    private void showSimpleErrorDialog(String message) {
        VisDialog dialog = new VisDialog("Edit clips");
        dialog.text(message != null ? message : "Unable to edit clips.");
        dialog.button("OK");
        dialog.show(getStage());
    }

    private void showDeleteAssetDialog(Array<AssetNode> nodes) {
        Array<AssetNode> targets = new Array<>();
        if (nodes != null) {
            targets.addAll(nodes);
        }

        if (targets.size == 0) {
            return;
        }

        VisDialog dialog = new VisDialog("Delete Asset") {
            @Override
            protected void result(Object object) {
                if (!Boolean.TRUE.equals(object)) return;

                ProjectConfig cfg = ProjectConfig.getInstance();
                FileHandle projectDir = StudioFs.requireStudioProjectDir(cfg);
                FileHandle metaFile = projectDir.child(StudioFs.FILE_ASSETS_JSON);

                AssetMetaDatabase db = AssetMetaDatabase.load(metaFile);

                Array<DeleteAssetTarget> resolvedTargets = resolveDeleteTargets(targets, db);
                if (resolvedTargets.size == 0) return;

                DeleteAssetTarget usedTarget = firstUsedDeleteTarget(resolvedTargets);
                if (usedTarget != null) {
                    showAssetInUseDialog(usedTarget.node, usedTarget.usageReport);
                    return;
                }

                deleteAssets(resolvedTargets);
            }
        };

        dialog.text(deleteDialogText(targets));
        dialog.button("Delete", true);
        dialog.button("Cancel", false);

        dialog.show(getStage());
    }

    private Array<DeleteAssetTarget> resolveDeleteTargets(Array<AssetNode> nodes, AssetMetaDatabase db) {
        Array<DeleteAssetTarget> targets = new Array<>();
        AssetUsageScanner usageScanner = new AssetUsageScanner(
                app.getCanvas().getEcsWorld(),
                ProjectConfig.getInstance(),
                db
        );

        for (AssetNode node : nodes) {
            String sourceRelPath = node.assetInfo != null
                    ? node.assetInfo.sourcePath()
                    : buildSourceRelPath(node);
            if (sourceRelPath == null || sourceRelPath.isBlank()) {
                continue;
            }

            int assetId = node.assetId;

            if (assetId <= 0) {
                Gdx.app.error("AssetDelete", "Asset id not found for " + sourceRelPath);
                continue;
            }

            AssetUsageScanner.AssetUsageReport report = usageScanner.scanAsset(assetId);

            targets.add(new DeleteAssetTarget(node, assetId, sourceRelPath, report));
        }

        return targets;
    }

    private DeleteAssetTarget firstUsedDeleteTarget(Array<DeleteAssetTarget> targets) {
        for (DeleteAssetTarget target : targets) {
            if (target.usageReport != null && target.usageReport.used()) {
                return target;
            }
        }
        return null;
    }

    private String deleteDialogText(Array<AssetNode> targets) {
        if (targets.size == 1) {
            return "Delete asset \"" + targets.first().name + "\"?\n\nThis action cannot be undone.";
        }

        return "Delete " + targets.size + " selected "
                + pluralAssetTypeLabel(targets)
                + "?\n\nThis action cannot be undone.";
    }

    private void deleteAssets(Array<DeleteAssetTarget> targets) {
        Array<Integer> assetIds = new Array<>();
        for (DeleteAssetTarget target : targets) {
            if (target.assetId > 0) {
                assetIds.add(target.assetId);
            }
        }

        if (assetIds.size > 0) {
            app.getSceneService().deleteProjectAssets(assetIds);
        }
    }

    private record DeleteAssetTarget(AssetNode node,
                                     int assetId,
                                     String sourceRelPath,
                                     AssetUsageScanner.AssetUsageReport usageReport) {
    }

    private void showAssetInUseDialog(AssetNode node, AssetUsageScanner.AssetUsageReport report) {
        VisDialog dialog = new VisDialog("Asset In Use");

        StringBuilder message = new StringBuilder();
        message.append("Cannot delete \"")
                .append(node.name)
                .append("\" because it is still used in the project.");

        if (report != null && report.sceneNames() != null && report.sceneNames().size > 0) {
            message.append("\n\nUsed in scene");
            if (report.sceneNames().size > 1) {
                message.append("s");
            }
            message.append(":");

            for (String sceneName : report.sceneNames()) {
                message.append("\n- ").append(sceneName);
            }
        }

        if (report != null && report.occurrenceCount() > 0) {
            message.append("\n\nOccurrences: ").append(report.occurrenceCount());
        }

        dialog.text(message.toString());
        dialog.button("OK");

        dialog.show(getStage());
    }

    private Actor buildPrefabThumb(AssetNode node) {
        ProjectConfig cfg = ProjectConfig.getInstance();
        FileHandle previewFile = StudioFs.requirePrefabPreviewFile(cfg, node.name);

        if (!previewFile.exists()) {
            PrefabPreviewWriter.writePlaceholder(previewFile);
        }

        Texture texture = new Texture(previewFile);
        ownedPrefabThumbTextures.add(texture);

        VisImage img = new VisImage(new TextureRegion(texture));
        img.setScaling(Scaling.fit);
        return img;
    }

    private void showDeletePrefabDialog(Array<AssetNode> nodes) {
        Array<AssetNode> targets = new Array<>();
        if (nodes != null) {
            targets.addAll(nodes);
        }

        if (targets.size == 0) {
            return;
        }

        VisDialog dialog = new VisDialog("Delete prefab") {
            @Override
            protected void result(Object object) {
                if (!Boolean.TRUE.equals(object)) return;
                ProjectConfig cfg = ProjectConfig.getInstance();
                for (AssetNode target : targets) {
                    PrefabAssetItem item = new PrefabAssetItem(
                            target.name,
                            StudioFs.requirePrefabFile(cfg, target.name),
                            StudioFs.requirePrefabPreviewFile(cfg, target.name)
                    );
                    prefabBrowserService.deletePrefab(item);
                }
                if (currentFolder != null) {
                    showForNode(currentFolder);
                }
            }
        };
        dialog.text(deletePrefabDialogText(targets));
        dialog.button("Delete", true);
        dialog.button("Cancel", false);
        dialog.show(getStage());
    }

    private String deletePrefabDialogText(Array<AssetNode> targets) {
        if (targets.size == 1) {
            return "Delete prefab \"" + targets.first().name + "\"?\n\nThis action cannot be undone.";
        }

        return "Delete " + targets.size + " selected prefabs?\n\nThis action cannot be undone.";
    }

    private void setSelectedNode(AssetNode node) {
        selectedNode = node;
        refreshSelectionVisuals();

        if (selectionListener != null && node != null) {
            selectionListener.accept(node);
        }
    }

    public void clearSelection() {
        selectedNode = null;
        clearMultiSelection();
        refreshSelectionVisuals();
    }

    public AssetNode getSelectedNode() {
        return selectedNode;
    }

    private void refreshSelectionVisuals() {
        for (VisTable t : tileWidgets) {
            Object uo = t.getUserObject();
            if (!(uo instanceof AssetNode node)) {
                t.setBackground("window-bg");
                continue;
            }

            if (node == selectedNode || isSelected(node)) {
                t.setBackground("border");
            } else {
                t.setBackground("window-bg");
            }
        }
    }

    private void attachDnD(Actor actor, AssetNode data) {
        actor.addCaptureListener(new InputListener() {

            private boolean dragging = false;
            private float startX, startY;

            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                if (button != Input.Buttons.LEFT) return false;

                dragging = false;
                startX = x;
                startY = y;
                return true;
            }

            @Override
            public void touchDragged(InputEvent event, float x, float y, int pointer) {
                if (dragging) return;

                if (Math.abs(x - startX) < 4 && Math.abs(y - startY) < 4) {
                    return;
                }

                DragPayload p = buildPayload(data);
                if (p == null) return;

                DragContext.get().begin(p);
                dragging = true;

                event.stop();
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                if (button == Input.Buttons.LEFT && DragContext.get().active()) {
                    DragContext.get().signalRelease();
                    resetCursor(false);
                    dragging = false;
                    event.stop();
                }
            }
        });
    }

    private Array<AssetNode> collectSelectedAssetsLike(AssetNode source) {
        Array<AssetNode> result = new Array<>();

        if (!isMultiSelectableAsset(source)) {
            return result;
        }

        if (isSelected(source)) {
            for (AssetNode node : selectedNodes) {
                if (isSameAssetSelectionFamily(source, node)) {
                    result.add(node);
                }
            }
        } else {
            result.add(source);
        }

        result.sort((a, b) -> Integer.compare(indexOfCurrentAsset(a), indexOfCurrentAsset(b)));
        return result;
    }

    private boolean isSameAssetSelectionFamily(AssetNode source, AssetNode candidate) {
        return source != null
                && candidate != null
                && isMultiSelectableAsset(candidate)
                && source.kind == candidate.kind
                && source.root == candidate.root;
    }

    private DragPayload buildPayload(AssetNode data) {
        DragPayload p = new DragPayload();
        p.guid = data.name;
        p.assetId = data.assetId > 0 ? data.assetId : resolveAssetId(data);

        switch (data.kind) {
            case IMAGE -> {
                if (data.root == AssetNode.Root.TILES) {
                    Array<AssetNode> dragged = collectSelectedAssetsLike(data);
                    if (dragged.size == 0) {
                        dragged.add(data);
                    }

                    p.type = "tile-asset";
                    p.path = data.path; // legacy first item
                    p.paths = new Array<>();

                    for (AssetNode node : dragged) {
                        p.paths.add(node.path);
                    }

                    buildImageGhost(p, data);
                } else {
                    p.type = "image-file";
                    p.path = data.path;
                    buildImageGhost(p, data);
                }
            }
            case ANIMATION -> {
                p.type = "anim-sheet";
                p.path = data.path;

                ProjectConfig cfg = ProjectConfig.getInstance();
                FileHandle projectDir = StudioFs.requireStudioProjectDir(cfg);

                Pixmap frame = AssetPreviewCache.extractAnimationFirstFramePixmap(
                        cfg, projectDir, data
                );

                if (frame != null) {
                    p.ghostPixmap = frame;
                    p.hotspotX = frame.getWidth() / 2;
                    p.hotspotY = frame.getHeight() / 2;
                    setGhostCursor(p);
                }
            }
            case PARTICLE -> {
                p.type = "particle";
                p.path = data.path;
                buildParticleGhost(p);
            }
            case PREFAB -> {
                p.type = "prefab";
                p.path = data.path;      // absolute path or project-relative .pixprefab path
                p.guid = data.name;
                buildPrefabGhost(p, data);
            }
            case TILED_ANIMATION -> {
                p.type = "tiled-animation";
                p.path = data.path;
                p.guid = data.name;
                p.tileAnimationId = data.tileAnimationId;
                buildTiledAnimationGhost(p, data);
            }
            default -> {
                return null;
            }
        }

        p.op = DragPayload.Op.COPY;
        return p;
    }

    private int resolveAssetId(AssetNode data) {
        if (data == null || data.path == null || data.path.isBlank()) return -1;

        String sourceRelPath = switch (data.root) {
            case IMAGES -> StudioFs.DIR_ORIG_IMAGES + "/" + data.path;
            case ANIMATIONS -> StudioFs.DIR_ORIG_ANIMATIONS + "/" + data.path;
            case PARTICLES -> StudioFs.DIR_ORIG_EFFECTS + "/" + data.path;
            case TILES -> StudioFs.DIR_ORIG_TILES + "/" + data.path;
            case PREFABS -> null;
        };

        if (sourceRelPath == null) return -1;

        AssetMetaDatabase db = loadAssetMetaDatabase();
        AssetType type = assetTypeForNode(data);
        AssetMeta meta = db != null && type != null
                ? db.findUniqueBySourceRelPath(sourceRelPath, type)
                : null;
        return meta != null ? meta.id() : -1;
    }

    private static AssetType assetTypeForNode(AssetNode node) {
        if (node == null) return null;
        return switch (node.root) {
            case IMAGES -> AssetType.IMAGE;
            case ANIMATIONS -> AssetType.ANIMATION;
            case PARTICLES -> AssetType.PARTICLE;
            case TILES -> AssetType.TILE;
            case PREFABS -> null;
        };
    }

    private void buildTiledAnimationGhost(DragPayload p, AssetNode data) {
        if (p == null || data == null || data.tileAnimationId <= 0) return;

        Pixmap frame = extractTiledAnimationFirstFramePixmap(data.tileAnimationId);
        if (frame == null) {
            frame = createTiledAnimationFallbackGhost();
        }

        p.ghostPixmap = frame;
        p.hotspotX = frame.getWidth() / 2;
        p.hotspotY = frame.getHeight() / 2;
        setGhostCursor(p);
    }

    private Pixmap extractTiledAnimationFirstFramePixmap(int tileAnimationId) {
        TileAnimationsMetaDatabase animDb = loadTileAnimationsMetaDatabase();
        AssetMetaDatabase assetDb = loadAssetMetaDatabase();
        if (animDb == null || assetDb == null) {
            return null;
        }

        TileAnimationProjectDefData def = findTiledAnimationDef(animDb, tileAnimationId);
        if (def == null || def.frameAssetIds == null || def.frameAssetIds.length == 0) {
            return null;
        }

        AssetMeta frameMeta = assetDb.findById(def.frameAssetIds[0]);
        if (frameMeta == null || frameMeta.sourceRelPath() == null || frameMeta.sourceRelPath().isBlank()) {
            return null;
        }

        ProjectConfig cfg = ProjectConfig.getInstance();
        if (cfg == null) {
            return null;
        }

        FileHandle frameFile = StudioFs.requireStudioProjectDir(cfg).child(frameMeta.sourceRelPath());
        if (!frameFile.exists() || frameFile.isDirectory()) {
            return null;
        }

        try {
            return new Pixmap(frameFile);
        } catch (RuntimeException ex) {
            Gdx.app.error("TiledAnimationDnD", "Failed to load tiled animation frame: " + frameFile.path(), ex);
            return null;
        }
    }

    private Pixmap createTiledAnimationFallbackGhost() {
        Pixmap pm = new Pixmap(48, 48, Pixmap.Format.RGBA8888);

        pm.setColor(0f, 0f, 0f, 0f);
        pm.fill();

        pm.setColor(0.12f, 0.13f, 0.16f, 0.9f);
        pm.fillRectangle(4, 4, 40, 40);

        pm.setColor(0.55f, 0.78f, 1f, 1f);
        pm.drawRectangle(4, 4, 40, 40);
        pm.drawRectangle(5, 5, 38, 38);

        pm.setColor(0.35f, 0.65f, 0.95f, 1f);
        pm.fillRectangle(11, 14, 10, 10);
        pm.fillRectangle(27, 14, 10, 10);
        pm.fillRectangle(19, 26, 10, 10);

        return pm;
    }

    private void buildPrefabGhost(DragPayload p, AssetNode data) {
        if (p == null || data == null) return;

        ProjectConfig cfg = ProjectConfig.getInstance();
        if (cfg == null) return;

        FileHandle previewFile = StudioFs.requirePrefabPreviewFile(cfg, data.name);

        Pixmap pm = null;

        if (previewFile.exists()) {
            try {
                pm = new Pixmap(previewFile);
            } catch (RuntimeException ex) {
                Gdx.app.error("PrefabDnD", "Failed to load prefab preview: " + previewFile.path(), ex);
            }
        }

        if (pm == null) {
            pm = new Pixmap(48, 48, Pixmap.Format.RGBA8888);

            pm.setColor(0f, 0f, 0f, 0f);
            pm.fill();

            pm.setColor(0.12f, 0.13f, 0.16f, 0.9f);
            pm.fillRectangle(4, 4, 40, 40);

            pm.setColor(0.55f, 0.78f, 1f, 1f);
            pm.drawRectangle(4, 4, 40, 40);
            pm.drawRectangle(5, 5, 38, 38);

            pm.setColor(0.35f, 0.65f, 0.95f, 1f);
            pm.fillRectangle(13, 14, 22, 5);
            pm.fillRectangle(13, 24, 16, 5);
        }

        // Keep ghost reasonably small if preview image is large.
        if (pm.getWidth() > 96 || pm.getHeight() > 96) {
            Pixmap scaled = scalePixmapFit(pm, 96, 96);
            pm.dispose();
            pm = scaled;
        }

        p.ghostPixmap = pm;
        p.hotspotX = pm.getWidth() / 2;
        p.hotspotY = pm.getHeight() / 2;

        setGhostCursor(p);
    }

    private static Pixmap scalePixmapFit(Pixmap src, int maxW, int maxH) {
        float scale = Math.min(
                maxW / (float) src.getWidth(),
                maxH / (float) src.getHeight()
        );

        int w = Math.max(1, Math.round(src.getWidth() * scale));
        int h = Math.max(1, Math.round(src.getHeight() * scale));

        Pixmap out = new Pixmap(w, h, Pixmap.Format.RGBA8888);
        out.setColor(0f, 0f, 0f, 0f);
        out.fill();

        out.drawPixmap(
                src,
                0, 0,
                src.getWidth(), src.getHeight(),
                0, 0,
                w, h
        );

        return out;
    }

    private void buildImageGhost(DragPayload p, AssetNode data) {
        ProjectConfig cfg = ProjectConfig.getInstance();
        FileHandle projectDir = StudioFs.requireStudioProjectDir(cfg);

        FileHandle file = switch (data.root) {
            case IMAGES -> projectDir.child(StudioFs.DIR_ORIG_IMAGES).child(data.path);
            case TILES -> projectDir.child(StudioFs.DIR_ORIG_TILES).child(data.path);
            case ANIMATIONS -> projectDir.child(StudioFs.DIR_ORIG_ANIMATIONS).child(data.path);
            case PARTICLES -> null;
            case PREFABS -> null;
        };
        if (file != null && file.exists()) {
            Pixmap pm = new Pixmap(file);
            p.ghostPixmap = pm;
            p.hotspotX = Math.min(6, pm.getWidth() - 1);
            p.hotspotY = Math.min(6, pm.getHeight() - 1);
            setGhostCursor(p);
        }
    }

    private void buildParticleGhost(DragPayload p) {
        Pixmap pm = new Pixmap(16, 16, Pixmap.Format.RGBA8888);
        pm.setColor(0, 0, 0, 0);
        pm.fill();
        pm.setColor(1f, 1f, 0.3f, 1f);
        pm.fillCircle(8, 8, 6);

        p.ghostPixmap = pm;
        p.hotspotX = 8;
        p.hotspotY = 8;
        setGhostCursor(p);
    }

    private static void setGhostCursor(DragPayload p) {
        resetCursor(true);
        if (p.ghostPixmap != null) {
            currentCursor = DragCursors.makeGhostCursor(
                    p.ghostPixmap, p.hotspotX, p.hotspotY
            );
            if (currentCursor != null) {
                Gdx.graphics.setCursor(currentCursor);
            }
        }
    }

    private static void resetCursor() {
        resetCursor(true);
    }

    private static void resetCursor(boolean disposePayloadPixmap) {
        if (currentCursor != null) {
            currentCursor.dispose();
            currentCursor = null;
        }

        DragPayload payload = DragContext.get().peek();
        if (disposePayloadPixmap && payload != null && payload.ghostPixmap != null) {
            payload.ghostPixmap.dispose();
            payload.ghostPixmap = null;
        }

        Gdx.graphics.setSystemCursor(Cursor.SystemCursor.Arrow);
    }

    public void setTileSize(float size) {
        size = Math.max(32f, Math.min(size, 192f));
        if (this.tileSize == size) return;

        this.tileSize = size;
        layoutDirty = true;
        invalidateHierarchy();
        layout();
    }

    private void disposeOwnedPrefabThumbTextures() {
        for (Texture texture : ownedPrefabThumbTextures) {
            if (texture != null) {
                texture.dispose();
            }
        }
        ownedPrefabThumbTextures.clear();
    }
}
