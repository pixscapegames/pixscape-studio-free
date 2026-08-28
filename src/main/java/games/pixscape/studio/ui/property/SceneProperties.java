package games.pixscape.studio.ui.property;

import games.pixscape.studio.ui.modal.StudioDialog;

import com.artemis.Aspect;
import com.artemis.World;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.kotcrab.vis.ui.widget.*;
import com.kotcrab.vis.ui.widget.spinner.SimpleFloatSpinnerModel;
import com.kotcrab.vis.ui.widget.spinner.Spinner;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.service.Box2dWorldService;
import games.pixscape.runtime.service.PhysicsService;
import games.pixscape.runtime.system.Box2dSyncSystem;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.configuration.SceneMeta;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.service.LayerService;
import games.pixscape.studio.service.SelectionService;
import games.pixscape.studio.service.physics.PhysicsSelectionReconciler;
import games.pixscape.studio.system.UiRefreshDispatchSystem;
import games.pixscape.studio.ui.config.CommonLayout;
import games.pixscape.studio.ui.widget.*;

import java.util.ArrayList;
import java.util.List;

public class SceneProperties extends VisTable {
    private final SimpleTextField nameField;
    private final VisLabel logicalNameValue;
    private final SimpleTextArea descriptionField;

    private final CollapsibleVisTable physicsBlock;
    private final VisCheckBox physicsEnabled;
    private final SimpleFloatField pixelsPerMeter;
    private final SimpleFloatField gravityXField;
    private final SimpleFloatField gravityYField;
    private final VisCheckBox physicsParallaxCheckBox;
    private final CollapsibleVisTable physicsParallaxBlock;
    private final Spinner physicsParallaxXSpinner;
    private final Spinner physicsParallaxYSpinner;
    private final SimpleFloatSpinnerModel physicsParallaxXModel;
    private final SimpleFloatSpinnerModel physicsParallaxYModel;
    private boolean internalParallaxRefresh = false;

    private final VisTable lightingBlock;
    private final ColorPickerField ambientColorPicker;
    private final SimpleFloatSlider ambientIntensitySlider;
    private boolean internalLightingRefresh = false;
    private boolean internalPhysicsRefresh = false;

    private final CollapsibleVisTable tiledBlock;
    private final VisLabel projection;
    private final VisLabel tileWidthField;
    private final VisLabel tileHeightField;

    private final int MY_TAG = EventFlow.tag(this);
    private boolean dirtyUi = false;
    private String pendingSceneName;
    private String pendingDescription;

    private String currentSceneName = "New Scene";
    private String lastValidName = "New Scene";
    private String lastValidDescription = "";

    private static final float DEFAULT_AMBIENT_R = 0.20f;
    private static final float DEFAULT_AMBIENT_G = 0.20f;
    private static final float DEFAULT_AMBIENT_B = 0.35f;

    private final World world;
    private final LayerService layerService;
    private final SelectionService selectionService;
    private final HistoryManager historyManager;
    private final PhysicsService physicsService;
    private final PhysicsSelectionReconciler physicsSelectionReconciler;
    private final Runnable markCurrentSceneSaveRequired;
    private final Runnable teardownBox2dAfterPurge;
    private SceneMeta pendingPhysicsPurge;

    public SceneProperties(World world,
                           HistoryManager historyManager,
                           PhysicsService physicsService,
                           SelectionService selectionService,
                           LayerService layerService,
                           PhysicsSelectionReconciler physicsSelectionReconciler,
                           Runnable teardownBox2dAfterPurge,
                           Runnable markCurrentSceneSaveRequired) {
        super(true);
        this.world = world;
        this.historyManager = historyManager;
        this.physicsService = physicsService;
        this.selectionService = selectionService;
        this.layerService = layerService;
        this.physicsSelectionReconciler = physicsSelectionReconciler;
        this.teardownBox2dAfterPurge = teardownBox2dAfterPurge;
        this.markCurrentSceneSaveRequired = markCurrentSceneSaveRequired;

        top().left();

        VisLabel nameLabel = new VisLabel("Scene Name:");
        VisLabel logicalNameLabel = new VisLabel("Logical name:");
        VisLabel descriptionLabel = new VisLabel("Description:");

        nameField = new SimpleTextField();
        logicalNameValue = new VisLabel();
        descriptionField = new SimpleTextArea();
        descriptionField.setPrefRows(4);

        nameField.setTextFieldFilter((tf, c) ->
                Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == ' '
        );

        nameField.addListener(new InputListener() {
            @Override
            public boolean keyDown(InputEvent event, int keycode) {
                if (keycode == Input.Keys.ENTER || keycode == Input.Keys.NUMPAD_ENTER) {
                    stripNewlines(nameField);
                    commitNameAndDescription();
                    if (getStage() != null) {
                        getStage().setKeyboardFocus(null);
                    }
                    return true;
                }
                return false;
            }
        });

        nameField.setTextFieldListener((tf, c) -> {
            if (c == '\n' || c == '\r') {
                stripNewlines(nameField);
                commitNameAndDescription();
                if (getStage() != null) {
                    getStage().setKeyboardFocus(null);
                }
            }
        });

        descriptionField.addListener(new InputListener() {
            @Override
            public boolean keyDown(InputEvent event, int keycode) {
                if (keycode == Input.Keys.ENTER || keycode == Input.Keys.NUMPAD_ENTER) {
                    commitDescription();
                    if (getStage() != null) {
                        getStage().setKeyboardFocus(null);
                    }
                    return true;
                }
                return false;
            }
        });

        add(new VisLabel("SCENE"))
                .center()
                .padBottom(CommonLayout.PROPERTY_SECTION_TITLE_BOTTOM_PAD)
                .colspan(2)
                .row();
        add(nameLabel).left();
        add(nameField).growX().row();

        add(logicalNameLabel).left();
        add(logicalNameValue).left().row();

        add(descriptionLabel).left().padTop(15).colspan(2).row();
        add(descriptionField).colspan(2).growX().row();

        addSeparator().colspan(2).growX().padTop(6).row();

        add(new VisLabel("Physics")).center().colspan(2).row();
        physicsEnabled = new VisCheckBox("Physics");
        add(physicsEnabled).left().colspan(2).row();

        physicsBlock = new CollapsibleVisTable(true, true);

        gravityXField = new SimpleFloatField();
        gravityYField = new SimpleFloatField();
        pixelsPerMeter = new SimpleFloatField().validateCommitWith(
                value -> value != null
                        && Float.isFinite(value)
                        && value > 0f);

        physicsBlock.content().add(new VisLabel("Gravity X:")).left();
        physicsBlock.content().add(gravityXField).width(100).left().row();

        physicsBlock.content().add(new VisLabel("Gravity Y:")).left();
        physicsBlock.content().add(gravityYField).width(100).left().row();

        physicsBlock.content().add(new VisLabel("Pixels/m:")).left();
        physicsBlock.content().add(pixelsPerMeter).width(100).left().row();

        physicsParallaxCheckBox = new VisCheckBox("Parallax");
        physicsParallaxCheckBox.setChecked(false);

        physicsParallaxXModel = new SimpleFloatSpinnerModel(1f, 0f, 10f, 0.01f);
        physicsParallaxYModel = new SimpleFloatSpinnerModel(1f, 0f, 10f, 0.01f);

        physicsParallaxXSpinner = new Spinner("Parallax x:", physicsParallaxXModel);
        physicsParallaxYSpinner = new Spinner("Parallax y:", physicsParallaxYModel);
        physicsParallaxXSpinner.setDisabled(true);
        physicsParallaxYSpinner.setDisabled(true);
        physicsParallaxXModel.setPrecision(2);
        physicsParallaxYModel.setPrecision(2);

        physicsParallaxBlock = new CollapsibleVisTable(true, true);
        physicsParallaxBlock.content().add(physicsParallaxXSpinner).left().growX().row();
        physicsParallaxBlock.content().add(physicsParallaxYSpinner).left().growX().row();

        physicsBlock.content().add(physicsParallaxCheckBox).left().colspan(2).row();
        physicsBlock.content().add(physicsParallaxBlock).padLeft(CommonLayout.PAD_LEFT_SUBMENU).left().colspan(2).row();

        add(physicsBlock).padLeft(CommonLayout.PAD_LEFT_SUBMENU).left().colspan(2).row();
        physicsBlock.show(false);

        physicsEnabled.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (internalPhysicsRefresh) return;

                SceneMeta m = currentMeta();
                if (m == null) return;

                boolean requestedEnabled = physicsEnabled.isChecked();
                if (!requestedEnabled && m.physicsEnabled) {
                    showRemoveAllPhysicsDialog(m);
                    return;
                }

                m.physicsEnabled = requestedEnabled;
                flagPreviewSaveRequired();
                physicsBlock.show(m.physicsEnabled);

                setPhysicsParallaxControlsEnabled(
                        m.physicsEnabled && physicsParallaxCheckBox.isChecked()
                );

                EventFlow.i().publish(
                        new EventFlow.ScenePhysicsEnabledChanged(m.physicsEnabled, MY_TAG)
                );
            }
        });

        physicsParallaxCheckBox.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (internalParallaxRefresh) return;
                applyPhysicsParallaxSelection();
            }
        });

        physicsParallaxXSpinner.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (internalParallaxRefresh) return;
                applyPhysicsParallaxSpinners();
            }
        });

        physicsParallaxYSpinner.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (internalParallaxRefresh) return;
                applyPhysicsParallaxSpinners();
            }
        });

        addSeparator().colspan(2).growX().padTop(6).row();
        add(new VisLabel("Ambient lighting")).center().colspan(2).row();

        lightingBlock = new VisTable(true);

        ambientColorPicker = new ColorPickerField("Ambient color", "Choose color...")
                .allowAlpha(false)
                .useColorSwatch(50f, 25f);
        ambientIntensitySlider = new SimpleFloatSlider(0.0f, 1.0f, 0.01f);

        lightingBlock.add(new VisLabel("Color:")).left();
        lightingBlock.add(ambientColorPicker).left().row();

        lightingBlock.add(new VisLabel("Bright")).left();
        lightingBlock.add(new VisLabel("Dark")).right().row();

        lightingBlock.add(ambientIntensitySlider).left().colspan(2).growX().row();

        add(lightingBlock).left().colspan(2).row();

        ambientColorPicker.bind(
                () -> {
                    SceneMeta m = currentMeta();
                    if (m == null) return new Color(DEFAULT_AMBIENT_R, DEFAULT_AMBIENT_G, DEFAULT_AMBIENT_B, 1f);
                    return new Color(
                            clamp01(fallbackIfNaN(m.ambientColorR, DEFAULT_AMBIENT_R)),
                            clamp01(fallbackIfNaN(m.ambientColorG, DEFAULT_AMBIENT_G)),
                            clamp01(fallbackIfNaN(m.ambientColorB, DEFAULT_AMBIENT_B)),
                            1f
                    );
                },
                color -> {
                    if (internalLightingRefresh) return;
                    SceneMeta m = currentMeta();
                    if (m == null) return;
                    m.ambientColorR = clamp01(color.r);
                    m.ambientColorG = clamp01(color.g);
                    m.ambientColorB = clamp01(color.b);
                    publishAmbientFromColorAndIntensity(m);
                }
        );

        ambientIntensitySlider.bind(
                () -> {
                    SceneMeta m = currentMeta();
                    if (m == null) return 0.0f;
                    return clamp01(fallbackIfNaN(m.ambientIntensity, 0.0f));
                },
                value -> {
                    if (internalLightingRefresh) return;
                    SceneMeta m = currentMeta();
                    if (m == null) return;
                    m.ambientIntensity = clamp01(value);
                    publishAmbientFromColorAndIntensity(m);
                }
        );

        EventFlow.i().subscribe(EventFlow.LayerOrderChanged.class, evt -> {
            if (evt.sourceTag() == MY_TAG) return;
            markDirty();
        });


        tiledBlock = new CollapsibleVisTable(true, true);

        projection = new VisLabel();
        tileWidthField = new VisLabel();
        tileHeightField = new VisLabel();
        tiledBlock.content().addSeparator().colspan(2).growX().padTop(6).row();
        tiledBlock.content().add(new VisLabel("Tiled map")).colspan(2).center().row();

        tiledBlock.content().add(new VisLabel("Projection:")).left();
        tiledBlock.content().add(projection).left().row();

        tiledBlock.content().add(new VisLabel("Tile Width:")).left();
        tiledBlock.content().add(tileWidthField).width(100).left().row();

        tiledBlock.content().add(new VisLabel("Tile Height:")).left();
        tiledBlock.content().add(tileHeightField).width(100).left().growX().row();

        add(tiledBlock).colspan(2).growX().row();
        tiledBlock.show(false);

        UiRefreshDispatchSystem postProcess = world.getSystem(UiRefreshDispatchSystem.class);
        postProcess.add(this::updateIfDirty);
        postProcess.add(this::completePendingPhysicsPurge);

        EventFlow.i().subscribe(EventFlow.CurrentSceneMeta.class, ev -> {
            if (ev.sourceTag() == MY_TAG) return;
            pendingSceneName = ev.sceneName();
            pendingDescription = ev.description();
            markDirty();
        });
    }

    private void flagPreviewSaveRequired() {
        if (markCurrentSceneSaveRequired != null) {
            markCurrentSceneSaveRequired.run();
        }
    }

    private void publishAmbientFromColorAndIntensity(SceneMeta m) {
        float t = clamp01(fallbackIfNaN(m.ambientIntensity, 0f));
        float r = clamp01(fallbackIfNaN(m.ambientColorR, DEFAULT_AMBIENT_R));
        float g = clamp01(fallbackIfNaN(m.ambientColorG, DEFAULT_AMBIENT_G));
        float b = clamp01(fallbackIfNaN(m.ambientColorB, DEFAULT_AMBIENT_B));

        m.ambientIntensity = t;
        m.ambientColorR = r;
        m.ambientColorG = g;
        m.ambientColorB = b;

        float mr = 1f + (r - 1f) * t;
        float mg = 1f + (g - 1f) * t;
        float mb = 1f + (b - 1f) * t;

        m.ambientMulR = mr;
        m.ambientMulG = mg;
        m.ambientMulB = mb;

        publishAmbient(m);
        flagPreviewSaveRequired();
    }

    private void commitNameAndDescription() {
        commitName();
        commitDescription();
    }

    private void commitName() {
        String raw = nameField.getText();
        String after = raw != null ? raw.trim() : "";

        if (after.isEmpty()) {
            nameField.setText(lastValidName);
            nameField.setCursorPosition(lastValidName.length());
            return;
        }

        String before = lastValidName;
        if (after.equals(before)) return;

        lastValidName = after;
        currentSceneName = after;

        nameField.setText(after);
        nameField.setCursorPosition(after.length());

        EventFlow.i().publish(new EventFlow.SceneNameChanged(before, after, MY_TAG));
        flagPreviewSaveRequired();
    }

    private void commitDescription() {
        if (currentSceneName == null) {
            return;
        }

        String text = descriptionField.getText();
        if (text == null) text = "";

        if (text.equals(lastValidDescription)) {
            return;
        }

        lastValidDescription = text;
        EventFlow.i().publish(new EventFlow.SceneDescriptionChanged(currentSceneName, text, MY_TAG));
        flagPreviewSaveRequired();
    }

    private static void stripNewlines(VisTextField field) {
        String t = field.getText();
        if (t == null) return;
        if (t.indexOf('\n') >= 0 || t.indexOf('\r') >= 0) {
            t = t.replace("\n", "").replace("\r", "");
            field.setText(t);
            field.setCursorPosition(t.length());
        }
    }

    private void refreshLogicalNameLabel() {
        ProjectConfig cfg = ProjectConfig.getInstance();
        if (cfg == null) {
            logicalNameValue.setText("(no project)");
            return;
        }

        SceneMeta meta = cfg.getSceneMeta(currentSceneName);
        if (meta == null) {
            logicalNameValue.setText("(unknown)");
            return;
        }

        String canonicalTag = cfg.canonicalSceneTagFor(meta);
        logicalNameValue.setText(canonicalTag != null ? canonicalTag : "(unknown)");
    }

    private SceneMeta currentMeta() {
        ProjectConfig cfg = ProjectConfig.getInstance();
        return cfg != null ? cfg.getCurrentSceneMeta() : null;
    }

    private void markDirty() {
        dirtyUi = true;
    }

    private void updateIfDirty() {
        if (!dirtyUi) return;
        dirtyUi = false;

        currentSceneName = pendingSceneName != null ? pendingSceneName : "New Scene";
        lastValidName = currentSceneName;
        lastValidDescription = pendingDescription != null ? pendingDescription : "";

        nameField.setText(lastValidName);
        nameField.setCursorPosition(lastValidName.length());
        refreshLogicalNameLabel();

        descriptionField.setText(lastValidDescription);
        descriptionField.setCursorPosition(lastValidDescription.length());

        refreshPhysicsFromMeta();
        refreshLightingFromMeta();
        refreshTiledFromMeta();
    }

    private void refreshPhysicsFromMeta() {
        SceneMeta m = currentMeta();
        if (m == null) {
            internalPhysicsRefresh = true;
            try {
                physicsEnabled.setChecked(false);
                physicsBlock.show(false);
                setPhysicsParallaxControlsEnabled(false);
            } finally {
                internalPhysicsRefresh = false;
            }
            return;
        }

        internalPhysicsRefresh = true;
        try {
            physicsEnabled.setChecked(m.physicsEnabled);
            physicsBlock.show(m.physicsEnabled);

            pixelsPerMeter.bind(
                    () -> m.pixelsPerMeter,
                    v -> {
                        m.pixelsPerMeter = v;
                        flagPreviewSaveRequired();
                        EventFlow.i().publish(new EventFlow.ScenePhysicsPixelsPerMeterChanged(
                                m.pixelsPerMeter,
                                MY_TAG
                        ));
                    }
            );
            pixelsPerMeter.refresh();

            gravityXField.bind(
                    () -> m.gravityX,
                    v -> {
                        m.gravityX = v;
                        flagPreviewSaveRequired();
                    }
            );
            gravityXField.refresh();

            gravityYField.bind(
                    () -> m.gravityY,
                    v -> {
                        m.gravityY = v;
                        flagPreviewSaveRequired();
                    }
            );
            gravityYField.refresh();

            refreshPhysicsParallaxFromMeta(m);
        } finally {
            internalPhysicsRefresh = false;
        }

        EventFlow.i().publish(new EventFlow.ScenePhysicsEnabledChanged(m.physicsEnabled, MY_TAG));
        EventFlow.i().publish(new EventFlow.SceneGravityChanged(m.gravityX, m.gravityY, MY_TAG));
    }

    private void refreshLightingFromMeta() {
        SceneMeta m = currentMeta();
        if (m == null) {
            internalLightingRefresh = true;
            try {
                ambientColorPicker.setDisabled(true);
                ambientIntensitySlider.setDisabled(true);
                ambientIntensitySlider.setValue(0.0f);
            } finally {
                internalLightingRefresh = false;
            }
            return;
        }

        internalLightingRefresh = true;
        try {
            ambientColorPicker.setDisabled(false);
            ambientIntensitySlider.setDisabled(false);
            ambientColorPicker.refresh();
            ambientIntensitySlider.refresh();
        } finally {
            internalLightingRefresh = false;
        }
    }

    private void refreshTiledFromMeta() {
        SceneMeta m = currentMeta();
        if (m == null) {
            tiledBlock.show(false);
            projection.setText("");
            tileWidthField.setText("");
            tileHeightField.setText("");
            return;
        }

        tiledBlock.show(m.tiledEnabled);
        projection.setText(m.tiledProjection.name());
        tileWidthField.setText(Float.toString(m.tileWidth));
        tileHeightField.setText(Float.toString(m.tileHeight));
    }

    private void refreshPhysicsParallaxFromMeta(SceneMeta m) {
        internalParallaxRefresh = true;
        try {
            boolean enabled = !(Float.isNaN(m.physicsParallaxX) && Float.isNaN(m.physicsParallaxY));
            physicsParallaxCheckBox.setChecked(enabled);

            physicsParallaxXModel.setValue(Float.isNaN(m.physicsParallaxX) ? 1f : m.physicsParallaxX);
            physicsParallaxYModel.setValue(Float.isNaN(m.physicsParallaxY) ? 1f : m.physicsParallaxY);

            setPhysicsParallaxControlsEnabled(enabled && physicsEnabled.isChecked());
        } finally {
            internalParallaxRefresh = false;
        }
    }

    private void applyPhysicsParallaxSelection() {
        SceneMeta m = currentMeta();
        if (m == null) return;

        boolean enabled = physicsParallaxCheckBox.isChecked();
        if (!enabled) {
            m.physicsParallaxX = Float.NaN;
            m.physicsParallaxY = Float.NaN;
        } else {
            m.physicsParallaxX = physicsParallaxXModel.getValue();
            m.physicsParallaxY = physicsParallaxYModel.getValue();
        }
        setPhysicsParallaxControlsEnabled(enabled && physicsEnabled.isChecked());
        flagPreviewSaveRequired();
    }

    private void applyPhysicsParallaxSpinners() {
        SceneMeta m = currentMeta();
        if (m == null) return;

        if (!physicsParallaxCheckBox.isChecked()) {
            return;
        }

        m.physicsParallaxX = physicsParallaxXModel.getValue();
        m.physicsParallaxY = physicsParallaxYModel.getValue();
        flagPreviewSaveRequired();
    }

    private void setPhysicsParallaxControlsEnabled(boolean enabled) {
        physicsParallaxBlock.show(enabled);
        physicsParallaxXSpinner.setDisabled(!enabled);
        physicsParallaxYSpinner.setDisabled(!enabled);
    }

    private void showRemoveAllPhysicsDialog(SceneMeta m) {
        if (m == null) {
            refreshPhysicsFromMeta();
            return;
        }

        VisDialog dialog = new StudioDialog("Warning") {
            @Override
            protected void result(Object object) {
                if (Boolean.TRUE.equals(object)) {
                    beginPhysicsPurge(m);
                } else {
                    refreshPhysicsFromMeta();
                }
            }
        };

        dialog.text(
                """
                        Disabling physics will permanently delete all physics in this scene.
                        This includes bodies, fixtures, sensors and attached joints.
                        Layers and non-physics entities will remain unchanged.
                        
                        Do you want to continue?"""
        );
        dialog.button("Remove", true);
        dialog.button("Cancel", false);
        dialog.setModal(true);
        dialog.setResizable(false);
        dialog.pack();

        if (getStage() != null) {
            dialog.show(getStage());
        } else {
            refreshPhysicsFromMeta();
        }
    }

    private void beginPhysicsPurge(SceneMeta sceneMeta) {
        List<Integer> bodyEntityIds = entitiesWith(Aspect.all(PhysicsBodyComponent.class));
        validateActive(bodyEntityIds);
        com.badlogic.gdx.utils.IntArray jointEntityIds =
                new com.badlogic.gdx.utils.IntArray(false, 16);
        com.badlogic.gdx.utils.IntSet uniqueJointIds =
                new com.badlogic.gdx.utils.IntSet();
        com.badlogic.gdx.utils.IntArray connected =
                new com.badlogic.gdx.utils.IntArray(false, 8);
        for (int bodyEntityId : bodyEntityIds) {
            physicsService.collectJointsAffectedByBodyRemoval(
                    bodyEntityId, connected);
            for (int i = 0; i < connected.size; i++) {
                int jointEntityId = connected.get(i);
                if (uniqueJointIds.add(jointEntityId)) {
                    jointEntityIds.add(jointEntityId);
                }
            }
        }

        for (int bodyEntityId : bodyEntityIds) {
            physicsService.removePhysics(bodyEntityId);
            EventFlow.i().publish(new EventFlow.PhysicsBodyStructureChanged(bodyEntityId, MY_TAG));
        }
        for (int i = 0; i < jointEntityIds.size; i++) {
            historyManager.historyIds().unbindEntity(jointEntityIds.get(i));
        }
        pendingPhysicsPurge = sceneMeta;
    }

    private void completePendingPhysicsPurge() {
        if (pendingPhysicsPurge == null) return;
        Box2dSyncSystem sync = world.getSystem(Box2dSyncSystem.class);
        Box2dWorldService box2d = sync != null ? sync.getBox2d() : null;
        if (box2d != null && box2d.world != null
                && (box2d.world.getBodyCount() != 0 || box2d.world.getJointCount() != 0)) {
            return;
        }

        if (teardownBox2dAfterPurge != null) {
            teardownBox2dAfterPurge.run();
        }
        historyManager.historyIds().pruneInactive(world);
        if (physicsSelectionReconciler != null) {
            physicsSelectionReconciler.reconcile();
        }
        clearInactiveGeneralSelection();

        SceneMeta completed = pendingPhysicsPurge;
        pendingPhysicsPurge = null;
        completed.physicsEnabled = false;
        physicsBlock.show(false);
        setPhysicsParallaxControlsEnabled(false);
        flagPreviewSaveRequired();
        historyManager.resetAfterIrreversibleChange();
        EventFlow.i().publish(new EventFlow.ScenePhysicsEnabledChanged(false, MY_TAG));
    }

    private void clearInactiveGeneralSelection() {
        if (selectionService == null) return;
        com.badlogic.gdx.utils.IntArray selected = selectionService.getSelectionSnapshot();
        for (int i = 0; i < selected.size; i++) {
            if (!world.getEntityManager().isActive(selected.get(i))) {
                selectionService.clearSelection();
                return;
            }
        }
    }

    private void validateActive(List<Integer> entityIds) {
        for (int i = 0; i < entityIds.size(); i++) {
            int entityId = entityIds.get(i);
            if (!world.getEntityManager().isActive(entityId)) {
                throw new IllegalStateException(
                        "Physics purge target is inactive: entityId " + entityId + ".");
            }
        }
    }

    private List<Integer> entitiesWith(Aspect.Builder aspect) {
        IntBag entities = world.getAspectSubscriptionManager().get(aspect).getEntities();
        List<Integer> result = new ArrayList<>(entities.size());
        int[] data = entities.getData();
        for (int i = 0, n = entities.size(); i < n; i++) {
            result.add(data[i]);
        }
        return result;
    }

    private static float clamp01(float v) {
        if (v < 0f) return 0f;
        if (v > 1f) return 1f;
        return v;
    }

    private static float fallbackIfNaN(float v, float fallback) {
        return Float.isNaN(v) ? fallback : v;
    }

    private void publishAmbient(SceneMeta m) {
        EventFlow.i().publish(new EventFlow.SceneAmbientMulChanged(
                m.ambientMulR, m.ambientMulG, m.ambientMulB, MY_TAG
        ));
    }
}
