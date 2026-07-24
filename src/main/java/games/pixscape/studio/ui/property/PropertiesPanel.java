package games.pixscape.studio.ui.property;

import com.artemis.ComponentMapper;
import com.artemis.World;
import com.badlogic.gdx.utils.IntArray;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisScrollPane;
import com.kotcrab.vis.ui.widget.VisTable;
import games.pixscape.runtime.component.light.ConeLightComponent;
import games.pixscape.runtime.component.light.PointLightComponent;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.component.physics.PhysicsJointComponent;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.event.GetScrollListener;
import games.pixscape.studio.event.LoseScroolListener;
import games.pixscape.studio.service.IconResolver;
import games.pixscape.studio.service.SelectionService;
import games.pixscape.studio.service.physics.PhysicsSelectionService;
import games.pixscape.studio.system.UiRefreshDispatchSystem;
import games.pixscape.studio.ui.docking.DockablePanel;
import games.pixscape.studio.ui.main.StudioApplicationAdapter;
import games.pixscape.studio.ui.property.entityproperties.ConeLightProperties;
import games.pixscape.studio.ui.property.entityproperties.EntityProperties;
import games.pixscape.studio.ui.property.entityproperties.EntityPropertiesContext;
import games.pixscape.studio.ui.property.entityproperties.PointLightProperties;
import games.pixscape.studio.ui.property.entityproperties.physics.BodyProperties;
import games.pixscape.studio.ui.property.entityproperties.physics.FixturesPanel;
import games.pixscape.studio.ui.property.entityproperties.physics.JointProperties;

public class PropertiesPanel extends DockablePanel {

    private final EntityProperties entityProperties;
    private final BodyProperties bodyProperties;
    private final FixturesPanel fixtureProperties;
    private final PointLightProperties pointLightProperties;
    private final ConeLightProperties coneLightProperties;
    private final JointProperties jointProperties;
    private final SpatialBlockProperties spatialBlockProperties;
    private final LayerProperties layerProperties;
    private final SceneProperties sceneProperties;
    private final TiledMapProperties tiledMapProperties;

    private final VisTable contentHolder;

    private int boundEntity = -1;
    private int boundBody = -1;
    private int boundFixtureBody = -1;
    private long boundFixtureId = PhysicsSelectionService.NO_SHAPE;
    private int boundJoint = -1;
    private int boundSpatialBlockLayer = -1;
    private int boundSpatialBlockId = -1;
    private int boundLayer = -1;
    private int boundLight = -1;
    private int boundTiledMap = -1;

    private int pendingTiledMap = -1;
    private int tiledMapContextLayer = -1;

    /**
     * Body currently used as the physics editing context.
     * As long as it remains valid, fixture deselection returns to BodyPanel.
     */
    private int physicsContextBody = -1;

    private final int MY_TAG = EventFlow.tag(this);

    private final World world;
    private final SelectionService selectionService;
    private final PhysicsSelectionService physicsSelectionService;
    private final ComponentMapper<PhysicsJointComponent> mJointBase;
    private final ComponentMapper<PhysicsBodyComponent> mPhysBody;
    private final ComponentMapper<PhysicsShapesComponent> mPhysFixtures;
    private final ComponentMapper<PointLightComponent> mPointLight;
    private final ComponentMapper<ConeLightComponent> mConeLight;

    private boolean dirty = true;
    private PendingView pendingView = PendingView.SCENE;
    private IntArray pendingSelection = null;
    private int pendingLayer = -1;
    private int pendingBody = -1;
    private int pendingFixtureBody = -1;
    private long pendingFixtureId = PhysicsSelectionService.NO_SHAPE;
    private int pendingSpatialBlockLayer = -1;
    private int pendingSpatialBlockId = -1;

    private enum PendingView {
        SCENE,
        SELECTION,
        BODY,
        FIXTURE,
        SPATIAL_BLOCK,
        LAYER,
        TILED_MAP
    }

    public PropertiesPanel(StudioApplicationAdapter app) {
        super("Properties");

        var canvas = app.getCanvas();
        var layerService = canvas.getLayerService();
        this.world = canvas.getEcsWorld();
        this.selectionService = canvas.getSelectionService();
        this.mJointBase = world.getMapper(PhysicsJointComponent.class);
        this.mPhysBody = world.getMapper(PhysicsBodyComponent.class);
        this.mPhysFixtures = world.getMapper(PhysicsShapesComponent.class);
        this.mPointLight = world.getMapper(PointLightComponent.class);
        this.mConeLight = world.getMapper(ConeLightComponent.class);
        this.physicsSelectionService = canvas.getPhysicsSelectionService();

        EntityPropertiesContext ctx = new EntityPropertiesContext(
                world,
                canvas.getHistoryManager(),
                physicsSelectionService,
                layerService,
                canvas.getAtlasService(),
                selectionService,
                canvas.getIdentityRegistry(),
                new IconResolver(world),
                app.getSceneService()::markPreviewSaveRequired,
                MY_TAG
        );

        entityProperties = new EntityProperties(ctx);
        bodyProperties = new BodyProperties(ctx);
        fixtureProperties = new FixturesPanel(ctx);
        pointLightProperties = new PointLightProperties(ctx);
        coneLightProperties = new ConeLightProperties(ctx);
        jointProperties = new JointProperties(world, canvas.getHistoryManager(), canvas.getEditorOps(), selectionService);
        spatialBlockProperties = new SpatialBlockProperties(
                world,
                canvas.getHistoryManager(),
                canvas.getSpatialBlockSelectionService(),
                app.getSceneService()::markPreviewSaveRequired
        );

        Runnable markPreviewSaveRequired = app.getSceneService()::markPreviewSaveRequired;
        layerProperties = new LayerProperties(world, canvas.getHistoryManager(), markPreviewSaveRequired);
        sceneProperties = new SceneProperties(
                world, canvas.getHistoryManager(), selectionService, layerService, markPreviewSaveRequired);
        tiledMapProperties = new TiledMapProperties(world, markPreviewSaveRequired);

        contentHolder = new VisTable(true);
        contentHolder.top().left().pad(8);

        VisScrollPane scroller = new VisScrollPane(contentHolder);
        scroller.setFadeScrollBars(false);
        scroller.setScrollingDisabled(true, false);
        scroller.setFlickScroll(false);
        scroller.addListener(new GetScrollListener(scroller));
        scroller.addListener(new LoseScroolListener());

        add(scroller).grow().row();

        UiRefreshDispatchSystem postProcess = world.getSystem(UiRefreshDispatchSystem.class);
        postProcess.add(this::updateIfDirty);

        showSceneProperties();

        EventFlow.i().subscribe(EventFlow.SelectionChanged.class, evt -> {
            if (evt.sourceTag() == MY_TAG) return;
            pendingSelection = evt.ids() != null ? new IntArray(evt.ids()) : null;
            pendingView = PendingView.SELECTION;
            markDirty();
        });

        EventFlow.i().subscribe(EventFlow.FixtureSelectionChanged.class, evt -> {
            if (evt.sourceTag() == MY_TAG) return;
            pendingFixtureBody = evt.bodyEntityId();
            pendingFixtureId = evt.physicsShapeId();
            pendingView = PendingView.FIXTURE;
            markDirty();
        });

        EventFlow.i().subscribe(EventFlow.FixtureParametersChanged.class, evt -> {
            if (evt.sourceTag() == MY_TAG) return;
            pendingFixtureBody = evt.bodyEntityId();
            pendingFixtureId = evt.physicsShapeId();
            pendingView = PendingView.FIXTURE;
            markDirty();
        });

        EventFlow.i().subscribe(EventFlow.FixtureSelectionCleared.class, evt -> {
            if (evt.sourceTag() == MY_TAG) return;
            pendingFixtureBody = -1;
            pendingFixtureId = PhysicsSelectionService.NO_SHAPE;
            pendingView = PendingView.FIXTURE;
            markDirty();
        });

        EventFlow.i().subscribe(EventFlow.SpatialBlockSelectionChanged.class, evt -> {
            if (evt.sourceTag() == MY_TAG) return;
            pendingSpatialBlockLayer = evt.layerEntityId();
            pendingSpatialBlockId = evt.blockId();
            pendingView = PendingView.SPATIAL_BLOCK;
            markDirty();
        });

        EventFlow.i().subscribe(EventFlow.SpatialBlocksChanged.class, evt -> {
            if (evt.sourceTag() == MY_TAG) return;
            if (evt.layerEntityId() == boundSpatialBlockLayer) {
                pendingSpatialBlockLayer = boundSpatialBlockLayer;
                pendingSpatialBlockId = boundSpatialBlockId;
                pendingView = PendingView.SPATIAL_BLOCK;
                markDirty();
            }
        });

        EventFlow.i().subscribe(EventFlow.CurrentLayerChanged.class, evt -> {
            if (evt.sourceTag() == MY_TAG) return;

            if (evt.source() != SelectionService.SelectionSource.TREE) {
                clearTiledMapContext();
            }

            pendingLayer = evt.layerEntityId();
            pendingView = PendingView.LAYER;
            markDirty();
        });

        EventFlow.i().subscribe(EventFlow.CurrentSceneMeta.class, evt -> {
            if (evt.sourceTag() == MY_TAG) return;
            pendingView = PendingView.SCENE;
            markDirty();
        });

        EventFlow.i().subscribe(EventFlow.ScenePhysicsEnabledChanged.class, evt -> {
            if (boundLayer < 0) return;
            pendingLayer = boundLayer;
            pendingView = PendingView.LAYER;
            markDirty();
        });

        EventFlow.i().subscribe(EventFlow.LayerSpatialDepthChanged.class, evt -> {
            if (evt.sourceTag() == MY_TAG) return;
            if (evt.layerEntityId() == boundLayer) {
                pendingLayer = boundLayer;
                pendingView = PendingView.LAYER;
            } else if (boundEntity >= 0) {
                pendingView = PendingView.SELECTION;
                pendingSelection = selectionService.getSelectionSnapshot();
            } else {
                return;
            }
            markDirty();
        });

        EventFlow.i().subscribe(EventFlow.CurrentCameraChanged.class, evt -> {
            if (evt.sourceTag() == MY_TAG) return;
            pendingView = PendingView.SCENE;
            markDirty();
        });

        EventFlow.i().subscribe(EventFlow.JointParametersChanged.class, evt -> {
            if (evt.sourceTag() == MY_TAG) return;
            if (evt.jointEntityId() == boundJoint) {
                jointProperties.markDirty();
            }
        });
    }

    public void requestBodyProperties(int bodyEntityId) {
        pendingBody = bodyEntityId;
        pendingView = PendingView.BODY;
        showBodyProperties(bodyEntityId);
    }

    public void requestTiledMapProperties(int layerEntityId) {
        tiledMapContextLayer = layerEntityId;
        pendingTiledMap = layerEntityId;
        pendingView = PendingView.TILED_MAP;
        showTiledMapProperties(layerEntityId);
    }

    public void clearTiledMapMode() {
        clearTiledMapContext();
    }

    private void showSceneProperties() {
        contentHolder.clearChildren();
        contentHolder.add(sceneProperties).growX().top().left().row();
        clearBindings();
        clearPhysicsContext();
        clearTiledMapContext();
    }

    private void showLayerProperties(int layerEntity) {
        contentHolder.clearChildren();
        layerProperties.setLayerEntityId(layerEntity);
        contentHolder.add(layerProperties).growX().top().left().row();
        clearBindings();
        clearPhysicsContext();
        clearTiledMapContext();
        boundLayer = layerEntity;
    }

    private void showEntityProperties(int entityId) {
        contentHolder.clearChildren();
        entityProperties.setEntityId(entityId);
        contentHolder.add(entityProperties).growX().top().left().row();
        clearBindings();
        clearPhysicsContext();
        clearTiledMapContext();
        boundEntity = entityId;
    }

    private void showBodyProperties(int bodyEntityId) {
        contentHolder.clearChildren();
        bodyProperties.setEntityId(bodyEntityId);
        contentHolder.add(bodyProperties).growX().top().left().row();
        clearBindings();
        clearTiledMapContext();
        boundBody = bodyEntityId;
        physicsContextBody = bodyEntityId;
    }

    private void showFixtureProperties(int bodyEntityId, long physicsShapeId) {
        contentHolder.clearChildren();
        fixtureProperties.setEntityId(bodyEntityId);
        fixtureProperties.refreshNow();
        contentHolder.add(fixtureProperties).growX().top().left().row();
        clearBindings();
        clearTiledMapContext();
        boundFixtureBody = bodyEntityId;
        boundFixtureId = physicsShapeId;
        physicsContextBody = bodyEntityId;
    }

    private void showLightPointProperties(int entityId) {
        contentHolder.clearChildren();
        pointLightProperties.setEntityId(entityId);
        contentHolder.add(pointLightProperties).growX().top().left().row();
        clearBindings();
        clearTiledMapContext();
        clearPhysicsContext();
        boundLight = entityId;
    }

    private void showLightConeProperties(int entityId) {
        contentHolder.clearChildren();
        coneLightProperties.setEntityId(entityId);
        contentHolder.add(coneLightProperties).growX().top().left().row();
        clearBindings();
        clearTiledMapContext();
        clearPhysicsContext();
        boundLight = entityId;
    }

    private void showJointProperties(int jointEid) {
        contentHolder.clearChildren();
        jointProperties.setJointEntityId(jointEid);
        contentHolder.add(jointProperties).growX().top().left().row();
        clearBindings();
        clearTiledMapContext();
        boundJoint = jointEid;
    }

    private void showSpatialBlockProperties(int layerEntityId, int blockId) {
        contentHolder.clearChildren();
        spatialBlockProperties.setSpatialBlock(layerEntityId, blockId);
        contentHolder.add(spatialBlockProperties).growX().top().left().row();
        clearBindings();
        clearPhysicsContext();
        clearTiledMapContext();
        boundSpatialBlockLayer = layerEntityId;
        boundSpatialBlockId = blockId;
    }

    private void showMultiSelection(int count) {
        contentHolder.clearChildren();
        contentHolder.add(new VisLabel("Multiple selection (" + count + " entities)"))
                .growX().top().left().row();
        clearBindings();
        clearPhysicsContext();
        clearTiledMapContext();
    }

    private void showTiledMapProperties(int layerEntityId) {
        contentHolder.clearChildren();
        tiledMapProperties.setLayerEntityId(layerEntityId);
        contentHolder.add(tiledMapProperties).growX().top().left().row();
        clearBindings();
        clearPhysicsContext();
        boundTiledMap = layerEntityId;
    }

    private void clearBindings() {
        boundEntity = -1;
        boundBody = -1;
        boundFixtureBody = -1;
        boundFixtureId = PhysicsSelectionService.NO_SHAPE;
        boundJoint = -1;
        boundSpatialBlockLayer = -1;
        boundSpatialBlockId = -1;
        boundLayer = -1;
        boundLight = -1;
        boundTiledMap = -1;
    }

    private void clearTiledMapContext() {
        tiledMapContextLayer = -1;
        pendingTiledMap = -1;
    }

    private void clearPhysicsContext() {
        physicsContextBody = -1;
        pendingBody = -1;
    }

    private boolean isValidBodyContext(int bodyEntityId) {
        return bodyEntityId >= 0
                && world.getEntityManager().isActive(bodyEntityId)
                && mPhysBody.has(bodyEntityId);
    }

    private boolean hasValidPhysicsContext() {
        return isValidBodyContext(physicsContextBody);
    }

    private boolean isExplicitPhysicsContextActive() {
        return physicsSelectionService.getFocusedBodyEid() >= 0;
    }

    public void markDirty() {
        dirty = true;
    }

    public void updateIfDirty() {
        if (!dirty) return;
        dirty = false;

        switch (pendingView) {
            case SELECTION -> onSelectionChanged(pendingSelection);
            case BODY -> onBodySelectionChanged(pendingBody);
            case FIXTURE -> onFixtureSelectionChanged(pendingFixtureBody, pendingFixtureId);
            case SPATIAL_BLOCK -> onSpatialBlockSelectionChanged(pendingSpatialBlockLayer, pendingSpatialBlockId);
            case LAYER -> onActiveLayerChanged(pendingLayer);
            case SCENE -> showSceneProperties();
            case TILED_MAP -> showTiledMapProperties(pendingTiledMap);
        }
    }

    public void onActiveLayerChanged(int newLayerEntityId) {
        if (newLayerEntityId == tiledMapContextLayer) {
            if (newLayerEntityId != boundTiledMap) {
                showTiledMapProperties(newLayerEntityId);
            }
            return;
        }

        if (newLayerEntityId == boundLayer) {
            layerProperties.setLayerEntityId(newLayerEntityId);
        } else {
            showLayerProperties(newLayerEntityId);
        }
    }

    public void onBodySelectionChanged(int bodyEntityId) {
        if (!isValidBodyContext(bodyEntityId)) {
            clearPhysicsContext();
            restoreAfterFixtureDeselection();
            return;
        }

        if (bodyEntityId != boundBody) {
            showBodyProperties(bodyEntityId);
        } else {
            physicsContextBody = bodyEntityId;
            bodyProperties.setEntityId(bodyEntityId);
        }
    }

    public void onFixtureSelectionChanged(int bodyEntityId, long physicsShapeId) {
        if (physicsShapeId > PhysicsSelectionService.NO_SHAPE
                && bodyEntityId >= 0
                && world.getEntityManager().isActive(bodyEntityId)
                && fixtureExists(bodyEntityId, physicsShapeId)) {
            if (bodyEntityId != boundFixtureBody || physicsShapeId != boundFixtureId) {
                showFixtureProperties(bodyEntityId, physicsShapeId);
            } else {
                physicsContextBody = bodyEntityId;
                fixtureProperties.refreshNow();
            }
            return;
        }
        restoreAfterFixtureDeselection();
    }

    private boolean fixtureExists(int bodyEntityId, long physicsShapeId) {
        PhysicsShapesComponent fixtures = mPhysFixtures.getSafe(bodyEntityId, null);
        if (fixtures == null || fixtures.shapes == null) return false;
        for (int i = 0; i < fixtures.shapes.size; i++) {
            var fixture = fixtures.shapes.get(i);
            if (fixture != null && fixture.physicsShapeId == physicsShapeId) return true;
        }
        return false;
    }

    public void onSpatialBlockSelectionChanged(int layerEntityId, int blockId) {
        if (layerEntityId >= 0 && blockId > 0) {
            if (layerEntityId != boundSpatialBlockLayer || blockId != boundSpatialBlockId) {
                showSpatialBlockProperties(layerEntityId, blockId);
            } else {
                spatialBlockProperties.refreshNow();
            }
            return;
        }

        if (layerEntityId >= 0) {
            showLayerProperties(layerEntityId);
            return;
        }

        onSelectionChanged(selectionService.getSelectionSnapshot());
    }

    public void onSelectionChanged(IntArray selectionSnapshot) {
        if (selectionSnapshot == null || selectionSnapshot.size == 0) {
            if (hasValidPhysicsContext() && isExplicitPhysicsContextActive()) {
                if (physicsContextBody != boundBody) showBodyProperties(physicsContextBody);
                return;
            }

            if (tiledMapContextLayer >= 0 && selectionService.getActivelayerId() == tiledMapContextLayer) {
                if (tiledMapContextLayer != boundTiledMap) {
                    showTiledMapProperties(tiledMapContextLayer);
                }
                return;
            }

            showSceneProperties();
            return;
        }

        clearTiledMapContext();

        int visibleSelectionCount = countNonJointSelection(selectionSnapshot);

        if (visibleSelectionCount > 1) {
            showMultiSelection(visibleSelectionCount);
            return;
        }

        int e = selectionSnapshot.get(0);
        if (e < 0 || !world.getEntityManager().isActive(e)) {
            if (hasValidPhysicsContext() && isExplicitPhysicsContextActive()) {
                if (physicsContextBody != boundBody) showBodyProperties(physicsContextBody);
                return;
            }
            showSceneProperties();
            return;
        }

        long selectedFixtureId = physicsSelectionService.getSelectedPhysicsShapeId();
        boolean explicitPhysicsActive = isExplicitPhysicsContextActive();
        boolean samePhysicsContext = hasValidPhysicsContext() && physicsContextBody == e;

        if (selectedFixtureId > PhysicsSelectionService.NO_SHAPE
                && (physicsSelectionService.isFocusedBody(e) || (samePhysicsContext && explicitPhysicsActive))) {
            if (e != boundFixtureBody || selectedFixtureId != boundFixtureId) {
                showFixtureProperties(e, selectedFixtureId);
            } else {
                physicsContextBody = e;
                fixtureProperties.refreshNow();
            }
            return;
        }

        if (samePhysicsContext && explicitPhysicsActive) {
            if (e != boundBody) showBodyProperties(e);
            return;
        }

        if (mJointBase.has(e)) {
            if (e != boundJoint) showJointProperties(e);
        } else if (mPointLight.has(e)) {
            if (e != boundLight) showLightPointProperties(e);
        } else if (mConeLight.has(e)) {
            if (e != boundLight) showLightConeProperties(e);
        } else {
            if (e != boundEntity) showEntityProperties(e);
        }
    }

    private int countNonJointSelection(IntArray selectionSnapshot) {
        if (selectionSnapshot == null || selectionSnapshot.size == 0) {
            return 0;
        }

        int count = 0;
        for (int i = 0; i < selectionSnapshot.size; i++) {
            int e = selectionSnapshot.get(i);
            if (e < 0 || !world.getEntityManager().isActive(e)) {
                continue;
            }

            if (mJointBase.has(e)) {
                continue;
            }

            count++;
        }

        return count;
    }

    private void restoreAfterFixtureDeselection() {
        IntArray selectionSnapshot = (selectionService != null)
                ? selectionService.getSelectionSnapshot()
                : pendingSelection;

        if (selectionSnapshot != null && selectionSnapshot.size == 1) {
            int e = selectionSnapshot.get(0);
            if (e >= 0 && world.getEntityManager().isActive(e) && mJointBase.has(e)) {
                onSelectionChanged(selectionSnapshot);
                return;
            }
        }

        if (hasValidPhysicsContext() && isExplicitPhysicsContextActive()) {
            if (physicsContextBody != boundBody) showBodyProperties(physicsContextBody);
            return;
        }

        onSelectionChanged(selectionSnapshot);
    }
}
