package games.pixscape.studio.ui.tree;

import com.artemis.*;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.IntArray;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.widget.VisScrollPane;
import com.kotcrab.vis.ui.widget.VisTable;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.component.light.ConeLightComponent;
import games.pixscape.runtime.component.light.PointLightComponent;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.service.ZOrderRuntimeService;
import games.pixscape.studio.component.EntityMetaComponent;
import games.pixscape.studio.component.LayerMetaComponent;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.event.GetScrollListener;
import games.pixscape.studio.event.LoseScroolListener;
import games.pixscape.studio.model.EntityKind;
import games.pixscape.studio.service.IconResolver;
import games.pixscape.studio.service.LayerService;
import games.pixscape.studio.service.SelectionService;
import games.pixscape.studio.service.physics.PhysicsSelectionService;
import games.pixscape.studio.service.spatial.SpatialBlockSelectionService;
import games.pixscape.studio.system.UiRefreshDispatchSystem;
import games.pixscape.studio.ui.docking.DockablePanel;
import games.pixscape.studio.ui.main.StudioApplicationAdapter;
import games.pixscape.studio.ui.property.PropertiesPanel;

import java.util.ArrayList;
import java.util.List;

import static games.pixscape.runtime.component.physics.PhysicsBodyComponent.*;

public class ItemTreePanel extends DockablePanel {

    private final World world;
    private final LayerService layerService;
    private final PhysicsSelectionService physicsSelectionService;
    private final SpatialBlockSelectionService spatialBlockSelectionService;
    private final SelectionService selectionService;
    private final ZOrderRuntimeService zOrderRuntimeService;

    private final ComponentMapper<EntityMetaComponent> mMeta;
    private final ComponentMapper<PixscapeIdentityComponent> mIdentity;
    private final ComponentMapper<EntityIndexComponent> mEntityIndex;
    private final ComponentMapper<ParticleEmitterComponent> mEmitter;
    private final ComponentMapper<TiledLayerComponent> mTiled;
    private final ComponentMapper<PhysicsBodyComponent> mBody;
    private final ComponentMapper<PhysicsShapesComponent> mFixtures;

    private final EntitySubscription layersSub;
    private final EntitySubscription layerItemsSub;

    private final IdVisTree tree;
    private final IconResolver iconResolver;
    private volatile boolean dirty = true;
    private boolean suppressTreeSelectionEvents = false;
    private boolean handlingTreeSelection = false;

    private int explicitTiledMapLayerEid = -1;

    private final VisScrollPane scroller;

    private PropertiesPanel propertiesPanel;

    public ItemTreePanel(StudioApplicationAdapter app) {
        super("Items");

        var canvas = app.getCanvas();
        this.world = canvas.getEcsWorld();
        this.layerService = canvas.getLayerService();
        this.physicsSelectionService = canvas.getPhysicsSelectionService();
        this.spatialBlockSelectionService = canvas.getSpatialBlockSelectionService();
        this.selectionService = canvas.getSelectionService();
        this.zOrderRuntimeService = app.getCanvas().getZOrderService();

        this.mMeta = world.getMapper(EntityMetaComponent.class);
        this.mIdentity = world.getMapper(PixscapeIdentityComponent.class);
        this.mEntityIndex = world.getMapper(EntityIndexComponent.class);
        this.mEmitter = world.getMapper(ParticleEmitterComponent.class);
        this.mTiled = world.getMapper(TiledLayerComponent.class);
        this.mBody = world.getMapper(PhysicsBodyComponent.class);
        this.mFixtures = world.getMapper(PhysicsShapesComponent.class);

        UiRefreshDispatchSystem postProcess = world.getSystem(UiRefreshDispatchSystem.class);
        postProcess.add(this::updateIfDirty);

        AspectSubscriptionManager asm = world.getAspectSubscriptionManager();
        this.layersSub = asm.get(Aspect.all(LayerComponent.class, LayerMetaComponent.class));
        this.layerItemsSub = asm.get(
                Aspect.all(EntityIndexComponent.class).one(
                        ParticleEmitterComponent.class,
                        AssetRefComponent.class,
                        PointLightComponent.class,
                        ConeLightComponent.class
                )
        );

        this.iconResolver = new IconResolver(world);

        tree = new IdVisTree();
        tree.setIndentSpacing(25);
        tree.getSelection().setMultiple(true);

        scroller = new VisScrollPane(tree);
        scroller.setFadeScrollBars(false);
        scroller.setSmoothScrolling(true);
        scroller.addListener(new GetScrollListener(scroller));
        scroller.addListener(new LoseScroolListener());

        VisTable root = new VisTable();

        VisTable toolbar = new VisTable(false);
        toolbar.left();

        Button btnUp = new Button(VisUI.getSkin(), "up");
        btnUp.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                moveSelectionUp();
            }
        });
        toolbar.add(btnUp);

        Button btnDown = new Button(VisUI.getSkin(), "down");
        btnDown.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                moveSelectionDown();
            }
        });
        toolbar.add(btnDown);

        root.add(scroller).grow().row();
        root.add(toolbar).bottom().center().row();

        add(root).grow();

        EventFlow.i().subscribe(EventFlow.SelectionChanged.class, evt -> {
            if (handlingTreeSelection || suppressTreeSelectionEvents) return;

            // Any real external entity selection exits explicit tiled-map mode.
            if (evt.ids() != null && evt.ids().size > 0) {
                explicitTiledMapLayerEid = -1;
            }

            boolean applyFocus = evt.source() != SelectionService.SelectionSource.TREE;
            syncTreeSelectionFromModel(evt.ids(), applyFocus);
        });

        EventFlow.i().subscribe(EventFlow.LayerNameChanged.class, evt -> {
            EntityNode en = tree.findNode(evt.layerEntityId());
            if (en != null) en.setLabelName(evt.newName());
        });

        EventFlow.i().subscribe(EventFlow.EntityNameChanged.class, evt -> {
            EntityNode en = tree.findNode(evt.entityId());
            if (en != null) en.setLabelName(evt.newName());
        });

        EventFlow.i().subscribe(EventFlow.LayerOrderChanged.class, evt -> markDirty());
        EventFlow.i().subscribe(EventFlow.LayerLockChanged.class, evt -> markDirty());
        EventFlow.i().subscribe(EventFlow.PhysicsBodyStructureChanged.class, evt -> markDirty());
        EventFlow.i().subscribe(EventFlow.LayerSpatialDepthChanged.class, evt -> markDirty());
        EventFlow.i().subscribe(EventFlow.SpatialBlocksChanged.class, evt -> markDirty());
        EventFlow.i().subscribe(EventFlow.SpatialBlockSelectionChanged.class, evt -> {
            if (handlingTreeSelection || suppressTreeSelectionEvents) return;
            syncTreeSelectionFromModel(selectionService.getSelectionSnapshot(), true);
        });

        EventFlow.i().subscribe(EventFlow.CurrentLayerChanged.class, evt -> {
            if (handlingTreeSelection || suppressTreeSelectionEvents) return;

            if (evt.source() != SelectionService.SelectionSource.TREE) {
                explicitTiledMapLayerEid = -1;
            }

            boolean applyFocus = evt.source() != SelectionService.SelectionSource.TREE;
            syncTreeSelectionFromModel(selectionService.getSelectionSnapshot(), applyFocus);
        });

        attachAutoRefresh();
        hookTreeSelection();
    }

    private void focusNode(EntityNode node) {
        if (node == null) return;

        Actor actor = node.getActor();
        if (actor == null) return;

        tree.validate();
        scroller.layout();

        scroller.scrollTo(
                0f,
                actor.getY(),
                actor.getWidth(),
                actor.getHeight(),
                false,
                true
        );
        scroller.updateVisualScroll();
    }

    public void bindPropertiesPanel(PropertiesPanel propertiesPanel) {
        this.propertiesPanel = propertiesPanel;
    }

    private void moveSelectionUp() {
        IntArray sel = selectionService.getSelectionSnapshot();
        if (sel.size == 0) return;

        int layer = requireEntityIndex(sel.get(0), "ItemTree move up").getLayerIndex();

        for (int i = 1; i < sel.size; i++) {
            int e = sel.get(i);
            int l = requireEntityIndex(e, "ItemTree move up").getLayerIndex();
            if (l != layer) {
                return;
            }
        }

        sel.sort();
        for (int i = sel.size - 1; i >= 0; i--) {
            zOrderRuntimeService.moveUp(sel.get(i));
        }
        markDirty();
    }

    private void moveSelectionDown() {
        IntArray sel = selectionService.getSelectionSnapshot();
        if (sel.size == 0) return;

        int layer = requireEntityIndex(sel.get(0), "ItemTree move down").getLayerIndex();

        for (int i = 1; i < sel.size; i++) {
            int e = sel.get(i);
            int l = requireEntityIndex(e, "ItemTree move down").getLayerIndex();
            if (l != layer) {
                return;
            }
        }

        sel.sort();
        for (int i = 0; i < sel.size; i++) {
            zOrderRuntimeService.moveDown(sel.get(i));
        }
        markDirty();
    }

    public void markDirty() {
        dirty = true;
    }

    public void updateIfDirty() {
        if (dirty && tree != null) {
            dirty = false;
            rebuildTreeFromWorld();
        }
    }

    public void attachAutoRefresh() {
        EntitySubscription.SubscriptionListener layersListener = new EntitySubscription.SubscriptionListener() {
            @Override
            public void inserted(IntBag entities) {
                markDirty();
            }

            @Override
            public void removed(IntBag entities) {
                markDirty();
            }
        };
        layersSub.addSubscriptionListener(layersListener);

        EntitySubscription.SubscriptionListener refsListener = new EntitySubscription.SubscriptionListener() {
            @Override
            public void inserted(IntBag entities) {
                markDirty();
            }

            @Override
            public void removed(IntBag entities) {
                markDirty();
            }
        };
        layerItemsSub.addSubscriptionListener(refsListener);
    }

    private void hookTreeSelection() {
        tree.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (suppressTreeSelectionEvents) return;

                var nodes = tree.getSelection().toArray();

                handlingTreeSelection = true;
                try {
                    EntityNode mapNode = findFirstTiledMapNode(nodes);
                    EntityNode bodyNode = findFirstBodyNode(nodes);
                    EntityNode spatialNode = findFirstSpatialBlocksNode(nodes);

                    if (mapNode != null && nodes.size == 1) {
                        handleTiledMapNodeSelection(mapNode);
                    } else if (bodyNode != null && nodes.size == 1) {
                        handleBodyNodeSelection(bodyNode);
                    } else if (spatialNode != null && nodes.size == 1) {
                        handleSpatialBlocksNodeSelection(spatialNode);
                    } else {
                        explicitTiledMapLayerEid = -1;
                        if (propertiesPanel != null) {
                            propertiesPanel.clearTiledMapMode();
                        }

                        exitExplicitPhysicsEditMode();
                        exitExplicitSpatialBlockMode();
                        selectionService.clearSelection(SelectionService.SelectionSource.TREE);

                        boolean layerSwitched = false;
                        for (EntityNode en : nodes) {
                            if (en == null) continue;

                            int eid = en.getEntityId();
                            if (eid < 0) continue;

                            if (en.isLayerNode()) {
                                selectionService.setActivelayerId(eid, SelectionService.SelectionSource.TREE);
                                continue;
                            }

                            if (!en.isEntityNode()) continue;

                            if (!layerSwitched) {
                                activateLayerForEntity(eid, SelectionService.SelectionSource.TREE);
                                layerSwitched = true;
                            }

                            selectionService.selectFromTree(eid);
                        }
                    }
                } finally {
                    handlingTreeSelection = false;
                }

                syncTreeSelectionFromModel(selectionService.getSelectionSnapshot(), false);
            }
        });
    }

    private EntityNode findFirstTiledMapNode(com.badlogic.gdx.utils.Array<EntityNode> nodes) {
        if (nodes == null) return null;
        for (int i = 0; i < nodes.size; i++) {
            EntityNode node = nodes.get(i);
            if (node != null && node.isTiledMapNode()) return node;
        }
        return null;
    }

    private void handleTiledMapNodeSelection(EntityNode mapNode) {
        if (mapNode == null || !mapNode.isTiledMapNode()) return;

        int layerEid = mapNode.getEntityId();
        if (layerEid < 0) return;

        explicitTiledMapLayerEid = layerEid;

        forceSingleTreeSelection(mapNode);
        exitExplicitPhysicsEditMode();
        exitExplicitSpatialBlockMode();

        selectionService.clearSelection(SelectionService.SelectionSource.TREE);
        selectionService.setActivelayerId(layerEid, SelectionService.SelectionSource.TREE);

        if (propertiesPanel != null) {
            propertiesPanel.requestTiledMapProperties(layerEid);
        }
    }

    private void handleSpatialBlocksNodeSelection(EntityNode spatialNode) {
        if (spatialNode == null || !spatialNode.isSpatialBlocksNode()) return;

        int layerEid = spatialNode.getEntityId();
        if (layerEid < 0) return;

        explicitTiledMapLayerEid = -1;
        if (propertiesPanel != null) {
            propertiesPanel.clearTiledMapMode();
        }

        forceSingleTreeSelection(spatialNode);
        exitExplicitPhysicsEditMode();

        selectionService.clearSelection(SelectionService.SelectionSource.TREE);
        selectionService.setActivelayerId(layerEid, SelectionService.SelectionSource.TREE);
        spatialBlockSelectionService.enterLayer(layerEid);
    }

    private void rebuildTreeFromWorld() {
        suppressTreeSelectionEvents = true;

        tree.clearNodes();

        int layerCount = layerService.count();
        IntBag itemsBag = layerItemsSub.getEntities();
        int[] itemData = itemsBag.getData();
        int itemCount = itemsBag.size();
        List<Integer> layerEntities = new ArrayList<>();

        for (int li = layerCount - 1; li >= 0; li--) {
            layerEntities.clear();
            int eLayer = layerService.getLayerEntity(li);
            var meta = layerService.meta(li);

            assert meta != null;
            EntityNode layerNode = new EntityNode(meta.name, null, eLayer, true, EntityNode.NodeKind.LAYER);
            tree.add(layerNode);
            tree.registerNode(layerNode, eLayer);

            if (meta.locked) {
                layerNode.getLabel().setColor(Color.DARK_GRAY);
            } else {
                layerNode.getLabel().setColor(Color.WHITE);
            }

            LayerComponent layerComp = world.getMapper(LayerComponent.class).get(eLayer);
            EntityNode mapNode = null;

            if (layerComp.type == LayerComponent.TYPE_TILED) {
                TiledLayerComponent tiled = mTiled.getSafe(eLayer, null);
                if (tiled != null) {
                    mapNode = new EntityNode(
                            "Map (" + tiled.mapWidthCells + " x " + tiled.mapHeightCells + ")",
                            IconResolver.getDrawable(EntityKind.TILED_MAP),
                            eLayer,
                            true,
                            EntityNode.NodeKind.TILED_MAP
                    );
                    if (layerNode.getActor() != null && mapNode.getActor() != null) {
                        mapNode.getActor().setUserObject(layerNode.getActor().getUserObject());
                    }
                    if (meta.locked) {
                        mapNode.getLabel().setColor(Color.DARK_GRAY);
                    } else {
                        mapNode.getLabel().setColor(Color.WHITE);
                    }
                    layerNode.add(mapNode);
                    tree.registerMapNode(mapNode, eLayer);

                    if (mBody.has(eLayer)) {
                        boolean selectableBody = !meta.locked;

                        EntityNode bodyNode = new EntityNode(
                                "Static body",
                                null,
                                eLayer,
                                selectableBody,
                                selectableBody ? EntityNode.NodeKind.BODY : EntityNode.NodeKind.INFO
                        );

                        if (meta.locked) {
                            bodyNode.getLabel().setColor(Color.DARK_GRAY);
                        } else if (selectableBody) {
                            bodyNode.getLabel().setColor(Color.WHITE);
                        } else {
                            bodyNode.getLabel().setColor(0.65f, 0.65f, 0.65f, 1f);
                        }

                        mapNode.add(bodyNode);
                        if (selectableBody) {
                            tree.registerNode(bodyNode, eLayer);
                        }
                    }

                    if (isLayerSpatialEnabled(eLayer, layerComp, tiled)) {
                        boolean selectableSpatial = !meta.locked;
                        EntityNode spatialNode = new EntityNode(
                                "Spatial volumes",
                                null,
                                eLayer,
                                selectableSpatial,
                                selectableSpatial ? EntityNode.NodeKind.SPATIAL_BLOCKS : EntityNode.NodeKind.INFO
                        );

                        if (meta.locked) {
                            spatialNode.getLabel().setColor(Color.DARK_GRAY);
                        } else if (selectableSpatial) {
                            spatialNode.getLabel().setColor(Color.WHITE);
                        } else {
                            spatialNode.getLabel().setColor(0.65f, 0.65f, 0.65f, 1f);
                        }

                        mapNode.add(spatialNode);
                        if (selectableSpatial) {
                            tree.registerNode(spatialNode, eLayer);
                        }
                    }
                }
            }

            for (int i = 0; i < itemCount; i++) {
                int e = itemData[i];
                EntityIndexComponent index = requireEntityIndex(e, "ItemTree rebuild");
                if (index.getLayerIndex() != li) continue;
                layerEntities.add(e);
            }

            layerEntities.sort(this::compareEntitiesByZIndex);

            for (int e : layerEntities) {
                PixscapeIdentityComponent identity = mIdentity.getSafe(e, null);
                ParticleEmitterComponent emitter = mEmitter.getSafe(e, null);
                String name = identity != null ? identity.name : null;
                if (name == null) {
                    if (emitter != null && emitter.effectPath != null && !emitter.effectPath.isEmpty()) {
                        name = "Particle: " + emitter.effectPath;
                    } else {
                        name = "Entity " + e;
                    }
                }

                boolean selectableBody = !meta.locked;
                EntityNode child = new EntityNode(
                        name,
                        iconResolver.iconForEntity(e),
                        e,
                        selectableBody
                );

                if (meta.locked) {
                    child.getLabel().setColor(Color.DARK_GRAY);
                } else if (child.isSelectable()) {
                    child.getLabel().setColor(Color.WHITE);
                } else {
                    child.getLabel().setColor(Color.GRAY);
                }

                layerNode.add(child);
                tree.registerNode(child, e);

                if (mBody.has(e)) {
                    PhysicsBodyComponent body = mBody.get(e);
                    String type = switch (body.type) {
                        case STATIC -> "Static";
                        case DYNAMIC -> "Dynamic";
                        case KINEMATIC -> "Kinematic";
                        default -> "Unknown";
                    };

                    EntityNode bodyNode = new EntityNode(
                            type + " body",
                            null,
                            e,
                            selectableBody,
                            selectableBody ? EntityNode.NodeKind.BODY : EntityNode.NodeKind.INFO
                    );

                    if (meta.locked) {
                        bodyNode.getLabel().setColor(Color.DARK_GRAY);
                    } else if (selectableBody) {
                        bodyNode.getLabel().setColor(Color.WHITE);
                    } else {
                        bodyNode.getLabel().setColor(0.65f, 0.65f, 0.65f, 1f);
                    }

                    child.add(bodyNode);
                    if (selectableBody) {
                        tree.registerNode(bodyNode, e);
                    }
                }
            }
        }

        tree.expandAll();

        suppressTreeSelectionEvents = false;
        syncTreeSelectionFromModel(selectionService.getSelectionSnapshot(), false);
    }

    private int compareEntitiesByZIndex(int e1, int e2) {
        int z1 = requireEntityIndex(e1, "ItemTree compare").getZIndex();
        int z2 = requireEntityIndex(e2, "ItemTree compare").getZIndex();
        if (z1 != z2) {
            return Integer.compare(z2, z1);
        }
        return Integer.compare(e1, e2);
    }

    private EntityIndexComponent requireEntityIndex(int entityId, String context) {
        EntityIndexComponent index = mEntityIndex.getSafe(entityId, null);
        if (index == null) {
            throw new IllegalStateException(context + ": drawable entity missing EntityIndexComponent: " + entityId);
        }
        return index;
    }

    public void onSelectionChanged(IntArray selectionSnapshot) {
        syncTreeSelectionFromModel(selectionSnapshot, true);
    }

    private void syncTreeSelectionFromModel(IntArray selectionSnapshot) {
        syncTreeSelectionFromModel(selectionSnapshot, true);
    }

    private void syncTreeSelectionFromModel(IntArray selectionSnapshot, boolean applyFocus) {
        tree.getSelection().setProgrammaticChangeEvents(false);
        tree.getSelection().clear();

        EntityNode physicsNode = resolvePhysicsContextNode();
        if (physicsNode != null) {
            tree.getSelection().add(physicsNode);
            if (applyFocus) {
                focusNode(physicsNode);
            }
            tree.getSelection().setProgrammaticChangeEvents(true);
            return;
        }

        EntityNode spatialNode = resolveSpatialBlockContextNode();
        if (spatialNode != null) {
            tree.getSelection().add(spatialNode);
            if (applyFocus) {
                focusNode(spatialNode);
            }
            tree.getSelection().setProgrammaticChangeEvents(true);
            return;
        }

        if (explicitTiledMapLayerEid >= 0) {
            EntityNode mapNode = tree.findMapNode(explicitTiledMapLayerEid);
            if (mapNode != null) {
                tree.getSelection().add(mapNode);
                if (applyFocus) {
                    focusNode(mapNode);
                }
                tree.getSelection().setProgrammaticChangeEvents(true);
                return;
            }
        }

        int activeLayerId = selectionService.getActivelayerId();
        if (activeLayerId >= 0 && (selectionSnapshot == null || selectionSnapshot.size == 0)) {
            EntityNode layerNode = tree.findNode(activeLayerId, EntityNode.NodeKind.LAYER);
            if (layerNode != null) {
                tree.getSelection().add(layerNode);
                if (applyFocus) {
                    focusNode(layerNode);
                }
                tree.getSelection().setProgrammaticChangeEvents(true);
                return;
            }
        }

        EntityNode firstSelectedNode = null;

        if (selectionSnapshot != null) {
            for (int i = 0; i < selectionSnapshot.size; i++) {
                int e = selectionSnapshot.get(i);
                EntityNode node = tree.findNode(e);
                if (node != null) {
                    tree.getSelection().add(node);
                    if (firstSelectedNode == null) {
                        firstSelectedNode = node;
                    }
                }
            }
        }

        if (applyFocus && firstSelectedNode != null) {
            focusNode(firstSelectedNode);
        }

        tree.getSelection().setProgrammaticChangeEvents(true);
    }

    private EntityNode resolvePhysicsContextNode() {
        int bodyEid = resolveExplicitPhysicsContextBody();
        if (bodyEid < 0) return null;
        return tree.findBodyNode(bodyEid);
    }

    private int resolveExplicitPhysicsContextBody() {
        int focusedFixtureBody = physicsSelectionService.getFocusedBodyEid();
        if (focusedFixtureBody >= 0) return focusedFixtureBody;

        int focusedJointBody = physicsSelectionService.getFocusedBodyEid();
        if (focusedJointBody >= 0) return focusedJointBody;

        return -1;
    }

    private EntityNode resolveSpatialBlockContextNode() {
        int layerEntityId = spatialBlockSelectionService.getEditingLayerEntityId();
        if (layerEntityId < 0) return null;
        return tree.findSpatialBlocksNode(layerEntityId);
    }

    private void handleBodyNodeSelection(EntityNode bodyNode) {
        if (bodyNode == null || !bodyNode.isBodyNode()) return;

        explicitTiledMapLayerEid = -1;
        if (propertiesPanel != null) {
            propertiesPanel.clearTiledMapMode();
        }

        int bodyEid = bodyNode.getEntityId();
        if (bodyEid < 0) return;

        forceSingleTreeSelection(bodyNode);
        exitExplicitSpatialBlockMode();

        EntityIndexComponent idx = mEntityIndex.getSafe(bodyEid, null);
        if (idx != null) {
            activateLayerForEntity(bodyEid, SelectionService.SelectionSource.TREE);
            selectionService.selectOnly(bodyEid, SelectionService.SelectionSource.TREE);
        } else {
            selectionService.clearSelection(SelectionService.SelectionSource.TREE);
            selectionService.setActivelayerIdForPhysicsContext(bodyEid, SelectionService.SelectionSource.TREE);
        }

        physicsSelectionService.clearSelectionOnly();
        physicsSelectionService.focusBody(bodyEid);

        if (propertiesPanel != null) {
            propertiesPanel.requestBodyProperties(bodyEid);
        }
    }

    private void activateLayerForEntity(int eid, SelectionService.SelectionSource source) {
        EntityIndexComponent idx = mEntityIndex.getSafe(eid, null);
        if (idx == null) return;

        int layerEntity = layerService.getLayerEntity(idx.getLayerIndex());
        if (layerEntity != -1) {
            selectionService.setActivelayerId(layerEntity, source);
        }
    }

    private void exitExplicitPhysicsEditMode() {
        physicsSelectionService.clear();
    }

    private void exitExplicitSpatialBlockMode() {
        spatialBlockSelectionService.clear();
    }

    private EntityNode findFirstBodyNode(com.badlogic.gdx.utils.Array<EntityNode> nodes) {
        if (nodes == null) return null;
        for (int i = 0; i < nodes.size; i++) {
            EntityNode node = nodes.get(i);
            if (node != null && node.isBodyNode()) return node;
        }
        return null;
    }

    private EntityNode findFirstSpatialBlocksNode(com.badlogic.gdx.utils.Array<EntityNode> nodes) {
        if (nodes == null) return null;
        for (int i = 0; i < nodes.size; i++) {
            EntityNode node = nodes.get(i);
            if (node != null && node.isSpatialBlocksNode()) return node;
        }
        return null;
    }

    private boolean isLayerSpatialEnabled(int layerEntityId, LayerComponent layer, TiledLayerComponent tiled) {
        if (layer != null && layer.spatialEnabled) return true;
        return tiled != null && (tiled.spatialEnabled || (tiled.data != null && tiled.data.spatialEnabled));
    }

    private void forceSingleTreeSelection(EntityNode node) {
        if (node == null) return;

        boolean prevSuppress = suppressTreeSelectionEvents;
        suppressTreeSelectionEvents = true;
        tree.getSelection().setProgrammaticChangeEvents(false);
        tree.getSelection().clear();
        tree.getSelection().add(node);
        tree.getSelection().setProgrammaticChangeEvents(true);
        suppressTreeSelectionEvents = prevSuppress;
    }

    private int countFixtures(int entityId) {
        PhysicsShapesComponent fixtures = mFixtures.getSafe(entityId, null);
        if (fixtures == null || fixtures.shapes == null) return 0;
        return fixtures.shapes.size;
    }
}
