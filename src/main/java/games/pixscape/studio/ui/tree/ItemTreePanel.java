package games.pixscape.studio.ui.tree;

import com.artemis.*;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.IntMap;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.widget.VisScrollPane;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.MenuItem;
import com.kotcrab.vis.ui.widget.PopupMenu;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsJointComponent;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.hierarchy.GameObjectCompositionState;
import games.pixscape.runtime.hierarchy.GameObjectTopologyState;
import games.pixscape.runtime.system.GameObjectCompositionSystem;
import games.pixscape.runtime.system.GameObjectHierarchySystem;
import games.pixscape.studio.component.EntityMetaComponent;
import games.pixscape.studio.component.LayerMetaComponent;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.history.commands.ReorderLogicalLayerCommand;
import games.pixscape.studio.history.commands.AddTiledMapCommand;
import games.pixscape.studio.event.GetScrollListener;
import games.pixscape.studio.event.LoseScroolListener;
import games.pixscape.studio.model.EntityKind;
import games.pixscape.studio.ops.EditorOps;
import games.pixscape.studio.service.IconResolver;
import games.pixscape.studio.service.LayerService;
import games.pixscape.studio.service.SelectionService;
import games.pixscape.studio.service.zorder.LayerLogicalOrderService;
import games.pixscape.studio.service.physics.PhysicsSelectionService;
import games.pixscape.studio.service.spatial.SpatialBlockSelectionService;
import games.pixscape.studio.system.UiRefreshDispatchSystem;
import games.pixscape.studio.ui.asset.AssetNode;
import games.pixscape.studio.ui.asset.AssetsPanel;
import games.pixscape.studio.ui.docking.DockablePanel;
import games.pixscape.studio.ui.main.StudioApplicationAdapter;
import games.pixscape.studio.ui.layer.AddTiledMapDialog;
import games.pixscape.studio.ui.property.PropertiesPanel;

import static games.pixscape.runtime.component.physics.PhysicsBodyComponent.*;

public class ItemTreePanel extends DockablePanel {

    private final StudioApplicationAdapter app;
    private final World world;
    private final LayerService layerService;
    private final PhysicsSelectionService physicsSelectionService;
    private final SpatialBlockSelectionService spatialBlockSelectionService;
    private final SelectionService selectionService;
    private final LayerLogicalOrderService logicalOrderService;
    private final HistoryManager historyManager;
    private final EditorOps editorOps;

    private final ComponentMapper<EntityMetaComponent> mMeta;
    private final ComponentMapper<PixscapeIdentityComponent> mIdentity;
    private final ComponentMapper<EntityIndexComponent> mEntityIndex;
    private final ComponentMapper<ParticleEmitterComponent> mEmitter;
    private final ComponentMapper<TiledLayerComponent> mTiled;
    private final ComponentMapper<PhysicsBodyComponent> mBody;
    private final ComponentMapper<PhysicsShapesComponent> mFixtures;
    private final ComponentMapper<GameObjectComponent> mGameObject;
    private final ComponentMapper<GameObjectMemberComponent> mGameObjectMember;

    private final EntitySubscription layersSub;
    private final EntitySubscription layerItemsSub;
    private final EntitySubscription jointsSub;
    private final EntitySubscription gameObjectMembersSub;
    private final GameObjectHierarchySystem gameObjectHierarchy;
    private final GameObjectCompositionSystem gameObjectComposition;

    private final IdVisTree tree;
    private final IconResolver iconResolver;
    private volatile boolean dirty = true;
    private boolean suppressTreeSelectionEvents = false;
    private boolean handlingTreeSelection = false;

    private int explicitTiledMapEntityId = -1;

    private final VisScrollPane scroller;

    private PropertiesPanel propertiesPanel;

    public ItemTreePanel(StudioApplicationAdapter app) {
        super("Items");

        this.app = app;
        var canvas = app.getCanvas();
        this.world = canvas.getEcsWorld();
        this.layerService = canvas.getLayerService();
        this.physicsSelectionService = canvas.getPhysicsSelectionService();
        this.spatialBlockSelectionService = canvas.getSpatialBlockSelectionService();
        this.selectionService = canvas.getSelectionService();
        this.logicalOrderService = new LayerLogicalOrderService(world);
        this.historyManager = canvas.getHistoryManager();
        this.editorOps = canvas.getEditorOps();

        this.mMeta = world.getMapper(EntityMetaComponent.class);
        this.mIdentity = world.getMapper(PixscapeIdentityComponent.class);
        this.mEntityIndex = world.getMapper(EntityIndexComponent.class);
        this.mEmitter = world.getMapper(ParticleEmitterComponent.class);
        this.mTiled = world.getMapper(TiledLayerComponent.class);
        this.mBody = world.getMapper(PhysicsBodyComponent.class);
        this.mFixtures = world.getMapper(PhysicsShapesComponent.class);
        this.mGameObject = world.getMapper(GameObjectComponent.class);
        this.mGameObjectMember = world.getMapper(GameObjectMemberComponent.class);
        this.gameObjectHierarchy = world.getSystem(GameObjectHierarchySystem.class);
        this.gameObjectComposition = world.getSystem(GameObjectCompositionSystem.class);

        UiRefreshDispatchSystem postProcess = world.getSystem(UiRefreshDispatchSystem.class);
        postProcess.add(this::updateIfDirty);

        AspectSubscriptionManager asm = world.getAspectSubscriptionManager();
        this.layersSub = asm.get(Aspect.all(LayerComponent.class, LayerMetaComponent.class));
        this.layerItemsSub = asm.get(layerItemAspect());
        this.jointsSub = asm.get(Aspect.all(PhysicsJointComponent.class));
        this.gameObjectMembersSub = asm.get(Aspect.all(GameObjectMemberComponent.class));

        this.iconResolver = new IconResolver(world);

        tree = new IdVisTree();
        tree.setIndentSpacing(25);
        tree.getSelection().setMultiple(true);
        hookItemContextMenus();

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

            if (evt.source() != SelectionService.SelectionSource.TREE) {
                explicitTiledMapEntityId = -1;
                if (propertiesPanel != null) {
                    propertiesPanel.clearTiledMapMode();
                }
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
        EventFlow.i().subscribe(EventFlow.EntityZOrderChanged.class, evt -> markDirty());
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
                explicitTiledMapEntityId = -1;
            }

            boolean applyFocus = evt.source() != SelectionService.SelectionSource.TREE;
            syncTreeSelectionFromModel(selectionService.getSelectionSnapshot(), applyFocus);
        });

        attachAutoRefresh();
        hookTreeSelection();
    }

    static Aspect.Builder layerItemAspect() {
        return Aspect.all(EntityIndexComponent.class, PixscapeIdentityComponent.class)
                .exclude(LayerComponent.class, PhysicsJointComponent.class);
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
        moveSelection(-1);
    }

    private void moveSelectionDown() {
        moveSelection(1);
    }

    private void moveSelection(int direction) {
        IntArray selection = selectionService.getSelectionSnapshot();
        if (selection.size != 1) return;
        int entityId = selection.first();
        // Member z is local sibling order; the global layer reorder command is not applicable.
        if (mGameObjectMember.has(entityId)) return;
        if (!ItemTreeJointSupport.isLogicalOrderMoveAllowed(world, entityId)) return;
        EntityIndexComponent index = requireEntityIndex(entityId, "ItemTree move");
        LayerLogicalOrderService.LayerOrder order =
                logicalOrderService.derive(index.layerIndex);
        executeLogicalReorder(index.layerIndex, order.moveEntity(entityId, direction));
    }

    private void executeLogicalReorder(int layerIndex, IntArray desiredOrder) {
        if (desiredOrder == null) return;
        ReorderLogicalLayerCommand command = new ReorderLogicalLayerCommand(
                world, historyManager.historyIds(), layerIndex, desiredOrder);
        if (command.isNoop()) return;
        historyManager.execute(command);
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
        jointsSub.addSubscriptionListener(refsListener);
        gameObjectMembersSub.addSubscriptionListener(refsListener);
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
                    EntityNode jointNode = findFirstJointNode(nodes);
                    EntityNode spatialNode = findFirstSpatialBlocksNode(nodes);
                    if (jointNode != null && nodes.size == 1) {
                        handleJointNodeSelection(jointNode);
                    } else if (mapNode != null && nodes.size == 1) {
                        handleTiledMapNodeSelection(mapNode);
                    } else if (bodyNode != null && nodes.size == 1) {
                        handleBodyNodeSelection(bodyNode);
                    } else if (spatialNode != null && nodes.size == 1) {
                        handleSpatialBlocksNodeSelection(spatialNode);
                    } else {
                        explicitTiledMapEntityId = -1;
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

        int mapEntityId = mapNode.getEntityId();
        if (mapEntityId < 0) return;
        EntityIndexComponent index = mEntityIndex.getSafe(mapEntityId, null);
        if (index == null) return;
        int hostLayerEntityId = layerService.getLayerEntity(index.layerIndex);
        if (hostLayerEntityId < 0) return;

        explicitTiledMapEntityId = mapEntityId;

        forceSingleTreeSelection(mapNode);
        exitExplicitPhysicsEditMode();
        exitExplicitSpatialBlockMode();

        selectionService.clearSelection(SelectionService.SelectionSource.TREE);
        selectionService.setTiledMapEditingTarget(
                mapEntityId, SelectionService.SelectionSource.TREE);

        if (propertiesPanel != null) {
            propertiesPanel.requestTiledMapProperties(mapEntityId);
        }
    }

    private void handleSpatialBlocksNodeSelection(EntityNode spatialNode) {
        if (spatialNode == null || !spatialNode.isSpatialBlocksNode()) return;

        int mapEntityId = spatialNode.getEntityId();
        if (mapEntityId < 0) return;
        EntityIndexComponent index = mEntityIndex.getSafe(mapEntityId, null);
        if (index == null) return;
        int hostLayerEntityId = layerService.getLayerEntity(index.layerIndex);
        if (hostLayerEntityId < 0) return;

        explicitTiledMapEntityId = -1;
        if (propertiesPanel != null) {
            propertiesPanel.clearTiledMapMode();
        }

        forceSingleTreeSelection(spatialNode);
        exitExplicitPhysicsEditMode();

        selectionService.clearSelection(SelectionService.SelectionSource.TREE);
        selectionService.setTiledMapEditingTarget(
                mapEntityId, SelectionService.SelectionSource.TREE);
        spatialBlockSelectionService.enterMap(mapEntityId);
    }

    private void rebuildTreeFromWorld() {
        suppressTreeSelectionEvents = true;

        tree.clearNodes();
        GameObjectTopologyState topology = gameObjectHierarchy != null
                ? gameObjectHierarchy.topology() : null;
        GameObjectCompositionState composition = gameObjectComposition != null
                ? gameObjectComposition.state() : null;

        int layerCount = layerService.count();
        for (int li = layerCount - 1; li >= 0; li--) {
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

            LayerLogicalOrderService.LayerOrder logicalOrder = logicalOrderService.derive(li);
            for (LayerLogicalOrderService.LogicalItem item : logicalOrder.items()) {
                if (isParented(item.entityId(), topology)) continue;
                if (mTiled.has(item.entityId())) {
                    int mapEntityId = item.entityId();
                    EntityNode mapNode = createTiledMapNode(mapEntityId, meta.locked);
                    layerNode.add(mapNode);
                    tree.registerMapNode(mapNode, mapEntityId);
                } else {
                    layerNode.add(createGameObjectHierarchyNode(
                            item.entityId(), meta.locked, topology, composition));
                }
            }
        }

        ItemTreeJointSupport.attachJointNodes(
                world,
                tree,
                layerIndex -> {
                    LayerMetaComponent meta = layerService.meta(layerIndex);
                    return meta != null && meta.locked;
                });

        tree.expandAll();

        suppressTreeSelectionEvents = false;
        syncTreeSelectionFromModel(selectionService.getSelectionSnapshot(), false);
    }

    private EntityNode createGameObjectHierarchyNode(
            int entityId, boolean layerLocked,
            GameObjectTopologyState topology, GameObjectCompositionState composition) {
        EntityNode node = createEntityNode(entityId, layerLocked);
        if (!mGameObject.has(entityId) || topology == null || composition == null
                || entityId >= composition.orderedFirstChildEntityId.length) {
            return node;
        }
        int child = composition.orderedFirstChildEntityId[entityId];
        while (child >= 0) {
            node.add(createGameObjectHierarchyNode(child, layerLocked, topology, composition));
            child = child < composition.orderedNextSiblingEntityId.length
                    ? composition.orderedNextSiblingEntityId[child] : -1;
        }
        return node;
    }

    private static boolean isParented(int entityId, GameObjectTopologyState topology) {
        return topology != null && entityId >= 0
                && entityId < topology.parented.length && topology.parented[entityId];
    }

    private void hookItemContextMenus() {
        tree.addListener(new ItemTreeContextMenuInputListener<>(
                tree,
                this::supportsContextMenu,
                this::activateContextMenuNode,
                this::selectedAssetNode,
                (node, selectedAsset, stage, stageX, stageY) -> {
                    if (node.isLayerNode()) {
                        showLayerContextMenu(
                                node.getEntityId(), selectedAsset, stage, stageX, stageY);
                    } else {
                        showGameObjectContextMenu(
                                node.getEntityId(), selectedAsset, stage, stageX, stageY);
                    }
                }));
    }

    private boolean supportsContextMenu(EntityNode node) {
        if (node == null) return false;
        if (node.isLayerNode()) return layerService.isLayerEntity(node.getEntityId());
        return node.isEntityNode() && mGameObject.has(node.getEntityId());
    }

    private void activateContextMenuNode(EntityNode node) {
        if (node.isLayerNode()) {
            selectionService.setActivelayerId(
                    node.getEntityId(), SelectionService.SelectionSource.TREE);
            return;
        }
        activateLayerForEntity(node.getEntityId(), SelectionService.SelectionSource.TREE);
        selectionService.selectOnly(node.getEntityId(), SelectionService.SelectionSource.TREE);
    }

    private void showLayerContextMenu(
            int layerEntityId,
            AssetNode selectedAsset,
            Stage stage,
            float stageX,
            float stageY) {
                PopupMenu addMenu = new PopupMenu();

                MenuItem addSprite = new MenuItem("Sprite");
                addSprite.setDisabled(!isSpriteAsset(selectedAsset));
                addSprite.addListener(new ClickListener() {
                    @Override
                    public void clicked(InputEvent click, float itemX, float itemY) {
                        if (addSprite.isDisabled()) return;
                        int entityId = editorOps.createStandaloneSprite(
                                selectedAsset.path, 0f, 0f, selectedAsset.name);
                        if (entityId >= 0) {
                            selectionService.selectOnly(entityId, SelectionService.SelectionSource.TREE);
                        }
                        click.handle();
                    }
                });
                addMenu.addItem(addSprite);

                MenuItem addAnimation = new MenuItem("Animation");
                addAnimation.setDisabled(!isAnimationAsset(selectedAsset));
                addAnimation.addListener(new ClickListener() {
                    @Override
                    public void clicked(InputEvent click, float itemX, float itemY) {
                        if (addAnimation.isDisabled()) return;
                        int entityId = editorOps.createAnimationSprite(
                                selectedAsset.path, 0f, 0f, selectedAsset.name);
                        if (entityId >= 0) {
                            selectionService.selectOnly(entityId, SelectionService.SelectionSource.TREE);
                        }
                        click.handle();
                    }
                });
                addMenu.addItem(addAnimation);

                MenuItem addMap = new MenuItem("Tiled Map");
                addMap.addListener(new ClickListener() {
                    @Override
                    public void clicked(InputEvent click, float itemX, float itemY) {
                        new AddTiledMapDialog(request -> historyManager.execute(
                                new AddTiledMapCommand(layerService, layerEntityId,
                                        request.mapWidth(), request.mapHeight(), request.projection(),
                                        request.tileWidth(), request.tileHeight(), request.chunkSize(),
                                        mapEntityId -> {
                                            if (mapEntityId >= 0) {
                                                selectionService.clearSelection(
                                                        SelectionService.SelectionSource.TREE);
                                                selectionService.setTiledMapEditingTarget(mapEntityId,
                                                        SelectionService.SelectionSource.TREE);
                                            } else {
                                                selectionService.clearTiledMapEditingTarget();
                                            }
                                        }))).show(getStage());
                        click.handle();
                    }
                });
                addMenu.addItem(addMap);

                MenuItem addGameObject = new MenuItem("Game Object");
                addGameObject.addListener(new ClickListener() {
                    @Override
                    public void clicked(InputEvent click, float itemX, float itemY) {
                        int entityId = editorOps.createGameObject(0f, 0f);
                        if (entityId >= 0) {
                            selectionService.selectOnly(
                                    entityId, SelectionService.SelectionSource.TREE);
                        }
                        click.handle();
                    }
                });
                addMenu.addItem(addGameObject);

                PopupMenu lights = new PopupMenu();
                MenuItem point = new MenuItem("Point Light");
                point.addListener(new ClickListener() {
                    @Override public void clicked(InputEvent click, float itemX, float itemY) {
                        int entityId = editorOps.createPointLight(0f, 0f);
                        selectionService.selectOnly(entityId, SelectionService.SelectionSource.TREE);
                        click.handle();
                    }
                });
                lights.addItem(point);
                MenuItem cone = new MenuItem("Cone Light");
                cone.addListener(new ClickListener() {
                    @Override public void clicked(InputEvent click, float itemX, float itemY) {
                        int entityId = editorOps.createConeLight(0f, 0f);
                        selectionService.selectOnly(entityId, SelectionService.SelectionSource.TREE);
                        click.handle();
                    }
                });
                lights.addItem(cone);
                MenuItem light = new MenuItem("Light");
                light.setSubMenu(lights);
                addMenu.addItem(light);

                PopupMenu menu = new PopupMenu();
                MenuItem add = new MenuItem("Add");
                add.setSubMenu(addMenu);
                menu.addItem(add);
                menu.showMenu(stage, stageX, stageY);
    }

    private void showGameObjectContextMenu(
            int parentEntityId,
            AssetNode selectedAsset,
            Stage stage,
            float stageX,
            float stageY) {
        PopupMenu addMenu = buildGameObjectAddMenu(
                selectedAsset,
                new GameObjectChildMenuActions() {
                    @Override
                    public void addSprite() {
                        editorOps.createStandaloneSpriteInGameObject(
                                parentEntityId, selectedAsset.path, selectedAsset.name);
                    }

                    @Override
                    public void addAnimation() {
                        editorOps.createAnimationSpriteInGameObject(
                                parentEntityId, selectedAsset.path, selectedAsset.name);
                    }

                    @Override
                    public void addPointLight() {
                        editorOps.createPointLightInGameObject(parentEntityId);
                    }

                    @Override
                    public void addConeLight() {
                        editorOps.createConeLightInGameObject(parentEntityId);
                    }

                    @Override
                    public void addGameObject() {
                        editorOps.createGameObjectInGameObject(parentEntityId);
                    }
                });

        PopupMenu menu = new PopupMenu();
        MenuItem add = new MenuItem("Add");
        add.setSubMenu(addMenu);
        menu.addItem(add);
        menu.showMenu(stage, stageX, stageY);
    }

    interface GameObjectChildMenuActions {
        void addSprite();
        void addAnimation();
        void addPointLight();
        void addConeLight();
        void addGameObject();
    }

    static PopupMenu buildGameObjectAddMenu(
            AssetNode selectedAsset,
            GameObjectChildMenuActions actions) {
        PopupMenu addMenu = new PopupMenu();

        MenuItem addSprite = new MenuItem("Sprite");
        addSprite.setDisabled(!isSpriteAsset(selectedAsset));
        addSprite.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (!addSprite.isDisabled()) actions.addSprite();
                event.handle();
            }
        });
        addMenu.addItem(addSprite);

        MenuItem addAnimation = new MenuItem("Animation");
        addAnimation.setDisabled(!isAnimationAsset(selectedAsset));
        addAnimation.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (!addAnimation.isDisabled()) actions.addAnimation();
                event.handle();
            }
        });
        addMenu.addItem(addAnimation);

        PopupMenu lights = new PopupMenu();
        MenuItem point = new MenuItem("Point Light");
        point.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                actions.addPointLight();
                event.handle();
            }
        });
        lights.addItem(point);
        MenuItem cone = new MenuItem("Cone Light");
        cone.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                actions.addConeLight();
                event.handle();
            }
        });
        lights.addItem(cone);
        MenuItem light = new MenuItem("Light");
        light.setSubMenu(lights);
        addMenu.addItem(light);

        MenuItem addGameObject = new MenuItem("Game Object");
        addGameObject.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                actions.addGameObject();
                event.handle();
            }
        });
        addMenu.addItem(addGameObject);
        return addMenu;
    }

    private AssetNode selectedAssetNode() {
        AssetsPanel assetsPanel = app.getAssetsPanel();
        return assetsPanel != null ? assetsPanel.getSelectedAssetNode() : null;
    }

    static boolean isSpriteAsset(AssetNode node) {
        return node != null
                && node.kind == AssetNode.Kind.IMAGE
                && node.root == AssetNode.Root.IMAGES;
    }

    static boolean isAnimationAsset(AssetNode node) {
        return node != null
                && node.kind == AssetNode.Kind.ANIMATION
                && node.root == AssetNode.Root.ANIMATIONS;
    }

    private EntityNode createTiledMapNode(int mapEntityId, boolean layerLocked) {
        TiledLayerComponent tiled = mTiled.get(mapEntityId);
        PixscapeIdentityComponent identity = mIdentity.getSafe(mapEntityId, null);
        String mapName = identity != null && identity.name != null && !identity.name.isBlank()
                ? identity.name : "Map";
        EntityNode mapNode = new EntityNode(
                mapName + " (" + tiled.mapWidthCells + " x " + tiled.mapHeightCells + ")",
                IconResolver.getDrawable(EntityKind.TILED_MAP), mapEntityId, !layerLocked,
                EntityNode.NodeKind.TILED_MAP);
        mapNode.getLabel().setColor(layerLocked ? Color.DARK_GRAY : Color.WHITE);

        if (mBody.has(mapEntityId)) {
            EntityNode bodyNode = new EntityNode("Static body", null, mapEntityId, !layerLocked,
                    EntityNode.NodeKind.BODY);
            bodyNode.getLabel().setColor(layerLocked ? Color.DARK_GRAY : Color.WHITE);
            mapNode.add(bodyNode);
            tree.registerNode(bodyNode, mapEntityId);
        }
        if (isMapSpatialEnabled(tiled)) {
            EntityNode spatialNode = new EntityNode("Spatial volumes", null, mapEntityId,
                    !layerLocked, !layerLocked
                    ? EntityNode.NodeKind.SPATIAL_BLOCKS : EntityNode.NodeKind.INFO);
            spatialNode.getLabel().setColor(layerLocked ? Color.DARK_GRAY : Color.WHITE);
            mapNode.add(spatialNode);
            if (!layerLocked) tree.registerNode(spatialNode, mapEntityId);
        }
        return mapNode;
    }

    private EntityNode createEntityNode(int entityId, boolean layerLocked) {
        PixscapeIdentityComponent identity = mIdentity.getSafe(entityId, null);
        ParticleEmitterComponent emitter = mEmitter.getSafe(entityId, null);
        String name = identity != null ? identity.name : null;
        if (name == null) {
            name = emitter != null && emitter.effectPath != null && !emitter.effectPath.isEmpty()
                    ? "Particle: " + emitter.effectPath
                    : "Entity " + entityId;
        }

        boolean selectable = !layerLocked;
        EntityNode entityNode = new EntityNode(
                name, iconResolver.iconForEntity(entityId), entityId, selectable);
        entityNode.getLabel().setColor(layerLocked ? Color.DARK_GRAY : Color.WHITE);
        tree.registerNode(entityNode, entityId);

        if (mBody.has(entityId)) {
            PhysicsBodyComponent body = mBody.get(entityId);
            String type = switch (body.type) {
                case STATIC -> "Static";
                case DYNAMIC -> "Dynamic";
                case KINEMATIC -> "Kinematic";
                default -> "Unknown";
            };
            EntityNode bodyNode = new EntityNode(
                    type + " body",
                    null,
                    entityId,
                    selectable,
                    EntityNode.NodeKind.BODY);
            bodyNode.getLabel().setColor(layerLocked
                    ? Color.DARK_GRAY
                    : selectable ? Color.WHITE : new Color(0.65f, 0.65f, 0.65f, 1f));
            entityNode.add(bodyNode);
            tree.registerNode(bodyNode, entityId);
        }
        return entityNode;
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

        if (explicitTiledMapEntityId >= 0) {
            EntityNode mapNode = tree.findMapNode(explicitTiledMapEntityId);
            if (mapNode != null) {
                tree.getSelection().add(mapNode);
                if (applyFocus) {
                    focusNode(mapNode);
                }
                tree.getSelection().setProgrammaticChangeEvents(true);
                return;
            }
            explicitTiledMapEntityId = -1;
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
        EntityNode jointNode = ItemTreeJointSupport.resolveSelectedJointNode(
                tree, physicsSelectionService);
        if (jointNode != null) return jointNode;
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
        int mapEntityId = spatialBlockSelectionService.getEditingMapEntityId();
        if (mapEntityId < 0) return null;
        return tree.findSpatialBlocksNode(mapEntityId);
    }

    private void handleBodyNodeSelection(EntityNode bodyNode) {
        if (bodyNode == null || !bodyNode.isBodyNode()) return;

        explicitTiledMapEntityId = -1;
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
            selectionService.setActivelayerId(bodyEid, SelectionService.SelectionSource.TREE);
        }

        physicsSelectionService.clearSelectionOnly();
        physicsSelectionService.focusBody(bodyEid);

        if (propertiesPanel != null) {
            propertiesPanel.requestBodyProperties(bodyEid);
        }
    }

    private void handleJointNodeSelection(EntityNode jointNode) {
        if (jointNode == null || !jointNode.isJointNode() || !jointNode.isSelectable()) return;
        int jointEntityId = jointNode.getEntityId();
        if (!ItemTreeJointSupport.selectJointContext(
                world, physicsSelectionService, jointEntityId)) {
            return;
        }

        explicitTiledMapEntityId = -1;
        if (propertiesPanel != null) propertiesPanel.clearTiledMapMode();
        exitExplicitSpatialBlockMode();
        activateLayerForEntity(jointEntityId, SelectionService.SelectionSource.TREE);
        selectionService.selectOnly(jointEntityId, SelectionService.SelectionSource.TREE);
        forceSingleTreeSelection(jointNode);
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

    private EntityNode findFirstJointNode(com.badlogic.gdx.utils.Array<EntityNode> nodes) {
        if (nodes == null) return null;
        for (int i = 0; i < nodes.size; i++) {
            EntityNode node = nodes.get(i);
            if (node != null && node.isJointNode()) return node;
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

    private boolean isMapSpatialEnabled(TiledLayerComponent tiled) {
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
