package games.pixscape.studio.ui.property;

import com.artemis.ComponentMapper;
import com.artemis.World;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Align;
import com.kotcrab.vis.ui.widget.VisCheckBox;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.spinner.SimpleFloatSpinnerModel;
import com.kotcrab.vis.ui.widget.spinner.Spinner;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.component.LayerParallaxComponent;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.history.commands.Command;
import games.pixscape.studio.history.commands.ToggleSpatialActorLayerCommand;
import games.pixscape.studio.service.LayerService;
import games.pixscape.studio.ui.modal.StudioDialog;
import games.pixscape.studio.ui.config.CommonLayout;
import games.pixscape.studio.ui.widget.CollapsibleVisTable;
import games.pixscape.studio.ui.widget.TextField;
import games.pixscape.studio.ui.widget.UiBinders;
import games.pixscape.studio.ui.widget.UiFieldFactory;

public class LayerProperties extends VisTable {

    private final World world;
    private final HistoryManager history;
    private final LayerService layerService;

    private final ComponentMapper<LayerComponent> mIndex;
    private final ComponentMapper<LayerParallaxComponent> mParallax;

    public final VisLabel indexValueLabel;
    public final TextField nameField;
    public final TextField descriptionField;

    private final UiBinders.FloatSpinnerBinder parallaxXBinder;
    private final UiBinders.FloatSpinnerBinder parallaxYBinder;

    private final VisCheckBox parallaxCheckBox;
    private final VisCheckBox spatialCheckBox;

    private final CollapsibleVisTable parallaxSection = new CollapsibleVisTable(true, true);
    private final CollapsibleVisTable parallaxBlock = new CollapsibleVisTable(true, true);
    private final CollapsibleVisTable spatialSection = new CollapsibleVisTable(true, true);

    private final int MY_TAG = EventFlow.tag(this);
    private boolean internalParallaxRefresh = false;
    private boolean internalSpatialRefresh = false;
    private final Runnable markCurrentSceneSaveRequired;

    public LayerProperties(
            World world, HistoryManager history,
            LayerService layerService,
            Runnable markCurrentSceneSaveRequired) {
        super(true);
        this.world = world;
        this.history = history;
        this.layerService = layerService;
        this.markCurrentSceneSaveRequired = markCurrentSceneSaveRequired;

        this.mIndex = world.getMapper(LayerComponent.class);
        this.mParallax = world.getMapper(LayerParallaxComponent.class);

        UiFieldFactory factory = new UiFieldFactory(world);

        VisLabel nameLabel = new VisLabel("Name:");
        VisLabel descriptionLabel = new VisLabel("Description:");
        VisLabel indexLabel = new VisLabel("Index:");

        indexValueLabel = new VisLabel();
        nameField = factory.layerName();
        nameField.onEnter(() -> {
            EventFlow.i().publish(
                    new EventFlow.LayerNameChanged(nameField.getEntityId(),
                            nameField.getText(),
                            MY_TAG));
        });
        descriptionField = factory.layerDescription();

        parallaxCheckBox = new VisCheckBox("Parallax");
        parallaxCheckBox.left();

        spatialCheckBox = new VisCheckBox("Spatial");
        spatialCheckBox.left();

        SimpleFloatSpinnerModel modelX = new SimpleFloatSpinnerModel(1f, 0f, 10f, 0.01f);
        SimpleFloatSpinnerModel modelY = new SimpleFloatSpinnerModel(1f, 0f, 10f, 0.01f);

        Spinner parallaxXSpinner = new Spinner("Parallax x", modelX);
        Spinner parallaxYSpinner = new Spinner("Parallax y", modelY);
        modelX.setPrecision(2);
        modelY.setPrecision(2);

        parallaxXBinder = new UiBinders.FloatSpinnerBinder(
                world,
                parallaxXSpinner,
                modelX,
                mParallax::has,
                eid -> {
                    LayerParallaxComponent lp = mParallax.getSafe(eid, null);
                    return (lp != null) ? lp.factorX : 1f;
                },
                (eid, value) -> {
                    LayerParallaxComponent lp = mParallax.getSafe(eid, null);
                    if (lp == null) return;
                    lp.factorX = value;
                    flagPreviewSaveRequired();
                }
        );

        parallaxYBinder = new UiBinders.FloatSpinnerBinder(
                world,
                parallaxYSpinner,
                modelY,
                mParallax::has,
                eid -> {
                    LayerParallaxComponent lp = mParallax.getSafe(eid, null);
                    return (lp != null) ? lp.factorY : 1f;
                },
                (eid, value) -> {
                    LayerParallaxComponent lp = mParallax.getSafe(eid, null);
                    if (lp == null) return;
                    lp.factorY = value;
                    flagPreviewSaveRequired();
                }
        );

        parallaxCheckBox.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (internalParallaxRefresh) return;

                int layerEntityId = nameField.getEntityId();
                if (layerEntityId == -1) return;
                if (!supportsEditableParallax(layerEntityId)) return;

                if (parallaxCheckBox.isChecked()) {
                    parallaxBlock.show(true);
                    LayerParallaxComponent lp = mParallax.getSafe(layerEntityId, null);
                    if (lp == null) {
                        lp = mParallax.create(layerEntityId);
                        lp.factorX = 1f;
                        lp.factorY = 1f;
                        flagPreviewSaveRequired();
                    }
                    parallaxXBinder.setEntityId(layerEntityId);
                    parallaxYBinder.setEntityId(layerEntityId);
                } else {
                    parallaxBlock.show(false);
                    if (mParallax.has(layerEntityId)) {
                        mParallax.remove(layerEntityId);
                        flagPreviewSaveRequired();
                    }
                    parallaxXBinder.setEntityId(-1);
                    parallaxYBinder.setEntityId(-1);
                }
            }
        });

        spatialCheckBox.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (internalSpatialRefresh) {
                    return;
                }

                int layerEntityId = nameField.getEntityId();
                if (layerEntityId < 0) {
                    return;
                }

                boolean currentlyActive = mIndex.get(layerEntityId).spatialEnabled;
                boolean requestedActive = spatialCheckBox.isChecked();
                if (requestedActive == currentlyActive) {
                    refreshFromModel(layerEntityId);
                    return;
                }

                if (!requestedActive && ToggleSpatialActorLayerCommand.footprintOwners(
                        world, mIndex.get(layerEntityId).layerIndex).notEmpty()) {
                    showDisableSpatialDialog(layerEntityId);
                } else {
                    executeOrdinarySpatialToggle(layerEntityId, requestedActive);
                }
                refreshFromModel(layerEntityId);
            }
        });

        parallaxBlock.content().add(parallaxXSpinner).width(80).left().growX().row();
        parallaxBlock.content().add(parallaxYSpinner).width(80).left().growX().row();
        parallaxBlock.show(false);

        add(new VisLabel("LAYER"))
                .center()
                .padBottom(CommonLayout.PROPERTY_SECTION_TITLE_BOTTOM_PAD)
                .colspan(2)
                .row();

        add(nameLabel).left();
        add(nameField).growX().row();

        add(descriptionLabel).top().left();
        add(descriptionField).growX().row();

        add(indexLabel).left();
        add(indexValueLabel).left().row();

        parallaxSection.content().addSeparator().growX().row();
        parallaxSection.content().add(parallaxCheckBox).left().growX().row();
        parallaxSection.content().add(parallaxBlock).padLeft(55).left().growX().row();
        parallaxSection.show(false);

        VisLabel spatialTitle = new VisLabel("SPATIAL");
        spatialTitle.setAlignment(Align.center);

        spatialSection.content().addSeparator().growX().row();
        spatialSection.content().add(spatialTitle)
                .colspan(2)
                .center()
                .padBottom(CommonLayout.PROPERTY_SECTION_TITLE_BOTTOM_PAD)
                .expandX()
                .row();
        spatialSection.content().add(spatialCheckBox).left().growX().row();
        spatialSection.show(false);

        add(parallaxSection).colspan(2).left().growX().row();
        add(spatialSection).colspan(2).left().growX().row();
    }

    private void flagPreviewSaveRequired() {
        if (markCurrentSceneSaveRequired != null) {
            markCurrentSceneSaveRequired.run();
        }
    }

    public void setLayerEntityId(int layerEntityId) {
        nameField.setEntityId(layerEntityId);
        descriptionField.setEntityId(layerEntityId);
        refreshFromModel(layerEntityId);
    }

    private void refreshFromModel(int layerEntityId) {
        LayerComponent lic = mIndex.getSafe(layerEntityId, null);
        if (lic == null) {
            Gdx.app.error(
                    "LayerProperties",
                    "Layer entity has no LayerComponent. layerEntityId=" +
                            layerEntityId +
                            " (likely stale id after world rebuild)"
            );
            return;
        }

        indexValueLabel.setText(lic.layerIndex);
        boolean ordinarySpatialVisible = shouldShowOrdinarySpatialProperty(
                lic.spatialEnabled,
                layerService.hasOtherSpatialActorLayer(layerEntityId));
        boolean spatialSupported = ordinarySpatialVisible;
        boolean spatialActive = lic.spatialEnabled;
        boolean supportsParallax = supportsEditableParallax(lic);
        boolean hasParallax = mParallax.has(layerEntityId);

        internalParallaxRefresh = true;
        try {
            parallaxCheckBox.setChecked(hasParallax);
            parallaxSection.show(supportsParallax);
            parallaxBlock.show(supportsParallax && hasParallax);
        } finally {
            internalParallaxRefresh = false;
        }

        if (supportsParallax) {
            parallaxXBinder.setEntityId(layerEntityId);
            parallaxYBinder.setEntityId(layerEntityId);
        } else {
            parallaxXBinder.setEntityId(-1);
            parallaxYBinder.setEntityId(-1);
        }

        internalSpatialRefresh = true;
        try {
            spatialCheckBox.setText("Spatial");
            spatialCheckBox.setChecked(spatialActive);
            spatialSection.show(spatialSupported);
        } finally {
            internalSpatialRefresh = false;
        }

        invalidateHierarchy();
    }

    private boolean supportsEditableParallax(int layerEntityId) {
        LayerComponent lic = mIndex.getSafe(layerEntityId, null);
        return supportsEditableParallax(lic);
    }

    private boolean supportsEditableParallax(LayerComponent lic) {
        return lic != null;
    }

    private void executeCommand(Command command) {
        if (command instanceof HistoryManager.SupportsNoop supportsNoop && supportsNoop.isNoop()) {
            return;
        }
        long revision = history.getMutationRevision();
        history.execute(command);
        if (history.getMutationRevision() != revision) {
            flagPreviewSaveRequired();
        }
    }

    private void showDisableSpatialDialog(int layerEntityId) {
        if (getStage() == null) {
            return;
        }
        long revision = history.getMutationRevision();
        var layer = mIndex.get(layerEntityId);
        StudioDialog dialog = new StudioDialog("Disable Spatial") {
            @Override
            protected void result(Object result) {
                if (Boolean.TRUE.equals(result) && history.getMutationRevision() == revision
                        && world.getEntityManager().isActive(layerEntityId)
                        && mIndex.getSafe(layerEntityId, null) == layer && layer.spatialEnabled) {
                    executeOrdinarySpatialToggle(layerEntityId, false);
                }
                refreshFromModel(nameField.getEntityId());
            }
        };
        dialog.setName("disable-layer-spatial-dialog");
        dialog.text("Disabling Spatial will remove all footprints in this layer,\n"
                + "including those owned by objects inside its compositions.\n"
                + "This operation can be undone.");
        dialog.button("Cancel", false);
        dialog.button("OK", true);
        dialog.closeOnEscape();
        dialog.setModal(true);
        dialog.setResizable(false);
        dialog.show(getStage());
    }

    static boolean shouldShowOrdinarySpatialProperty(
            boolean currentlyEnabled,
            boolean anotherOrdinaryLayerEnabled) {
        return currentlyEnabled || !anotherOrdinaryLayerEnabled;
    }

    private void executeOrdinarySpatialToggle(int layerEntityId, boolean enabled) {
        executeCommand(new ToggleSpatialActorLayerCommand(
                world,
                history.historyIds(),
                layerService,
                layerEntityId,
                enabled
        ));
    }

}
