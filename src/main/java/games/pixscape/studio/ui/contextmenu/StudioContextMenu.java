package games.pixscape.studio.ui.contextmenu;

import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import com.artemis.ComponentMapper;
import com.artemis.World;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.IntArray;
import com.kotcrab.vis.ui.util.dialog.Dialogs;
import com.kotcrab.vis.ui.widget.*;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.component.light.ConeLightComponent;
import games.pixscape.runtime.component.light.PointLightComponent;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.runtime.physics.PhysicsGeometryData;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.component.physics.PhysicsJointComponent;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.io.StudioFs;
import games.pixscape.studio.ops.EditorOps;
import games.pixscape.studio.service.ClipboardService;
import games.pixscape.studio.service.CoordSpaces;
import games.pixscape.studio.service.LayerService;
import games.pixscape.studio.service.SelectionService;
import games.pixscape.studio.service.entitygraph.EntityGraph;
import games.pixscape.studio.service.entitygraph.EntityGraphCaptureService;
import games.pixscape.studio.service.physics.PhysicsSelectionService;
import games.pixscape.studio.service.prefab.PrefabAssetService;
import games.pixscape.studio.service.prefab.PrefabPreviewWriter;
import games.pixscape.studio.service.spatial.SpatialBlockPlacementTarget;
import games.pixscape.studio.service.spatial.SpatialBlockSelectionService;
import games.pixscape.studio.service.spatial.SpatialTileSelectionService;
import games.pixscape.studio.ui.main.WorldCanvas;

public final class StudioContextMenu extends InputListener {
    private static final boolean DEBUG_WHEEL_CREATE = Boolean.getBoolean("pixscape.debug.wheelJointCreate");

    private final Stage stage;
    private final SelectionService selectionService;
    private final PhysicsSelectionService physicsSelectionService;
    private final SpatialBlockSelectionService spatialBlockSelectionService;
    private final SpatialTileSelectionService spatialTileSelectionService;
    private final LayerService layerService;
    private final ClipboardService clipboardService;
    private final World world;
    private final CoordSpaces coordSpaces;

    private final ComponentMapper<PhysicsBodyComponent> mBody;
    private final ComponentMapper<PhysicsJointComponent> mJointBase;
    private final ComponentMapper<PointLightComponent> mPointLight;
    private final ComponentMapper<ConeLightComponent> mConeLight;

    private final PopupMenu menu = new PopupMenu();
    private final Vector2 lastRightClickWorld = new Vector2();
    private final Vector2 tmpStage = new Vector2();
    private SpatialBlockPlacementTarget lastRightClickSpatialTarget = SpatialBlockPlacementTarget.invalid();
    private final EditorOps ops;

    private final EntityGraphCaptureService entityGraphCaptureService;
    private final PrefabAssetService prefabAssetService;

    private final int MY_TAG = EventFlow.tag(this);

    public StudioContextMenu(WorldCanvas canvas, Stage stage) {
        this.stage = stage;
        this.world = canvas.getEcsWorld();
        this.selectionService = canvas.getSelectionService();
        this.physicsSelectionService = canvas.getPhysicsSelectionService();
        this.spatialBlockSelectionService = canvas.getSpatialBlockSelectionService();
        this.spatialTileSelectionService = canvas.getSpatialTileSelectionService();
        this.layerService = canvas.getLayerService();
        this.clipboardService = canvas.getClipboardService();
        this.coordSpaces = canvas.getCoordSpaces();
        this.ops = canvas.getEditorOps();

        this.mBody = world.getMapper(PhysicsBodyComponent.class);
        this.mJointBase = world.getMapper(PhysicsJointComponent.class);
        this.mPointLight = world.getMapper(PointLightComponent.class);
        this.mConeLight = world.getMapper(ConeLightComponent.class);

        this.entityGraphCaptureService = new EntityGraphCaptureService(world);
        this.prefabAssetService = new PrefabAssetService(world);
    }

    @Override
    public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
        if (button != Input.Buttons.RIGHT) return false;

        // Only open the global context menu when right-clicking on the stage background.
        // If a UI widget is the target, let that widget handle the event.
        if (event.getTarget() != null && event.getTarget() != stage.getRoot()) {
            return false;
        }

        storeClickWorld(event);
        storeSpatialPlacementTargetSnapshot();
        buildMenu();

        if (!hasEnabledMenuItems(menu)) {
            return false;
        }

        menu.showMenu(stage, event.getStageX(), event.getStageY());
        return true;
    }

    private static boolean hasEnabledMenuItems(PopupMenu menu) {
        if (menu == null) return false;

        for (Actor child : menu.getChildren()) {
            if (child instanceof MenuItem item && !item.isDisabled()) {
                return true;
            }
        }
        return false;
    }

    private void buildMenu() {
        menu.clear();
        if (showSpatialBlocksMenu()) {
            return;
        }
        showEditMenu();
        showShapeMenu();
        showLightsMenu();
        showJointsMenu();
    }

    private boolean showSpatialBlocksMenu() {
        if (spatialBlockSelectionService == null || !spatialBlockSelectionService.isEditingActive()) {
            return false;
        }

        int layerEntityId = spatialBlockSelectionService.getEditingLayerEntityId();
        if (layerEntityId < 0) return false;

        if (spatialTileSelectionService != null && spatialTileSelectionService.hasSelection()) {
            MenuItem createFromTiles = new MenuItem("Create Spatial Block from Selected Tiles");
            String validationMessage = spatialTileSelectionValidationMessage(layerEntityId);
            if (validationMessage != null) {
                createFromTiles.setDisabled(true);
                Tooltip tip = new Tooltip.Builder(validationMessage)
                        .target(createFromTiles)
                        .build();
                tip.setAppearDelayTime(0f);
            }
            createFromTiles.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    if (createFromTiles.isDisabled()) {
                        event.handle();
                        return;
                    }
                    ops.createSpatialBlockFromSelectedTiles();
                    event.handle();
                }
            });
            menu.addItem(createFromTiles);

            MenuItem clearTileSelection = new MenuItem("Clear Spatial Tile Selection");
            clearTileSelection.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    ops.clearSpatialTileSelection();
                    event.handle();
                }
            });
            menu.addItem(clearTileSelection);
            menu.addSeparator();
        }

        MenuItem addBlock = new MenuItem("Add spatial block");
        addBlock.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                SpatialBlockPlacementTarget target = lastRightClickSpatialTarget;
                if (target != null && target.valid() && target.tiledLayerEntity() == layerEntityId) {
                    ops.addSpatialBlock(layerEntityId, target);
                } else {
                    ops.addSpatialBlock(layerEntityId, lastRightClickWorld.x, lastRightClickWorld.y);
                }
                event.handle();
            }
        });
        menu.addItem(addBlock);

        MenuItem deleteBlock = new MenuItem("Delete selected spatial block");
        deleteBlock.setDisabled(!spatialBlockSelectionService.hasSelectedBlock());
        deleteBlock.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                ops.deleteSelectedSpatialBlock();
                event.handle();
            }
        });
        menu.addItem(deleteBlock);

        return true;
    }

    private String spatialTileSelectionValidationMessage(int layerEntityId) {
        if (world == null || spatialTileSelectionService == null) return null;
        TiledLayerComponent tiled = world.getMapper(TiledLayerComponent.class).getSafe(layerEntityId, null);
        SpatialBlocksComponent walls = world.getMapper(SpatialBlocksComponent.class).getSafe(layerEntityId, null);
        return tiled != null
                ? spatialTileSelectionService.validationMessage(
                        tiled.data, walls, tiled.defaultTileAltitude, tiled.defaultTileHeight)
                : spatialTileSelectionService.validationMessage(null);
    }

    private void showShapeMenu() {
        if (physicsSelectionService == null || ops == null) return;

        int bodyEid = physicsSelectionService.getFocusedBodyEid();

        // Useful fallback for the static body of a Tiled layer:
        // if no body is focused yet, use the active layer if it has a body.
        if (bodyEid < 0) {
            int activeLayerId = selectionService.getActivelayerId();
            if (activeLayerId >= 0 && mBody.has(activeLayerId)) {
                bodyEid = activeLayerId;
            }
        }

        if (bodyEid < 0) return;

        PhysicsShapesComponent fixtures =
                world.getMapper(PhysicsShapesComponent.class).getSafe(bodyEid, null);

        int physicsShapeId = physicsSelectionService.getSelectedPhysicsShapeId();
        PhysicsShapeData selectedFixture = null;

        if (physicsShapeId > 0 && fixtures != null && fixtures.shapes != null) {
            for (int i = 0, n = fixtures.shapes.size; i < n; i++) {
                PhysicsShapeData f = fixtures.shapes.get(i);
                if (f == null) continue;
                if (f.physicsShapeId == physicsShapeId) {
                    selectedFixture = f;
                    break;
                }
            }
        }

        MenuItem addBoxShape = new MenuItem("Add box shape");
        int finalBodyEid = bodyEid;
        addBoxShape.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                coordSpaces.screenToWorld(Gdx.input.getX(), Gdx.input.getY(), tmpStage);
                physicsSelectionService.focusBody(finalBodyEid);
                physicsSelectionService.clearSelectionOnly();
                ops.addBoxFixture(finalBodyEid, tmpStage.x, tmpStage.y);
                event.handle();
            }
        });
        menu.addItem(addBoxShape);

        MenuItem addCircleShape = new MenuItem("Add circle shape");
        addCircleShape.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                coordSpaces.screenToWorld(Gdx.input.getX(), Gdx.input.getY(), tmpStage);
                physicsSelectionService.focusBody(finalBodyEid);
                physicsSelectionService.clearSelectionOnly();
                ops.addCircleFixture(finalBodyEid, tmpStage.x, tmpStage.y);
                event.handle();
            }
        });
        menu.addItem(addCircleShape);

        MenuItem addPolygonShape = new MenuItem("Add polygon shape");
        addPolygonShape.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                physicsSelectionService.focusBody(finalBodyEid);
                physicsSelectionService.clearSelectionOnly();
                ops.beginAddPolygonFixture(finalBodyEid);
                event.handle();
            }
        });
        menu.addItem(addPolygonShape);

        if (selectedFixture != null
                && selectedFixture.geometry.shapeType == PhysicsGeometryData.SHAPE_POLYGON) {
            menu.addSeparator();

            MenuItem editPolygon = new MenuItem("Edit polygon");
            editPolygon.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    physicsSelectionService.focusBody(finalBodyEid);
                    ops.beginEditPolygonFixture(finalBodyEid, physicsShapeId);
                    event.handle();
                }
            });
            menu.addItem(editPolygon);
        }

        if (selectedFixture != null) {
            if (selectedFixture.geometry.shapeType != PhysicsGeometryData.SHAPE_POLYGON) {
                menu.addSeparator();
            }

            MenuItem deleteShape = new MenuItem("Delete shape");
            deleteShape.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    physicsSelectionService.focusBody(finalBodyEid);
                    ops.deleteFixture(finalBodyEid, physicsShapeId);
                    event.handle();
                }
            });
            menu.addItem(deleteShape);
        }
    }

    private void showLightsMenu() {
        IntArray selection = selectionService.getSelectionSnapshot();
        boolean hasJointSelected = selection.size == 1 && mJointBase.has(selection.get(0));
        boolean isLightLayer = layerService != null
                && layerService.getLayerTypeByIndex(selectionService.getActiveLayerIndex()) == LayerComponent.TYPE_LIGHT;

        if (!hasJointSelected && isLightLayer) {
            PopupMenu addLightSub = new PopupMenu();
            MenuItem addPoint = new MenuItem("Point Light");
            addPoint.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    int eid = ops.createPointLight(lastRightClickWorld.x, lastRightClickWorld.y);
                    selectionService.selectOnly(eid);
                }
            });
            addLightSub.addItem(addPoint);

            MenuItem addCone = new MenuItem("Cone Light");
            addCone.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    int eid = ops.createConeLight(lastRightClickWorld.x, lastRightClickWorld.y);
                    selectionService.selectOnly(eid);
                }
            });
            addLightSub.addItem(addCone);

            PopupMenu addSub = new PopupMenu();
            MenuItem addLight = new MenuItem("Light");
            addLight.setSubMenu(addLightSub);
            addSub.addItem(addLight);

            MenuItem addRoot = new MenuItem("Add");
            addRoot.setSubMenu(addSub);
            menu.addItem(addRoot);
        }

        if (selection.size == 1) {
            int e = selection.get(0);
            if (e >= 0 && (mPointLight.has(e) || mConeLight.has(e))) {
                MenuItem del = new MenuItem("Delete light");
                del.addListener(new ClickListener() {
                    @Override
                    public void clicked(InputEvent event, float x, float y) {
                        world.delete(e);
                        selectionService.clearSelection();
                    }
                });
                menu.addItem(del);
            }
        }
    }

    private void showJointsMenu() {
        IntArray selection = selectionService.getSelectionSnapshot();

        // Delete joint (single selection)
        if (selection.size == 1) {
            int e = selection.get(0);
            if (e >= 0 && mJointBase.has(e)) {
                MenuItem del = new MenuItem("Delete joint");
                del.addListener(new ClickListener() {
                    @Override
                    public void clicked(InputEvent event, float x, float y) {
                        ops.deleteJoint(e);
                        selectionService.clearSelection();
                    }
                });
                menu.addItem(del);
            }
            return;
        }

        // Add joint (2 selected bodies)
        if (selection.size == 2) {
            int a = selection.get(0);
            int b = selection.get(1);

            if (isGearSourceJoint(a) && isGearSourceJoint(b)) {
                MenuItem addGear = new MenuItem("Add gear joint");
                addGear.addListener(new ClickListener() {
                    @Override
                    public void clicked(InputEvent event, float x, float y) {
                        int jEid = ops.createGearJoint(a, b);
                        if (jEid >= 0) selectionService.selectOnly(jEid);
                    }
                });
                menu.addItem(addGear);
                return;
            }

            if (a >= 0 && b >= 0 && mBody.has(a) && mBody.has(b)) {
                PopupMenu addJointSub = new PopupMenu();

                MenuItem distance = new MenuItem("Distance");
                distance.addListener(new ClickListener() {
                    @Override
                    public void clicked(InputEvent event, float x, float y) {
                        int jEid = ops.createJoint(PhysicsJointComponent.TYPE_DISTANCE, a, b, lastRightClickWorld.x, lastRightClickWorld.y);
                        if (jEid >= 0) selectionService.selectOnly(jEid);
                    }
                });
                addJointSub.addItem(distance);

                MenuItem revolute = new MenuItem("Revolute");
                revolute.addListener(new ClickListener() {
                    @Override
                    public void clicked(InputEvent event, float x, float y) {
                        int jEid = ops.createJoint(PhysicsJointComponent.TYPE_REVOLUTE, a, b, lastRightClickWorld.x, lastRightClickWorld.y);
                        if (jEid >= 0) selectionService.selectOnly(jEid);
                    }
                });
                addJointSub.addItem(revolute);

                MenuItem prismatic = new MenuItem("Prismatic");
                prismatic.addListener(new ClickListener() {
                    @Override
                    public void clicked(InputEvent event, float x, float y) {
                        int jEid = ops.createJoint(PhysicsJointComponent.TYPE_PRISMATIC, a, b, lastRightClickWorld.x, lastRightClickWorld.y);
                        if (jEid >= 0) selectionService.selectOnly(jEid);
                    }
                });
                addJointSub.addItem(prismatic);

                MenuItem wheel = new MenuItem("Wheel (support → wheel)");
                wheel.addListener(new ClickListener() {
                    @Override
                    public void clicked(InputEvent event, float x, float y) {
                        WheelEndpoints endpoints = resolveWheelEndpoints(a, b);
                        if (DEBUG_WHEEL_CREATE) {
                            Gdx.app.log(
                                    "WheelJointCreate",
                                    "selectionPair=(" + a + "," + b + ")"
                                            + " resolved=(" + (endpoints == null ? "null" : endpoints.bodyA + "," + endpoints.bodyB) + ")"
                                            + " clickWorld=(" + lastRightClickWorld.x + "," + lastRightClickWorld.y + ")"
                            );
                        }
                        if (endpoints == null) {
                            Gdx.app.error("SceneManager", "Cannot create wheel joint: invalid selected physics bodies.");
                            return;
                        }
                        int jEid = ops.createJoint(
                                PhysicsJointComponent.TYPE_WHEEL,
                                endpoints.bodyA,
                                endpoints.bodyB,
                                lastRightClickWorld.x,
                                lastRightClickWorld.y
                        );
                        if (jEid >= 0) selectionService.selectOnly(jEid);
                    }
                });
                addJointSub.addItem(wheel);

                MenuItem friction = new MenuItem("Friction");
                friction.addListener(new ClickListener() {
                    @Override
                    public void clicked(InputEvent event, float x, float y) {
                        int jEid = ops.createJoint(PhysicsJointComponent.TYPE_FRICTION, a, b, lastRightClickWorld.x, lastRightClickWorld.y);
                        if (jEid >= 0) selectionService.selectOnly(jEid);
                    }
                });
                addJointSub.addItem(friction);

                MenuItem motor = new MenuItem("Motor");
                motor.addListener(new ClickListener() {
                    @Override
                    public void clicked(InputEvent event, float x, float y) {
                        int jEid = ops.createJoint(PhysicsJointComponent.TYPE_MOTOR, a, b, lastRightClickWorld.x, lastRightClickWorld.y);
                        if (jEid >= 0) selectionService.selectOnly(jEid);
                    }
                });
                addJointSub.addItem(motor);

                MenuItem weld = new MenuItem("Weld");
                weld.addListener(new ClickListener() {
                    @Override
                    public void clicked(InputEvent event, float x, float y) {
                        int jEid = ops.createJoint(PhysicsJointComponent.TYPE_WELD, a, b, lastRightClickWorld.x, lastRightClickWorld.y);
                        if (jEid >= 0) selectionService.selectOnly(jEid);
                    }
                });
                addJointSub.addItem(weld);

                MenuItem pulley = new MenuItem("Pulley");
                pulley.addListener(new ClickListener() {
                    @Override
                    public void clicked(InputEvent event, float x, float y) {
                        int jEid = ops.createJoint(
                                PhysicsJointComponent.TYPE_PULLEY,
                                a,
                                b,
                                lastRightClickWorld.x,
                                lastRightClickWorld.y
                        );
                        if (jEid >= 0) selectionService.selectOnly(jEid);
                    }
                });
                addJointSub.addItem(pulley);

                MenuItem addJoint = new MenuItem("Add joint");
                addJoint.setSubMenu(addJointSub);
                menu.addItem(addJoint);
            }
        }
    }

    private boolean isGearSourceJoint(int entityId) {
        if (entityId < 0 || !mJointBase.has(entityId)) return false;
        PhysicsJointComponent base = mJointBase.getSafe(entityId, null);
        if (base == null) return false;
        return base.type == PhysicsJointComponent.TYPE_REVOLUTE
                || base.type == PhysicsJointComponent.TYPE_PRISMATIC;
    }

    private void showEditMenu() {
        IntArray selection = selectionService.getSelectionSnapshot();
        boolean hasSelection = selection.size > 0;

        boolean isLightLayer = layerService != null
                && layerService.getLayerTypeByIndex(selectionService.getActiveLayerIndex()) == LayerComponent.TYPE_LIGHT;

        if (isLightLayer) return;

        boolean canPaste = clipboardService != null && clipboardService.hasContent();

        MenuItem copy = new MenuItem("Copy");
        copy.setDisabled(!hasSelection);
        copy.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (clipboardService != null) {
                    clipboardService.copySelection();
                }
                event.handle();
            }
        });
        menu.addItem(copy);

        MenuItem cut = new MenuItem("Cut");
        cut.setDisabled(!hasSelection);
        cut.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (clipboardService != null) {
                    clipboardService.cutSelection();
                }
                event.handle();
            }
        });
        menu.addItem(cut);

        MenuItem paste = new MenuItem("Paste");
        paste.setDisabled(!canPaste);
        paste.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (clipboardService != null) {
                    clipboardService.paste();
                }
                event.handle();
            }
        });
        menu.addItem(paste);

        MenuItem createPrefab = new MenuItem("Create prefab from selection");
        createPrefab.setDisabled(!hasSelection);
        createPrefab.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                showCreatePrefabDialog();
                event.handle();
            }
        });
        menu.addItem(createPrefab);

        MenuItem delete = new MenuItem("Delete");
        delete.setDisabled(!hasSelection);
        delete.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                ops.deleteEntities(selectionService.getSelectionSnapshot());
                selectionService.clearSelection();
                event.handle();
            }
        });
        menu.addItem(delete);

        if (hasSelection || canPaste) {
            menu.addSeparator();
        }
    }

    private void showCreatePrefabDialog() {
        VisDialog dialog = new VisDialog("Create Prefab") {
            private final VisTextField nameField = new VisTextField();

            {
                nameField.setMessageText("Prefab name");

                getContentTable().defaults().pad(6).left();
                getContentTable().add(new VisLabel("Name")).left();
                getContentTable().add(nameField).width(220).row();

                getButtonsTable().defaults().pad(8).minWidth(100);
                button("Cancel", false);
                button("Create", true);
            }

            @Override
            protected void result(Object object) {
                if (!Boolean.TRUE.equals(object)) {
                    hide();
                    return;
                }

                createPrefabFromSelection(nameField.getText());
                hide();
            }
        };

        dialog.setModal(true);
        dialog.show(stage);
    }

    private void createPrefabFromSelection(String rawName) {
        String name = sanitizePrefabName(rawName);

        if (name.isEmpty()) {
            Dialogs.showOKDialog(stage, "Create Prefab", "Prefab name is required.");
            return;
        }

        IntArray selection = selectionService.getSelectionSnapshot();
        if (selection == null || selection.size == 0) {
            Dialogs.showOKDialog(stage, "Create Prefab", "No entity selected.");
            return;
        }

        EntityGraph graph = entityGraphCaptureService.capture(selection);
        if (graph == null || graph.isEmpty()) {
            Dialogs.showOKDialog(stage, "Create Prefab", "Selection cannot be saved as a prefab.");
            return;
        }

        FileHandle prefabFile = StudioFs.requirePrefabFile(ProjectConfig.getInstance(), name);

        try {
            prefabAssetService.savePrefab(prefabFile, name, graph);
            PrefabPreviewWriter.writePrefabPreview(
                    StudioFs.requirePrefabPreviewFile(ProjectConfig.getInstance(), name),
                    ProjectConfig.getInstance(),
                    graph
            );
            EventFlow.i().publish(new EventFlow.PrefabsChanged(MY_TAG));
            Gdx.app.log("Prefab", "Created prefab: " + prefabFile.path());
            Dialogs.showOKDialog(stage, "Create Prefab", "Prefab created:\n" + prefabFile.path());
        } catch (RuntimeException ex) {
            Gdx.app.error("Prefab", "Failed to create prefab", ex);
            Dialogs.showOKDialog(stage, "Create Prefab failed", ex.getMessage());
        }
    }

    private static String sanitizePrefabName(String raw) {
        if (raw == null) return "";

        String value = raw.trim().toLowerCase();

        StringBuilder out = new StringBuilder();
        boolean lastDash = false;

        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);

            boolean ok =
                    (c >= 'a' && c <= 'z')
                            || (c >= '0' && c <= '9')
                            || c == '_'
                            || c == '-';

            if (ok) {
                out.append(c);
                lastDash = false;
            } else if (!lastDash) {
                out.append('-');
                lastDash = true;
            }
        }

        while (out.length() > 0 && out.charAt(out.length() - 1) == '-') {
            out.deleteCharAt(out.length() - 1);
        }

        return out.toString();
    }

    private void storeClickWorld(InputEvent event) {
        if (coordSpaces == null || stage == null) {
            lastRightClickWorld.set(0f, 0f);
            return;
        }
        tmpStage.set(event.getStageX(), event.getStageY());
        stage.stageToScreenCoordinates(tmpStage);
        coordSpaces.screenToWorld(tmpStage.x, tmpStage.y, lastRightClickWorld);
    }

    private void storeSpatialPlacementTargetSnapshot() {
        if (spatialBlockSelectionService == null || !spatialBlockSelectionService.isEditingActive()) {
            lastRightClickSpatialTarget = SpatialBlockPlacementTarget.invalid();
            return;
        }

        SpatialBlockPlacementTarget target = spatialBlockSelectionService.getPlacementTarget();
        lastRightClickSpatialTarget = target != null ? target : SpatialBlockPlacementTarget.invalid();
    }

    /**
     * Wheel joints are order-sensitive.
     * <p>
     * Selection convention for Wheel:
     * - first selected body = BodyA = support/chassis
     * - second selected body = BodyB = wheel/rotating body
     * <p>
     * The right-click position only defines the pivot.
     */
    private WheelEndpoints resolveWheelEndpoints(int selA, int selB) {
        if (!isValidPhysicsBody(selA) || !isValidPhysicsBody(selB) || selA == selB) {
            return null;
        }
        return new WheelEndpoints(selA, selB);
    }

    private boolean isValidPhysicsBody(int eid) {
        return eid >= 0 && mBody.has(eid);
    }

    private record WheelEndpoints(int bodyA, int bodyB) {
    }
}
