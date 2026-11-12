package games.pixscape.studio.ui.property;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.DragListener;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntSet;
import com.badlogic.gdx.utils.Scaling;
import com.kotcrab.vis.ui.widget.*;
import games.pixscape.studio.asset.*;
import games.pixscape.studio.io.StudioFs;
import games.pixscape.studio.service.StandaloneTextureCache;
import games.pixscape.studio.ui.asset.AnimatedThumbnail;
import games.pixscape.studio.ui.asset.dnd.DragContext;
import games.pixscape.studio.ui.asset.dnd.DragPayload;
import games.pixscape.studio.ui.main.StudioApplicationAdapter;
import games.pixscape.studio.ui.widget.CollapsibleVisTable;
import games.pixscape.studio.ui.widget.InsertionPreviewGrid;
import games.pixscape.studio.ui.widget.SimpleTextField;

/**
 * Immediate edits through SceneService commands. This view owns actors, never cached textures.
 */
public final class TileAnimationProperties extends VisTable {
    private final StudioApplicationAdapter app;
    private final VisTable details;
    private final VisLabel status = new VisLabel();
    private final InsertionPreviewGrid sequence = new InsertionPreviewGrid(84, 94);
    private final Vector2 pointer = new Vector2();
    private final Array<Actor> ghosts = new Array<>();
    private final Array<SimpleTextField> durations = new Array<>();
    private final Array<VisTable> cells = new Array<>();
    private final IntSet selected = new IntSet();
    private int anchor = -1;
    private PopupMenu contextMenu;
    private int id = -1;
    private boolean editing;
    private TileAnimationProjectDefData displayed;
    private AnimatedThumbnail preview;
    private com.badlogic.gdx.scenes.scene2d.ui.Container<AnimatedThumbnail> previewFrame;
    private VisLabel summary;
    private DragPayload drag;
    private int[] draggedIds;
    private String rejection;
    private TileAnimationProjectDefData movingSource;
    private int[] movingIndices;
    private final Vector2 movePointer = new Vector2();
    private int moveDestination = -1;

    TileAnimationProperties(StudioApplicationAdapter app) {
        super(true);
        this.app = app;
        top().left();
        var section = new CollapsibleVisTable(true);
        details = section.content();
        details.top().left();
        add(section).growX().row();
        sequence.setName("tileAnimationSequence");
        sequence.addListener(new InputListener() {
            @Override
            public boolean keyDown(InputEvent event, int keycode) {
                if (movingSource != null && keycode == Input.Keys.ESCAPE) {
                    getStage().cancelTouchFocus();
                    clearMove();
                    event.stop();
                    return true;
                }
                if (!editing || keycode != Input.Keys.FORWARD_DEL || getStage().getKeyboardFocus() != sequence)
                    return false;
                event.stop();
                deleteSelected();
                return true;
            }

            @Override
            public boolean keyUp(InputEvent event, int keycode) {
                if (!editing || keycode != Input.Keys.FORWARD_DEL || getStage().getKeyboardFocus() != sequence)
                    return false;
                event.stop();
                return true;
            }
        });
        status.setName("tileAnimationStatus");
        status.setWrap(true);
    }

    public boolean isEditing() {
        return editing;
    }

    public int assetId() {
        return id;
    }

    public boolean canUndo() {
        return app.getSceneService().tileAnimationHistory().canUndo();
    }

    public boolean canRedo() {
        return app.getSceneService().tileAnimationHistory().canRedo();
    }

    public boolean undo() {
        if (!canUndo()) return false;
        app.getSceneService().tileAnimationHistory().undo();
        return true;
    }

    public boolean redo() {
        if (!canRedo()) return false;
        app.getSceneService().tileAnimationHistory().redo();
        return true;
    }

    boolean select(int target, boolean edit) {
        selected.clear();
        anchor = -1;
        id = target;
        editing = edit;
        displayed = null;
        return refresh();
    }

    /**
     * Asset reloads can replace borrowed textures without replacing the animation definition.
     */
    boolean reload() {
        var previous = displayed;
        var next = app.getSceneService().findTileAnimationProjectDef(id);
        if (next == null) return false;
        if (previous != null && !java.util.Arrays.equals(previous.frameAssetIds, next.frameAssetIds)) {
            selected.clear();
            anchor = -1;
        }
        displayed = next;
        rebuild();
        return true;
    }

    public boolean refresh() {
        TileAnimationProjectDefData next = app.getSceneService().findTileAnimationProjectDef(id);
        if (next == null) return false;
        if (displayed == next) return true;
        if (displayed != null && displayed.baseTileAssetId == next.baseTileAssetId
                && java.util.Objects.equals(displayed.name, next.name)
                && java.util.Arrays.equals(displayed.frameAssetIds, next.frameAssetIds)) {
            displayed = next;
            // Keep focus/traversal targets alive while a duration field publishes its command.
            for (SimpleTextField duration : durations) duration.refresh();
            refreshSummary();
            if (previewFrame != null) {
                preview.stopPreview();
                preview = createPreview();
                previewFrame.setActor(preview);
            }
            return true;
        }
        selected.clear();
        anchor = -1;
        displayed = next;
        rebuild();
        return true;
    }

    private void rebuild() {
        clearMove();
        closeMenu();
        cells.clear();
        unbindDurations();
        clearDrag();
        if (preview != null) preview.stopPreview();
        preview = null;
        previewFrame = null;
        details.clearChildren();
        details.add(new VisLabel("TILE ANIMATION")).center().row();
        VisLabel name = new VisLabel(displayed.name);
        name.setName("tileAnimationName");
        name.setWrap(true);
        details.add(name).growX().row();
        String tileset = tilesetName();
        if (tileset != null) {
            var label = new VisLabel("Tileset: " + tileset);
            label.setWrap(true);
            details.add(label).growX().row();
        }
        summary = new VisLabel();
        refreshSummary();
        summary.setName("tileAnimationSummary");
        details.add(summary).left().row();
        Array<Texture> textures = new Array<>();
        for (int tile : displayed.frameAssetIds) textures.add(texture(tile));
        if (textures.size > 0) {
            preview = createPreview();
            previewFrame = new com.badlogic.gdx.scenes.scene2d.ui.Container<>(preview);
            previewFrame.fill().minWidth(0).prefWidth(0);
            previewFrame.setClip(true);
            details.add(previewFrame).growX().height(120).row();
        }
        // UI navigation action, not an ECS-bound field.
        VisTextButton edit = new VisTextButton(editing ? "Done" : "Edit");
        edit.setName("tileAnimationEdit");
        edit.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                // Done is a user validation boundary; replacing a target is not.
                if (editing && getStage() != null) getStage().setKeyboardFocus(null);
                editing = !editing;
                selected.clear();
                anchor = -1;
                rebuild();
            }
        });
        details.add(edit).left().padTop(4).padBottom(4).row();
        Array<Actor> rows = new Array<>();
        for (int i = 0; i < displayed.frameAssetIds.length; i++) rows.add(frameRow(i, textures.get(i)));
        VisLabel empty = editing && rows.isEmpty() ? new VisLabel("Drop tiles here") : null;
        if (empty != null) {
            empty.setName("tileAnimationEmptyDrop");
            empty.setAlignment(com.badlogic.gdx.utils.Align.center);
        }
        sequence.setItems(rows, empty);
        if (editing || rows.notEmpty()) details.add(sequence).growX().row();
        details.add(status).growX().row();
        status.setText("");
    }

    private Actor frameRow(int index, Texture texture) {
        VisTable row = new VisTable(true);
        row.setName("tileAnimationFrame" + index);
        row.top().pad(4);
        cells.add(row);
        row.setBackground(selected.contains(index) ? "border" : "window-bg");
        row.add(thumbnail(texture)).size(48).colspan(2).row();
        SimpleTextField duration = new SimpleTextField();
        duration.setName("tileAnimationDuration" + index);
        int target = id;
        var project = games.pixscape.studio.configuration.ProjectConfig.getInstance();
        duration.onFocusLostCommit(() -> editing && id == target
                && games.pixscape.studio.configuration.ProjectConfig.getInstance() == project
                && app.getSceneService().findTileAnimationProjectDef(target) == displayed);
        durations.add(duration);
        duration.bind(() -> {
            var def = app.getSceneService().findTileAnimationProjectDef(id);
            return def != null && index < def.frameDurationsMs.length ? Integer.toString(def.frameDurationsMs[index]) : "";
        }, text -> runEdit(() -> app.getSceneService().updateTileAnimationFrameDuration(id, index, Integer.parseInt(text.trim()))));
        duration.setDisabled(!editing);
        row.add(duration).width(48);
        row.add(new VisLabel("ms")).left();
        new Tooltip.Builder("Duration in ms - Enter, Tab or leave the field to apply").target(duration).build();
        ClickListener click = new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (!editing || event.getTarget() == duration) return;
                boolean ctrl = Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT) || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT);
                boolean shift = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT);
                if (shift && anchor >= 0) {
                    selected.clear();
                    for (int i = Math.min(anchor, index); i <= Math.max(anchor, index); i++) selected.add(i);
                } else if (ctrl) {
                    if (!selected.remove(index)) selected.add(index);
                    anchor = index;
                } else {
                    selected.clear();
                    selected.add(index);
                    anchor = index;
                }
                refreshSelection();
                getStage().setKeyboardFocus(sequence);
                event.stop();
            }
        };
        row.addListener(click);
        DragListener move = new DragListener() {
            private TileAnimationProjectDefData gestureSource;

            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                if (!editing || event.getTarget() == duration || DragContext.get().active()) return false;
                return super.touchDown(event, x, y, pointer, button);
            }

            @Override
            public void dragStart(InputEvent event, float x, float y, int pointer) {
                click.cancel();
                if (!editing || row.getStage() == null) return;
                // User focus departure commits a pending duration before the gesture snapshot.
                getStage().setKeyboardFocus(sequence);
                clearDrag();
                closeMenu();
                if (!selected.contains(index)) {
                    selected.clear();
                    selected.add(index);
                    anchor = index;
                }
                refreshSelection();
                movingIndices = selected.iterator().toArray().toArray();
                java.util.Arrays.sort(movingIndices);
                movingSource = displayed;
                gestureSource = movingSource;
                sequence.beginMove(movingIndices);
                for (int occurrence : movingIndices)
                    ghosts.add(ghost(texture(displayed.frameAssetIds[occurrence]), displayed.frameDurationsMs[occurrence]));
                int initial = index;
                for (int occurrence : movingIndices) if (occurrence < index) initial--;
                sequence.showInsertion(initial, ghosts);
            }

            @Override
            public void drag(InputEvent event, float x, float y, int pointer) {
                if (movingSource != gestureSource) return;
                movePointer.set(event.getStageX(), event.getStageY());
                updateMove();
            }

            @Override
            public void dragStop(InputEvent event, float x, float y, int pointer) {
                if (movingSource == null || movingSource != gestureSource) return;
                if (event.isTouchFocusCancel()) {
                    clearMove();
                    return;
                }
                movePointer.set(event.getStageX(), event.getStageY());
                updateMove();
                var source = movingSource;
                int[] indices = movingIndices;
                int destination = moveDestination;
                clearMove();
                if (source == null || destination < 0) return;
                try {
                    app.getSceneService().moveFramesInTileAnimation(id, indices, destination, source);
                    refresh();
                    selected.clear();
                    for (int i = 0; i < indices.length; i++) selected.add(destination + i);
                    anchor = destination;
                    refreshSelection();
                    if (getStage() != null) getStage().setKeyboardFocus(sequence);
                } catch (RuntimeException failure) {
                    status.setText(failure.getMessage());
                }
            }
        };
        move.setTapSquareSize(4);
        row.addCaptureListener(move);
        row.addListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                if (!editing || button != Input.Buttons.RIGHT || event.getTarget() == duration) return false;
                if (!selected.contains(index)) {
                    selected.clear();
                    selected.add(index);
                    anchor = index;
                }
                refreshSelection();
                getStage().setKeyboardFocus(sequence);
                showMenu(event.getStageX(), event.getStageY());
                event.stop();
                return true;
            }
        });
        return row;
    }

    private void refreshSelection() {
        for (int i = 0; i < cells.size; i++) cells.get(i).setBackground(selected.contains(i) ? "border" : "window-bg");
    }

    private void showMenu(float x, float y) {
        closeMenu();
        PopupMenu menu = new PopupMenu();
        contextMenu = menu;
        MenuItem delete = new MenuItem("Delete");
        delete.setName("tileAnimationDelete");
        delete.setDisabled(selected.isEmpty());
        delete.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (contextMenu == menu) deleteSelected();
            }
        });
        menu.addItem(delete);
        menu.showMenu(getStage(), x, y);
    }

    private void closeMenu() {
        if (contextMenu != null) contextMenu.remove();
        contextMenu = null;
    }

    private void deleteSelected() {
        closeMenu();
        if (!editing || selected.isEmpty()) return;
        int[] indices = selected.iterator().toArray().toArray();
        runEdit(() -> app.getSceneService().removeFramesFromTileAnimation(id, indices));
        if (getStage() != null) getStage().setKeyboardFocus(sequence);
    }

    private void refreshSummary() {
        long total = 0;
        for (int duration : displayed.frameDurationsMs) total += duration;
        summary.setText(displayed.frameAssetIds.length + " frames, " + total + " ms");
    }

    private AnimatedThumbnail createPreview() {
        Array<Texture> textures = new Array<>();
        for (int tile : displayed.frameAssetIds) textures.add(texture(tile));
        var actor = new AnimatedThumbnail(textures, displayed.frameDurationsMs);
        actor.setName("tileAnimationPreview");
        actor.setScaling(Scaling.fit);
        actor.startPreview();
        return actor;
    }

    private void runEdit(Runnable edit) {
        if (!editing) return;
        try {
            edit.run();
            refresh();
            status.setText("");
        } catch (NumberFormatException failure) {
            status.setText("Enter a positive duration in milliseconds.");
        } catch (RuntimeException failure) {
            status.setText(failure.getMessage());
        }
    }

    private Actor thumbnail(Texture texture) {
        if (texture == null) return new VisLabel("Missing");
        VisImage image = new VisImage(texture);
        image.setScaling(Scaling.fit);
        return image;
    }

    private Texture texture(int assetId) {
        var meta = app.getSceneService().getAssetMetaDatabase().findById(assetId);
        if (meta == null || meta.sourceRelPath() == null) return null;
        try {
            return StandaloneTextureCache.getOrLoadProjectRelative(meta.sourceRelPath());
        } catch (RuntimeException missing) {
            return null;
        }
    }

    private String tilesetName() {
        var db = app.getSceneService().getAssetMetaDatabase();
        var base = db.findById(displayed.baseTileAssetId);
        int owner = base instanceof TileAssetMeta tile ? tile.tilesetId : -1;
        if (owner < 0 && displayed.frameAssetIds.length > 0) {
            for (int frame : displayed.frameAssetIds) {
                var meta = db.findById(frame);
                if (!(meta instanceof TileAssetMeta tile) || tile.tilesetId <= 0) return null;
                if (owner > 0 && owner != tile.tilesetId) return "Mixed tilesets";
                owner = tile.tilesetId;
            }
        }
        var tileset = db.findById(owner);
        return tileset != null ? AssetDisplayInfo.from(tileset).displayName() : null;
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        if (movingSource != null) updateMove();
        else updateDrop();
    }

    private void updateMove() {
        if (movingSource == null) return;
        if (!editing || getStage() == null || app.getSceneService().findTileAnimationProjectDef(id) != movingSource) {
            clearMove();
            return;
        }
        for (Actor parent = this; parent != null; parent = parent.getParent())
            if (!parent.isVisible()) {
                clearMove();
                return;
            }
        Actor hit = getStage().hit(movePointer.x, movePointer.y, true);
        moveDestination = -1;
        if (hit == null || (hit != sequence && !hit.isDescendantOf(sequence))) return;
        pointer.set(movePointer);
        sequence.stageToLocalCoordinates(pointer);
        moveDestination = sequence.insertionAt(pointer.x, pointer.y);
        sequence.showInsertion(moveDestination, ghosts);
    }

    private void clearMove() {
        if (movingSource == null) return;
        movingSource = null;
        movingIndices = null;
        moveDestination = -1;
        sequence.clearInsertion();
        sequence.endMove();
        ghosts.clear();
    }

    private Actor ghost(Texture texture, int duration) {
        VisTable ghost = new VisTable(true);
        ghost.top().pad(4);
        ghost.add(thumbnail(texture)).size(48).row();
        ghost.add(new VisLabel(duration + " ms"));
        return ghost;
    }

    private void updateDrop() {
        DragPayload payload = DragContext.get().peek();
        if (!editing || getStage() == null || payload == null) {
            clearDrag();
            return;
        }
        for (Actor parent = this; parent != null; parent = parent.getParent())
            if (!parent.isVisible()) {
                clearDrag();
                return;
            }
        pointer.set(Gdx.input.getX(), Gdx.input.getY());
        getStage().screenToStageCoordinates(pointer);
        Actor hit = getStage().hit(pointer.x, pointer.y, true);
        if (hit == null || (hit != sequence && !hit.isDescendantOf(sequence))) {
            clearDrag();
            return;
        }
        if (drag != payload) prepareDrop(payload);
        if (draggedIds == null) {
            sequence.clearInsertion();
        } else {
            sequence.stageToLocalCoordinates(pointer);
            sequence.showInsertion(sequence.insertionAt(pointer.x, pointer.y), ghosts);
        }
        DragPayload released = DragContext.get().consumeIfReleasedInside(true);
        if (released != null) {
            int index = sequence.insertionIndex();
            int[] tiles = draggedIds;
            String error = rejection;
            clearDrag();
            try {
                if (tiles != null) runEdit(() -> app.getSceneService().insertTileAnimationFrames(id, index, tiles));
                else status.setText(error);
            } finally {
                if (released.ghostPixmap != null) {
                    released.ghostPixmap.dispose();
                    released.ghostPixmap = null;
                }
            }
        }
    }

    /**
     * Interpret the existing ordered browser payload once on entry; hover never mutates the model.
     */
    private void prepareDrop(DragPayload payload) {
        clearDrag();
        drag = payload;
        try {
            if (!"tile-asset".equals(payload.type))
                throw new IllegalArgumentException("Only tile assets can be added.");
            Array<String> paths = payload.paths != null && payload.paths.size > 0 ? payload.paths : Array.with(payload.path);
            int[] ids = new int[paths.size];
            for (int i = 0; i < paths.size; i++)
                ids[i] = app.getSceneService().resolveAssetIdBySourceRelPath(
                        StudioFs.DIR_ORIG_TILES + "/" + paths.get(i), AssetType.TILE);
            app.getSceneService().validateTileAnimationFrames(id, ids);
            draggedIds = ids;
            for (int tile : ids) {
                ghosts.add(ghost(texture(tile), 300));
            }
            status.setText("Drop to add " + ids.length + " frame(s)");
        } catch (RuntimeException failure) {
            rejection = failure.getMessage();
            draggedIds = null;
            status.setText(rejection);
        }
    }

    private void clearDrag() {
        if (drag != null) status.setText("");
        drag = null;
        draggedIds = null;
        rejection = null;
        ghosts.clear();
        sequence.clearInsertion();
    }

    @Override
    protected void setStage(Stage stage) {
        if (stage == null) {
            clearMove();
            clearDrag();
            closeMenu();
        }
        super.setStage(stage);
    }

    void clearProject() {
        clearMove();
        closeMenu();
        selected.clear();
        anchor = -1;
        cells.clear();
        unbindDurations();
        clearDrag();
        editing = false;
        id = -1;
        displayed = null;
        if (preview != null) preview.stopPreview();
        preview = null;
        previewFrame = null;
        summary = null;
        sequence.setItems(new Array<>(), null);
        details.clearChildren();
    }

    private void unbindDurations() {
        for (SimpleTextField field : durations) field.unbind();
        durations.clear();
    }
}
