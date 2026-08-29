package games.pixscape.studio.ui.asset;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Cursor;
import com.badlogic.gdx.math.Vector2;
import com.kotcrab.vis.ui.widget.VisTable;
import games.pixscape.studio.asset.AssetMeta;
import games.pixscape.studio.asset.AssetMetaDatabase;
import games.pixscape.studio.asset.AssetType;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.io.StudioFs;
import games.pixscape.studio.service.runtimeavailability.RuntimeAvailabilityCategory;
import games.pixscape.studio.ui.asset.dnd.DragContext;
import games.pixscape.studio.ui.asset.dnd.DragCursors;
import games.pixscape.studio.ui.asset.dnd.DragPayload;
import games.pixscape.studio.ui.main.StudioApplicationAdapter;

public final class RuntimeAvailabilityPanel extends VisTable {

    private final StudioApplicationAdapter app;
    private final RuntimeAvailabilityTreeView treeView;
    private final RuntimeAvailabilityThumbsView thumbsView;
    private final Vector2 tmpStageCoords = new Vector2();
    private final Vector2 tmpLocalCoords = new Vector2();
    private Cursor currentCursor;
    private boolean currentCursorForbidden;

    public RuntimeAvailabilityPanel(StudioApplicationAdapter app) {
        this.app = app;
        top().left();

        treeView = new RuntimeAvailabilityTreeView();
        thumbsView = new RuntimeAvailabilityThumbsView(app);

        treeView.setSelectionListener(this::showCategory);

        AssetBrowserPanel browserPanel = new AssetBrowserPanel("Runtime", treeView, thumbsView);
        add(browserPanel).grow();

        showCategory(treeView.getSelectedCategory());

        EventFlow.i().subscribe(EventFlow.CurrentSceneMeta.class, evt -> refreshForCurrentScene());
    }

    public void refreshForCurrentScene() {
        showCategory(treeView.getSelectedCategory());
    }

    public void selectCategory(RuntimeAvailabilityCategory category) {
        if (category == null) {
            return;
        }

        if (!treeView.selectCategory(category)) {
            showCategory(category);
        }
    }

    private void showCategory(RuntimeAvailabilityCategory category) {
        ProjectConfig cfg = ProjectConfig.getInstance();
        if (cfg == null || cfg.getCurrentSceneMeta() == null) {
            thumbsView.showCategory(category);
            return;
        }
        thumbsView.showCategory(category);
    }

    private void updateDropReceiver() {
        DragPayload payload = DragContext.get().peek();
        if (payload == null) {
            clearCursorIfAny();
            return;
        }

        boolean inside = pointerInside();
        RuntimeAvailabilityCategory previewCategory = RuntimeAvailabilityDropPolicy.resolveCategory(payload);
        ProjectConfig cfg = ProjectConfig.getInstance();
        if (previewCategory == null || cfg == null || cfg.getCurrentSceneMeta() == null) {
            if (inside) {
                setDropCursor(payload, true);
            } else {
                clearCursorIfAny();
            }
            cleanupPayload(DragContext.get().consumeIfReleasedInside(inside));
            return;
        }

        if (inside) {
            setDropCursor(payload, false);
        } else {
            clearCursorIfAny();
        }

        DragPayload released = DragContext.get().consumeIfReleasedInside(inside);
        if (released == null) {
            return;
        }

        RuntimeAvailabilityCategory targetCategory = RuntimeAvailabilityDropPolicy.resolveCategory(released);
        if (targetCategory == null) {
            return;
        }

        addRuntimeAvailabilityDeclaration(targetCategory, released);
        selectCategory(targetCategory);
        cleanupPayload(released);
        clearCursorIfAny();
    }

    private void setDropCursor(DragPayload payload, boolean forbidden) {
        if (currentCursor != null && currentCursorForbidden == forbidden) {
            Gdx.graphics.setCursor(currentCursor);
            return;
        }

        clearCursorIfAny();

        currentCursor = forbidden
                ? DragCursors.makeForbiddenCursor()
                : DragCursors.makeGhostCursor(payload.ghostPixmap, 6, 6);
        currentCursorForbidden = forbidden;

        if (currentCursor != null) {
            Gdx.graphics.setCursor(currentCursor);
        }
    }

    private void clearCursorIfAny() {
        boolean hadCursor = currentCursor != null;
        if (currentCursor != null) {
            currentCursor.dispose();
            currentCursor = null;
        }
        currentCursorForbidden = false;
        if (hadCursor) {
            Gdx.graphics.setSystemCursor(Cursor.SystemCursor.Arrow);
        }
    }

    private void cleanupPayload(DragPayload payload) {
        if (payload != null && payload.ghostPixmap != null) {
            payload.ghostPixmap.dispose();
            payload.ghostPixmap = null;
        }
    }

    private void addRuntimeAvailabilityDeclaration(RuntimeAvailabilityCategory category, DragPayload payload) {
        if (app == null || app.getSceneService() == null || payload == null) {
            return;
        }

        switch (category) {
            case SPRITES -> app.getSceneService().addRuntimeAvailableSprite(resolveAssetId(payload));
            case ANIMATIONS -> app.getSceneService().addRuntimeAvailableAnimation(resolveAssetId(payload));
            case PARTICLES -> app.getSceneService().addRuntimeAvailableParticle(resolveParticleEffectPath(payload));
            case PREFABS -> app.getSceneService().addRuntimeAvailablePrefab(resolvePrefabId(payload));
            case TILED_ANIMATIONS -> app.getSceneService().addRuntimeAvailableTiledAnimation(payload.tileAnimationId);
            case TILED_TILES -> addRuntimeAvailableTiles(payload);
        }
    }

    private void addRuntimeAvailableTiles(DragPayload payload) {
        if (payload == null || app == null || app.getSceneService() == null) return;

        if (payload.paths != null && payload.paths.size > 0) {
            for (String path : payload.paths) {
                int assetId = resolveAssetIdForProjectRelativePath(
                        StudioFs.DIR_ORIG_TILES + "/" + path,
                        AssetType.TILE
                );
                app.getSceneService().addRuntimeAvailableTiledTile(assetId);
            }
            return;
        }

        app.getSceneService().addRuntimeAvailableTiledTile(resolveAssetId(payload));
    }

    private String resolvePrefabId(DragPayload payload) {
        if (payload.guid != null && !payload.guid.isBlank()) {
            return payload.guid;
        }

        if (payload.path != null && !payload.path.isBlank()) {
            return StudioFs.removeExtension(new FileHandle(payload.path).name());
        }

        return null;
    }

    private int resolveAssetId(DragPayload payload) {
        if (payload == null) return -1;
        if (payload.assetId > 0) return payload.assetId;
        if (payload.path == null || payload.path.isBlank()) return -1;

        return switch (payload.type) {
            case "image-file" -> resolveAssetIdForProjectRelativePath(
                    StudioFs.DIR_ORIG_IMAGES + "/" + payload.path,
                    AssetType.IMAGE
            );
            case "anim-sheet" -> resolveAssetIdForProjectRelativePath(
                    StudioFs.DIR_ORIG_ANIMATIONS + "/" + payload.path,
                    AssetType.ANIMATION
            );
            case "tile-asset" -> resolveAssetIdForProjectRelativePath(
                    StudioFs.DIR_ORIG_TILES + "/" + payload.path,
                    AssetType.TILE
            );
            default -> -1;
        };
    }

    private int resolveAssetIdForProjectRelativePath(String sourceRelPath,
                                                     AssetType type) {
        if (sourceRelPath == null || sourceRelPath.isBlank()) return -1;

        ProjectConfig cfg = ProjectConfig.getInstance();
        if (cfg == null) return -1;

        FileHandle metaFile = StudioFs.requireStudioProjectDir(cfg).child(StudioFs.FILE_ASSETS_JSON);
        if (!metaFile.exists()) return -1;

        AssetMeta meta = AssetMetaDatabase.load(metaFile)
                .findUniqueBySourceRelPath(sourceRelPath, type);
        return meta != null ? meta.id() : -1;
    }

    private String resolveParticleEffectPath(DragPayload payload) {
        if (payload == null || payload.path == null || payload.path.isBlank()) return null;
        return payload.path.replace('\\', '/');
    }

    private boolean pointerInside() {
        if (getStage() == null) {
            return false;
        }

        tmpStageCoords.set(Gdx.input.getX(), Gdx.input.getY());
        getStage().screenToStageCoordinates(tmpStageCoords);
        tmpLocalCoords.set(tmpStageCoords);
        stageToLocalCoordinates(tmpLocalCoords);

        return tmpLocalCoords.x >= 0f
                && tmpLocalCoords.x <= getWidth()
                && tmpLocalCoords.y >= 0f
                && tmpLocalCoords.y <= getHeight();
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        updateDropReceiver();
    }
}
