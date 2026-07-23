package games.pixscape.studio.ui.property;

import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import com.artemis.ComponentMapper;
import com.artemis.World;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.kotcrab.vis.ui.widget.VisCheckBox;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.Tooltip;
import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.history.commands.EditSpatialBlockCommand;
import games.pixscape.studio.service.spatial.SpatialBlockSelectionService;
import games.pixscape.studio.service.spatial.SpatialBlockInteractiveEditSupport;
import games.pixscape.studio.service.spatial.SpatialWallEditSession;
import games.pixscape.studio.ui.config.CommonLayout;
import games.pixscape.studio.ui.widget.CollapsibleVisTable;
import games.pixscape.studio.ui.widget.SimpleFloatField;
import games.pixscape.studio.ui.widget.SimpleTextField;

import java.util.function.Consumer;

public final class SpatialBlockProperties extends VisTable {
    private final World world;
    private final HistoryManager history;
    private final SpatialBlockSelectionService selection;
    private final Runnable markPreviewSaveRequired;
    private final ComponentMapper<SpatialBlocksComponent> mBlocks;

    private final SimpleTextField nameField = new SimpleTextField();
    private final VisLabel structureIdValue = new VisLabel("-");
    private final SimpleFloatField xField = new SimpleFloatField().useExactText();
    private final SimpleFloatField yField = new SimpleFloatField().useExactText();
    private final SimpleFloatField widthField = new SimpleFloatField().useExactText();
    private final SimpleFloatField depthField = new SimpleFloatField().useExactText();
    private final SimpleFloatField altitudeField = new SimpleFloatField();
    private final SimpleFloatField heightField = new SimpleFloatField();
    private final VisCheckBox actorOccluderBox = new VisCheckBox("Actor occluder");
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

        VisLabel title = new VisLabel("SPATIAL WALL");
        title.setName("spatialWallTitle");
        add(title)
                .center()
                .padBottom(CommonLayout.PROPERTY_SECTION_TITLE_BOTTOM_PAD)
                .colspan(2)
                .row();

        CollapsibleVisTable dataBlock = new CollapsibleVisTable(true);
        VisTable data = dataBlock.content();
        data.defaults().left().pad(1);

        nameField.setName("spatialWallName");
        structureIdValue.setName("spatialWallStructureId");
        altitudeField.setName("spatialWallStructureAltitude");
        heightField.setName("spatialWallStructureHeight");
        actorOccluderBox.setName("spatialWallActorOccluder");
        lightOccluderBox.setName("spatialWallLightOccluder");
        shadowCasterBox.setName("spatialWallShadowCaster");
        particleOccluderBox.setName("spatialWallParticleOccluder");

        addRow(data, "Name (optional)", nameField);
        addRow(data, "Structure ID", structureIdValue);
        addRow(data, "X", xField);
        addRow(data, "Y", yField);
        addRow(data, "Width", widthField);
        addRow(data, "Depth", depthField);
        addRow(data, "Structure altitude", altitudeField);
        addRow(data, "Structure height", heightField);
        addTooltip(altitudeField,
                "Updates every wall in the structure as one atomic, undoable operation.");
        addTooltip(heightField,
                "Updates every wall in the structure as one atomic, undoable operation.");

        data.addSeparator().colspan(2).growX().padTop(4).padBottom(4).row();
        data.add(actorOccluderBox).colspan(2).left().row();
        data.add(lightOccluderBox).colspan(2).left().row();
        data.add(shadowCasterBox).colspan(2).left().row();
        data.add(particleOccluderBox).colspan(2).left().row();
        addTooltip(actorOccluderBox, "Controls actor spatial ordering.");
        addTooltip(lightOccluderBox,
                "Stored and compiled; the downstream light-occlusion consumer is not implemented yet.");
        addTooltip(shadowCasterBox,
                "Stored and compiled; the downstream shadow consumer is not implemented yet.");
        addTooltip(particleOccluderBox,
                "Stored and compiled; the downstream particle-occlusion consumer is not implemented yet.");

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

        xField.bind(() -> readFloat(block -> block.x), value -> submitFootprintEdit(value, null, null, null));
        yField.bind(() -> readFloat(block -> block.y), value -> submitFootprintEdit(null, value, null, null));
        widthField.bind(() -> readFloat(block -> block.width), value -> submitFootprintEdit(null, null, value, null));
        depthField.bind(() -> readFloat(block -> block.depth), value -> submitFootprintEdit(null, null, null, value));
        altitudeField.bind(() -> readFloat(block -> block.altitude), value -> submitEdit(block -> block.altitude = value));
        heightField.bind(() -> readFloat(block -> block.height), value -> submitEdit(block -> block.height = Math.max(0f, value)));

        bindCheckBox(actorOccluderBox, (block, value) -> block.actorOccluder = value);
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

    private static void addTooltip(Actor actor, String text) {
        Tooltip tooltip = new Tooltip.Builder(text)
                .target(actor)
                .build();
        tooltip.setAppearDelayTime(0f);
    }

    void submitFootprintEdit(Float x, Float y, Float width, Float depth) {
        SpatialBlockData current = activeBlock();
        SpatialBlocksComponent component = activeComponent();
        TiledLayerComponent tiled = activeLayerEntity() >= 0
                ? world.getMapper(TiledLayerComponent.class).getSafe(activeLayerEntity(), null) : null;
        SpatialWallEditSession session = new SpatialWallEditSession();
        if (current == null || component == null || tiled == null || tiled.data == null
                || !session.begin(layerEntityId, blockId, component, tiled.data)
                || !session.updateProperty(x, y, width, depth)) {
            if (Gdx.app != null && session.rejectionReason() != null) {
                Gdx.app.error("SpatialBlockProperties", session.rejectionReason());
            }
            session.cancel();
            refreshFromModel();
            return;
        }
        SpatialBlockData after = session.candidate().copy();
        session.cancel();
        EditSpatialBlockCommand command = new EditSpatialBlockCommand(
                world, history.historyIds(), selection, layerEntityId, blockId, current, after);
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
            SpatialBlocksComponent component = activeComponent();
            TiledLayerComponent tiled = activeLayerEntity() >= 0
                    ? world.getMapper(TiledLayerComponent.class).getSafe(activeLayerEntity(), null) : null;
            SpatialWallEditSession constraints = new SpatialWallEditSession();
            boolean constrained = active && component != null && tiled != null && tiled.data != null
                    && constraints.begin(layerEntityId, blockId, component, tiled.data);
            boolean attached = constrained && constraints.attachments().isAttached();
            nameField.setDisabled(!active);
            xField.setDisabled(!active || attached);
            yField.setDisabled(!active || attached);
            widthField.setDisabled(!active || constrained && !constraints.isHandleEnabled(
                    SpatialBlockInteractiveEditSupport.ResizeHandle.MAX_X));
            depthField.setDisabled(!active || constrained && !constraints.isHandleEnabled(
                    SpatialBlockInteractiveEditSupport.ResizeHandle.MAX_Y));
            altitudeField.setDisabled(!active);
            heightField.setDisabled(!active);
            actorOccluderBox.setDisabled(!active);
            lightOccluderBox.setDisabled(!active);
            shadowCasterBox.setDisabled(!active);
            particleOccluderBox.setDisabled(!active);
            constraints.cancel();

            nameField.refresh();
            structureIdValue.setText(block != null ? Integer.toString(block.structureId) : "-");
            xField.refresh();
            yField.refresh();
            widthField.refresh();
            depthField.refresh();
            altitudeField.refresh();
            heightField.refresh();
            actorOccluderBox.setChecked(block != null && block.actorOccluder);
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
