package games.pixscape.studio.ui.asset;

import games.pixscape.studio.ui.modal.StudioDialog;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.kotcrab.vis.ui.widget.*;
import games.pixscape.studio.asset.AssetType;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.io.StudioFs;
import games.pixscape.studio.service.tiled.TiledPaintService;
import games.pixscape.studio.ui.config.CommonLayout;
import games.pixscape.studio.ui.docking.DockablePanel;
import games.pixscape.studio.ui.main.StudioApplicationAdapter;

public final class AssetsPanel extends DockablePanel {

    private final FolderTreeView treeView;
    private final AssetsThumbsView thumbsView;
    private final RuntimeAvailabilityPanel runtimeAvailabilityPanel;
    private final VisTextButton importButton;
    private final TiledPaintService tiledPaintService;
    private final StudioApplicationAdapter app;

    public AssetsPanel(StudioApplicationAdapter app) {
        super("Assets");
        this.app = app;
        tiledPaintService = app.getCanvas().getTiledPaintService();
        treeView = new FolderTreeView(app);

        thumbsView = new AssetsThumbsView(app);
        importButton = createImportButton();
        importButton.setVisible(false);
        thumbsView.addHeaderLeftAction(importButton);

        runtimeAvailabilityPanel = new RuntimeAvailabilityPanel(app);
        thumbsView.setCreateTiledAnimationListener(this::showCreateTiledAnimationDialog);

        thumbsView.setTileSelectionListener(node -> {
            if (node.kind == AssetNode.Kind.TILED_ANIMATION) {
                tiledPaintService.setActiveTileAssetId(node.tileAnimationId);
                return;
            }

            if (node.kind == AssetNode.Kind.TILED_ANIMATION_FRAME) {
                return;
            }

            int tileAssetId = tileAssetIdForSelection(node);
            if (tileAssetId > 0) {
                tiledPaintService.setActiveTileAssetId(tileAssetId);
            }
        });

        treeView.setTileDroppedOnTiledAnimationListener((tileAnimationId, tilePaths) -> {
            try {
                if (tilePaths == null || tilePaths.size == 0) {
                    return;
                }

                for (String tilePath : tilePaths) {
                    String sourceRelPath = StudioFs.DIR_ORIG_TILES + "/" + tilePath;
                    int tileAssetId = app.getSceneService()
                            .resolveAssetIdBySourceRelPath(sourceRelPath, AssetType.TILE);

                    if (tileAssetId <= 0) {
                        throw new IllegalStateException("Tile asset id could not be resolved.");
                    }

                    app.getSceneService().addTileToTileAnimation(tileAnimationId, tileAssetId);
                }

                ProjectConfig cfg = ProjectConfig.getInstance();
                reloadFromProject(cfg);
                selectTiledAnimationById(tileAnimationId);

                AssetNode selected = treeView.getSelectedFolder();
                thumbsView.clear();
                if (selected != null) {
                    thumbsView.showForNode(selected);
                }
            } catch (IllegalArgumentException | IllegalStateException ex) {
                showSimpleErrorDialog(ex.getMessage());
            }
        });
        thumbsView.setTiledAnimationFrameRemoveListener((tileAnimationId, frameIndex) -> {
            try {
                app.getSceneService().removeFrameFromTileAnimation(tileAnimationId, frameIndex);

                ProjectConfig cfg = ProjectConfig.getInstance();
                reloadFromProject(cfg);
                selectTiledAnimationById(tileAnimationId);

                AssetNode selected = treeView.getSelectedFolder();
                thumbsView.clear();
                if (selected != null) {
                    thumbsView.showForNode(selected);
                }
            } catch (IllegalArgumentException | IllegalStateException ex) {
                showSimpleErrorDialog(ex.getMessage());
            }
        });
        thumbsView.setTiledAnimationFrameDurationChangeListener((tileAnimationId, frameIndex, durationMs) -> {
            try {
                app.getSceneService().updateTileAnimationFrameDuration(tileAnimationId, frameIndex, durationMs);
            } catch (IllegalArgumentException | IllegalStateException ex) {
                showSimpleErrorDialog(ex.getMessage());
            }
        });
        thumbsView.setTiledAnimationFrameMoveListener((tileAnimationId, fromIndex, toIndex) -> {
            try {
                app.getSceneService().moveFrameInTileAnimation(tileAnimationId, fromIndex, toIndex);

                ProjectConfig cfg = ProjectConfig.getInstance();
                reloadFromProject(cfg);
                selectTiledAnimationById(tileAnimationId);

                AssetNode selected = treeView.getSelectedFolder();
                thumbsView.clear();
                if (selected != null) {
                    thumbsView.showForNode(selected);
                }
            } catch (IllegalArgumentException | IllegalStateException ex) {
                showSimpleErrorDialog(ex.getMessage());
            }
        });
        thumbsView.setTiledAnimationDeleteListener(this::showDeleteTiledAnimationDialog);
        // selection binding
        treeView.setSelectionListener(thumbsView::showForNode);

        buildLayout();
    }

    static int tileAssetIdForSelection(AssetNode node) {
        return node != null
                && node.root == AssetNode.Root.TILES
                && node.kind == AssetNode.Kind.IMAGE
                ? node.assetId
                : -1;
    }

    public AssetNode getSelectedAssetNode() {
        return selectedAssetNode(thumbsView);
    }

    static AssetNode selectedAssetNode(AssetsThumbsView thumbsView) {
        return thumbsView != null ? thumbsView.getSelectedNode() : null;
    }

    private void buildLayout() {
        AssetBrowserPanel projectAssetsBrowser = new AssetBrowserPanel("Project", treeView, thumbsView);
        VisSplitPane browserSplit = new VisSplitPane(
                projectAssetsBrowser,
                runtimeAvailabilityPanel,
                false
        );
        browserSplit.setSplitAmount(0.65f);

        add(browserSplit).grow();
    }

    public void reloadFromProject(ProjectConfig cfg) {
        AssetNode selected = treeView.getSelectedFolder();
        importButton.setVisible(cfg != null && cfg.getCurrentSceneName() != null);

        AssetPreviewCache.clear();
        thumbsView.clear();
        treeView.reloadFromProject(cfg);
        runtimeAvailabilityPanel.refreshForCurrentScene();

        if (selected != null) {
            treeView.selectFolder(selected);
            thumbsView.showForNode(selected);
            return;
        }

        AssetNode current = treeView.getSelectedFolder();
        if (current != null) {
            thumbsView.showForNode(current);
        }
    }

    private VisTextButton createImportButton() {
        VisTextButton button = new VisTextButton("Import…");
        button.setColor(CommonLayout.BUTTON_COLOR);
        button.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                new ImportDialog(
                        app,
                        items -> app.getSceneService().importAssets(items),
                        (directory, profileSettings) -> app.getSceneService().importTilesetDirectory(directory, profileSettings)
                ).show(getStage());
            }
        });
        return button;
    }

    private void showCreateTiledAnimationDialog() {
        VisTextField nameField = new VisTextField();
        nameField.setMessageText("Animation name");

        VisDialog dialog = new StudioDialog("New tiled animation") {
            @Override
            protected void result(Object object) {
                if (!Boolean.TRUE.equals(object)) {
                    return;
                }

                String name = nameField.getText() != null ? nameField.getText().trim() : "";
                if (name.isEmpty()) {
                    showSimpleErrorDialog("Animation name cannot be empty.");
                    return;
                }

                try {
                    int tileAnimationId = app.getSceneService().createEmptyTileAnimation(name);

                    ProjectConfig cfg = ProjectConfig.getInstance();

                    treeView.reloadFromProject(cfg);
                    selectTiledAnimationById(tileAnimationId);

                    AssetNode selected = treeView.getSelectedFolder();
                    thumbsView.clear();
                    if (selected != null) {
                        thumbsView.showForNode(selected);
                    }
                } catch (IllegalArgumentException | IllegalStateException ex) {
                    showSimpleErrorDialog(ex.getMessage());
                }
            }
        };

        dialog.getContentTable().add(new VisLabel("Animation name")).left().row();
        dialog.getContentTable().add(nameField).width(280f).growX().row();

        dialog.button("Create", true);
        dialog.button("Cancel", false);
        dialog.setModal(true);
        dialog.setResizable(false);
        dialog.pack();
        dialog.show(getStage());

        if (getStage() != null) {
            getStage().setKeyboardFocus(nameField);
        }
    }

    private void showSimpleErrorDialog(String message) {
        VisDialog dialog = new StudioDialog("Assets");
        dialog.text(message != null && !message.isBlank()
                ? message
                : "The requested asset operation could not be completed.");
        dialog.button("OK");
        dialog.setModal(true);
        dialog.setResizable(false);
        dialog.pack();

        if (getStage() != null) {
            dialog.show(getStage());
        }
    }

    private void showDeleteTiledAnimationDialog(int tileAnimationId, String name) {
        VisDialog dialog = new StudioDialog("Delete tiled animation") {
            @Override
            protected void result(Object object) {
                if (!Boolean.TRUE.equals(object)) {
                    return;
                }

                try {
                    app.getSceneService().deleteTileAnimation(tileAnimationId);
                } catch (IllegalArgumentException | IllegalStateException ex) {
                    showSimpleErrorDialog(ex.getMessage());
                }
            }
        };

        String displayName = name != null && !name.isBlank()
                ? name
                : ("Animation " + tileAnimationId);
        dialog.text(
                "Delete tiled animation \"" + displayName + "\"?\n\n"
                        + "This action cannot be undone."
        );
        dialog.button("Delete", true);
        dialog.button("Cancel", false);
        dialog.setModal(true);
        dialog.setResizable(false);
        dialog.pack();

        if (getStage() != null) {
            dialog.show(getStage());
        }
    }

    public void selectTiledAnimationById(int tileAnimationId) {
        if (tileAnimationId <= 0) {
            return;
        }

        AssetNode target = new AssetNode(
                AssetNode.Kind.TILED_ANIMATION,
                AssetNode.Root.TILES,
                AssetNode.TILED_ANIMATIONS_NODE_PATH + "/" + tileAnimationId,
                "",
                null
        );
        target.tileAnimationId = tileAnimationId;

        treeView.selectFolder(target);
    }
}
