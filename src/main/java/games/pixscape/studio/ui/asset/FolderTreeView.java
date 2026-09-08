package games.pixscape.studio.ui.asset;

import games.pixscape.studio.ui.modal.StudioDialog;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Array;
import com.kotcrab.vis.ui.widget.*;
import games.pixscape.runtime.helper.RuntimeFs;
import games.pixscape.studio.asset.AssetMetaDatabase;
import games.pixscape.studio.asset.TileAnimationProjectDefData;
import games.pixscape.studio.asset.TileAnimationsMetaDatabase;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.io.StudioFs;
import games.pixscape.studio.io.TileAnimationsIO;
import games.pixscape.studio.ui.asset.dnd.DragContext;
import games.pixscape.studio.ui.asset.dnd.DragPayload;
import games.pixscape.studio.ui.main.StudioApplicationAdapter;

import java.util.function.Consumer;

public final class FolderTreeView extends VisTable {

    private final VisTree tree = new VisTree();
    private final VisScrollPane scroller;
    private PopupMenu activeContextMenu;

    private Consumer<AssetNode> selectionListener;

    private boolean tileMode = false;
    private ProjectConfig currentCfg;

    private boolean internalSelectionChange = false;

    private AssetNode lastTileFolderSelection;
    private AssetNode lastNonTileFolderSelection;

    private final Vector2 tmpStageCoords = new Vector2();

    private TileDroppedOnTiledAnimationListener tileDroppedOnTiledAnimationListener;
    private final Vector2 tmpLocalCoords = new Vector2();

    private static final String TILED_ANIMATIONS_NODE_PATH = "__tile_animations__";

    public FolderTreeView(StudioApplicationAdapter app) {

        top().left();

        tree.setIndentSpacing(22);
        tree.getSelection().setMultiple(false);

        scroller = new VisScrollPane(tree);
        scroller.setFadeScrollBars(false);
        scroller.setScrollingDisabled(true, false);

        add(scroller).padLeft(10).grow().row();

        hookSelection();
        hookContextMenu(app);

        EventFlow.i().subscribe(EventFlow.EditorModeChanged.class, ev -> {
            tileMode = ev.mode() == EventFlow.EditorMode.TILE;
            updateSelectableNodes();
            restoreBestSelection();
        });
    }

    @FunctionalInterface
    public interface TileDroppedOnTiledAnimationListener {
        void accept(int tileAnimationId, Array<String> tilePaths);
    }

    public void setTileDroppedOnTiledAnimationListener(TileDroppedOnTiledAnimationListener listener) {
        this.tileDroppedOnTiledAnimationListener = listener;
    }

    public void reloadFromProject(ProjectConfig cfg) {
        this.currentCfg = cfg;
        rebuildTree();
        restoreBestSelection();
    }

    private void rebuildTree() {
        tree.clearChildren();
        tree.getSelection().clear();

        if (currentCfg == null || currentCfg.projectFileName == null) {
            return;
        }

        FileHandle projectDir = StudioFs.requireStudioProjectDir(currentCfg);

        addGameObjectsNode();

        FolderTreeBuilder.buildFolders(
                tree,
                projectDir.child(StudioFs.DIR_ORIG_IMAGES),
                "Images",
                AssetNode.Root.IMAGES
        );

        FolderTreeBuilder.buildFolders(
                tree,
                projectDir.child(StudioFs.DIR_ORIG_EFFECTS),
                "Particles",
                AssetNode.Root.PARTICLES
        );

        AssetMetaDatabase assetSnapshot = AssetMetaDatabase.load(
                projectDir.child(StudioFs.FILE_ASSETS_JSON)
        );
        FolderTreeBuilder.buildFolders(
                tree,
                projectDir.child(StudioFs.DIR_ORIG_ANIMATIONS),
                "Animations",
                AssetNode.Root.ANIMATIONS,
                assetSnapshot
        );

        FolderTreeBuilder.buildFolders(
                tree,
                projectDir.child(StudioFs.DIR_ORIG_TILES),
                "Tiles",
                AssetNode.Root.TILES
        );

        addTiledAnimationsLogicalNode();

        tree.expandAll();
        updateSelectableNodes();
    }


    private void addGameObjectsNode() {
        AssetNode data = new AssetNode(
                AssetNode.Kind.FOLDER,
                AssetNode.Root.GAME_OBJECTS,
                "",
                "Game Objects",
                null
        );

        VisLabel label = new VisLabel(data.name);
        label.setUserObject(data);
        tree.add(new VisTree.Node(label) {
        });
    }

    private void addTiledAnimationsLogicalNode() {
        AssetNode folderData = new AssetNode(
                AssetNode.Kind.TILED_ANIMATIONS_FOLDER,
                AssetNode.Root.TILES,
                TILED_ANIMATIONS_NODE_PATH,
                "Tile animations",
                null
        );

        VisLabel folderLabel = new VisLabel(folderData.name);
        folderLabel.setUserObject(folderData);

        VisTree.Node folderNode = new VisTree.Node(folderLabel) {
        };
        tree.add(folderNode);

        TileAnimationsMetaDatabase db = loadTileAnimationsMetaDatabase();
        if (db == null || db.animations == null) {
            return;
        }

        for (TileAnimationProjectDefData def : db.animations) {
            if (def == null) {
                continue;
            }

            AssetNode childData = new AssetNode(
                    AssetNode.Kind.TILED_ANIMATION,
                    AssetNode.Root.TILES,
                    TILED_ANIMATIONS_NODE_PATH + "/" + def.id,
                    def.name,
                    null
            );
            childData.tileAnimationId = def.id;

            VisLabel childLabel = new VisLabel(def.name);
            childLabel.setUserObject(childData);

            folderNode.add(new VisTree.Node(childLabel) {
            });
        }
    }

    private TileAnimationsMetaDatabase loadTileAnimationsMetaDatabase() {
        if (currentCfg == null || currentCfg.projectFileName == null) {
            return null;
        }

        FileHandle projectDir = StudioFs.requireStudioProjectDir(currentCfg);
        FileHandle file = projectDir.child(RuntimeFs.FILE_TILE_ANIMATIONS_JSON);
        if (!file.exists()) {
            return null;
        }

        return TileAnimationsIO.load(file);
    }

    private void hookSelection() {
        tree.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {

                if (internalSelectionChange) {
                    return;
                }

                VisTree.Node node = (VisTree.Node) tree.getSelection().first();
                if (node == null || node.getActor() == null) {
                    return;
                }

                Object uo = node.getActor().getUserObject();
                if (!(uo instanceof AssetNode assetNode)) {
                    return;
                }

                // NEW: remember last folder per mode
                if (assetNode.kind == AssetNode.Kind.FOLDER
                        || assetNode.kind == AssetNode.Kind.TILED_ANIMATIONS_FOLDER
                        || assetNode.kind == AssetNode.Kind.TILED_ANIMATION) {
                    if (assetNode.root == AssetNode.Root.TILES) {
                        lastTileFolderSelection = assetNode;
                    } else {
                        lastNonTileFolderSelection = assetNode;
                    }
                }

                if (selectionListener != null) {
                    selectionListener.accept(assetNode);
                }
            }
        });
    }

    private void updateSelectableNodes() {
        for (Object root : tree.getRootNodes()) {
            updateSelectableRecursive((VisTree.Node) root);
        }
    }

    private void restoreBestSelection() {
        AssetNode preferred = tileMode ? lastTileFolderSelection : lastNonTileFolderSelection;

        if (selectFolderIfSelectable(preferred)) {
            return;
        }

        ensureValidSelection();
    }

    private void ensureValidSelection() {
        VisTree.Node current = (VisTree.Node) tree.getSelection().first();

        if (current != null && current.isSelectable()) {
            return;
        }

        VisTree.Node firstSelectable = firstSelectable(tree.getRootNodes());
        if (firstSelectable != null) {
            setSelectionSilently(firstSelectable);
        }
    }

    private VisTree.Node firstSelectable(Array<VisTree.Node> nodes) {
        for (VisTree.Node node : nodes) {
            if (node.isSelectable()) {
                return node;
            }

            VisTree.Node child = firstSelectable(node.getChildren());
            if (child != null) {
                return child;
            }
        }
        return null;
    }

    private void updateSelectableRecursive(VisTree.Node node) {
        Actor actor = node.getActor();
        if (actor == null) return;

        Object uo = actor.getUserObject();

        if (uo instanceof AssetNode assetNode) {
            node.setSelectable(true);
            actor.setColor(Color.WHITE);
            actor.setTouchable(Touchable.enabled);
        }

        for (Object child : node.getChildren()) {
            updateSelectableRecursive((VisTree.Node) child);
        }
    }

    public void setSelectionListener(Consumer<AssetNode> listener) {
        this.selectionListener = listener;
    }

    public AssetNode getSelectedFolder() {
        VisTree.Node node = (VisTree.Node) tree.getSelection().first();
        if (node == null || node.getActor() == null) return null;

        Object uo = node.getActor().getUserObject();
        return (uo instanceof AssetNode an
                && (an.kind == AssetNode.Kind.FOLDER
                || an.kind == AssetNode.Kind.TILED_ANIMATIONS_FOLDER
                || an.kind == AssetNode.Kind.TILED_ANIMATION))
                ? an
                : null;
    }

    public void selectFolder(AssetNode target) {
        if (target == null) return;
        selectRecursive(tree.getRootNodes(), target, false);
    }

    private boolean selectFolderIfSelectable(AssetNode target) {
        if (target == null) return false;
        return selectRecursive(tree.getRootNodes(), target, true);
    }

    private boolean selectRecursive(Array<VisTree.Node> nodes, AssetNode target, boolean requireSelectable) {
        for (VisTree.Node n : nodes) {
            Object uo = n.getActor().getUserObject();

            if (uo instanceof AssetNode an) {
                boolean restorableKind =
                        an.kind == AssetNode.Kind.FOLDER
                                || an.kind == AssetNode.Kind.TILED_ANIMATIONS_FOLDER
                                || an.kind == AssetNode.Kind.TILED_ANIMATION;

                boolean targetRestorableKind =
                        target.kind == AssetNode.Kind.FOLDER
                                || target.kind == AssetNode.Kind.TILED_ANIMATIONS_FOLDER
                                || target.kind == AssetNode.Kind.TILED_ANIMATION;

                if (restorableKind
                        && targetRestorableKind
                        && an.kind == target.kind
                        && an.root == target.root
                        && safeEquals(an.path, target.path)) {

                    if (requireSelectable && !n.isSelectable()) {
                        return false;
                    }

                    setSelectionSilently(n);
                    n.expandAll();
                    return true;
                }
            }

            if (selectRecursive(n.getChildren(), target, requireSelectable)) {
                return true;
            }
        }
        return false;
    }

    private void setSelectionSilently(VisTree.Node node) {
        internalSelectionChange = true;
        try {
            tree.getSelection().set(node);
        } finally {
            internalSelectionChange = false;
        }

        if (selectionListener != null && node != null && node.getActor() != null) {
            Object uo = node.getActor().getUserObject();
            if (uo instanceof AssetNode assetNode) {
                selectionListener.accept(assetNode);
            }
        }
    }

    private static boolean safeEquals(String a, String b) {
        if (a == null) return b == null || b.isEmpty();
        if (b == null) return a.isEmpty();
        return a.equals(b);
    }

    private boolean isDeletableTilesetFolder(AssetNode assetNode) {
        if (assetNode == null) return false;
        if (assetNode.kind != AssetNode.Kind.FOLDER) return false;
        if (assetNode.root != AssetNode.Root.TILES) return false;
        return assetNode.path != null && !assetNode.path.isBlank();

        // Protect the logical root "Tiles"
    }

    private AssetNode assetNodeFromEventTarget(Actor actor) {
        for (Actor a = actor; a != null; a = a.getParent()) {
            Object uo = a.getUserObject();
            if (uo instanceof AssetNode assetNode) {
                return assetNode;
            }
        }
        return null;
    }

    private void hookContextMenu(StudioApplicationAdapter app) {
        tree.addCaptureListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                if (button != Input.Buttons.RIGHT) {
                    return false;
                }

                closeActiveContextMenu();

                AssetNode assetNode = assetNodeAt(y);
                VisTree.Node node = nodeAt(y);

                if (assetNode == null || node == null) {
                    return false;
                }

                setSelectionSilently(node);

                if (isDeletableTilesetFolder(assetNode)) {
                    showTilesetContextMenu(app, assetNode, event.getStageX(), event.getStageY());
                    event.stop();
                    return true;
                }

                return false;
            }
        });
    }

    private void showTilesetContextMenu(StudioApplicationAdapter app,
                                        AssetNode assetNode,
                                        float stageX,
                                        float stageY) {
        if (getStage() == null || app == null || assetNode == null) {
            return;
        }

        PopupMenu menu = new PopupMenu();
        MenuItem deleteItem = new MenuItem("Delete tileset...");

        deleteItem.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                showDeleteTilesetConfirmation(app, assetNode);
            }
        });

        menu.addItem(deleteItem);

        Gdx.app.postRunnable(() -> {
            if (getStage() != null) {
                closeActiveContextMenu();
                activeContextMenu = menu;
                menu.showMenu(getStage(), stageX, stageY);
            }
        });
    }

    private void showDeleteTilesetConfirmation(StudioApplicationAdapter app, AssetNode assetNode) {
        if (assetNode == null || app == null) return;

        VisDialog dialog = new StudioDialog("Delete tileset") {
            @Override
            protected void result(Object object) {
                if (!Boolean.TRUE.equals(object)) {
                    return;
                }

                try {
                    app.getSceneService().deleteTilesetDirectory(assetNode.path);
                    reloadFromProject(ProjectConfig.getInstance());
                } catch (IllegalArgumentException | IllegalStateException ex) {
                    showTilesetDeleteError(ex.getMessage());
                }
            }
        };

        dialog.text(
                """
                        Deleting this tileset will remove its PNG files and metadata from the project.
                        
                        This action cannot be undone.
                        
                        Do you want to continue?"""
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

    private void showTilesetDeleteError(String message) {
        VisDialog dialog = new StudioDialog("Cannot delete tileset");
        dialog.text(message != null && !message.isBlank()
                ? message
                : "The tileset could not be deleted.");
        dialog.button("OK");
        dialog.setModal(true);
        dialog.setResizable(false);
        dialog.pack();

        if (getStage() != null) {
            dialog.show(getStage());
        }
    }

    private void updateTileAnimationDrop() {
        DragPayload peek = DragContext.get().peek();
        if (peek == null || !"tile-asset".equals(peek.type)) {
            return;
        }

        AssetNode hovered = assetNodeUnderPointer();
        if (hovered == null || hovered.kind != AssetNode.Kind.TILED_ANIMATION) {
            return;
        }

        DragPayload released = DragContext.get().consumeIfReleasedInside(true);
        if (released == null) {
            return;
        }

        if (tileDroppedOnTiledAnimationListener != null) {
            Array<String> paths = (released.paths != null && released.paths.size > 0)
                    ? new Array<>(released.paths)
                    : new Array<>();

            if (paths.size == 0 && released.path != null && !released.path.isBlank()) {
                paths.add(released.path);
            }

            tileDroppedOnTiledAnimationListener.accept(
                    hovered.tileAnimationId,
                    paths
            );
        }

        cleanupPayload(released);
    }

    private void cleanupPayload(DragPayload payload) {
        if (payload != null && payload.ghostPixmap != null) {
            payload.ghostPixmap.dispose();
            payload.ghostPixmap = null;
        }
    }

    private VisTree.Node nodeAt(float y) {
        return tree.getNodeAt(y);
    }

    private AssetNode assetNodeAt(float y) {
        VisTree.Node node = nodeAt(y);
        if (node == null || node.getActor() == null) {
            return null;
        }

        Object uo = node.getActor().getUserObject();
        return (uo instanceof AssetNode assetNode) ? assetNode : null;
    }

    private void closeActiveContextMenu() {
        if (activeContextMenu != null) {
            activeContextMenu.remove();
            activeContextMenu = null;
        }
    }

    private AssetNode assetNodeUnderPointer() {
        if (getStage() == null) {
            return null;
        }

        tmpStageCoords.set(Gdx.input.getX(), Gdx.input.getY());
        getStage().screenToStageCoordinates(tmpStageCoords);

        tmpLocalCoords.set(tmpStageCoords);
        tree.stageToLocalCoordinates(tmpLocalCoords);

        if (tmpLocalCoords.x < 0f
                || tmpLocalCoords.x > tree.getWidth()
                || tmpLocalCoords.y < 0f
                || tmpLocalCoords.y > tree.getHeight()) {
            return null;
        }

        VisTree.Node node = nodeAt(tmpLocalCoords.y);
        if (node == null || node.getActor() == null) {
            return null;
        }

        Object uo = node.getActor().getUserObject();
        return (uo instanceof AssetNode assetNode) ? assetNode : null;
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        updateTileAnimationDrop();
    }
}
