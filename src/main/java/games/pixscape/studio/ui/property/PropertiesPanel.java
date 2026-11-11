package games.pixscape.studio.ui.property;

import com.artemis.ComponentMapper;
import com.artemis.World;
import com.badlogic.gdx.utils.IntArray;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisScrollPane;
import com.kotcrab.vis.ui.widget.VisTable;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.component.light.ConeLightComponent;
import games.pixscape.runtime.component.light.PointLightComponent;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsJointComponent;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.studio.document.EditorDocumentManager;
import games.pixscape.studio.document.EditorDocumentType;
import games.pixscape.studio.document.OpenEditorDocument;
import games.pixscape.studio.document.SceneEditorDocument;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.event.GetScrollListener;
import games.pixscape.studio.event.LoseScroolListener;
import games.pixscape.studio.scene.SceneEditorContext;
import games.pixscape.studio.service.IconResolver;
import games.pixscape.studio.service.LayerService;
import games.pixscape.studio.service.SelectionService;
import games.pixscape.studio.service.physics.PhysicsSelectionService;
import games.pixscape.studio.system.UiRefreshDispatchSystem;
import games.pixscape.studio.ui.docking.DockablePanel;
import games.pixscape.studio.ui.hud.HudInspectorView;
import games.pixscape.studio.ui.main.StudioApplicationAdapter;
import games.pixscape.studio.ui.property.entityproperties.ConeLightProperties;
import games.pixscape.studio.ui.property.entityproperties.EntityProperties;
import games.pixscape.studio.ui.property.entityproperties.EntityPropertiesContext;
import games.pixscape.studio.ui.property.entityproperties.PointLightProperties;
import games.pixscape.studio.ui.property.entityproperties.physics.BodyProperties;
import games.pixscape.studio.ui.property.entityproperties.physics.FixturesPanel;
import games.pixscape.studio.ui.property.entityproperties.physics.JointProperties;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

public class PropertiesPanel extends DockablePanel {

    private EntityProperties entityProperties;
    private BodyProperties bodyProperties;
    private FixturesPanel fixtureProperties;
    private PointLightProperties pointLightProperties;
    private ConeLightProperties coneLightProperties;
    private JointProperties jointProperties;
    private SpatialBlockProperties spatialBlockProperties;
    private LayerProperties layerProperties;
    private SceneProperties sceneProperties;
    private TiledMapProperties tiledMapProperties;

    private final VisTable contentHolder;
    private final HudInspectorView hudInspectorView;
    private boolean hudMode;
    private boolean gameObjectMode;

    private int boundEntity = -1;
    private int boundBody = -1;
    private int boundFixtureBody = -1;
    private long boundFixtureId = PhysicsSelectionService.NO_SHAPE;
    private int boundJoint = -1;
    private int boundSpatialBlockMap = -1;
    private int boundSpatialBlockId = -1;
    private int boundLayer = -1;
    private int boundLight = -1;
    private int boundTiledMap = -1;

    private int pendingTiledMap = -1;
    private int tiledMapContextEntityId = -1;

    /**
     * Body currently used as the physics editing context.
     * As long as it remains valid, fixture deselection returns to BodyPanel.
     */
    private int physicsContextBody = -1;

    private final int MY_TAG = EventFlow.tag(this);

    private World world;
    private SelectionService selectionService;
    private LayerService layerService;
    private PhysicsSelectionService physicsSelectionService;
    private ComponentMapper<PhysicsJointComponent> mJointBase;
    private ComponentMapper<PhysicsBodyComponent> mPhysBody;
    private ComponentMapper<PhysicsShapesComponent> mPhysFixtures;
    private ComponentMapper<PointLightComponent> mPointLight;
    private ComponentMapper<ConeLightComponent> mConeLight;
    private ComponentMapper<EntityIndexComponent> mEntityIndex;
    private ComponentMapper<TiledLayerComponent> mTiled;
    private final StudioApplicationAdapter app;
    private final Set<World> refreshBoundWorlds =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<SceneEditorContext, ContextEditors> editorsByContext =
            new IdentityHashMap<>();

    private boolean dirty = true;
    private boolean transformTitleDirty;
    private boolean spatialPreviewDirty;
    private final TileAssetProperties tileAssetProperties;
    private final AnimationAssetProperties animationAssetProperties;
    private final ResourceAssetProperties resourceAssetProperties;
    private final ResourceAssetProperties hudLayerAssetProperties;
    private int pendingTileAsset = -1;
    private int pendingAnimationAsset = -1;
    private PendingView pendingView = PendingView.SCENE;
    private IntArray pendingSelection = null;
    private int pendingLayer = -1;
    private int pendingBody = -1;
    private int pendingFixtureBody = -1;
    private int pendingFixtureId = PhysicsSelectionService.NO_SHAPE;
    private int pendingSpatialBlockMap = -1;
    private int pendingSpatialBlockId = -1;

    private enum PendingView {
        SCENE,
        SELECTION,
        BODY,
        FIXTURE,
        SPATIAL_BLOCK,
        LAYER,
        HUD_LAYER,
        TILED_MAP,
        TILE_ASSET,
        ANIMATION_ASSET,
        RESOURCE_ASSET
    }

    public PropertiesPanel(StudioApplicationAdapter app) {
        super("Inspector");

        this.app = app;
        var sceneContext = app.getSceneEditorContext();
        initializeContextEditors(sceneContext);

        contentHolder = new VisTable(true);
        contentHolder.top().left().pad(8);
        tileAssetProperties = new TileAssetProperties(app.getSceneService()::getAssetMetaDatabase,
                () -> games.pixscape.studio.io.StudioFs.requireStudioProjectDir(
                        games.pixscape.studio.configuration.ProjectConfig.getInstance())
                        .child(games.pixscape.studio.io.StudioFs.FILE_ASSETS_JSON),
                database -> app.getCanvas().publishAssetMetaDatabase(database));
        animationAssetProperties = new AnimationAssetProperties(app.getSceneService()::getAssetMetaDatabase,
                assetId -> games.pixscape.studio.ui.asset.AnimationAssetClipsEditor.show(app, assetId, getStage()));

        resourceAssetProperties = new ResourceAssetProperties(app, this::leaveAssetInspection);
        hudLayerAssetProperties = new ResourceAssetProperties(app, this::restoreCurrentCanvasProperties);

        VisScrollPane scroller = new VisScrollPane(contentHolder);
        scroller.setFadeScrollBars(false);
        scroller.setScrollingDisabled(true, false);
        scroller.setFlickScroll(false);
        scroller.addListener(new GetScrollListener(scroller));
        scroller.addListener(new LoseScroolListener());

        add(scroller).grow().row();

        hudInspectorView = new HudInspectorView(app.getHudEditorSession());
        showSceneProperties();
        app.getEditorDocumentManager().addListener(new EditorDocumentManager.Listener() {
            @Override public void documentActivated(OpenEditorDocument previous, OpenEditorDocument current) {
                showDocumentType(current != null ? current.type() : null);
            }
        });
        showDocumentType(app.getEditorDocumentManager().activeDocument() != null
                ? app.getEditorDocumentManager().activeDocument().type() : null);

        bindRefresh(sceneContext);

        EventFlow.i().subscribe(EventFlow.GameObjectsChanged.class, evt -> resourceAssetProperties.gameObjectsChanged());

        EventFlow.i().subscribe(EventFlow.SelectionChanged.class, evt -> {
            if (evt.sourceTag() == MY_TAG || selectionService == null) return;
            pendingSelection = evt.ids() != null ? new IntArray(evt.ids()) : null;
            int focusedBody = physicsSelectionService.getFocusedBodyEid();
            if (selectionIsFocusedBody(pendingSelection, focusedBody)) {
                pendingBody = focusedBody;
                pendingView = PendingView.BODY;
            } else {
                pendingView = PendingView.SELECTION;
            }
            markDirty();
        });

        EventFlow.i().subscribe(EventFlow.EntityZOrderChanged.class, evt -> {
            transformTitleDirty = true;
            if (boundEntity < 0 || !world.getEntityManager().isActive(boundEntity)) return;
            EntityIndexComponent index = mEntityIndex.getSafe(boundEntity, null);
            if (index != null && index.layerIndex == evt.layerIndex()) {
                entityProperties.refreshZIndex();
            }
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
            pendingSpatialBlockMap = evt.mapEntityId();
            pendingSpatialBlockId = evt.blockId();
            pendingView = PendingView.SPATIAL_BLOCK;
            markDirty();
        });

        EventFlow.i().subscribe(EventFlow.SpatialBlockPreviewChanged.class, evt -> {
            if (evt.mapEntityId() == boundSpatialBlockMap && evt.blockId() == boundSpatialBlockId) {
                spatialPreviewDirty = true;
            }
        });

        EventFlow.i().subscribe(EventFlow.SpatialBlocksChanged.class, evt -> {
            if (evt.sourceTag() == MY_TAG) return;
            if (evt.layerEntityId() == boundSpatialBlockMap) {
                pendingSpatialBlockMap = boundSpatialBlockMap;
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

        EventFlow.i().subscribe(EventFlow.SceneHudLayerSelected.class, evt -> {
            if (app.getEditorDocumentManager().activeDocument() instanceof SceneEditorDocument scene
                    && java.util.Objects.equals(scene.context().sceneIdentity(), evt.sceneTag())) {
                pendingView = PendingView.HUD_LAYER;
                markDirty();
            }
        });
        EventFlow.i().subscribe(EventFlow.SceneHudAssociationChanged.class, evt -> {
            if (pendingView == PendingView.HUD_LAYER
                    && app.getEditorDocumentManager().activeDocument() instanceof SceneEditorDocument scene
                    && java.util.Objects.equals(scene.context().sceneIdentity(), evt.sceneTag())) markDirty();
        });

        EventFlow.i().subscribe(EventFlow.TiledMapEditingTargetChanged.class, evt -> {
            if (evt.sourceTag() == MY_TAG) return;
            int mapEntityId = evt.mapEntityId();
            if (mapEntityId >= 0 && mTiled != null && mTiled.has(mapEntityId)) {
                tiledMapContextEntityId = mapEntityId;
                pendingTiledMap = mapEntityId;
                pendingView = PendingView.TILED_MAP;
                markDirty();
            }
        });

        EventFlow.i().subscribe(EventFlow.CurrentSceneMeta.class, evt -> {
            if (evt.sourceTag() == MY_TAG) return;
            pendingView = PendingView.SCENE;
            markDirty();
        });

        EventFlow.i().subscribe(EventFlow.ScenePhysicsEnabledChanged.class, evt -> {
            if (boundTiledMap >= 0) {
                pendingTiledMap = boundTiledMap;
                pendingView = PendingView.TILED_MAP;
            } else if (boundLayer >= 0) {
                pendingLayer = boundLayer;
                pendingView = PendingView.LAYER;
            } else {
                return;
            }
            markDirty();
        });

        EventFlow.i().subscribe(EventFlow.PhysicsBodyStructureChanged.class, evt -> {
            if (evt.sourceTag() == MY_TAG || evt.entityId() != boundTiledMap) return;
            pendingTiledMap = boundTiledMap;
            pendingView = PendingView.TILED_MAP;
            markDirty();
        });

        EventFlow.i().subscribe(EventFlow.LayerSpatialDepthChanged.class, evt -> {
            if (evt.sourceTag() == MY_TAG) return;
            if (evt.layerEntityId() == boundTiledMap) {
                pendingTiledMap = boundTiledMap;
                pendingView = PendingView.TILED_MAP;
            } else if (evt.layerEntityId() == boundLayer) {
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
            if (jointProperties != null && evt.jointEntityId() == boundJoint) {
                jointProperties.markDirty();
            }
        });
    }

    public void bindSceneContext(SceneEditorContext context) {
        if (context == null || context.isDisposed()) return;
        initializeContextEditors(context);
        bindRefresh(context);
        pendingSelection = context.selectionService().getSelectionSnapshot();
        if (inspectedResourceProperties() == null)
            pendingView = pendingSelection.size > 0 ? PendingView.SELECTION : PendingView.SCENE;
        clearBindings();
        clearPhysicsContext();
        clearTiledMapContext();
        dirty = true;
        if (!hudMode) updateIfDirty();
    }

    public void releaseSceneContext(SceneEditorContext context) {
        if (context == null || !context.isInitialized()) return;
        refreshBoundWorlds.remove(context.world());
        editorsByContext.remove(context);
        if (world == context.world()) clearSceneContext();
    }

    /** Releases project inspection even when there is no document activation to observe. */
    public void clearProject() {
        if (inspectedResourceProperties() != null) clearContent();
        resourceAssetProperties.clearProject();
        hudLayerAssetProperties.remove();
        hudLayerAssetProperties.clearProject();
        showDocumentType(null);
        clearSceneContext();
        animationAssetProperties.clearProject();
        tileAssetProperties.clearProject();
        pendingAnimationAsset = -1;
        pendingTileAsset = -1;
        editorsByContext.clear();
        refreshBoundWorlds.clear();
    }

    private void clearSceneContext() {
        clearBindings();
        clearPhysicsContext();
        clearTiledMapContext();
        world = null;
        selectionService = null;
        layerService = null;
        physicsSelectionService = null;
        mJointBase = null;
        mPhysBody = null;
        mPhysFixtures = null;
        mPointLight = null;
        mConeLight = null;
        mEntityIndex = null;
        mTiled = null;
        // Release context-owned panels; create replacements only when binding a live context.
        new ContextEditors().apply(this);
        pendingSelection = null;
    }

    private void initializeContextEditors(SceneEditorContext sceneContext) {
        var canvas = app.getCanvas();
        world = sceneContext.world();
        selectionService = sceneContext.selectionService();
        layerService = sceneContext.layerService();
        mJointBase = world.getMapper(PhysicsJointComponent.class);
        mPhysBody = world.getMapper(PhysicsBodyComponent.class);
        mPhysFixtures = world.getMapper(PhysicsShapesComponent.class);
        mPointLight = world.getMapper(PointLightComponent.class);
        mConeLight = world.getMapper(ConeLightComponent.class);
        mEntityIndex = world.getMapper(EntityIndexComponent.class);
        mTiled = world.getMapper(TiledLayerComponent.class);
        physicsSelectionService = sceneContext.physicsSelectionService();

        ContextEditors cached = editorsByContext.get(sceneContext);
        if (cached != null) {
            cached.apply(this);
            return;
        }
        sceneContext.collectActiveUiSubscriptions(() -> createContextEditors(sceneContext));
        editorsByContext.put(sceneContext, ContextEditors.capture(this));
    }

    private void createContextEditors(SceneEditorContext sceneContext) {
        var canvas = app.getCanvas();

        EntityPropertiesContext ctx = new EntityPropertiesContext(
                world,
                sceneContext.historyManager(),
                physicsSelectionService,
                canvas.getPhysicsService(),
                layerService,
                canvas.getAtlasService(),
                selectionService,
                sceneContext.identityRegistry(),
                new IconResolver(world),
                sceneContext::markExplicitSaveRequired,
                app.getSceneService()::getAssetMeta,
                canvas.getAnimationPreviewRefresher()::refreshSelectedFrame,
                app.getSceneService()::getAnimationAssetMetas,
                app.getAnimationAssetAuthoringService(),
                MY_TAG, sceneContext.sceneMeta());
        entityProperties = new EntityProperties(ctx);
        bodyProperties = new BodyProperties(ctx);
        fixtureProperties = new FixturesPanel(ctx);
        pointLightProperties = new PointLightProperties(ctx);
        coneLightProperties = new ConeLightProperties(ctx);
        jointProperties = new JointProperties(
                world, sceneContext.historyManager(), canvas.getEditorOps(), selectionService,
                sceneContext.sceneMeta());
        spatialBlockProperties = new SpatialBlockProperties(
                world,
                sceneContext.historyManager(),
                sceneContext.spatialBlockSelectionService(),
                canvas.getPhysicsService(),
                sceneContext::markExplicitSaveRequired);
        Runnable markSaveRequired = sceneContext::markExplicitSaveRequired;
        layerProperties = new LayerProperties(
                world, sceneContext.historyManager(), layerService, markSaveRequired);
        sceneProperties = new SceneProperties(
                world, sceneContext.historyManager(), canvas.getPhysicsService(),
                selectionService, layerService, sceneContext.physicsSelectionReconciler(),
                canvas::disposeBox2dAfterPhysicsPurge, markSaveRequired);
        sceneProperties.bindLighting(sceneContext.sceneMeta(), enabled -> {
            if(enabled && sceneContext.sceneMeta().useNormalMaps) {
                if(sceneContext.sceneIdentity()==null)app.getSceneService().prepareGameObjectNormals(ctx.world,sceneContext.sceneMeta(),true);
                else ctx.atlasStudioService.prepareSceneNormalMaps(sceneContext.sceneMeta(),true);
            }
            ctx.world.getSystem(games.pixscape.studio.system.StudioRenderSubmitSystem.class).setLightingEnabled(enabled);
        });
        tiledMapProperties = new TiledMapProperties(
                world, sceneContext.historyManager(), canvas.getPhysicsService(), markSaveRequired);
    }

    private static final class ContextEditors {
        EntityProperties entityProperties;
        BodyProperties bodyProperties;
        FixturesPanel fixtureProperties;
        PointLightProperties pointLightProperties;
        ConeLightProperties coneLightProperties;
        JointProperties jointProperties;
        SpatialBlockProperties spatialBlockProperties;
        LayerProperties layerProperties;
        SceneProperties sceneProperties;
        TiledMapProperties tiledMapProperties;

        static ContextEditors capture(PropertiesPanel panel) {
            ContextEditors out = new ContextEditors();
            out.entityProperties = panel.entityProperties;
            out.bodyProperties = panel.bodyProperties;
            out.fixtureProperties = panel.fixtureProperties;
            out.pointLightProperties = panel.pointLightProperties;
            out.coneLightProperties = panel.coneLightProperties;
            out.jointProperties = panel.jointProperties;
            out.spatialBlockProperties = panel.spatialBlockProperties;
            out.layerProperties = panel.layerProperties;
            out.sceneProperties = panel.sceneProperties;
            out.tiledMapProperties = panel.tiledMapProperties;
            return out;
        }

        void apply(PropertiesPanel panel) {
            panel.entityProperties = entityProperties;
            panel.bodyProperties = bodyProperties;
            panel.fixtureProperties = fixtureProperties;
            panel.pointLightProperties = pointLightProperties;
            panel.coneLightProperties = coneLightProperties;
            panel.jointProperties = jointProperties;
            panel.spatialBlockProperties = spatialBlockProperties;
            panel.layerProperties = layerProperties;
            panel.sceneProperties = sceneProperties;
            panel.tiledMapProperties = tiledMapProperties;
        }
    }

    private void bindRefresh(SceneEditorContext context) {
        if (!refreshBoundWorlds.add(context.world())) return;
        UiRefreshDispatchSystem postProcess =
                context.world().getSystem(UiRefreshDispatchSystem.class);
        postProcess.add(this::updateIfDirty);
    }

    public void requestBodyProperties(int bodyEntityId) {
        pendingBody = bodyEntityId;
        pendingView = PendingView.BODY;
        showBodyProperties(bodyEntityId);
    }

    public void requestTiledMapProperties(int mapEntityId) {
        tiledMapContextEntityId = mapEntityId;
        pendingTiledMap = mapEntityId;
        pendingView = PendingView.TILED_MAP;
        showTiledMapProperties(mapEntityId);
    }

    public void clearTiledMapMode() {
        clearTiledMapContext();
    }

    private void showSceneProperties() {
        if (gameObjectMode) {
            showGameObjectHint();
            return;
        }
        clearContent();
        contentHolder.add(sceneProperties).growX().top().left().row();
        clearBindings();
        clearPhysicsContext();
        clearTiledMapContext();
    }

    private void showDocumentType(EditorDocumentType type) {
        if (inspectedResourceProperties() != null) {
            hudMode = type == EditorDocumentType.HUD_SCREEN;
            gameObjectMode = type == EditorDocumentType.GAME_OBJECT;
            return;
        }
        if (pendingView == PendingView.TILE_ASSET || pendingView == PendingView.ANIMATION_ASSET
                || pendingView == PendingView.RESOURCE_ASSET) {
            pendingView = PendingView.SCENE;
        }
        if (type == null) {
            hudMode = false;
            gameObjectMode = false;
            clearContent();
            clearBindings();
            clearPhysicsContext();
            clearTiledMapContext();
            return;
        }
        hudMode = type == EditorDocumentType.HUD_SCREEN;
        gameObjectMode = type == EditorDocumentType.GAME_OBJECT;
        if (hudMode) {
            clearContent();
            hudInspectorView.rebuild();
            contentHolder.add(hudInspectorView).growX().top().left().row();
        } else {
            dirty = true;
        }
    }

    /** Game Object tabs edit an isolated hierarchy and deliberately expose no project Scene metadata. */
    private void showGameObjectHint() {
        clearContent();
        contentHolder.add(new VisLabel("Select a Game Object element to edit its properties."))
                .growX().top().left().row();
        clearBindings();
        clearPhysicsContext();
        clearTiledMapContext();
    }

    private void showLayerProperties(int layerEntity) {
        clearContent();
        layerProperties.setLayerEntityId(layerEntity);
        contentHolder.add(layerProperties).growX().top().left().row();
        clearBindings();
        clearPhysicsContext();
        clearTiledMapContext();
        boundLayer = layerEntity;
    }

    private void showSceneHudLayerProperties() {
        if (!(app.getEditorDocumentManager().activeDocument() instanceof SceneEditorDocument scene)) return;
        String screenId = scene.context().sceneMeta().defaultHudScreenId;
        clearContent();
        clearBindings();
        clearPhysicsContext();
        clearTiledMapContext();
        hudLayerAssetProperties.selectHud(screenId);
        contentHolder.add(hudLayerAssetProperties).growX().top().left().row();
    }

    private void showEntityProperties(int entityId) {
        clearContent();
        entityProperties.setEntityId(entityId);
        contentHolder.add(entityProperties).growX().top().left().row();
        clearBindings();
        clearPhysicsContext();
        clearTiledMapContext();
        boundEntity = entityId;
    }

    private void showBodyProperties(int bodyEntityId) {
        clearContent();
        bodyProperties.setEntityId(bodyEntityId);
        contentHolder.add(bodyProperties).growX().top().left().row();
        clearBindings();
        clearTiledMapContext();
        boundBody = bodyEntityId;
        physicsContextBody = bodyEntityId;
    }

    private void showFixtureProperties(int bodyEntityId, int physicsShapeId) {
        clearContent();
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
        clearContent();
        pointLightProperties.setEntityId(entityId);
        contentHolder.add(pointLightProperties).growX().top().left().row();
        clearBindings();
        clearTiledMapContext();
        clearPhysicsContext();
        boundLight = entityId;
    }

    private void showLightConeProperties(int entityId) {
        clearContent();
        coneLightProperties.setEntityId(entityId);
        contentHolder.add(coneLightProperties).growX().top().left().row();
        clearBindings();
        clearTiledMapContext();
        clearPhysicsContext();
        boundLight = entityId;
    }

    private void showJointProperties(int jointEid) {
        clearContent();
        jointProperties.setJointEntityId(jointEid);
        contentHolder.add(jointProperties).growX().top().left().row();
        clearBindings();
        clearTiledMapContext();
        boundJoint = jointEid;
    }

    private void showSpatialBlockProperties(int mapEntityId, int blockId) {
        clearContent();
        spatialBlockProperties.setSpatialBlock(mapEntityId, blockId);
        contentHolder.add(spatialBlockProperties).growX().top().left().row();
        clearBindings();
        clearPhysicsContext();
        clearTiledMapContext();
        boundSpatialBlockMap = mapEntityId;
        boundSpatialBlockId = blockId;
    }

    private void showMultiSelection(int count) {
        clearContent();
        contentHolder.add(new VisLabel("Multiple selection (" + count + " entities)"))
                .growX().top().left().row();
        clearBindings();
        clearPhysicsContext();
        clearTiledMapContext();
    }

    private void showTiledMapProperties(int mapEntityId) {
        clearContent();
        tiledMapProperties.setMapEntityId(mapEntityId);
        contentHolder.add(tiledMapProperties).growX().top().left().row();
        clearBindings();
        clearPhysicsContext();
        boundTiledMap = mapEntityId;
    }

    private void clearBindings() {
        boundEntity = -1;
        boundBody = -1;
        boundFixtureBody = -1;
        boundFixtureId = PhysicsSelectionService.NO_SHAPE;
        boundJoint = -1;
        boundSpatialBlockMap = -1;
        boundSpatialBlockId = -1;
        boundLayer = -1;
        boundLight = -1;
        boundTiledMap = -1;
    }

    private void clearTiledMapContext() {
        tiledMapContextEntityId = -1;
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
        return physicsSelectionService.isPhysicsEditingActive();
    }

    static boolean selectionIsFocusedBody(IntArray selection, int focusedBodyEntityId) {
        return focusedBodyEntityId >= 0
                && selection != null
                && selection.size == 1
                && selection.first() == focusedBodyEntityId;
    }

    public void markDirty() {
        dirty = true;
    }

    public void updateIfDirty() {
        if (hudLayerAssetProperties.getStage() != null) hudLayerAssetProperties.refreshIfChanged();
        if (inspectedResourceProperties() != null && !resourceAssetProperties.refreshIfChanged()) {
            restoreCurrentCanvasProperties();
        }
        if (inspectedAnimationProperties() != null) {
            var database = app.getSceneService().getAssetMetaDatabase();
            if (database == null || !(database.findById(pendingAnimationAsset)
                    instanceof games.pixscape.studio.asset.AnimationAssetMeta)) {
                restoreCurrentCanvasProperties();
            } else {
                animationAssetProperties.refreshIfChanged();
            }
        }
        if (hudMode && pendingView != PendingView.TILE_ASSET && pendingView != PendingView.ANIMATION_ASSET
                && pendingView != PendingView.RESOURCE_ASSET) return;
        if (app.getEditorDocumentManager().activeDocument() == null
                && pendingView != PendingView.TILE_ASSET && pendingView != PendingView.ANIMATION_ASSET
                && pendingView != PendingView.RESOURCE_ASSET) {
            clearContent();
            dirty = transformTitleDirty = spatialPreviewDirty = false;
            return;
        }
        if (transformTitleDirty) {
            transformTitleDirty = false;
            if (boundEntity >= 0) entityProperties.refreshTransformTitle();
        }
        if (spatialPreviewDirty) {
            spatialPreviewDirty = false;
            if (boundSpatialBlockId > 0) spatialBlockProperties.refreshGeometry();
        }
        if (!dirty) return;
        dirty = false;

        switch (pendingView) {
            case SELECTION -> onSelectionChanged(pendingSelection);
            case BODY -> onBodySelectionChanged(pendingBody);
            case FIXTURE -> onFixtureSelectionChanged(pendingFixtureBody, pendingFixtureId);
            case SPATIAL_BLOCK -> onSpatialBlockSelectionChanged(pendingSpatialBlockMap, pendingSpatialBlockId);
            case LAYER -> onActiveLayerChanged(pendingLayer);
            case HUD_LAYER -> showSceneHudLayerProperties();
            case SCENE -> showSceneProperties();
            case TILED_MAP -> showTiledMapProperties(pendingTiledMap);
            case TILE_ASSET -> showTileAssetProperties(pendingTileAsset);
            case ANIMATION_ASSET -> showAnimationAssetProperties(pendingAnimationAsset);
            case RESOURCE_ASSET -> { } // The view is already prepared at selection/reload boundaries.
        }
    }

    public void inspectAsset(games.pixscape.studio.ui.asset.AssetNode node) {
        if (node == null) {
            leaveAssetInspection();
            return;
        }
        var database = app.getSceneService().getAssetMetaDatabase();
        if (node.kind == games.pixscape.studio.ui.asset.AssetNode.Kind.HUD_SCREEN
                || node.kind == games.pixscape.studio.ui.asset.AssetNode.Kind.GAME_OBJECT
                || node.kind == games.pixscape.studio.ui.asset.AssetNode.Kind.PARTICLE
                || node.kind == games.pixscape.studio.ui.asset.AssetNode.Kind.FONT
                || node.kind == games.pixscape.studio.ui.asset.AssetNode.Kind.SKIN
                || (node.kind == games.pixscape.studio.ui.asset.AssetNode.Kind.IMAGE && database != null
                    && database.findById(node.assetId) instanceof games.pixscape.studio.asset.ImageAssetMeta)) {
            clearBindings();
            clearPhysicsContext();
            clearTiledMapContext();
            clearContent();
            if (!resourceAssetProperties.select(node)) {
                resourceAssetProperties.clearProject();
                restoreCurrentCanvasProperties();
                updateIfDirty();
                return;
            }
            pendingView = PendingView.RESOURCE_ASSET;
            contentHolder.add(resourceAssetProperties).growX().top().left().row();
            dirty = false;
            return;
        }
        if (database != null
                && database.findById(node.assetId) instanceof games.pixscape.studio.asset.TileAssetMeta) {
            pendingTileAsset = node.assetId;
            pendingView = PendingView.TILE_ASSET;
            showTileAssetProperties(node.assetId);
        } else if (database != null
                && database.findById(node.assetId) instanceof games.pixscape.studio.asset.AnimationAssetMeta) {
            pendingAnimationAsset = node.assetId;
            pendingView = PendingView.ANIMATION_ASSET;
            showAnimationAssetProperties(node.assetId);
        } else {
            leaveAssetInspection();
        }
    }

    private void leaveAssetInspection() {
        if (inspectedAnimationProperties() != null || inspectedTileProperties() != null
                || inspectedResourceProperties() != null) {
            restoreCurrentCanvasProperties();
            updateIfDirty();
        }
    }

    public ResourceAssetProperties inspectedResourceProperties() {
        return resourceAssetProperties.getParent() == contentHolder ? resourceAssetProperties : null;
    }

    /** Abandon a target explicitly; visual docking detaches the panel without calling this. */
    private void clearContent() {
        if (inspectedResourceProperties() != null) resourceAssetProperties.clearProject();
        if (hudLayerAssetProperties.getParent() == contentHolder) hudLayerAssetProperties.clearProject();
        contentHolder.clearChildren();
    }

    public void reloadAssetInspection() {
        if (hudLayerAssetProperties.getParent() == contentHolder) hudLayerAssetProperties.reload();
        boolean available = true;
        if (inspectedResourceProperties() != null) {
            available = resourceAssetProperties.reload();
        } else if (inspectedTileProperties() != null) {
            var database = app.getSceneService().getAssetMetaDatabase();
            available = database != null && database.findById(pendingTileAsset)
                    instanceof games.pixscape.studio.asset.TileAssetMeta;
            if (available) tileAssetProperties.select(pendingTileAsset);
        } else if (inspectedAnimationProperties() != null) {
            var database = app.getSceneService().getAssetMetaDatabase();
            available = database != null && database.findById(pendingAnimationAsset)
                    instanceof games.pixscape.studio.asset.AnimationAssetMeta;
            if (available) animationAssetProperties.select(pendingAnimationAsset);
        }
        if (!available) {
            restoreCurrentCanvasProperties();
            updateIfDirty();
        }
    }

    private void showTileAssetProperties(int assetId) {
        clearBindings();
        clearPhysicsContext();
        clearContent();
        tileAssetProperties.select(assetId);
        contentHolder.add(tileAssetProperties).growX().top().left().row();
        dirty = false;
    }

    public TileAssetProperties inspectedTileProperties() {
        return tileAssetProperties.getParent() == contentHolder ? tileAssetProperties : null;
    }

    private void showAnimationAssetProperties(int assetId) {
        clearBindings();
        clearPhysicsContext();
        clearTiledMapContext();
        clearContent();
        animationAssetProperties.select(assetId);
        contentHolder.add(animationAssetProperties).growX().top().left().row();
        dirty = false;
    }

    public AnimationAssetProperties inspectedAnimationProperties() {
        return animationAssetProperties.getParent() == contentHolder ? animationAssetProperties : null;
    }

    private void restoreCurrentCanvasProperties() {
        clearContent();
        if (app.getEditorDocumentManager().activeDocument() == null) {
            showDocumentType(null);
            return;
        }
        // Read the live selection only. Inspecting/deleting an asset never edits canvas selection.
        pendingSelection = selectionService != null ? selectionService.getSelectionSnapshot() : new IntArray();
        pendingLayer = selectionService != null ? selectionService.getActivelayerId() : -1;
        pendingView = pendingSelection.size > 0 ? PendingView.SELECTION
                : pendingLayer >= 0 ? PendingView.LAYER : PendingView.SCENE;
        if (hudMode) {
            hudInspectorView.rebuild();
            contentHolder.add(hudInspectorView).growX().top().left().row();
        }
        markDirty();
    }

    public void onActiveLayerChanged(int newLayerEntityId) {
        int mapEntityId = selectionService.getTiledMapEditingTargetEntityId();
        if (mapEntityId >= 0 && mapEntityId == tiledMapContextEntityId) {
            if (mapEntityId != boundTiledMap) {
                showTiledMapProperties(mapEntityId);
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

    public void onFixtureSelectionChanged(int bodyEntityId, int physicsShapeId) {
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

    private boolean fixtureExists(int bodyEntityId, int physicsShapeId) {
        PhysicsShapesComponent fixtures = mPhysFixtures.getSafe(bodyEntityId, null);
        if (fixtures == null || fixtures.shapes == null) return false;
        for (int i = 0; i < fixtures.shapes.size; i++) {
            var fixture = fixtures.shapes.get(i);
            if (fixture != null && fixture.physicsShapeId == physicsShapeId) return true;
        }
        return false;
    }

    public void onSpatialBlockSelectionChanged(int mapEntityId, int blockId) {
        if (mapEntityId >= 0 && blockId > 0) {
            if (mapEntityId != boundSpatialBlockMap || blockId != boundSpatialBlockId) {
                showSpatialBlockProperties(mapEntityId, blockId);
            } else {
                spatialBlockProperties.refreshNow();
            }
            return;
        }

        if (mapEntityId >= 0) {
            showTiledMapProperties(mapEntityId);
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

            int mapEntityId = selectionService.getTiledMapEditingTargetEntityId();
            if (mapEntityId >= 0 && mapEntityId == tiledMapContextEntityId) {
                if (mapEntityId != boundTiledMap) {
                    showTiledMapProperties(mapEntityId);
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

        int selectedFixtureId = physicsSelectionService.getSelectedPhysicsShapeId();
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

        if (mTiled.has(e)) {
            if (e != boundTiledMap) showTiledMapProperties(e);
        } else if (mJointBase.has(e)) {
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
