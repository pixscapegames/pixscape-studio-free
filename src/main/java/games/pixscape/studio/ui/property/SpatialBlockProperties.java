package games.pixscape.studio.ui.property;

import com.artemis.ComponentMapper;
import com.artemis.World;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.kotcrab.vis.ui.widget.VisCheckBox;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisTable;
import games.pixscape.runtime.component.SpatialBlockData;
import games.pixscape.runtime.component.SpatialBlockOrientation;
import games.pixscape.runtime.component.SpatialBlocksComponent;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.history.commands.EditSpatialBlockCommand;
import games.pixscape.studio.service.spatial.SpatialBlockSelectionService;
import games.pixscape.studio.ui.config.CommonLayout;
import games.pixscape.studio.ui.widget.CollapsibleVisTable;
import games.pixscape.studio.ui.widget.SimpleFloatField;
import games.pixscape.studio.ui.widget.SimpleSelectBox;
import games.pixscape.studio.ui.widget.SimpleTextField;

import java.util.function.Consumer;

public final class SpatialBlockProperties extends VisTable {
    private final World world;
    private final HistoryManager history;
    private final SpatialBlockSelectionService selection;
    private final Runnable markPreviewSaveRequired;
    private final ComponentMapper<SpatialBlocksComponent> mBlocks;

    private final SimpleTextField nameField = new SimpleTextField();
    private final SimpleFloatField xField = new SimpleFloatField();
    private final SimpleFloatField yField = new SimpleFloatField();
    private final SimpleFloatField widthField = new SimpleFloatField();
    private final SimpleFloatField depthField = new SimpleFloatField();
    private final SimpleFloatField altitudeField = new SimpleFloatField();
    private final SimpleFloatField heightField = new SimpleFloatField();
    private final SimpleSelectBox<SpatialBlockOrientation> orientationBox = new SimpleSelectBox<>();

    private final VisCheckBox enabledBox = new VisCheckBox("Enabled");
    private final VisCheckBox actorOccluderBox = new VisCheckBox("Actor occluder");
    private final VisCheckBox physicsCollisionBox = new VisCheckBox("Use for physics collision");
    private final VisCheckBox lightOccluderBox = new VisCheckBox("Light occluder");
    private final VisCheckBox shadowCasterBox = new VisCheckBox("Shadow caster");
    private final VisCheckBox particleOccluderBox = new VisCheckBox("Particle occluder");

    private int layerEntityId = -1;
    private int blockId = -1;
    private boolean internalRefresh = false;

    public SpatialBlockProperties(World world,
                                  HistoryManager history,
                                  SpatialBlockSelectionService selection,
                                  Runnable markPreviewSaveRequired) {
        super(true);
        this.world = world;
        this.history = history;
        this.selection = selection;
        this.markPreviewSaveRequired = markPreviewSaveRequired;
        this.mBlocks = world.getMapper(SpatialBlocksComponent.class);

        buildUi();
        bindFields();
    }

    public void setSpatialBlock(int layerEntityId, int blockId) {
        this.layerEntityId = layerEntityId;
        this.blockId = blockId;
        refreshFromModel();
    }

    public void refreshNow() {
        refreshFromModel();
    }

    boolean hasValidSelection() {
        return layerEntityId >= 0 && blockId > 0;
    }

    int activeLayerEntity() {
        if (layerEntityId < 0 || world == null || !world.getEntityManager().isActive(layerEntityId)) {
            return -1;
        }
        return layerEntityId;
    }

    SpatialBlocksComponent activeComponent() {
        int activeLayer = activeLayerEntity();
        if (activeLayer < 0 || !hasValidSelection()) {
            return null;
        }
        return mBlocks.getSafe(activeLayer, null);
    }

    private void buildUi() {
        left().top();
        defaults().left().pad(1);

        add(new VisLabel("SPATIAL BLOCK"))
                .center()
                .padBottom(CommonLayout.PROPERTY_SECTION_TITLE_BOTTOM_PAD)
                .colspan(2)
                .row();

        CollapsibleVisTable dataBlock = new CollapsibleVisTable(true);
        VisTable data = dataBlock.content();
        data.defaults().left().pad(1);

        addRow(data, "Name", nameField);
        data.add(enabledBox).colspan(2).left().row();
        addRow(data, "X", xField);
        addRow(data, "Y", yField);
        addRow(data, "Width", widthField);
        addRow(data, "Depth", depthField);
        addRow(data, "Altitude", altitudeField);
        addRow(data, "Height", heightField);
        addRow(data, "Orientation", orientationBox);

        data.addSeparator().colspan(2).growX().padTop(4).padBottom(4).row();
        data.add(actorOccluderBox).colspan(2).left().row();
        data.add(physicsCollisionBox).colspan(2).left().row();
        data.add(lightOccluderBox).colspan(2).left().row();
        data.add(shadowCasterBox).colspan(2).left().row();
        data.add(particleOccluderBox).colspan(2).left().row();

        add(dataBlock).colspan(2).growX().left().row();
    }

    private static void addRow(VisTable table, String label, Actor actor) {
        table.add(new VisLabel(label)).width(CommonLayout.LABEL_WIDTH).left();
        table.add(actor).width(CommonLayout.FIELD_WIDTH).left().row();
    }

    private void bindFields() {
        nameField.bind(
                () -> {
                    SpatialBlockData block = activeBlock();
                    return block != null && block.name != null ? block.name : "";
                },
                value -> submitEdit(block -> block.name = value != null && !value.isBlank() ? value : null)
        );

        xField.bind(() -> readFloat(block -> block.x), value -> submitEdit(block -> block.x = value));
        yField.bind(() -> readFloat(block -> block.y), value -> submitEdit(block -> block.y = value));
        widthField.bind(() -> readFloat(block -> block.width), value -> submitEdit(block -> block.width = Math.max(0.001f, value)));
        depthField.bind(() -> readFloat(block -> block.depth), value -> submitEdit(block -> block.depth = Math.max(0.001f, value)));
        altitudeField.bind(() -> readFloat(block -> block.altitude), value -> submitEdit(block -> block.altitude = value));
        heightField.bind(() -> readFloat(block -> block.height), value -> submitEdit(block -> block.height = Math.max(0f, value)));

        orientationBox.setItems(SpatialBlockOrientation.values());
        orientationBox.bind(
                () -> {
                    SpatialBlockData block = activeBlock();
                    return block != null && block.orientation != null ? block.orientation : SpatialBlockOrientation.TILE_CELL;
                },
                value -> submitEdit(block -> block.orientation = value != null ? value : SpatialBlockOrientation.TILE_CELL)
        );

        bindCheckBox(enabledBox, (block, value) -> block.enabled = value);
        bindCheckBox(actorOccluderBox, (block, value) -> block.actorOccluder = value);
        bindCheckBox(physicsCollisionBox, (block, value) -> block.physicsCollision = value);
        bindCheckBox(lightOccluderBox, (block, value) -> block.lightOccluder = value);
        bindCheckBox(shadowCasterBox, (block, value) -> block.shadowCaster = value);
        bindCheckBox(particleOccluderBox, (block, value) -> block.particleOccluder = value);
    }

    private void bindCheckBox(VisCheckBox checkBox, BooleanWriter writer) {
        checkBox.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (internalRefresh) return;
                submitEdit(block -> writer.write(block, checkBox.isChecked()));
                event.handle();
            }
        });
    }

    private Float readFloat(FloatReader reader) {
        SpatialBlockData block = activeBlock();
        return block != null ? reader.read(block) : 0f;
    }

    private void submitEdit(Consumer<SpatialBlockData> edit) {
        SpatialBlockData current = activeBlock();
        if (current == null || edit == null) return;

        SpatialBlockData before = current.copy();
        SpatialBlockData after = current.copy();
        edit.accept(after);

        EditSpatialBlockCommand command = new EditSpatialBlockCommand(
                world,
                history.historyIds(),
                selection,
                layerEntityId,
                blockId,
                before,
                after
        );
        if (!command.isNoop()) {
            history.execute(command);
            if (markPreviewSaveRequired != null) markPreviewSaveRequired.run();
        }
        refreshFromModel();
    }

    SpatialBlockData activeBlock() {
        SpatialBlocksComponent component = activeComponent();
        if (component == null || component.blocks == null) {
            clearStaleSelection();
            return null;
        }

        for (int i = 0, n = component.blocks.size; i < n; i++) {
            SpatialBlockData block = component.blocks.get(i);
            if (block != null && block.id == blockId) return block;
        }

        clearStaleSelection();
        return null;
    }

    private void clearStaleSelection() {
        if (activeLayerEntity() < 0) {
            if (selection != null && selection.getEditingLayerEntityId() == layerEntityId) {
                selection.clear();
            }
            layerEntityId = SpatialBlockSelectionService.NO_LAYER;
            blockId = SpatialBlockSelectionService.NO_BLOCK;
            return;
        }

        if (selection != null && selection.getEditingLayerEntityId() == layerEntityId) {
            selection.enterLayer(layerEntityId);
        }
        blockId = SpatialBlockSelectionService.NO_BLOCK;
    }

    private void refreshFromModel() {
        internalRefresh = true;
        try {
            SpatialBlockData block = activeBlock();
            boolean active = block != null;
            nameField.setDisabled(!active);
            xField.setDisabled(!active);
            yField.setDisabled(!active);
            widthField.setDisabled(!active);
            depthField.setDisabled(!active);
            altitudeField.setDisabled(!active);
            heightField.setDisabled(!active);
            orientationBox.setDisabled(!active);
            enabledBox.setDisabled(!active);
            actorOccluderBox.setDisabled(!active);
            physicsCollisionBox.setDisabled(!active);
            lightOccluderBox.setDisabled(!active);
            shadowCasterBox.setDisabled(!active);
            particleOccluderBox.setDisabled(!active);

            nameField.refresh();
            xField.refresh();
            yField.refresh();
            widthField.refresh();
            depthField.refresh();
            altitudeField.refresh();
            heightField.refresh();
            orientationBox.refresh();

            enabledBox.setChecked(block != null && block.enabled);
            actorOccluderBox.setChecked(block != null && block.actorOccluder);
            physicsCollisionBox.setChecked(block != null && block.physicsCollision);
            lightOccluderBox.setChecked(block != null && block.lightOccluder);
            shadowCasterBox.setChecked(block != null && block.shadowCaster);
            particleOccluderBox.setChecked(block != null && block.particleOccluder);
        } finally {
            internalRefresh = false;
        }
        invalidateHierarchy();
    }

    private interface FloatReader {
        float read(SpatialBlockData block);
    }

    private interface BooleanWriter {
        void write(SpatialBlockData block, boolean value);
    }
}
