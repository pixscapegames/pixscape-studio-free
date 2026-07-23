package games.pixscape.studio.system;

import com.artemis.Aspect;
import com.artemis.BaseSystem;
import com.artemis.ComponentMapper;
import com.artemis.EntitySubscription;
import com.artemis.World;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Cursor;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.LongArray;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.component.light.ConeLightComponent;
import games.pixscape.runtime.component.light.PointLightComponent;
import games.pixscape.runtime.component.physics.*;
import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.helper.OrientedBoundsHelper;
import games.pixscape.runtime.render.GeometryDirty;
import games.pixscape.runtime.render.JointDirtyBits;
import games.pixscape.runtime.render.PhysicsDirtyBits;
import games.pixscape.runtime.render.TiledMapRenderState;
import games.pixscape.runtime.service.PhysicsService;
import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import games.pixscape.studio.component.physics.AuthoredPolygonData;
import games.pixscape.studio.component.physics.PhysicsAuthoringComponent;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.configuration.SceneMeta;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.helper.*;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.history.commands.*;
import games.pixscape.studio.input.InputManipulationContext;
import games.pixscape.studio.input.InputState;
import games.pixscape.studio.service.CoordSpaces;
import games.pixscape.studio.service.LayerService;
import games.pixscape.studio.service.SelectionService;
import games.pixscape.studio.service.physics.*;
import games.pixscape.studio.service.spatial.SpatialBlockInteractiveEditSupport;
import games.pixscape.studio.service.spatial.SpatialBlockPicking;
import games.pixscape.studio.service.spatial.SpatialBlockProjection;
import games.pixscape.studio.service.spatial.SpatialBlockSelectionService;
import games.pixscape.studio.service.spatial.SpatialCellPicker;
import games.pixscape.studio.service.spatial.SpatialTileSelectionService;
import games.pixscape.studio.service.spatial.SpatialWallCreationService;
import games.pixscape.studio.service.spatial.SpatialStructureTopology;
import games.pixscape.studio.service.spatial.SpatialWallEditSession;
import games.pixscape.studio.service.spatial.SpatialPointerInteraction;

import java.util.Objects;

public final class PickingSystem extends BaseSystem {

    private final OrthographicCamera worldCam;
    private final CoordSpaces coordSpaces;
    private final InputState inputState;
    private final InputManipulationContext ctx;
    private final HistoryManager historyManager;
    private final HistoryIdRegistry historyIds;
    private final Stage uiStage;
    private final TiledMapRenderState tiledState;

    private SelectionService selectionService;
    private LayerService layerService;
    private PhysicsService physicsService;
    private final PhysicsSelectionService physicsSelectionService;
    private final SpatialBlockSelectionService spatialBlockSelectionService;
    private final SpatialTileSelectionService spatialTileSelectionService;
    private final PolygonDrawSession polygonDrawSession;
    private PhysicsPolygonAuthoringService polygonAuthoringService;
    private PhysicsFixturePickingService fixturePickingService;

    private ComponentMapper<OrientedBoundsComponent> mOBB;
    private ComponentMapper<VisibilityComponent> mVis;
    private ComponentMapper<PhysicsBodyComponent> mPhysBody;
    private ComponentMapper<PhysicsFixturesComponent> mFixDefs;
    private ComponentMapper<TransformComponent> mT;
    private ComponentMapper<DimensionsComponent> mDim;
    private ComponentMapper<EntityIndexComponent> mEntityIndex;
    private ComponentMapper<PhysicsJointComponent> mJointBase;
    private ComponentMapper<PhysicsPulleyJointComponent> mPulley;
    private ComponentMapper<PhysicsGearJointComponent> mGear;
    private ComponentMapper<PointLightComponent> mPointLight;
    private ComponentMapper<ConeLightComponent> mConeLight;
    private ComponentMapper<PhysicsAuthoringComponent> mPhysicsAuthoring;
    private ComponentMapper<SpatialBlocksComponent> mSpatialBlocks;
    private ComponentMapper<TiledLayerComponent> mTiledLayer;

    private DirtyTrackerSystem dirty;
    private GizmoSystem gizmoSystem;

    private final Vector2 tmpMouseWorld = new Vector2();
    private final Vector2 tmpUi = new Vector2();

    private static final float PICK_TOLERANCE_PX = 3f;
    private static final float HOVER_TOLER_PX = 2f;
    private static final float JOINT_PICK_TOL_PX = 6f;
    private static final float LIGHT_ICON_SIZE_PX = 24f;
    private static final float LIGHT_PICK_TOL_PX = 4f;

    private boolean translatingActive = false;
    private final Vector2 oldDrag = new Vector2();

    private final float[] tmpCorners = new float[8];
    private float[] tmpFixtureVerts = new float[8];
    private final float[] tmp2 = new float[2];

    private final LongArray gizmoHistoryIds = new LongArray();
    private final Array<GizmoTransformCommand.Snapshot> gizmoBefore = new Array<>(false, 16);

    private Cursor.SystemCursor lastCursor = Cursor.SystemCursor.Arrow;
    private boolean osCursorHidden = false;

    private Integer lastPressHit = null;
    private boolean lastPressCtrl = false;
    private boolean lastPressOnHandle = false;

    private boolean pressStartedOnSelection = false;
    private boolean jointAnchorDragActive = false;
    private int jointAnchorDragEid = -1;
    private int jointAnchorDragHandle = JointAnchorHandle.NONE;
    private EditJointBaseCommand.Snapshot jointAnchorDragBefore = null;

    private static final class JointAnchorHandle {
        static final int NONE = 0;
        static final int ANCHOR_A = 1;
        static final int ANCHOR_B = 2;
        static final int PIVOT_BOTH = 3;
    }

    private boolean lassoActive = false;
    private final Vector2 lassoStart = new Vector2();
    private final Vector2 lassoEnd = new Vector2();

    // MOVE FIXTURES
    private boolean movingFixtureActive = false;
    private int movingFixtureBodyEid = -1;
    private int movingFixtureId = PhysicsSelectionService.NO_FIXTURE;
    private float movingFixtureBeforeOffsetX = 0f;
    private float movingFixtureBeforeOffsetY = 0f;
    private boolean movingAuthoredPolygonActive = false;
    private long movingAuthoredPolygonId = 0L;
    private float movingAuthoredPolygonBeforeOffsetX = 0f;
    private float movingAuthoredPolygonBeforeOffsetY = 0f;

    // BOX HANDLE AND RESIZING
    private boolean resizingBoxActive = false;
    private int resizingBoxBodyEid = -1;
    private int resizingBoxFixtureId = PhysicsSelectionService.NO_FIXTURE;
    private InputManipulationContext.Handle resizingBoxHandle = InputManipulationContext.Handle.NONE;
    private final float[] tmpFixtureBoxWorldCorners = new float[8];
    private final float[] tmpSpatialBlockTopCorners = new float[8];
    private float resizeBoxBeforeOffsetX = 0f;
    private float resizeBoxBeforeOffsetY = 0f;
    private float resizeBoxBeforeHalfW = 0f;
    private float resizeBoxBeforeHalfH = 0f;

    // POLYGON
    private int hoveredPolygonVertexIndex = -1;
    private boolean lightRadiusDragActive = false;
    private int lightRadiusEntityId = -1;
    private float lightRadiusBefore = 0f;
    private float lightRadiusCurrent = 0f;
    private float lightRotationBeforeRad = 0f;
    private float lightRotationCurrentRad = 0f;
    private boolean lightDragIsCone = false;
    private boolean movingPolygonVertexActive = false;
    private boolean resizingCircleActive = false;
    private int resizingCircleBodyEid = -1;
    private int resizingCircleFixtureId = PhysicsSelectionService.NO_FIXTURE;
    private float resizeCircleBeforeRadius = 0f;
    private float circleRadiusDragCurrent = 0f;
    private int movingPolygonVertexBodyEid = -1;
    private int movingPolygonVertexFixtureId = PhysicsSelectionService.NO_FIXTURE;
    private int movingPolygonVertexIndex = -1;
    private float movingPolygonVertexBeforeX = 0f;
    private float movingPolygonVertexBeforeY = 0f;

    // POLYGON EDITION
    private final Vector2 tmpPolygonLocalPx = new Vector2();
    private boolean movingAuthoredPolygonVertexActive = false;
    private float[] movingAuthoredPolygonBeforeVerts = new float[0];
    private int movingAuthoredPolygonBeforeCount = 0;

    private boolean movingSpatialBlockActive = false;
    private int movingSpatialBlockLayerEid = -1;
    private int movingSpatialBlockId = SpatialBlockSelectionService.NO_BLOCK;
    private float movingSpatialBlockStartTileX;
    private float movingSpatialBlockStartTileY;
    private boolean resizingSpatialBlockActive = false;
    private int resizingSpatialBlockLayerEid = -1;
    private int resizingSpatialBlockId = SpatialBlockSelectionService.NO_BLOCK;
    private int resizingSpatialBlockHandle = SpatialBlockHandle.NONE;
    private boolean spatialTileSelectionActive = false;
    private final SpatialCellPicker.Result spatialCell = new SpatialCellPicker.Result();
    private final SpatialWallEditSession spatialHandleProbe = new SpatialWallEditSession();
    private final SpatialPointerInteraction spatialPointer = new SpatialPointerInteraction();
    private int pressedSpatialBlockId = SpatialBlockSelectionService.NO_BLOCK;
    private int pressedSpatialHandle = SpatialBlockHandle.NONE;
    private int pressedSpatialGx;
    private int pressedSpatialGy;
    private float spatialCreationStartTileX;
    private float spatialCreationStartTileY;

    private static final class SpatialBlockHandle {
        static final int NONE = -1;
        static final int TOP = 0;
        static final int RIGHT = 1;
        static final int BOTTOM = 2;
        static final int LEFT = 3;
        static final int TOP_CORNER = 4;
        static final int RIGHT_CORNER = 5;
        static final int BOTTOM_CORNER = 6;
        static final int LEFT_CORNER = 7;
        static final int HEIGHT = 8;
    }

    public static float SNAP_STEP_RAD =  MathUtils.degreesToRadians;
    private final int MY_TAG = EventFlow.tag(this);

    private final Vector2 tmpA = new Vector2();
    private final Vector2 tmpB = new Vector2();
    private final Vector2 tmp2Vec = new Vector2();

    public PickingSystem(OrthographicCamera worldCam,
                         CoordSpaces coordSpaces,
                         InputState inputState,
                         HistoryManager historyManager,
                         HistoryIdRegistry historyIds,
                         Stage uiStage,
                         TiledMapRenderState tiledState,
                         PhysicsSelectionService physicsSelectionService,
                         SpatialBlockSelectionService spatialBlockSelectionService,
                         SpatialTileSelectionService spatialTileSelectionService,
                         PolygonDrawSession polygonDrawSession) {
        this.worldCam = worldCam;
        this.coordSpaces = coordSpaces;
        this.inputState = inputState;
        this.ctx = new InputManipulationContext();
        this.historyManager = historyManager;
        this.historyIds = historyIds;
        this.uiStage = uiStage;
        this.tiledState = tiledState;
        this.physicsSelectionService = physicsSelectionService;
        this.spatialBlockSelectionService = spatialBlockSelectionService;
        this.spatialTileSelectionService = spatialTileSelectionService;
        this.polygonDrawSession = polygonDrawSession;
        EventFlow.i().subscribe(EventFlow.FixtureSelectionCleared.class,
                event -> clearFixtureEditingState());
    }

    private void clearFixtureEditingState() {
        clearFixtureMoveState();
        clearBoxResizeState();
        clearCircleResizeState();
        clearPolygonVertexMoveState();
        clearHoveredPolygonVertex();
        if (polygonDrawSession != null && polygonDrawSession.isActive()) {
            polygonDrawSession.cancel();
        }
    }

    public void setSelectionService(SelectionService selectionService) {
        this.selectionService = selectionService;
    }

    public void setLayerService(LayerService layerService) {
        this.layerService = layerService;
    }

    public void setPhysicsService(PhysicsService physicsService) {
        this.physicsService = physicsService;
        this.fixturePickingService = new PhysicsFixturePickingService(physicsService);
    }

    @Override
    protected void processSystem() {
        readMouseWorld(tmpMouseWorld);
        float mx = tmpMouseWorld.x;
        float my = tmpMouseWorld.y;

        if (processPolygonDrawMode(mx, my)) {
            updateCursorForHover(
                    InputManipulationContext.Handle.NONE,
                    false,
                    ctx.mode(),
                    InputManipulationContext.Handle.NONE
            );
            return;
        }

        if (isPointerOverUI()) {
            cancelLassoIfNeeded();
            if (selectionService != null) selectionService.clearHoveredEntity();
            if (spatialBlockSelectionService != null) spatialBlockSelectionService.clearHover();
            if (spatialTileSelectionService != null) spatialTileSelectionService.clearHover();
            return;
        }

        boolean leftPressed = inputState.leftJustPressed();
        boolean leftDown = inputState.isLeftDown() || Gdx.input.isButtonPressed(Input.Buttons.LEFT);
        boolean leftReleased = inputState.leftJustReleased();

        if (isSpatialBlockModeActive()) {
            cancelLassoIfNeeded();
            if (selectionService != null) selectionService.clearHoveredEntity();
            if (processSpatialEscape()) {
                updateCursorForHover(
                        InputManipulationContext.Handle.NONE,
                        false,
                        InputManipulationContext.Mode.IDLE,
                        InputManipulationContext.Handle.NONE
                );
                return;
            }
            int spatialHandle = processSpatialBlockMode(mx, my, leftPressed, leftDown, leftReleased);
            InputManipulationContext.Handle cursorHandle = spatialBlockCursorHandle(
                    resizingSpatialBlockActive ? resizingSpatialBlockHandle : spatialHandle
            );
            updateCursorForHover(
                    cursorHandle,
                    resizingSpatialBlockActive,
                    resizingSpatialBlockActive ? InputManipulationContext.Mode.HANDLE_RESIZE : InputManipulationContext.Mode.IDLE,
                    cursorHandle
            );
            return;
        }

        if (isTiledModeActive()) {
            cancelLassoIfNeeded();
            if (selectionService != null) selectionService.clearHoveredEntity();
            updateCursorForHover(
                    InputManipulationContext.Handle.NONE,
                    false,
                    ctx.mode(),
                    InputManipulationContext.Handle.NONE
            );
            return;
        }

        if (lightRadiusDragActive) {
            onLightRadiusDragging(mx, my, leftDown, leftReleased);
            return;
        }

        IntArray currentSel = selectionService.getSelectionSnapshot();
        IntArray viewportSel = filterSelectableInViewport(currentSel);

        syncPhysicsSelectionState();

        boolean physicsEditMode = isExplicitPhysicsEditMode();
        boolean singleSel = viewportSel.size == 1;
        if (leftPressed && singleSel && tryBeginLightRadiusDrag(mx, my, viewportSel.get(0))) {
            lastPressHit = null;
            return;
        }

        InputManipulationContext.Handle hovered = resolveHoveredHandle(mx, my, viewportSel);

        ctx.setHovered(hovered);
        updateHoveredPhysics(mx, my, hovered, viewportSel);
        updateHoveredEntity(mx, my, hovered, viewportSel, physicsEditMode);
        updateCursorForHover(hovered, ctx.isDragging(), ctx.mode(), ctx.activeHandle());

        if (leftPressed) {
            onLeftPress(mx, my, hovered, viewportSel);
        }

        if (movingPolygonVertexActive) {
            if (leftDown) {
                onPolygonVertexDragging(mx, my);
            }
            if (leftReleased) {
                onPolygonVertexReleased();
                resetPressState();
            }
            return;
        }
        if (jointAnchorDragActive) {
            if (leftDown) {
                onJointAnchorDragging(mx, my);
            }
            if (leftReleased) {
                onJointAnchorDragReleased();
                resetPressState();
            }
            return;
        }

        if (resizingBoxActive) {
            if (leftDown) {
                onBoxResizeDragging(mx, my);
            }
            if (leftReleased) {
                onBoxResizeReleased();
                resetPressState();
            }
            return;
        }
        if (resizingCircleActive) {
            if (leftDown) onCircleResizeDragging(mx, my);
            if (leftReleased) {
                onCircleResizeReleased();
                resetPressState();
            }
            return;
        }

        if (movingFixtureActive) {
            if (leftDown) {
                onFixtureMoveDragging(mx, my);
            }
            if (leftReleased) {
                onFixtureMoveReleased();
                resetPressState();
            }
            return;
        }

        if (ctx.isDragging()) {
            onGizmoDragging(mx, my, singleSel ? viewportSel.get(0) : -1, leftReleased);
            return;
        }

        if (leftDown && lassoActive) {
            onLassoDragging(mx, my);
        }

        boolean didMoveThisRelease = false;
        if (leftDown) {
            didMoveThisRelease = onFreeMoveDragging(mx, my, viewportSel);
        }
        if (leftReleased) {
            didMoveThisRelease |= onFreeMoveReleased(viewportSel);
        }

        if (leftReleased && lassoActive) {
            onLassoReleased();
            resetPressState();
            return;
        }

        if (leftReleased && !didMoveThisRelease && !lastPressOnHandle) {
            onClickReleased();
        }

        if (leftReleased) {
            resetPressState();
        }
    }

    private boolean isPointerOverUI() {
        if (uiStage == null) return false;

        tmpUi.set(Gdx.input.getX(), Gdx.input.getY());
        uiStage.screenToStageCoordinates(tmpUi);

        Actor hit = uiStage.hit(tmpUi.x, tmpUi.y, true);

        return hit != null && hit != uiStage.getRoot();
    }

    private boolean isTiledModeActive() {
        SceneMeta meta = currentSceneMeta();
        if (meta == null) return false;
        if (!meta.tiledEnabled) return false;
        if (meta.editorMode != SceneMeta.EditorMode.TILE) return false;
        if (selectionService == null) return false;

        int layerEntity = selectionService.getActivelayerId();
        if (layerEntity == -1) return false;

        LayerComponent lc = world.getMapper(LayerComponent.class).getSafe(layerEntity, null);
        return lc != null && lc.type == LayerComponent.TYPE_TILED;
    }

    private boolean isSpatialBlockModeActive() {
        return spatialBlockSelectionService != null && spatialBlockSelectionService.isEditingActive();
    }

    private int processSpatialBlockMode(float mx,
                                        float my,
                                        boolean leftPressed,
                                        boolean leftDown,
                                        boolean leftReleased) {
        int layerEntityId = spatialBlockSelectionService.getEditingLayerEntityId();

        if (resizingSpatialBlockActive) {
            if (leftDown) {
                onSpatialBlockResizeDragging(mx, my);
            }
            if (leftReleased || !leftDown) {
                onSpatialBlockResizeReleased();
                spatialPointer.release();
            }
            return resizingSpatialBlockHandle;
        }

        if (movingSpatialBlockActive) {
            if (leftDown) {
                onSpatialBlockMoveDragging(mx, my);
            }
            if (leftReleased || !leftDown) {
                onSpatialBlockMoveReleased();
                spatialPointer.release();
            }
            return SpatialBlockHandle.NONE;
        }

        if (spatialTileSelectionActive) {
            if (leftDown) {
                updateSpatialTileSelection(layerEntityId, mx, my);
            }
            if (leftReleased || !leftDown) {
                finishSpatialTileSelection();
                spatialPointer.release();
            }
            return SpatialBlockHandle.NONE;
        }

        SpatialPointerTarget target = resolveSpatialPointerTarget(layerEntityId, mx, my);
        int hoveredHandle = target.handle;
        spatialPointer.hover(target.type);
        spatialBlockSelectionService.setHoveredBlock(target.blockId);
        if (target.type == SpatialPointerInteraction.Target.SELECTED_HANDLE) {
            spatialBlockSelectionService.setHoveredHandle(
                    spatialBlockResizeHandle(target.handle),
                    target.handle == SpatialBlockHandle.HEIGHT);
        } else {
            spatialBlockSelectionService.clearHoveredHandle();
        }
        if (spatialTileSelectionService != null) {
            if (target.type == SpatialPointerInteraction.Target.OCCUPIED_TILE && !leftDown) {
                spatialTileSelectionService.setHover(layerEntityId, target.gx, target.gy);
            } else {
                spatialTileSelectionService.clearHover();
            }
        }

        if (leftPressed) {
            if (SpatialPointerInteraction.clearsWallSelection(target.type)) {
                spatialBlockSelectionService.clearSelectionOnly();
            }
            spatialPointer.press(target.type, Gdx.input.getX(), Gdx.input.getY());
            pressedSpatialBlockId = target.blockId;
            pressedSpatialHandle = target.handle;
            pressedSpatialGx = target.gx;
            pressedSpatialGy = target.gy;
            if (target.blockId > 0) {
                selectionService.clearSelection();
                physicsSelectionService.clear();
                if (spatialTileSelectionService != null) spatialTileSelectionService.clear();
                spatialBlockSelectionService.selectBlock(layerEntityId, target.blockId);
            }
        }

        if (leftDown && spatialPointer.crossedDragThreshold(Gdx.input.getX(), Gdx.input.getY())) {
            if (spatialPointer.target() == SpatialPointerInteraction.Target.SELECTED_HANDLE) {
                selectionService.clearSelection();
                physicsSelectionService.clear();
                beginSpatialBlockResize(layerEntityId, pressedSpatialBlockId, pressedSpatialHandle, mx, my);
                if (resizingSpatialBlockActive) spatialPointer.beginResize();
            } else if (spatialPointer.target() == SpatialPointerInteraction.Target.SELECTED_FOOTPRINT) {
                beginSpatialBlockMove(layerEntityId, pressedSpatialBlockId, mx, my);
                if (movingSpatialBlockActive) {
                    spatialPointer.beginMove(spatialBlockSelectionService.wallEditSession().isSlidingAttachedWall());
                }
            } else if (spatialPointer.target() == SpatialPointerInteraction.Target.OCCUPIED_TILE) {
                beginSpatialTileSelection(layerEntityId, pressedSpatialGx, pressedSpatialGy, mx, my);
                if (spatialTileSelectionActive) spatialPointer.beginCreation();
            }
        }

        if (leftReleased) {
            spatialPointer.release();
            pressedSpatialBlockId = SpatialBlockSelectionService.NO_BLOCK;
            pressedSpatialHandle = SpatialBlockHandle.NONE;
        }
        return hoveredHandle;
    }

    private boolean processSpatialEscape() {
        if (!Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) return false;
        if (uiStage != null && uiStage.getKeyboardFocus() != null) return false;
        if (resizingSpatialBlockActive) {
            spatialBlockSelectionService.wallEditSession().cancel();
            clearSpatialBlockResizeState();
        } else if (movingSpatialBlockActive) {
            spatialBlockSelectionService.wallEditSession().cancel();
            clearSpatialBlockMoveState();
        } else if (spatialTileSelectionActive) {
            spatialTileSelectionActive = false;
            if (spatialTileSelectionService != null) spatialTileSelectionService.clear();
        } else {
            spatialBlockSelectionService.clearSelectionOnly();
        }
        spatialPointer.release();
        pressedSpatialBlockId = SpatialBlockSelectionService.NO_BLOCK;
        pressedSpatialHandle = SpatialBlockHandle.NONE;
        return true;
    }

    private SpatialPointerTarget resolveSpatialPointerTarget(int layerEntityId, float mx, float my) {
        int handle = detectSelectedSpatialBlockTopHandle(layerEntityId, mx, my);
        if (handle != SpatialBlockHandle.NONE) {
            return SpatialPointerTarget.handle(spatialBlockSelectionService.getSelectedBlockId(), handle);
        }
        SpatialBlockData selected = findSpatialBlock(layerEntityId, spatialBlockSelectionService.getSelectedBlockId());
        TiledLayerComponent tiled = mTiledLayer.getSafe(layerEntityId, null);
        if (selected != null && tiled != null && tiled.data != null
                && SpatialBlockPicking.containsBase(tiled.data, selected, mx, my, tmpFixtureBoxWorldCorners)) {
            return SpatialPointerTarget.wall(SpatialPointerInteraction.Target.SELECTED_FOOTPRINT, selected.id);
        }
        SpatialBlockHit wall = findTopmostSpatialBlockHit(layerEntityId, mx, my);
        if (wall != null) {
            return SpatialPointerTarget.wall(wall.blockId == spatialBlockSelectionService.getSelectedBlockId()
                    ? SpatialPointerInteraction.Target.SELECTED_WALL
                    : SpatialPointerInteraction.Target.OTHER_WALL, wall.blockId);
        }
        if (tiled != null && tiled.data != null
                && SpatialCellPicker.pickForSpatialSelection(tiled.data, tiledState, mx, my, spatialCell)
                && tiled.data.getTile(spatialCell.gx, spatialCell.gy) > 0) {
            return SpatialPointerTarget.tile(spatialCell.gx, spatialCell.gy);
        }
        return SpatialPointerTarget.none();
    }

    private void beginSpatialTileSelection(int layerEntityId, int gx, int gy, float mx, float my) {
        if (spatialTileSelectionService == null) return;
        TiledLayerComponent tiled = mTiledLayer.getSafe(layerEntityId, null);
        if (tiled == null || tiled.data == null || tiled.data.getTile(gx, gy) <= 0) return;

        selectionService.clearSelection();
        physicsSelectionService.clear();
        spatialTileSelectionService.beginDrag(layerEntityId, gx, gy);
        spatialCreationStartTileX = tiled.data.projectWorldToTileX(mx, my);
        spatialCreationStartTileY = tiled.data.projectWorldToTileY(mx, my);
        spatialTileSelectionActive = true;
    }

    private static final class SpatialPointerTarget {
        final SpatialPointerInteraction.Target type;
        final int blockId;
        final int handle;
        final int gx;
        final int gy;

        private SpatialPointerTarget(SpatialPointerInteraction.Target type, int blockId, int handle, int gx, int gy) {
            this.type = type;
            this.blockId = blockId;
            this.handle = handle;
            this.gx = gx;
            this.gy = gy;
        }
        static SpatialPointerTarget none() { return new SpatialPointerTarget(SpatialPointerInteraction.Target.NONE, -1, -1, 0, 0); }
        static SpatialPointerTarget handle(int blockId, int handle) { return new SpatialPointerTarget(SpatialPointerInteraction.Target.SELECTED_HANDLE, blockId, handle, 0, 0); }
        static SpatialPointerTarget wall(SpatialPointerInteraction.Target type, int blockId) { return new SpatialPointerTarget(type, blockId, -1, 0, 0); }
        static SpatialPointerTarget tile(int gx, int gy) { return new SpatialPointerTarget(SpatialPointerInteraction.Target.OCCUPIED_TILE, -1, -1, gx, gy); }
    }

    private void updateSpatialTileSelection(int layerEntityId, float mx, float my) {
        if (spatialTileSelectionService == null) return;
        TiledLayerComponent tiled = mTiledLayer.getSafe(layerEntityId, null);
        if (tiled == null || tiled.data == null) return;

        if (SpatialCellPicker.pickForSpatialSelection(tiled.data, tiledState, mx, my, spatialCell)) {
            spatialTileSelectionService.updateDrag(spatialCell.gx, spatialCell.gy);
            spatialTileSelectionService.updateGesture(
                    tiled.data.projectWorldToTileX(mx, my) - spatialCreationStartTileX,
                    tiled.data.projectWorldToTileY(mx, my) - spatialCreationStartTileY);
        } else {
            spatialTileSelectionService.updateDragOutsideMap();
        }
    }

    private void finishSpatialTileSelection() {
        spatialTileSelectionActive = false;
        if (spatialTileSelectionService != null) {
            spatialTileSelectionService.finishDrag();
            SpatialWallCreationService.executeSelectedRectangle(
                    world, historyManager, spatialBlockSelectionService, spatialTileSelectionService);
        }
    }

    private static InputManipulationContext.Handle spatialBlockCursorHandle(int handle) {
        return switch (handle) {
            case SpatialBlockHandle.TOP -> InputManipulationContext.Handle.N;
            case SpatialBlockHandle.RIGHT -> InputManipulationContext.Handle.E;
            case SpatialBlockHandle.BOTTOM -> InputManipulationContext.Handle.S;
            case SpatialBlockHandle.LEFT -> InputManipulationContext.Handle.W;
            case SpatialBlockHandle.TOP_CORNER -> InputManipulationContext.Handle.NW;
            case SpatialBlockHandle.RIGHT_CORNER -> InputManipulationContext.Handle.NE;
            case SpatialBlockHandle.BOTTOM_CORNER -> InputManipulationContext.Handle.SE;
            case SpatialBlockHandle.LEFT_CORNER -> InputManipulationContext.Handle.SW;
            case SpatialBlockHandle.HEIGHT -> InputManipulationContext.Handle.N;
            default -> InputManipulationContext.Handle.NONE;
        };
    }

    private void beginSpatialBlockMove(int layerEntityId, int blockId, float mx, float my) {
        SpatialBlockData block = findSpatialBlock(layerEntityId, blockId);
        if (block == null) return;
        TiledLayerComponent tiled = mTiledLayer.getSafe(layerEntityId, null);
        SpatialBlocksComponent blocks = mSpatialBlocks.getSafe(layerEntityId, null);
        if (tiled == null || tiled.data == null
                || !spatialBlockSelectionService.wallEditSession().begin(
                layerEntityId, blockId, blocks, tiled.data)) return;
        if (!spatialBlockSelectionService.wallEditSession().canMove()) {
            spatialBlockSelectionService.wallEditSession().cancel();
            return;
        }
        movingSpatialBlockActive = true;
        movingSpatialBlockLayerEid = layerEntityId;
        movingSpatialBlockId = blockId;
        movingSpatialBlockStartTileX = tiled.data.projectWorldToTileX(mx, my);
        movingSpatialBlockStartTileY = tiled.data.projectWorldToTileY(mx, my);
        spatialBlockSelectionService.setEditPreview(true);
        oldDrag.set(mx, my);
    }

    private void beginSpatialBlockResize(int layerEntityId, int blockId, int handle, float mx, float my) {
        SpatialBlockData block = findSpatialBlock(layerEntityId, blockId);
        if (block == null || handle == SpatialBlockHandle.NONE) return;
        TiledLayerComponent tiled = mTiledLayer.getSafe(layerEntityId, null);
        SpatialBlocksComponent blocks = mSpatialBlocks.getSafe(layerEntityId, null);
        if (tiled == null || tiled.data == null
                || !spatialBlockSelectionService.wallEditSession().begin(
                layerEntityId, blockId, blocks, tiled.data)) return;
        SpatialBlockInteractiveEditSupport.ResizeHandle resizeHandle = spatialBlockResizeHandle(handle);
        if (handle != SpatialBlockHandle.HEIGHT
                && !spatialBlockSelectionService.wallEditSession().isHandleEnabled(resizeHandle)) {
            spatialBlockSelectionService.wallEditSession().cancel();
            return;
        }
        resizingSpatialBlockActive = true;
        resizingSpatialBlockLayerEid = layerEntityId;
        resizingSpatialBlockId = blockId;
        resizingSpatialBlockHandle = handle;
        spatialBlockSelectionService.setEditPreview(true);
        ctx.beginResize(spatialBlockCursorHandle(handle), mx, my, block.width, block.depth);
        oldDrag.set(mx, my);
    }

    private void onSpatialBlockMoveDragging(float mx, float my) {
        TiledLayerComponent tiled = mTiledLayer.getSafe(movingSpatialBlockLayerEid, null);
        if (tiled == null || tiled.data == null) return;

        float afterTileX = tiled.data.projectWorldToTileX(mx, my);
        float afterTileY = tiled.data.projectWorldToTileY(mx, my);

        spatialBlockSelectionService.wallEditSession().updateMove(
                afterTileX - movingSpatialBlockStartTileX,
                afterTileY - movingSpatialBlockStartTileY);
        spatialBlockSelectionService.setEditPreview(
                spatialBlockSelectionService.wallEditSession().isCandidateValid());
        oldDrag.set(mx, my);
    }

    private void onSpatialBlockMoveReleased() {
        spatialBlockSelectionService.wallEditSession().commit(
                world, historyManager, spatialBlockSelectionService);
        clearSpatialBlockMoveState();
    }

    private void onSpatialBlockResizeDragging(float mx, float my) {
        TiledLayerComponent tiled = mTiledLayer.getSafe(resizingSpatialBlockLayerEid, null);
        SpatialBlockData preview = spatialBlockSelectionService.wallEditSession().candidate();
        if (preview == null || tiled == null || tiled.data == null) return;

        float heightDelta = my - oldDrag.y;

        if (resizingSpatialBlockHandle == SpatialBlockHandle.HEIGHT) {
            spatialBlockSelectionService.wallEditSession().updateHeight(
                    Math.max(0f, preview.height + heightDelta));
            spatialBlockSelectionService.setEditPreview(
                    spatialBlockSelectionService.wallEditSession().isCandidateValid());
            oldDrag.set(mx, my);
            return;
        }

        SpatialBlockProjection.footprintWorldToTileLocal(
                tiled.data,
                mx,
                my,
                preview.altitude,
                tmpA
        );
        SpatialBlockInteractiveEditSupport.ResizeHandle handle = spatialBlockResizeHandle(resizingSpatialBlockHandle);
        spatialBlockSelectionService.wallEditSession().updateResize(handle, tmpA.x, tmpA.y);
        spatialBlockSelectionService.setEditPreview(
                spatialBlockSelectionService.wallEditSession().isCandidateValid());

        oldDrag.set(mx, my);
    }

    private static SpatialBlockInteractiveEditSupport.ResizeHandle spatialBlockResizeHandle(int handle) {
        return switch (handle) {
            case SpatialBlockHandle.TOP -> SpatialBlockInteractiveEditSupport.ResizeHandle.MIN_Y;
            case SpatialBlockHandle.RIGHT -> SpatialBlockInteractiveEditSupport.ResizeHandle.MAX_X;
            case SpatialBlockHandle.BOTTOM -> SpatialBlockInteractiveEditSupport.ResizeHandle.MAX_Y;
            case SpatialBlockHandle.LEFT -> SpatialBlockInteractiveEditSupport.ResizeHandle.MIN_X;
            case SpatialBlockHandle.TOP_CORNER -> SpatialBlockInteractiveEditSupport.ResizeHandle.MIN_X_MIN_Y;
            case SpatialBlockHandle.RIGHT_CORNER -> SpatialBlockInteractiveEditSupport.ResizeHandle.MAX_X_MIN_Y;
            case SpatialBlockHandle.BOTTOM_CORNER -> SpatialBlockInteractiveEditSupport.ResizeHandle.MAX_X_MAX_Y;
            case SpatialBlockHandle.LEFT_CORNER -> SpatialBlockInteractiveEditSupport.ResizeHandle.MIN_X_MAX_Y;
            default -> null;
        };
    }

    private void onSpatialBlockResizeReleased() {
        spatialBlockSelectionService.wallEditSession().commit(
                world, historyManager, spatialBlockSelectionService);
        clearSpatialBlockResizeState();
    }

    private void clearSpatialBlockResizeState() {
        resizingSpatialBlockActive = false;
        resizingSpatialBlockLayerEid = -1;
        resizingSpatialBlockId = SpatialBlockSelectionService.NO_BLOCK;
        resizingSpatialBlockHandle = SpatialBlockHandle.NONE;
        spatialBlockSelectionService.clearEditPreview();
        ctx.end();
    }

    private void clearSpatialBlockMoveState() {
        movingSpatialBlockActive = false;
        movingSpatialBlockLayerEid = -1;
        movingSpatialBlockId = SpatialBlockSelectionService.NO_BLOCK;
        movingSpatialBlockStartTileX = 0f;
        movingSpatialBlockStartTileY = 0f;
        spatialBlockSelectionService.clearEditPreview();
    }

    private SpatialBlockData findSpatialBlock(int layerEntityId, int blockId) {
        SpatialBlocksComponent component = mSpatialBlocks.getSafe(layerEntityId, null);
        if (component == null || component.blocks == null || blockId <= 0) return null;
        for (int i = 0, n = component.blocks.size; i < n; i++) {
            SpatialBlockData block = component.blocks.get(i);
            if (block != null && block.id == blockId) return block;
        }
        return null;
    }

    private SpatialBlockHit findTopmostSpatialBlockHit(int layerEntityId, float mouseX, float mouseY) {
        SpatialBlocksComponent component = mSpatialBlocks.getSafe(layerEntityId, null);
        TiledLayerComponent tiled = mTiledLayer.getSafe(layerEntityId, null);
        if (component == null || component.blocks == null || tiled == null || tiled.data == null) return null;

        int blockId = SpatialBlockPicking.find(
                component, tiled.data, spatialBlockSelectionService.getSelectedBlockId(),
                mouseX, mouseY, tmpFixtureBoxWorldCorners, tmpSpatialBlockTopCorners);
        return blockId > 0 ? new SpatialBlockHit(blockId) : null;
    }

    private int detectSelectedSpatialBlockTopHandle(int layerEntityId, float mouseX, float mouseY) {
        int selectedBlockId = spatialBlockSelectionService.getSelectedBlockId();
        if (selectedBlockId <= 0) return SpatialBlockHandle.NONE;

        SpatialBlockData block = findSpatialBlock(layerEntityId, selectedBlockId);
        TiledLayerComponent tiled = mTiledLayer.getSafe(layerEntityId, null);
        if (block == null || tiled == null || tiled.data == null) return SpatialBlockHandle.NONE;

        float[] verts = tmpFixtureBoxWorldCorners;
        SpatialBlockProjection.projectTopFootprint(tiled.data, block, verts);

        float halfWorld = HandleHelper.pxToWorld(
                worldCam,
                GizmoDrawHelper.SHAPE_VERTEX_HANDLE_SIZE_PX * 0.5f + HOVER_TOLER_PX
        );

        float topCx = (verts[0] + verts[2] + verts[4] + verts[6]) * 0.25f;
        float topCy = (verts[1] + verts[3] + verts[5] + verts[7]) * 0.25f;
        if (HandleHelper.insideSquare(mouseX, mouseY, topCx, topCy, halfWorld)) {
            return SpatialBlockHandle.HEIGHT;
        }

        SpatialBlockProjection.projectBaseFootprint(tiled.data, block, verts);
        float bestDist2 = Float.POSITIVE_INFINITY;
        int bestHandle = SpatialBlockHandle.NONE;

        for (int i = 0; i < 4; i++) {
            float vx = verts[i * 2];
            float vy = verts[i * 2 + 1];
            if (!HandleHelper.insideSquare(mouseX, mouseY, vx, vy, halfWorld)) continue;
            float d2 = dst2(mouseX, mouseY, vx, vy);
            if (d2 < bestDist2) {
                bestDist2 = d2;
                bestHandle = SpatialBlockHandle.TOP_CORNER + i;
            }
        }
        if (bestHandle != SpatialBlockHandle.NONE) {
            return isSpatialBlockHandleAvailable(layerEntityId, block, tiled.data, bestHandle)
                    ? bestHandle : SpatialBlockHandle.NONE;
        }

        for (int i = 0; i < 4; i++) {
            int next = (i + 1) & 3;
            float vx = (verts[i * 2] + verts[next * 2]) * 0.5f;
            float vy = (verts[i * 2 + 1] + verts[next * 2 + 1]) * 0.5f;
            if (!HandleHelper.insideSquare(mouseX, mouseY, vx, vy, halfWorld)) continue;

            float d2 = dst2(mouseX, mouseY, vx, vy);
            if (d2 < bestDist2) {
                bestDist2 = d2;
                bestHandle = i;
            }
        }

        return bestHandle == SpatialBlockHandle.NONE
                || isSpatialBlockHandleAvailable(layerEntityId, block, tiled.data, bestHandle)
                ? bestHandle : SpatialBlockHandle.NONE;
    }

    private boolean isSpatialBlockHandleAvailable(int layerEntityId,
                                                  SpatialBlockData block,
                                                  TiledMapLayerData map,
                                                  int handle) {
        if (handle == SpatialBlockHandle.HEIGHT) return true;
        SpatialBlocksComponent component = mSpatialBlocks.getSafe(layerEntityId, null);
        SpatialBlockInteractiveEditSupport.ResizeHandle resizeHandle = spatialBlockResizeHandle(handle);
        boolean available = spatialHandleProbe.begin(layerEntityId, block.id, component, map)
                && spatialHandleProbe.isHandleEnabled(resizeHandle);
        spatialHandleProbe.cancel();
        return available;
    }

    private record SpatialBlockHit(int blockId) {
    }

    private void readMouseWorld(Vector2 out) {
        coordSpaces.screenToWorld(Gdx.input.getX(), Gdx.input.getY(), out);
    }

    private void cancelLassoIfNeeded() {
        if (lassoActive && gizmoSystem != null) {
            lassoActive = false;
            gizmoSystem.setLassoRect(false, 0f, 0f, 0f, 0f);
        }
    }

    private void resetPressState() {
        lastPressHit = null;
        lastPressOnHandle = false;
        pressStartedOnSelection = false;
        oldDrag.setZero();
        ctx.setHovered(InputManipulationContext.Handle.NONE);
        if (!jointAnchorDragActive) {
            jointAnchorDragEid = -1;
            jointAnchorDragHandle = JointAnchorHandle.NONE;
            jointAnchorDragBefore = null;
        }
    }

    private void onLeftPress(float mx, float my,
                             InputManipulationContext.Handle hovered,
                             IntArray viewportSel) {

        oldDrag.set(mx, my);
        pressStartedOnSelection = false;

        if (lassoActive && gizmoSystem != null) {
            gizmoSystem.setLassoRect(false, 0f, 0f, 0f, 0f);
        }
        lassoActive = false;

        boolean physicsEditMode = isExplicitPhysicsEditMode();
        boolean singleSel = viewportSel.size == 1;
        lastPressOnHandle = (!physicsEditMode && singleSel && hovered != InputManipulationContext.Handle.NONE);
        lastPressCtrl = inputState.isCtrl();
        if (lastPressOnHandle) {
            beginHandleDragIfPossible(mx, my, hovered, viewportSel);
            return;
        }
        if (tryBeginPolygonVertexMove(mx, my)) {
            lastPressHit = null;
            return;
        }
        if (tryBeginBoxResize(mx, my)) {
            lastPressHit = null;
            return;
        }
        if (tryBeginCircleResize(mx, my)) {
            lastPressHit = null;
            return;
        }
        if (tryBeginJointAnchorDrag(mx, my)) {
            lastPressHit = null;
            return;
        }
        if (tryBeginFixtureMove(mx, my)) {
            lastPressHit = null;
            return;
        }

        Integer specialHit = findTopmostJointHit(mx, my);
        if (!physicsEditMode && (specialHit == null || specialHit < 0)) {
            specialHit = findTopmostLightHit(mx, my);
        }

        boolean fixtureHit = false;

        if (specialHit != null && specialHit >= 0) {
            physicsSelectionService.clearSelectionOnly();
            lastPressHit = specialHit;
        } else {
            fixtureHit = tryPickVisibleFixture(mx, my);
            if (!fixtureHit) {
                physicsSelectionService.clearSelectionOnly();
                lastPressHit = physicsEditMode ? null : findTopmostObbHit(mx, my);
            }
        }

        if (!lastPressCtrl) {
            if (lastPressHit != null && lastPressHit >= 0) {
                boolean hitInSelection = isInSelection(viewportSel, lastPressHit);
                if (!hitInSelection) {
                    activateLayerForEntity(lastPressHit);
                    selectionService.selectOnly(lastPressHit);
                }
                pressStartedOnSelection = true;
            } else {
                if (physicsEditMode) {
                    physicsSelectionService.clear();
                    selectionService.clearSelection();
                    EventFlow.i().publish(new EventFlow.FixtureSelectionCleared(MY_TAG));
                    pressStartedOnSelection = false;
                    return;
                }

                selectionService.clearSelection();
                physicsSelectionService.clearSelectionOnly();
                pressStartedOnSelection = false;

                lassoActive = true;
                lassoStart.set(mx, my);
                lassoEnd.set(mx, my);
                if (gizmoSystem != null) {
                    gizmoSystem.setLassoRect(true, lassoStart.x, lassoStart.y, lassoEnd.x, lassoEnd.y);
                }
            }
        } else {
            if (lastPressHit != null && lastPressHit >= 0) {
                pressStartedOnSelection = isInSelection(viewportSel, lastPressHit);
            } else {
                pressStartedOnSelection = false;
            }
        }
    }

    private void activateLayerForEntity(int eid) {
        if (selectionService == null || layerService == null || mEntityIndex == null) return;

        EntityIndexComponent idx = mEntityIndex.getSafe(eid, null);
        if (idx == null) return;

        int layerEntity = layerService.getLayerEntity(idx.getLayerIndex());
        if (layerEntity != -1 && layerEntity != selectionService.getActivelayerId()) {
            selectionService.setActivelayerId(layerEntity);
        }
    }

    private void syncPhysicsSelectionState() {
        if (physicsService == null) {
            physicsSelectionService.clear();
            return;
        }

        int focusedBodyEid = physicsSelectionService.getFocusedBodyEid();
        if (focusedBodyEid < 0) {
            physicsSelectionService.clearHover();
            return;
        }

        if (!world.getEntityManager().isActive(focusedBodyEid)
                || !isSelectableInViewport(focusedBodyEid)
                || !mPhysBody.has(focusedBodyEid)) {
            boolean hadSelectedFixture = physicsSelectionService.hasSelectedFixture();
            physicsSelectionService.clear();
            if (hadSelectedFixture) {
                EventFlow.i().publish(new EventFlow.FixtureSelectionCleared(MY_TAG));
            }
            return;
        }

        int selectedFixtureId = physicsSelectionService.getSelectedFixtureId();
        if (selectedFixtureId > 0
                && physicsService.getFixtureById(focusedBodyEid, selectedFixtureId) == null) {
            physicsSelectionService.clearSelectedFixtureIfMatches(
                    focusedBodyEid, selectedFixtureId);
        }
    }

    private void updateHoveredPhysics(float mx,
                                      float my,
                                      InputManipulationContext.Handle hoveredHandle,
                                      IntArray viewportSel) {
        if (hoveredHandle != InputManipulationContext.Handle.NONE || ctx.isDragging()) {
            clearPhysicsHover();
            return;
        }

        Integer jointHit = findTopmostJointHit(mx, my);
        if (jointHit != null && jointHit >= 0) {
            physicsSelectionService.setHoveredJoint(jointHit);
            return;
        }

        if (!isFixturePickingEnabled()) {
            clearPhysicsHover();
            return;
        }

        float tolWorld = Math.max(PICK_TOLERANCE_PX, HOVER_TOLER_PX)
                * HandleHelper.worldUnitsPerPixel(worldCam);

        FixtureHit fixtureHit = findTopmostFixtureHit(mx, my, tolWorld);
        if (fixtureHit != null) {
            physicsSelectionService.setHoveredFixture(fixtureHit.bodyEid, fixtureHit.fixtureId);
        } else {
            clearPhysicsHover();
        }
    }

    private InputManipulationContext.Handle resolveHoveredHandle(float mx, float my, IntArray viewportSel) {
        clearHoveredPolygonVertex();
        hoveredPolygonVertexIndex = detectSelectedPolygonVertexHover(mx, my);
        if (hoveredPolygonVertexIndex >= 0) {
            return InputManipulationContext.Handle.NONE;
        }
        if (isExplicitPhysicsEditMode()) {
            InputManipulationContext.Handle box = detectSelectedBoxHandleHover(mx, my);
            if (box != InputManipulationContext.Handle.NONE) return box;
            return detectSelectedCircleRadiusHandleHover(mx, my);
        }
        if (viewportSel.size == 1) {
            return detectHandleHover(viewportSel.get(0), mx, my);
        }
        return InputManipulationContext.Handle.NONE;
    }

    private boolean tryPickVisibleFixture(float mx, float my) {
        if (!isFixturePickingEnabled() || physicsService == null) return false;

        float tolWorld = PICK_TOLERANCE_PX * HandleHelper.worldUnitsPerPixel(worldCam);
        FixtureHit hit = findTopmostFixtureHit(mx, my, tolWorld);
        if (hit == null) return false;

        physicsSelectionService.focusBody(hit.bodyEid);
        physicsSelectionService.setSelectedFixture(hit.bodyEid, hit.fixtureId);
        EventFlow.i().publish(new EventFlow.FixtureSelectionChanged(hit.bodyEid, hit.fixtureId, MY_TAG));

        lastPressHit = hit.bodyEid;
        pressStartedOnSelection = false;
        return true;
    }

    private void clearPhysicsHover() {
        physicsSelectionService.clearHover();
    }

    private boolean tryBeginJointAnchorDrag(float mx, float my) {
        if (!isExplicitPhysicsEditMode() || physicsService == null || !physicsService.isAvailable()) return false;
        int selectedJointEid = resolveSelectedJointEidForAnchorDrag();
        if (selectedJointEid < 0) return false;
        PhysicsJointComponent base = mJointBase.getSafe(selectedJointEid, null);
        if (base == null) return false;

        int handle = hitTestJointAnchorHandle(selectedJointEid, base, mx, my);
        if (handle == JointAnchorHandle.NONE) return false;

        jointAnchorDragBefore = EditJointBaseCommand.Snapshot.capture(base);
        jointAnchorDragActive = true;
        jointAnchorDragEid = selectedJointEid;
        jointAnchorDragHandle = handle;
        return true;
    }

    private int resolveSelectedJointEidForAnchorDrag() {
        IntArray selection = selectionService.getSelectionSnapshot();

        if (selection != null && selection.size == 1) {
            int eid = selection.get(0);
            if (eid >= 0
                    && world.getEntityManager().isActive(eid)
                    && mJointBase.getSafe(eid, null) != null) {
                return eid;
            }
        }

        int physicsSelected = physicsSelectionService.getSelectedJointEid();
        if (physicsSelected >= 0
                && world.getEntityManager().isActive(physicsSelected)
                && mJointBase.getSafe(physicsSelected, null) != null) {
            return physicsSelected;
        }

        return -1;
    }

    private void onJointAnchorDragging(float mx, float my) {
        if (!jointAnchorDragActive) return;
        if (!world.getEntityManager().isActive(jointAnchorDragEid)) {
            cancelJointAnchorDrag();
            return;
        }
        PhysicsJointComponent base = mJointBase.getSafe(jointAnchorDragEid, null);
        if (base == null) {
            cancelJointAnchorDrag();
            return;
        }
        if (!isValidJointBodies(base)) {
            cancelJointAnchorDrag();
            return;
        }

        switch (jointAnchorDragHandle) {
            case JointAnchorHandle.ANCHOR_A -> updateAnchorFromWorld(base.aEid, mx, my, true, base);
            case JointAnchorHandle.ANCHOR_B -> updateAnchorFromWorld(base.bEid, mx, my, false, base);
            case JointAnchorHandle.PIVOT_BOTH -> {
                updateAnchorFromWorld(base.aEid, mx, my, true, base);
                updateAnchorFromWorld(base.bEid, mx, my, false, base);
            }
            default -> {
                return;
            }
        }
        if (dirty != null) dirty.joint(jointAnchorDragEid, JointDirtyBits.ALL);
    }

    private void onJointAnchorDragReleased() {
        if (!jointAnchorDragActive) return;
        jointAnchorDragActive = false;

        if (!world.getEntityManager().isActive(jointAnchorDragEid)) {
            cancelJointAnchorDrag();
            return;
        }
        PhysicsJointComponent base = mJointBase.getSafe(jointAnchorDragEid, null);
        if (base == null || jointAnchorDragBefore == null) {
            cancelJointAnchorDrag();
            return;
        }
        EditJointBaseCommand.Snapshot after = EditJointBaseCommand.Snapshot.capture(base);
        if (after == null) return;

        jointAnchorDragBefore.apply(base);
        EditJointBaseCommand cmd = new EditJointBaseCommand(world, historyIds, jointAnchorDragEid, jointAnchorDragBefore, after);
        if (!cmd.isNoop()) historyManager.execute(cmd);

        cancelJointAnchorDrag();
    }

    private void cancelJointAnchorDrag() {
        jointAnchorDragActive = false;
        jointAnchorDragEid = -1;
        jointAnchorDragHandle = JointAnchorHandle.NONE;
        jointAnchorDragBefore = null;
    }

    private int hitTestJointAnchorHandle(int jointEid, PhysicsJointComponent base, float mx, float my) {
        float tol = (JOINT_PICK_TOL_PX + 2f) * HandleHelper.worldUnitsPerPixel(worldCam);
        float tol2 = tol * tol;

        if (base.type == PhysicsJointComponent.TYPE_DISTANCE) {
            if (!computeJointEndpointsForAnchorDrag(jointEid, base, tmpA, tmpB)) return JointAnchorHandle.NONE;
            if (dst2(mx, my, tmpA.x, tmpA.y) <= tol2) return JointAnchorHandle.ANCHOR_A;
            if (dst2(mx, my, tmpB.x, tmpB.y) <= tol2) return JointAnchorHandle.ANCHOR_B;
            return JointAnchorHandle.NONE;
        }
        if (base.type == PhysicsJointComponent.TYPE_WHEEL) {
            if (!computeJointPivotForPicking(jointEid, base, tmpA)) return JointAnchorHandle.NONE;
            return dst2(mx, my, tmpA.x, tmpA.y) <= tol2 ? JointAnchorHandle.PIVOT_BOTH : JointAnchorHandle.NONE;
        }
        if (base.type == PhysicsJointComponent.TYPE_REVOLUTE
                || base.type == PhysicsJointComponent.TYPE_WELD
                || base.type == PhysicsJointComponent.TYPE_FRICTION) {
            if (!computeJointPivotForPicking(jointEid, base, tmpA)) return JointAnchorHandle.NONE;
            return dst2(mx, my, tmpA.x, tmpA.y) <= tol2 ? JointAnchorHandle.PIVOT_BOTH : JointAnchorHandle.NONE;
        }
        return JointAnchorHandle.NONE;
    }

    private boolean computeJointEndpointsForAnchorDrag(int jointEid, PhysicsJointComponent base, Vector2 outA, Vector2 outB) {
        if (base.type == PhysicsJointComponent.TYPE_DISTANCE) {
            if (!physicsService.computeDistanceJointEndpointsWU(jointEid, outA, outB)) return false;
            applyDisplayOffset(base.aEid, outA);
            applyDisplayOffset(base.bEid, outB);
            return true;
        }
        if (base.type == PhysicsJointComponent.TYPE_WHEEL) {
            TransformComponent ta = mT.getSafe(base.aEid, null);
            TransformComponent tb = mT.getSafe(base.bEid, null);
            if (ta == null || tb == null) return false;
            float ppm = resolvePixelsPerMeter();
            outA.set(
                    ta.x + rotateX(base.anchorAx * ppm, base.anchorAy * ppm, ta.rotationRad),
                    ta.y + rotateY(base.anchorAx * ppm, base.anchorAy * ppm, ta.rotationRad)
            );
            outB.set(
                    tb.x + rotateX(base.anchorBx * ppm, base.anchorBy * ppm, tb.rotationRad),
                    tb.y + rotateY(base.anchorBx * ppm, base.anchorBy * ppm, tb.rotationRad)
            );
            applyDisplayOffset(base.aEid, outA);
            applyDisplayOffset(base.bEid, outB);
            return true;
        }
        return false;
    }

    private boolean isValidJointBodies(PhysicsJointComponent base) {
        return base.aEid >= 0 && base.bEid >= 0
                && world.getEntityManager().isActive(base.aEid)
                && world.getEntityManager().isActive(base.bEid)
                && mT.getSafe(base.aEid, null) != null
                && mT.getSafe(base.bEid, null) != null;
    }

    private void updateAnchorFromWorld(int bodyEid, float mouseWorldX, float mouseWorldY, boolean anchorA, PhysicsJointComponent base) {
        TransformComponent t = mT.getSafe(bodyEid, null);
        if (t == null) return;
        tmpA.set(mouseWorldX, mouseWorldY);
        removeDisplayOffset(bodyEid, tmpA);
        float ppm = resolvePixelsPerMeter();
        worldPointToLocalAnchorMeters(tmpA.x, tmpA.y, t.x, t.y, t.rotationRad, ppm, tmpB);
        float ax = tmpB.x;
        float ay = tmpB.y;
        if (anchorA) {
            base.anchorAx = ax;
            base.anchorAy = ay;
        } else {
            base.anchorBx = ax;
            base.anchorBy = ay;
        }
    }

    private static float dst2(float x0, float y0, float x1, float y1) {
        float dx = x0 - x1;
        float dy = y0 - y1;
        return dx * dx + dy * dy;
    }

    static void worldPointToLocalAnchorMeters(float mouseWorldX,
                                              float mouseWorldY,
                                              float bodyX,
                                              float bodyY,
                                              float bodyRotationRad,
                                              float ppm,
                                              Vector2 out) {
        float dx = mouseWorldX - bodyX;
        float dy = mouseWorldY - bodyY;
        float cos = MathUtils.cos(bodyRotationRad);
        float sin = MathUtils.sin(bodyRotationRad);
        float localXWorld = dx * cos + dy * sin;
        float localYWorld = -dx * sin + dy * cos;
        out.set(localXWorld / ppm, localYWorld / ppm);
    }

    private void updateHoveredEntity(float mx,
                                     float my,
                                     InputManipulationContext.Handle hoveredHandle,
                                     IntArray viewportSel,
                                     boolean physicsEditMode) {
        if (selectionService == null) return;

        if (physicsEditMode || ctx.isDragging() || hoveredHandle != InputManipulationContext.Handle.NONE) {
            selectionService.clearHoveredEntity();
            return;
        }

        Integer hit = findTopmostObbHit(mx, my);
        if (hit == null || hit < 0 || isInSelection(viewportSel, hit)) {
            selectionService.clearHoveredEntity();
            return;
        }

        selectionService.setHoveredEntityId(hit);
    }

    private InputManipulationContext.Handle detectSelectedBoxHandleHover(float mx, float my) {
        if (!isExplicitPhysicsEditMode()) return InputManipulationContext.Handle.NONE;
        if (physicsService == null) return InputManipulationContext.Handle.NONE;

        int bodyEid = physicsSelectionService.getFocusedBodyEid();
        long fixtureId = physicsSelectionService.getSelectedFixtureId();
        if (bodyEid < 0 || fixtureId <= 0) return InputManipulationContext.Handle.NONE;
        if (!isFixtureGeometryEditable(world, bodyEid, fixtureId)) {
            return InputManipulationContext.Handle.NONE;
        }

        FixtureDefData fixture = getSelectedFixture(bodyEid, fixtureId);
        if (fixture == null || fixture.shapeType != FixtureDefData.SHAPE_BOX) {
            return InputManipulationContext.Handle.NONE;
        }

        ensureFixtureVertsCapacity(8);
        int vertexCount = physicsService.computeFixtureVerticesWU(bodyEid, fixture, tmpFixtureVerts);
        if (vertexCount != 4) return InputManipulationContext.Handle.NONE;

        applyDisplayOffset(bodyEid, tmpFixtureVerts);

        return FixtureHandleHelper.detectBoxCornerHover(
                worldCam,
                tmpFixtureVerts,
                mx,
                my,
                HOVER_TOLER_PX,
                GizmoDrawHelper.SHAPE_VERTEX_HANDLE_SIZE_PX
        );
    }

    private void ensureFixtureVertsCapacity(int floatCount) {
        if (tmpFixtureVerts == null || tmpFixtureVerts.length < floatCount) {
            tmpFixtureVerts = new float[Math.max(floatCount, 8)];
        }
    }

    private boolean isBoxCornerHandle(InputManipulationContext.Handle h) {
        return h == InputManipulationContext.Handle.SW
                || h == InputManipulationContext.Handle.SE
                || h == InputManipulationContext.Handle.NE
                || h == InputManipulationContext.Handle.NW;
    }

    private boolean tryBeginBoxResize(float mx, float my) {
        if (!isExplicitPhysicsEditMode()) return false;
        if (!isBoxCornerHandle(ctx.hoveredHandle())) return false;
        if (physicsService == null) return false;

        int bodyEid = physicsSelectionService.getFocusedBodyEid();
        int fixtureId = physicsSelectionService.getSelectedFixtureId();
        if (bodyEid < 0 || fixtureId <= 0L) return false;
        if (!isFixtureGeometryEditable(world, bodyEid, fixtureId)) return false;

        FixtureDefData fixture = getSelectedFixture(bodyEid, fixtureId);
        if (fixture == null || fixture.shapeType != FixtureDefData.SHAPE_BOX) return false;

        resizingBoxActive = true;
        resizingBoxBodyEid = bodyEid;
        resizingBoxFixtureId = fixtureId;
        resizingBoxHandle = ctx.hoveredHandle();

        resizeBoxBeforeOffsetX = fixture.offsetX;
        resizeBoxBeforeOffsetY = fixture.offsetY;
        resizeBoxBeforeHalfW = fixture.halfW;
        resizeBoxBeforeHalfH = fixture.halfH;

        oldDrag.set(mx, my);
        return true;
    }

    private InputManipulationContext.Handle detectSelectedCircleRadiusHandleHover(float mx, float my) {
        int bodyEid = physicsSelectionService.getFocusedBodyEid();
        long fixtureId = physicsSelectionService.getSelectedFixtureId();
        if (bodyEid < 0 || fixtureId <= 0L) return InputManipulationContext.Handle.NONE;
        if (!isFixtureGeometryEditable(world, bodyEid, fixtureId)) {
            return InputManipulationContext.Handle.NONE;
        }
        FixtureDefData fixture = getSelectedFixture(bodyEid, fixtureId);
        if (fixture == null || fixture.shapeType != FixtureDefData.SHAPE_CIRCLE)
            return InputManipulationContext.Handle.NONE;
        if (!physicsService.computeFixtureCenterWU(bodyEid, fixture, tmpA)) return InputManipulationContext.Handle.NONE;
        applyDisplayOffset(bodyEid, tmpA);
        float hx = tmpA.x + physicsService.computeFixtureRadiusWU(fixture);
        float hy = tmpA.y;
        float halfWorld = HandleHelper.pxToWorld(worldCam, GizmoDrawHelper.SHAPE_VERTEX_HANDLE_SIZE_PX * 0.5f + HOVER_TOLER_PX);
        return HandleHelper.insideSquare(mx, my, hx, hy, halfWorld) ? InputManipulationContext.Handle.E : InputManipulationContext.Handle.NONE;
    }

    private boolean tryBeginCircleResize(float mx, float my) {
        if (!isExplicitPhysicsEditMode()) return false;
        if (ctx.hoveredHandle() != InputManipulationContext.Handle.E) return false;
        int bodyEid = physicsSelectionService.getFocusedBodyEid();
        int fixtureId = physicsSelectionService.getSelectedFixtureId();
        if (bodyEid < 0 || fixtureId <= 0L) return false;
        if (!isFixtureGeometryEditable(world, bodyEid, fixtureId)) return false;
        FixtureDefData fixture = getSelectedFixture(bodyEid, fixtureId);
        if (fixture == null || fixture.shapeType != FixtureDefData.SHAPE_CIRCLE) return false;
        resizingCircleActive = true;
        resizingCircleBodyEid = bodyEid;
        resizingCircleFixtureId = fixtureId;
        resizeCircleBeforeRadius = fixture.radius;
        circleRadiusDragCurrent = fixture.radius;
        oldDrag.set(mx, my);
        return true;
    }

    private void worldToBodyLocalPx(int bodyEid, float wx, float wy, Vector2 out) {
        TransformComponent t = mT.getSafe(bodyEid, null);
        if (t == null) {
            out.setZero();
            return;
        }

        float dx = wx - t.x;
        float dy = wy - t.y;

        float cos = MathUtils.cos(t.rotationRad);
        float sin = MathUtils.sin(t.rotationRad);

        float localX = dx * cos + dy * sin;
        float localY = -dx * sin + dy * cos;
        out.set(localX, localY);
    }

    private void copyFixtureBoxCornersWorld(int bodyEid, FixtureDefData fixture, float[] out8) {
        ensureFixtureVertsCapacity(8);
        int vertexCount = physicsService.computeFixtureVerticesWU(bodyEid, fixture, tmpFixtureVerts);
        if (vertexCount != 4) return;
        System.arraycopy(tmpFixtureVerts, 0, out8, 0, 8);
        applyDisplayOffset(bodyEid, out8);
    }

    private void onBoxResizeDragging(float mx, float my) {
        if (!resizingBoxActive) return;

        FixtureDefData fixture = getSelectedFixture(resizingBoxBodyEid, resizingBoxFixtureId);
        if (fixture == null || fixture.shapeType != FixtureDefData.SHAPE_BOX) return;

        copyFixtureBoxCornersWorld(resizingBoxBodyEid, fixture, tmpFixtureBoxWorldCorners);
        float[] worldCorners = tmpFixtureBoxWorldCorners;

        float fx, fy;
        switch (resizingBoxHandle) {
            case SW -> {
                fx = HandleLayout.neX(worldCorners);
                fy = HandleLayout.neY(worldCorners);
            }
            case SE -> {
                fx = HandleLayout.nwX(worldCorners);
                fy = HandleLayout.nwY(worldCorners);
            }
            case NE -> {
                fx = HandleLayout.swX(worldCorners);
                fy = HandleLayout.swY(worldCorners);
            }
            case NW -> {
                fx = HandleLayout.seX(worldCorners);
                fy = HandleLayout.seY(worldCorners);
            }
            default -> {
                return;
            }
        }

        Vector2 fixedLocalPx = tmpA;
        Vector2 dragLocalPx = tmpB;

        worldToBodyLocalPx(resizingBoxBodyEid, fx, fy, fixedLocalPx);
        worldToBodyLocalPx(resizingBoxBodyEid, mx, my, dragLocalPx);

        float minHalfPx = 1f;

        float centerLocalPxX = (fixedLocalPx.x + dragLocalPx.x) * 0.5f;
        float centerLocalPxY = (fixedLocalPx.y + dragLocalPx.y) * 0.5f;
        float halfWPx = Math.max(minHalfPx, Math.abs(dragLocalPx.x - fixedLocalPx.x) * 0.5f);
        float halfHPx = Math.max(minHalfPx, Math.abs(dragLocalPx.y - fixedLocalPx.y) * 0.5f);

        fixture.offsetX = physicsService.pxToM(centerLocalPxX);
        fixture.offsetY = physicsService.pxToM(centerLocalPxY);
        fixture.halfW = physicsService.pxToM(halfWPx);
        fixture.halfH = physicsService.pxToM(halfHPx);

        FixtureCommandSupport.markDirty(world, resizingBoxBodyEid);
    }

    private void onBoxResizeReleased() {
        if (!resizingBoxActive) return;

        FixtureDefData fixture = getSelectedFixture(resizingBoxBodyEid, resizingBoxFixtureId);
        if (fixture == null || fixture.shapeType != FixtureDefData.SHAPE_BOX) {
            clearBoxResizeState();
            return;
        }

        ResizeBoxFixtureCommand cmd = new ResizeBoxFixtureCommand(
                world,
                historyIds,
                physicsSelectionService,
                resizingBoxBodyEid,
                resizingBoxFixtureId,
                resizeBoxBeforeOffsetX,
                resizeBoxBeforeOffsetY,
                resizeBoxBeforeHalfW,
                resizeBoxBeforeHalfH,
                fixture.offsetX,
                fixture.offsetY,
                fixture.halfW,
                fixture.halfH
        );

        if (!cmd.isNoop()) {
            historyManager.execute(cmd);
        }

        clearBoxResizeState();
    }

    private void clearBoxResizeState() {
        resizingBoxActive = false;
        resizingBoxBodyEid = -1;
        resizingBoxFixtureId = PhysicsSelectionService.NO_FIXTURE;
        resizingBoxHandle = InputManipulationContext.Handle.NONE;
    }

    private void onCircleResizeDragging(float mx, float my) {
        FixtureDefData fixture = getSelectedFixture(resizingCircleBodyEid, resizingCircleFixtureId);
        if (fixture == null || fixture.shapeType != FixtureDefData.SHAPE_CIRCLE) return;
        if (!physicsService.computeFixtureCenterWU(resizingCircleBodyEid, fixture, tmpA)) return;
        applyDisplayOffset(resizingCircleBodyEid, tmpA);
        float radiusWorld = Vector2.dst(tmpA.x, tmpA.y, mx, my);
        float radiusM = Math.max(0.001f, physicsService.pxToM(radiusWorld));
        fixture.radius = radiusM;
        circleRadiusDragCurrent = radiusM;
        FixtureCommandSupport.markDirty(world, resizingCircleBodyEid);
    }

    private void onCircleResizeReleased() {
        FixtureDefData fixture = getSelectedFixture(resizingCircleBodyEid, resizingCircleFixtureId);
        if (fixture == null || fixture.shapeType != FixtureDefData.SHAPE_CIRCLE) {
            clearCircleResizeState();
            return;
        }
        float afterRadius = Math.max(0.001f, circleRadiusDragCurrent);
        FixtureDefData beforeData = fixture.copy();
        beforeData.radius = Math.max(0.001f, resizeCircleBeforeRadius);
        EditFixtureCommand.Snapshot before = EditFixtureCommand.Snapshot.capture(beforeData);
        FixtureDefData edited = fixture.copy();
        edited.radius = afterRadius;
        EditFixtureCommand.Snapshot after = EditFixtureCommand.Snapshot.capture(edited);
        fixture.radius = resizeCircleBeforeRadius;
        EditFixtureCommand cmd = new EditFixtureCommand(
                world, historyIds, physicsSelectionService,
                resizingCircleBodyEid, resizingCircleFixtureId,
                before, after, PhysicsDirtyBits.FIXTURE, false
        );
        if (!cmd.isNoop()) historyManager.execute(cmd);
        clearCircleResizeState();
    }

    private void clearCircleResizeState() {
        resizingCircleActive = false;
        resizingCircleBodyEid = -1;
        resizingCircleFixtureId = PhysicsSelectionService.NO_FIXTURE;
        resizeCircleBeforeRadius = 0f;
        circleRadiusDragCurrent = 0f;
    }

    private void clearHoveredPolygonVertex() {
        hoveredPolygonVertexIndex = -1;
    }

    private int detectSelectedPolygonVertexHover(float mx, float my) {
        if (!isExplicitPhysicsEditMode()) return -1;
        if (physicsService == null) return -1;

        int bodyEid = physicsSelectionService.getFocusedBodyEid();
        long fixtureId = physicsSelectionService.getSelectedFixtureId();
        if (bodyEid < 0 || fixtureId <= 0L) return -1;
        if (!isFixtureGeometryEditable(world, bodyEid, fixtureId)) return -1;

        AuthoredPolygonData authored =
                polygonAuthoringService().findByGeneratedFixtureId(bodyEid, fixtureId);

        if (authored != null) {
            if (authored.sourceVerts == null
                    || authored.sourceCount < 3
                    || authored.sourceVerts.length < authored.sourceCount * 2) {
                return -1;
            }

            int floatCount = authored.sourceCount * 2;
            ensureFixtureVertsCapacity(floatCount);

            int vertexCount = computeAuthoredPolygonVertsWU(
                    bodyEid,
                    authored.sourceVerts,
                    authored.sourceCount,
                    authored,
                    tmpFixtureVerts
            );

            if (vertexCount < 3) return -1;

            return FixtureHandleHelper.detectPolygonVertexHover(
                    worldCam,
                    tmpFixtureVerts,
                    vertexCount,
                    mx,
                    my,
                    HOVER_TOLER_PX,
                    GizmoDrawHelper.SHAPE_VERTEX_HANDLE_SIZE_PX
            );
        }

        FixtureDefData fixture = getSelectedFixture(bodyEid, fixtureId);
        if (fixture == null || fixture.shapeType != FixtureDefData.SHAPE_POLYGON) {
            return -1;
        }

        int floatCount = Math.max(0, fixture.polyCount * 2);
        if (floatCount < 6) return -1;

        ensureFixtureVertsCapacity(floatCount);

        int vertexCount = physicsService.computeFixtureVerticesWU(bodyEid, fixture, tmpFixtureVerts);
        if (vertexCount < 3) return -1;

        applyDisplayOffset(bodyEid, tmpFixtureVerts);

        return FixtureHandleHelper.detectPolygonVertexHover(
                worldCam,
                tmpFixtureVerts,
                vertexCount,
                mx,
                my,
                HOVER_TOLER_PX,
                GizmoDrawHelper.SHAPE_VERTEX_HANDLE_SIZE_PX
        );
    }

    private boolean tryBeginPolygonVertexMove(float mx, float my) {
        if (!isExplicitPhysicsEditMode()) return false;
        if (hoveredPolygonVertexIndex < 0) return false;
        if (physicsService == null) return false;

        int bodyEid = physicsSelectionService.getFocusedBodyEid();
        int fixtureId = physicsSelectionService.getSelectedFixtureId();
        if (bodyEid < 0 || fixtureId <= 0L) return false;
        if (!isFixtureGeometryEditable(world, bodyEid, fixtureId)) return false;

        AuthoredPolygonData authored =
                polygonAuthoringService().findByGeneratedFixtureId(bodyEid, fixtureId);

        if (authored != null) {
            if (authored.sourceVerts == null
                    || authored.sourceCount < 3
                    || authored.sourceVerts.length < authored.sourceCount * 2) {
                return false;
            }

            int base = hoveredPolygonVertexIndex * 2;
            if (base < 0 || base + 1 >= authored.sourceVerts.length) return false;

            movingPolygonVertexActive = true;
            movingAuthoredPolygonVertexActive = true;

            movingPolygonVertexBodyEid = bodyEid;
            movingPolygonVertexFixtureId = fixtureId;
            movingPolygonVertexIndex = hoveredPolygonVertexIndex;

            movingAuthoredPolygonId = authored.authoringId;
            movingAuthoredPolygonBeforeCount = authored.sourceCount;
            movingAuthoredPolygonBeforeVerts = copyVerts(authored.sourceVerts, authored.sourceCount);

            movingPolygonVertexBeforeX = authored.sourceVerts[base];
            movingPolygonVertexBeforeY = authored.sourceVerts[base + 1];

            oldDrag.set(mx, my);
            return true;
        }

        FixtureDefData fixture = getSelectedFixture(bodyEid, fixtureId);
        if (fixture == null || fixture.shapeType != FixtureDefData.SHAPE_POLYGON) return false;
        if (fixture.polyVerts == null) return false;

        int base = hoveredPolygonVertexIndex * 2;
        if (base < 0 || base + 1 >= fixture.polyVerts.length) return false;

        movingPolygonVertexActive = true;
        movingAuthoredPolygonVertexActive = false;

        movingPolygonVertexBodyEid = bodyEid;
        movingPolygonVertexFixtureId = fixtureId;
        movingPolygonVertexIndex = hoveredPolygonVertexIndex;

        movingPolygonVertexBeforeX = fixture.polyVerts[base];
        movingPolygonVertexBeforeY = fixture.polyVerts[base + 1];

        oldDrag.set(mx, my);
        return true;
    }

    private int computeAuthoredPolygonVertsWU(
            int bodyEid,
            float[] localVertsMeters,
            int count,
            AuthoredPolygonData polygon,
            float[] out
    ) {
        if (bodyEid < 0) return 0;
        if (localVertsMeters == null || count < 3 || localVertsMeters.length < count * 2) return 0;
        if (out == null || out.length < count * 2) return 0;

        TransformComponent t = mT.getSafe(bodyEid, null);
        if (t == null) return 0;

        float ppm = resolvePixelsPerMeter();

        float fixtureOffsetX = polygon != null ? polygon.offsetX : 0f;
        float fixtureOffsetY = polygon != null ? polygon.offsetY : 0f;
        float fixtureAngleRad = (polygon != null ? MathUtils.degreesToRadians * polygon.angleDeg : 0f);

        float fixtureCos = MathUtils.cos(fixtureAngleRad);
        float fixtureSin = MathUtils.sin(fixtureAngleRad);

        float bodyCos = MathUtils.cos(t.rotationRad);
        float bodySin = MathUtils.sin(t.rotationRad);

        for (int i = 0; i < count; i++) {
            float lx = localVertsMeters[i * 2];
            float ly = localVertsMeters[i * 2 + 1];

            float fx = lx * fixtureCos - ly * fixtureSin + fixtureOffsetX;
            float fy = lx * fixtureSin + ly * fixtureCos + fixtureOffsetY;

            float wx = t.x + (fx * bodyCos - fy * bodySin) * ppm;
            float wy = t.y + (fx * bodySin + fy * bodyCos) * ppm;

            out[i * 2] = wx;
            out[i * 2 + 1] = wy;
        }

        applyDisplayOffset(bodyEid, out, count);
        return count;
    }

    private void applyDisplayOffset(int entityId, float[] verts, int vertexCount) {
        // Studio tools operate in logical world space; preview/runtime display offsets
        // are intentionally not applied to picking, gizmos, and physics handles.
    }

    private void onPolygonVertexDragging(float mx, float my) {
        if (!movingPolygonVertexActive) return;

        if (movingAuthoredPolygonVertexActive) {
            onAuthoredPolygonVertexDragging(mx, my);
            return;
        }

        FixtureDefData fixture = getSelectedFixture(movingPolygonVertexBodyEid, movingPolygonVertexFixtureId);
        if (fixture == null || fixture.shapeType != FixtureDefData.SHAPE_POLYGON) return;
        if (fixture.polyVerts == null) return;

        int base = movingPolygonVertexIndex * 2;
        if (base < 0 || base + 1 >= fixture.polyVerts.length) return;

        worldToBodyLocalPx(movingPolygonVertexBodyEid, mx, my, tmpA);

        fixture.polyVerts[base] = physicsService.pxToM(tmpA.x);
        fixture.polyVerts[base + 1] = physicsService.pxToM(tmpA.y);

        FixtureCommandSupport.markDirty(world, movingPolygonVertexBodyEid);
    }

    private void onAuthoredPolygonVertexDragging(float mx, float my) {
        AuthoredPolygonData authored = polygonAuthoringService()
                .findByAuthoringId(movingPolygonVertexBodyEid, movingAuthoredPolygonId);

        if (authored == null
                || authored.sourceVerts == null
                || authored.sourceCount < 3
                || authored.sourceVerts.length < authored.sourceCount * 2) {
            return;
        }

        int base = movingPolygonVertexIndex * 2;
        if (base < 0 || base + 1 >= authored.sourceVerts.length) return;

        worldToAuthoredSourceLocalMeters(
                movingPolygonVertexBodyEid,
                authored,
                mx,
                my,
                tmpA
        );

        float[] candidate = copyVerts(authored.sourceVerts, authored.sourceCount);
        candidate[base] = tmpA.x;
        candidate[base + 1] = tmpA.y;

        try {
            FixtureDefData materialSource = materialFromAuthoredPolygon(authored);

            AuthoredPolygonData applied =
                    polygonAuthoringService().applyAuthoredPolygonReplacingFixture(
                            movingPolygonVertexBodyEid,
                            authored.authoringId,
                            candidate,
                            authored.sourceCount,
                            materialSource,
                            -1L
                    );

            if (applied.generatedFixtureIds != null && applied.generatedFixtureIds.length > 0) {
                int selectedId = applied.generatedFixtureIds[0];

                physicsSelectionService.focusBody(movingPolygonVertexBodyEid);
                physicsSelectionService.setSelectedFixture(movingPolygonVertexBodyEid, selectedId);
            }
        } catch (IllegalArgumentException ignored) {
            // Invalid position: simply reject this drag step.
            // The last valid shape remains in place.
        }
    }

    private static float[] copyVerts(float[] verts, int count) {
        int floatCount = Math.max(0, count) * 2;
        float[] out = new float[floatCount];

        if (verts != null && floatCount > 0) {
            System.arraycopy(verts, 0, out, 0, Math.min(floatCount, verts.length));
        }

        return out;
    }

    private void worldToAuthoredSourceLocalMeters(
            int bodyEid,
            AuthoredPolygonData polygon,
            float wx,
            float wy,
            Vector2 outMeters
    ) {
        tmp2Vec.set(wx, wy);
        removeDisplayOffset(bodyEid, tmp2Vec);

        worldToBodyLocalPx(bodyEid, tmp2Vec.x, tmp2Vec.y, tmpA);

        float ppm = resolvePixelsPerMeter();

        float bodyLocalX = tmpA.x / ppm;
        float bodyLocalY = tmpA.y / ppm;

        float offsetX = polygon != null ? polygon.offsetX : 0f;
        float offsetY = polygon != null ? polygon.offsetY : 0f;
        float angleRad = (polygon != null ? MathUtils.degreesToRadians * polygon.angleDeg : 0f);

        float dx = bodyLocalX - offsetX;
        float dy = bodyLocalY - offsetY;

        float cos = MathUtils.cos(angleRad);
        float sin = MathUtils.sin(angleRad);

        float lx = dx * cos + dy * sin;
        float ly = -dx * sin + dy * cos;

        outMeters.set(lx, ly);
    }

    private FixtureDefData materialFromAuthoredPolygon(AuthoredPolygonData polygon) {
        FixtureDefData fixture = FixtureCommandSupport.createDefaultFixture();

        fixture.shapeType = FixtureDefData.SHAPE_POLYGON;
        fixture.polyVerts = new float[0];
        fixture.polyCount = 0;

        if (polygon == null) {
            return fixture;
        }

        fixture.density = polygon.density;
        fixture.friction = polygon.friction;
        fixture.restitution = polygon.restitution;
        fixture.isSensor = polygon.isSensor;

        fixture.categoryBits = polygon.categoryBits;
        fixture.maskBits = polygon.maskBits;
        fixture.groupIndex = polygon.groupIndex;

        fixture.offsetX = polygon.offsetX;
        fixture.offsetY = polygon.offsetY;
        fixture.angleDeg = polygon.angleDeg;

        return fixture;
    }

    private void onPolygonVertexReleased() {
        if (!movingPolygonVertexActive) return;

        if (movingAuthoredPolygonVertexActive) {
            AuthoredPolygonData authored =
                    polygonAuthoringService().findByAuthoringId(
                            movingPolygonVertexBodyEid,
                            movingAuthoredPolygonId
                    );

            if (authored == null
                    || authored.sourceVerts == null
                    || authored.sourceCount < 3
                    || authored.sourceVerts.length < authored.sourceCount * 2) {
                clearPolygonVertexMoveState();
                return;
            }

            FixtureDefData materialSource = materialFromAuthoredPolygon(authored);

            MoveAuthoredPolygonVertexCommand cmd = new MoveAuthoredPolygonVertexCommand(
                    world,
                    historyIds,
                    physicsSelectionService,
                    movingPolygonVertexBodyEid,
                    movingAuthoredPolygonId,
                    physicsSelectionService.getSelectedFixtureId(),
                    movingAuthoredPolygonBeforeVerts,
                    movingAuthoredPolygonBeforeCount,
                    authored.sourceVerts,
                    authored.sourceCount,
                    materialSource,
                    true // after already applied live during drag
            );

            if (!cmd.isNoop()) {
                historyManager.execute(cmd);
            }

            clearPolygonVertexMoveState();
            return;
        }

        FixtureDefData fixture = getSelectedFixture(movingPolygonVertexBodyEid, movingPolygonVertexFixtureId);
        if (fixture == null || fixture.shapeType != FixtureDefData.SHAPE_POLYGON || fixture.polyVerts == null) {
            clearPolygonVertexMoveState();
            return;
        }

        int base = movingPolygonVertexIndex * 2;
        if (base < 0 || base + 1 >= fixture.polyVerts.length) {
            clearPolygonVertexMoveState();
            return;
        }

        MovePolygonVertexCommand cmd = new MovePolygonVertexCommand(
                world,
                historyIds,
                physicsSelectionService,
                movingPolygonVertexBodyEid,
                movingPolygonVertexFixtureId,
                movingPolygonVertexIndex,
                movingPolygonVertexBeforeX,
                movingPolygonVertexBeforeY,
                fixture.polyVerts[base],
                fixture.polyVerts[base + 1]
        );

        if (!cmd.isNoop()) {
            historyManager.execute(cmd);
        }

        clearPolygonVertexMoveState();
    }

    private void clearPolygonVertexMoveState() {
        movingPolygonVertexActive = false;
        movingAuthoredPolygonVertexActive = false;

        movingPolygonVertexBodyEid = -1;
        movingPolygonVertexFixtureId = PhysicsSelectionService.NO_FIXTURE;
        movingPolygonVertexIndex = -1;

        movingAuthoredPolygonId = 0L;
        movingAuthoredPolygonBeforeVerts = new float[0];
        movingAuthoredPolygonBeforeCount = 0;
    }

    private void commitPolygonDrawSession() {
        if (polygonDrawSession == null || !polygonDrawSession.isValidPolygon()) {
            if (polygonDrawSession != null) {
                polygonDrawSession.cancel();
            }
            return;
        }

        int bodyEid = polygonDrawSession.getBodyEid();
        if (bodyEid < 0 || physicsService == null || !physicsService.isAvailable()) {
            polygonDrawSession.cancel();
            return;
        }

        int vertexCount = polygonDrawSession.pointCount();
        if (vertexCount < 3) {
            polygonDrawSession.cancel();
            return;
        }

        float[] localVertsM = polygonSessionToBodyLocalMeters(bodyEid);
        if (localVertsM == null || localVertsM.length < vertexCount * 2) {
            polygonDrawSession.cancel();
            return;
        }

        PolygonBuildResult build = polygonAuthoringService().buildFromSource(
                localVertsM,
                vertexCount
        );

        if (build == null || !build.isValid()) {
            String message = build != null ? build.message() : "Invalid polygon.";
            Gdx.app.error("PickingSystem", "Polygon rejected: " + message);

            // Explicitly reject this case.
            // Keep the session active but reopen the outline to allow correction/redrawing.
            polygonDrawSession.reset();
            return;
        }

        long fixtureId = polygonDrawSession.getFixtureId();

        AuthoredPolygonData existingAuthored = fixtureId > 0L
                ? polygonAuthoringService().findByGeneratedFixtureId(bodyEid, fixtureId)
                : null;

        long authoringId = existingAuthored != null
                ? existingAuthored.authoringId
                : 0L;

        FixtureDefData materialSource = resolvePolygonMaterialSource(bodyEid, fixtureId);

        long fixtureToReplaceId = existingAuthored == null && fixtureId > 0L
                ? fixtureId
                : -1L;

        ApplyAuthoredPolygonCommand cmd = new ApplyAuthoredPolygonCommand(
                world,
                historyIds,
                physicsSelectionService,
                bodyEid,
                authoringId,
                localVertsM,
                vertexCount,
                materialSource,
                fixtureToReplaceId
        );

        if (!cmd.isNoop()) {
            historyManager.execute(cmd);
        }

        polygonDrawSession.cancel();
    }

    private FixtureDefData resolvePolygonMaterialSource(int bodyEid, long fixtureId) {
        FixtureDefData selected = fixtureId > 0L
                ? getSelectedFixture(bodyEid, fixtureId)
                : null;

        if (selected != null) {
            return selected.copy();
        }

        FixtureDefData fallback = FixtureCommandSupport.createDefaultFixture();
        fallback.shapeType = FixtureDefData.SHAPE_POLYGON;
        fallback.offsetX = 0f;
        fallback.offsetY = 0f;
        fallback.angleDeg = 0f;
        fallback.polyVerts = new float[0];
        fallback.polyCount = 0;

        return fallback;
    }

    private float[] polygonSessionToBodyLocalMeters(int bodyEid) {
        if (polygonDrawSession == null || polygonDrawSession.pointCount() < 3) {
            return new float[0];
        }

        int count = polygonDrawSession.pointCount();
        float[] out = new float[count * 2];

        Array<Vector2> points = polygonDrawSession.points();
        if (points == null || points.size < count) {
            return new float[0];
        }

        for (int i = 0; i < count; i++) {
            Vector2 p = points.get(i);
            if (p == null) {
                return new float[0];
            }

            worldToBodyLocalPx(bodyEid, p.x, p.y, tmpPolygonLocalPx);

            out[i * 2] = physicsService.pxToM(tmpPolygonLocalPx.x);
            out[i * 2 + 1] = physicsService.pxToM(tmpPolygonLocalPx.y);
        }

        return out;
    }

    private PhysicsPolygonAuthoringService polygonAuthoringService() {
        if (polygonAuthoringService == null) {
            polygonAuthoringService = new PhysicsPolygonAuthoringService(world);
        }
        return polygonAuthoringService;
    }

    private boolean processPolygonDrawMode(float mx, float my) {
        if (polygonDrawSession == null || !polygonDrawSession.isActive()) {
            return false;
        }

        // Polygon mode takes over the viewport
        cancelLassoIfNeeded();
        if (selectionService != null) selectionService.clearHoveredEntity();
        physicsSelectionService.clearHover();

        // ESC = annulation
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            polygonDrawSession.cancel();
            return true;
        }

        // Backspace to remove last vertice
        if (Gdx.input.isKeyJustPressed(Input.Keys.BACKSPACE)) {
            polygonDrawSession.removeLastPoint();
            return true;
        }

        // Clic gauche = add point ou close+commit
        if (inputState.leftJustPressed()) {
            float closeRadiusWorld = HandleHelper.pxToWorld(
                    worldCam,
                    GizmoDrawHelper.SHAPE_VERTEX_HANDLE_SIZE_PX * 0.5f + HOVER_TOLER_PX
            );

            if (polygonDrawSession.tryCloseFromPoint(mx, my, closeRadiusWorld)) {
                commitPolygonDrawSession();
                resetPressState();
                return true;
            }

            if (!polygonDrawSession.wouldDuplicateLast(mx, my)) {
                polygonDrawSession.addPoint(mx, my);
            }
            resetPressState();
            return true;
        }

        return true;
    }

    private AuthoredPolygonData findAuthoredPolygonByGeneratedFixture(int bodyEid, long fixtureId) {
        if (bodyEid < 0 || fixtureId <= 0L) return null;

        PhysicsAuthoringComponent authoring =
                mPhysicsAuthoring != null ? mPhysicsAuthoring.getSafe(bodyEid, null) : null;

        if (authoring == null || authoring.polygons == null) return null;

        for (int i = 0; i < authoring.polygons.size; i++) {
            AuthoredPolygonData polygon = authoring.polygons.get(i);
            if (polygon == null || polygon.generatedFixtureIds == null) continue;

            if (containsFixtureId(polygon.generatedFixtureIds, fixtureId)) {
                return polygon;
            }
        }

        return null;
    }

    private static boolean containsFixtureId(int[] ids, long fixtureId) {
        if (ids == null || fixtureId <= 0L) return false;

        for (int id : ids) {
            if (id == fixtureId) return true;
        }

        return false;
    }

    private void applyAuthoredPolygonOffsetLive(int bodyEid,
                                                AuthoredPolygonData polygon,
                                                float offsetX,
                                                float offsetY) {
        if (polygon == null) return;

        polygon.offsetX = offsetX;
        polygon.offsetY = offsetY;

        PhysicsFixturesComponent fixtures =
                mFixDefs != null ? mFixDefs.getSafe(bodyEid, null) : null;

        if (fixtures != null && fixtures.fixtures != null && polygon.generatedFixtureIds != null) {
            for (int i = 0; i < fixtures.fixtures.size; i++) {
                FixtureDefData fixture = fixtures.fixtures.get(i);
                if (fixture == null) continue;

                if (containsFixtureId(polygon.generatedFixtureIds, fixture.fixtureId)) {
                    fixture.offsetX = offsetX;
                    fixture.offsetY = offsetY;
                }
            }
        }

        FixtureCommandSupport.markDirty(world, bodyEid);
    }

    private boolean hasAuthoringFixtures(int bodyEid) {
        PhysicsFixturesComponent fixtures = mFixDefs != null ? mFixDefs.getSafe(bodyEid, null) : null;
        return fixtures != null && fixtures.hasFixtures();
    }

    private int pickFixtureOnBody(int bodyEid, float mouseX, float mouseY, float tolWorld) {
        if (!isFixtureBodyPickCandidate(bodyEid)) return PhysicsSelectionService.NO_FIXTURE;
        tmp2Vec.set(mouseX, mouseY);
        removeDisplayOffset(bodyEid, tmp2Vec);
        return fixturePickingService.pickFixtureId(bodyEid, tmp2Vec.x, tmp2Vec.y, tolWorld);
    }

    private FixtureHit findTopmostFixtureHit(float mouseX, float mouseY, float tolWorld) {
        EntitySubscription sub = world.getAspectSubscriptionManager().get(
                Aspect.all(PhysicsFixturesComponent.class)
        );
        IntBag bag = sub.getEntities();
        int[] data = bag.getData();

        int bestBody = -1;
        int bestFixtureId = PhysicsSelectionService.NO_FIXTURE;
        int bestLayer = Integer.MIN_VALUE;
        int bestZ = Integer.MIN_VALUE;

        for (int i = 0, n = bag.size(); i < n; i++) {
            int bodyEid = data[i];
            if (bodyEid == -1) continue;
            if (!isFixtureBodyPickCandidate(bodyEid)) continue;

            int fixtureId = pickFixtureOnBody(bodyEid, mouseX, mouseY, tolWorld);
            if (fixtureId <= 0L) continue;

            int layer = layerOf(bodyEid);
            int z = zOf(bodyEid);
            if (!isBetterHit(bestBody, bestLayer, bestZ, bodyEid, layer, z)) continue;

            bestBody = bodyEid;
            bestFixtureId = fixtureId;
            bestLayer = layer;
            bestZ = z;
        }

        return bestFixtureId > 0L ? new FixtureHit(bestBody, bestFixtureId) : null;
    }

    private boolean isFixturePickingEnabled() {
        return physicsService != null
                && isExplicitPhysicsEditMode();
    }

    private boolean isJointPickingEnabled() {
        return physicsService != null
                && physicsService.isAvailable()
                && isExplicitPhysicsEditMode();
    }

    private boolean isFixtureBodyPickCandidate(int bodyEid) {
        return bodyEid >= 0
                && physicsService != null
                && (mJointBase == null || !mJointBase.has(bodyEid))
                && hasAuthoringFixtures(bodyEid)
                && isSelectableInViewport(bodyEid);
    }

    private boolean computeMotorJointLineForPicking(int jointEid, PhysicsJointComponent base, Vector2 outA, Vector2 outB) {
        if (physicsService == null || base == null) return false;

        PhysicsMotorJointComponent motor = world.getMapper(PhysicsMotorJointComponent.class).getSafe(jointEid, null);
        if (motor == null) return false;

        int aEid = base.aEid;
        TransformComponent ta = mT.getSafe(aEid, null);
        if (aEid < 0 || ta == null) return false;

        float ppm = resolvePixelsPerMeter();

        float cos = MathUtils.cos(ta.rotationRad);
        float sin = MathUtils.sin(ta.rotationRad);

        float dxWu = motor.linearOffsetX * ppm;
        float dyWu = motor.linearOffsetY * ppm;

        float targetX = ta.x + dxWu * cos - dyWu * sin;
        float targetY = ta.y + dxWu * sin + dyWu * cos;

        outA.set(ta.x, ta.y);
        outB.set(targetX, targetY);

        applyDisplayOffset(aEid, outA);
        applyDisplayOffset(aEid, outB);
        return true;
    }

    private boolean computePulleyJointForPicking(int jointEid,
                                                 PhysicsJointComponent base,
                                                 Vector2 outGroundA,
                                                 Vector2 outAnchorA,
                                                 Vector2 outGroundB,
                                                 Vector2 outAnchorB) {
        if (physicsService == null || base == null) return false;

        PhysicsPulleyJointComponent pulley = mPulley.getSafe(jointEid, null);
        if (pulley == null) return false;

        int aEid = base.aEid;
        int bEid = base.bEid;
        TransformComponent ta = mT.getSafe(aEid, null);
        TransformComponent tb = mT.getSafe(bEid, null);
        if (aEid < 0 || bEid < 0 || aEid == bEid || ta == null || tb == null) return false;

        float ppm = resolvePixelsPerMeter();

        outGroundA.set(pulley.groundAx * ppm, pulley.groundAy * ppm);
        outGroundB.set(pulley.groundBx * ppm, pulley.groundBy * ppm);

        float ax = ta.x + rotateX(base.anchorAx * ppm, base.anchorAy * ppm, ta.rotationRad);
        float ay = ta.y + rotateY(base.anchorAx * ppm, base.anchorAy * ppm, ta.rotationRad);
        float bx = tb.x + rotateX(base.anchorBx * ppm, base.anchorBy * ppm, tb.rotationRad);
        float by = tb.y + rotateY(base.anchorBx * ppm, base.anchorBy * ppm, tb.rotationRad);

        outAnchorA.set(ax, ay);
        outAnchorB.set(bx, by);

        applyDisplayOffset(aEid, outGroundA);
        applyDisplayOffset(aEid, outAnchorA);
        applyDisplayOffset(bEid, outGroundB);
        applyDisplayOffset(bEid, outAnchorB);

        return true;
    }

    private boolean computeGearJointLineForPicking(int jointEid, PhysicsJointComponent base, Vector2 outA, Vector2 outB) {
        if (physicsService == null || base == null) return false;

        PhysicsGearJointComponent gear = mGear.getSafe(jointEid, null);
        if (gear == null) return false;

        PhysicsJointComponent src1 = mJointBase.getSafe(gear.joint1Eid, null);
        PhysicsJointComponent src2 = mJointBase.getSafe(gear.joint2Eid, null);
        if (src1 == null || src2 == null) return false;

        boolean ok1 = false;
        boolean ok2 = false;

        if (src1.type == PhysicsJointComponent.TYPE_REVOLUTE) {
            ok1 = physicsService.computeRevoluteJointPivotWU(gear.joint1Eid, outA);
            if (ok1) applyDisplayOffset(src1.aEid, outA);
        } else if (src1.type == PhysicsJointComponent.TYPE_PRISMATIC) {
            ok1 = physicsService.computePrismaticJointPivotWU(gear.joint1Eid, outA);
            if (ok1) applyDisplayOffset(src1.aEid, outA);
        }

        if (src2.type == PhysicsJointComponent.TYPE_REVOLUTE) {
            ok2 = physicsService.computeRevoluteJointPivotWU(gear.joint2Eid, outB);
            if (ok2) applyDisplayOffset(src2.aEid, outB);
        } else if (src2.type == PhysicsJointComponent.TYPE_PRISMATIC) {
            ok2 = physicsService.computePrismaticJointPivotWU(gear.joint2Eid, outB);
            if (ok2) applyDisplayOffset(src2.aEid, outB);
        }

        return ok1 && ok2;
    }

    private static float rotateX(float x, float y, float angleRad) {
        float cos = MathUtils.cos(angleRad);
        float sin = MathUtils.sin(angleRad);
        return x * cos - y * sin;
    }

    private static float rotateY(float x, float y, float angleRad) {
        float cos = MathUtils.cos(angleRad);
        float sin = MathUtils.sin(angleRad);
        return x * sin + y * cos;
    }

    private float resolvePixelsPerMeter() {
        ProjectConfig cfg = ProjectConfig.getInstance();
        SceneMeta meta = cfg.getCurrentSceneMeta();
        return meta.pixelsPerMeter;
    }

    private record FixtureHit(int bodyEid, int fixtureId) {
    }

    private void beginHandleDragIfPossible(float mx, float my,
                                           InputManipulationContext.Handle hovered,
                                           IntArray viewportSel) {
        lastPressHit = null;

        if (viewportSel.size != 1) return;

        int e0 = viewportSel.get(0);
        if (isLightEntity(e0)) return;
        TransformComponent t0 = mT.get(e0);
        if (t0 == null) return;

        gizmoHistoryIds.clear();
        gizmoBefore.clear();

        for (int i = 0; i < viewportSel.size; i++) {
            int e = viewportSel.get(i);
            if (!world.getEntityManager().isActive(e)) continue;

            TransformComponent t = mT.get(e);
            if (t == null) continue;

            long historyId = historyIds.ensureForEntity(e);
            gizmoHistoryIds.add(historyId);
            gizmoBefore.add(GizmoTransformCommand.Snapshot.of(t));
        }

        if (hovered == InputManipulationContext.Handle.ROTATE) {
            float px = t0.x + t0.originX;
            float py = t0.y + t0.originY;
            ctx.beginRotate(px, py, mx, my, t0.rotationRad);
        } else {
            ctx.beginResize(hovered, mx, my, t0.scaleX, t0.scaleY);
        }
    }

    private void onClickReleased() {
        if (lastPressHit != null && lastPressHit >= 0) {
            if (lastPressCtrl) {
                activateLayerForEntity(lastPressHit);
                selectionService.toggle(lastPressHit);
            }
        }
    }

    private void onGizmoDragging(float mx, float my, int e0, boolean leftReleased) {
        ctx.updateDrag(mx, my);

        if (e0 != -1) {
            if (ctx.mode() == InputManipulationContext.Mode.HANDLE_ROTATE
                    || ctx.activeHandle() == InputManipulationContext.Handle.ROTATE) {
                applyRotate(e0, mx, my);
            } else {
                applyResize(e0, mx, my, ctx.activeHandle());
            }
        }

        if (!leftReleased) return;

        TransformOp type =
                (ctx.mode() == InputManipulationContext.Mode.HANDLE_ROTATE
                        || ctx.activeHandle() == InputManipulationContext.Handle.ROTATE)
                        ? TransformOp.ROTATE
                        : TransformOp.SCALE;

        GizmoTransformCommand cmd = new GizmoTransformCommand(world, historyIds, type);
        for (int i = 0; i < gizmoHistoryIds.size; i++) {
            long historyId = gizmoHistoryIds.get(i);
            GizmoTransformCommand.Snapshot before = gizmoBefore.get(i);
            GizmoTransformCommand.Snapshot after =
                    GizmoTransformCommand.Snapshot.of(
                            Objects.requireNonNull(findTransformByHistoryId(historyId))
                    );
            cmd.addEntry(historyId, before, after);
        }

        gizmoHistoryIds.clear();
        gizmoBefore.clear();

        if (e0 != -1) {
            EventFlow.i().publish(new EventFlow.EntityChanged(e0, ctx.currentOp(), MY_TAG));
        }

        ctx.end();

        if (!cmd.isNoop()) {
            historyManager.execute(cmd);
        }
    }

    private void onLassoDragging(float mx, float my) {
        lassoEnd.set(mx, my);
        if (gizmoSystem != null) {
            gizmoSystem.setLassoRect(true, lassoStart.x, lassoStart.y, lassoEnd.x, lassoEnd.y);
        }
    }

    private void onLassoReleased() {
        applyLassoSelection();
        lassoActive = false;

        if (gizmoSystem != null) {
            gizmoSystem.setLassoRect(false, 0f, 0f, 0f, 0f);
        }
    }

    private void applyLassoSelection() {
        float x0 = lassoStart.x;
        float y0 = lassoStart.y;
        float x1 = lassoEnd.x;
        float y1 = lassoEnd.y;

        float minX = Math.min(x0, x1);
        float maxX = Math.max(x0, x1);
        float minY = Math.min(y0, y1);
        float maxY = Math.max(y0, y1);

        IntArray hits = new IntArray();

        EntitySubscription sub = world.getAspectSubscriptionManager().get(
                Aspect.all(OrientedBoundsComponent.class, VisibilityComponent.class)
                        .exclude(PointLightComponent.class, ConeLightComponent.class)
        );

        IntBag bag = sub.getEntities();
        int[] data = bag.getData();

        for (int i = 0, n = bag.size(); i < n; i++) {
            int e = data[i];

            if (!isSelectableInViewport(e)) continue;

            float[] obb = computeOBBWorldCorners(e);
            if (obb == null) continue;

            float cx = (obb[0] + obb[4]) * 0.5f;
            float cy = (obb[1] + obb[5]) * 0.5f;

            if (cx >= minX && cx <= maxX && cy >= minY && cy <= maxY) {
                hits.add(e);
            }
        }

        if (hits.size == 0) {
            selectionService.clearSelection();
            return;
        }

        selectionService.selectOnly(hits.get(0));
        for (int i = 1; i < hits.size; i++) {
            selectionService.toggle(hits.get(i));
        }
    }

    private boolean onFreeMoveDragging(float mx, float my, IntArray viewportSel) {
        if (isExplicitPhysicsEditMode()) return false;
        if (viewportSel.size == 0) return false;
        if (oldDrag.isZero()) return false;
        if (!pressStartedOnSelection) return false;
        if (lastPressOnHandle) return false;
        if (lassoActive) return false;

        float dx = mx - oldDrag.x;
        float dy = my - oldDrag.y;

        if (dx == 0f && dy == 0f) return false;

        if (!translatingActive) {
            translatingActive = true;
            gizmoHistoryIds.clear();
            gizmoBefore.clear();

            for (int i = 0; i < viewportSel.size; i++) {
                int e = viewportSel.get(i);
                if (!world.getEntityManager().isActive(e)) continue;

                TransformComponent t = mT.get(e);
                if (t == null) continue;

                long historyId = historyIds.ensureForEntity(e);
                gizmoHistoryIds.add(historyId);
                gizmoBefore.add(GizmoTransformCommand.Snapshot.of(t));
            }
        }

        for (int i = 0; i < viewportSel.size; i++) {
            int e = viewportSel.get(i);
            if (!world.getEntityManager().isActive(e)) continue;

            TransformComponent t = mT.get(e);
            if (t == null) continue;

            t.x = t.x + dx;
            t.y = t.y + dy;
            if (dirty != null) dirty.geometry(e, GeometryDirty.POSITION);
        }

        oldDrag.set(mx, my);
        return false;
    }

    private boolean onFreeMoveReleased(IntArray viewportSel) {
        if (!translatingActive) return false;

        GizmoTransformCommand cmd = new GizmoTransformCommand(world, historyIds, TransformOp.MOVE);

        for (int i = 0; i < gizmoHistoryIds.size; i++) {
            long historyId = gizmoHistoryIds.get(i);
            GizmoTransformCommand.Snapshot before = gizmoBefore.get(i);
            GizmoTransformCommand.Snapshot after =
                    GizmoTransformCommand.Snapshot.of(
                            Objects.requireNonNull(findTransformByHistoryId(historyId))
                    );
            cmd.addEntry(historyId, before, after);
        }

        gizmoHistoryIds.clear();
        gizmoBefore.clear();
        translatingActive = false;

        if (!cmd.isNoop()) {
            historyManager.execute(cmd);
            if (viewportSel.size == 1) {
                EventFlow.i().publish(new EventFlow.EntityChanged(viewportSel.get(0), TransformOp.MOVE, MY_TAG));
            }
        }

        return true;
    }

    private FixtureDefData getSelectedFixture(int bodyEid, long fixtureId) {
        if (bodyEid < 0 || fixtureId <= 0L) return null;
        PhysicsFixturesComponent fixtures = mFixDefs != null ? mFixDefs.getSafe(bodyEid, null) : null;
        if (fixtures == null || !fixtures.hasFixtures()) return null;

        for (int i = 0, n = fixtures.fixtures.size; i < n; i++) {
            FixtureDefData fixture = fixtures.fixtures.get(i);
            if (fixture == null) continue;
            if (fixture.fixtureId == fixtureId) return fixture;
        }
        return null;
    }

    private boolean tryBeginFixtureMove(float mx, float my) {
        if (!isExplicitPhysicsEditMode()) return false;
        if (physicsService == null || !physicsService.isAvailable()) return false;

        int bodyEid = physicsSelectionService.getFocusedBodyEid();
        int fixtureId = physicsSelectionService.getSelectedFixtureId();
        if (bodyEid < 0 || fixtureId <= 0L) return false;

        FixtureDefData fixture = getSelectedFixture(bodyEid, fixtureId);
        if (fixture == null) return false;

        float tolWorld = PICK_TOLERANCE_PX * HandleHelper.worldUnitsPerPixel(worldCam);
        long pickedFixtureId = pickFixtureOnBody(bodyEid, mx, my, tolWorld);
        if (pickedFixtureId == fixtureId
                && !isFixtureGeometryEditable(world, bodyEid, fixtureId)) return false;

        AuthoredPolygonData authored = findAuthoredPolygonByGeneratedFixture(bodyEid, fixtureId);

        if (authored != null) {
            if (!containsFixtureId(authored.generatedFixtureIds, pickedFixtureId)) {
                return false;
            }

            movingFixtureActive = true;
            movingAuthoredPolygonActive = true;

            movingFixtureBodyEid = bodyEid;
            movingFixtureId = fixtureId;

            movingAuthoredPolygonId = authored.authoringId;
            movingAuthoredPolygonBeforeOffsetX = authored.offsetX;
            movingAuthoredPolygonBeforeOffsetY = authored.offsetY;

            oldDrag.set(mx, my);
            return true;
        }

        if (pickedFixtureId != fixtureId) return false;

        movingFixtureActive = true;
        movingAuthoredPolygonActive = false;

        movingFixtureBodyEid = bodyEid;
        movingFixtureId = fixtureId;
        movingFixtureBeforeOffsetX = fixture.offsetX;
        movingFixtureBeforeOffsetY = fixture.offsetY;

        oldDrag.set(mx, my);
        return true;
    }

    private boolean onFixtureMoveDragging(float mx, float my) {
        if (!movingFixtureActive) return false;

        if (movingAuthoredPolygonActive) {
            return onAuthoredPolygonMoveDragging(mx, my);
        }

        FixtureDefData fixture = getSelectedFixture(movingFixtureBodyEid, movingFixtureId);
        TransformComponent bodyT = mT.getSafe(movingFixtureBodyEid, null);
        if (fixture == null || bodyT == null) return false;

        float dxWorld = mx - oldDrag.x;
        float dyWorld = my - oldDrag.y;
        if (dxWorld == 0f && dyWorld == 0f) return true;

        float cos = MathUtils.cos(bodyT.rotationRad);
        float sin = MathUtils.sin(bodyT.rotationRad);

        // monde -> local body
        float dxLocalPx = dxWorld * cos + dyWorld * sin;
        float dyLocalPx = -dxWorld * sin + dyWorld * cos;

        // fixture offsets stored in local Box2D authoring meters
        fixture.offsetX += physicsService.pxToM(dxLocalPx);
        fixture.offsetY += physicsService.pxToM(dyLocalPx);

        FixtureCommandSupport.markDirty(world, movingFixtureBodyEid);
        oldDrag.set(mx, my);
        return true;
    }

    private boolean onAuthoredPolygonMoveDragging(float mx, float my) {
        AuthoredPolygonData authored =
                findAuthoredPolygonByGeneratedFixture(movingFixtureBodyEid, movingFixtureId);

        TransformComponent bodyT = mT.getSafe(movingFixtureBodyEid, null);

        if (authored == null || bodyT == null) return false;

        float dxWorld = mx - oldDrag.x;
        float dyWorld = my - oldDrag.y;

        if (dxWorld == 0f && dyWorld == 0f) return true;

        float cos = MathUtils.cos(bodyT.rotationRad);
        float sin = MathUtils.sin(bodyT.rotationRad);

        float dxLocalPx = dxWorld * cos + dyWorld * sin;
        float dyLocalPx = -dxWorld * sin + dyWorld * cos;

        float nextOffsetX = authored.offsetX + physicsService.pxToM(dxLocalPx);
        float nextOffsetY = authored.offsetY + physicsService.pxToM(dyLocalPx);

        applyAuthoredPolygonOffsetLive(
                movingFixtureBodyEid,
                authored,
                nextOffsetX,
                nextOffsetY
        );

        oldDrag.set(mx, my);
        return true;
    }

    private boolean onFixtureMoveReleased() {
        if (!movingFixtureActive) return false;

        if (movingAuthoredPolygonActive) {
            return onAuthoredPolygonMoveReleased();
        }

        FixtureDefData fixture = getSelectedFixture(movingFixtureBodyEid, movingFixtureId);
        if (fixture == null) {
            clearFixtureMoveState();
            return true;
        }

        float afterOffsetX = fixture.offsetX;
        float afterOffsetY = fixture.offsetY;

        MoveFixtureCommand cmd = new MoveFixtureCommand(
                world,
                historyIds,
                physicsSelectionService,
                movingFixtureBodyEid,
                movingFixtureId,
                movingFixtureBeforeOffsetX,
                movingFixtureBeforeOffsetY,
                afterOffsetX,
                afterOffsetY
        );

        if (!cmd.isNoop()) {
            historyManager.execute(cmd);
        } else {
            FixtureCommandSupport.markDirty(world, movingFixtureBodyEid);
            FixtureCommandSupport.publishStructureChanged(movingFixtureBodyEid, this);
        }

        clearFixtureMoveState();
        return true;
    }

    private boolean onAuthoredPolygonMoveReleased() {
        AuthoredPolygonData authored =
                findAuthoredPolygonByGeneratedFixture(movingFixtureBodyEid, movingFixtureId);

        if (authored == null) {
            clearFixtureMoveState();
            return true;
        }

        MoveAuthoredPolygonCommand cmd = new MoveAuthoredPolygonCommand(
                world,
                historyIds,
                physicsSelectionService,
                movingFixtureBodyEid,
                movingAuthoredPolygonId,
                movingFixtureId,
                movingAuthoredPolygonBeforeOffsetX,
                movingAuthoredPolygonBeforeOffsetY,
                authored.offsetX,
                authored.offsetY
        );

        if (!cmd.isNoop()) {
            historyManager.execute(cmd);
        } else {
            FixtureCommandSupport.markDirty(world, movingFixtureBodyEid);
            FixtureCommandSupport.publishStructureChanged(movingFixtureBodyEid, this);
        }

        clearFixtureMoveState();
        return true;
    }

    private void clearFixtureMoveState() {
        movingFixtureActive = false;
        movingFixtureBodyEid = -1;
        movingFixtureId = PhysicsSelectionService.NO_FIXTURE;

        movingFixtureBeforeOffsetX = 0f;
        movingFixtureBeforeOffsetY = 0f;

        movingAuthoredPolygonActive = false;
        movingAuthoredPolygonId = 0L;
        movingAuthoredPolygonBeforeOffsetX = 0f;
        movingAuthoredPolygonBeforeOffsetY = 0f;
    }

    private boolean isExplicitPhysicsEditMode() {
        return physicsSelectionService.getFocusedBodyEid() >= 0;
    }

    static boolean isFixtureGeometryEditable(World world, int bodyEid, long fixtureId) {
        return !SpatialOwnedFixtureSupport.isOwned(world, bodyEid, fixtureId);
    }

    private boolean isInSelection(IntArray sel, int e) {
        for (int i = 0, n = sel.size; i < n; i++) {
            if (sel.get(i) == e) return true;
        }
        return false;
    }

    private boolean isSelectableInViewport(int e) {
        if (selectionService != null) {
            return selectionService.isSelectableInViewport(e);
        }
        return !mVis.has(e) || mVis.get(e).isVisible();
    }

    private IntArray filterSelectableInViewport(IntArray selection) {
        if (selectionService == null) return selection;

        IntArray filtered = new IntArray(selection.size);
        for (int i = 0, n = selection.size; i < n; i++) {
            int e = selection.get(i);
            if (selectionService.isSelectableInViewport(e)) {
                filtered.add(e);
            }
        }
        return filtered;
    }

    private TransformComponent findTransformByHistoryId(long historyId) {
        int e = historyIds.entityOfHistoryId(historyId);
        if (e == -1 || !world.getEntityManager().isActive(e)) return null;
        return mT.get(e);
    }

    private void applyResize(int e0, float mx, float my, InputManipulationContext.Handle handle) {
        TransformComponent t = mT.get(e0);
        DimensionsComponent d = mDim.get(e0);
        if (t == null || d == null) return;

        float w0 = d.width;
        float h0 = d.height;
        if (w0 == 0f || h0 == 0f) return;

        float cx = t.x + t.originX;
        float cy = t.y + t.originY;

        float cos = MathUtils.cos(t.rotationRad);
        float sin = MathUtils.sin(t.rotationRad);

        float dx = mx - cx;
        float dy = my - cy;
        float mxLocal = dx * cos - dy * sin;
        float myLocal = dx * sin + dy * cos;

        float sx0w = ctx.dragStartMouseX();
        float sy0w = ctx.dragStartMouseY();
        float dx0 = sx0w - cx;
        float dy0 = sy0w - cy;
        float mxLocal0 = dx0 * cos - dy0 * sin;
        float myLocal0 = dx0 * sin + dy0 * cos;

        float deltaX = mxLocal - mxLocal0;
        float deltaY = myLocal - myLocal0;

        float baseSx = ctx.scaleXstart();
        float baseSy = ctx.scaleYstart();

        final float minScale = 0.01f;

        float sx = baseSx;
        float sy = baseSy;

        switch (handle) {
            case E -> sx = baseSx + (deltaX / w0);
            case W -> sx = baseSx - (deltaX / w0);
            case N -> sy = baseSy + (deltaY / h0);
            case S -> sy = baseSy - (deltaY / h0);
            case NE -> {
                sx = baseSx + (deltaX / w0);
                sy = baseSy + (deltaY / h0);
            }
            case NW -> {
                sx = baseSx - (deltaX / w0);
                sy = baseSy + (deltaY / h0);
            }
            case SE -> {
                sx = baseSx + (deltaX / w0);
                sy = baseSy - (deltaY / h0);
            }
            case SW -> {
                sx = baseSx - (deltaX / w0);
                sy = baseSy - (deltaY / h0);
            }
            default -> {
                return;
            }
        }

        sx = clampScaleAwayFromZero(sx, baseSx, minScale);
        sy = clampScaleAwayFromZero(sy, baseSy, minScale);

        if (inputState.isCtrl()) {
            float k = switch (handle) {
                case E, W -> sx;
                case N, S -> sy;
                case NE, NW, SE, SW -> absMax(sx, sy);
                default -> sx;
            };
            k = clampScaleAwayFromZero(k, absMax(baseSx, baseSy), minScale);
            sx = k;
            sy = k;
        }

        t.scaleX = sx;
        t.scaleY = sy;
        if (dirty != null) dirty.geometry(e0, GeometryDirty.SCALE);
    }

    static float clampScaleAwayFromZero(float value, float fallbackSignValue, float minAbs) {
        if (value > 0f && value < minAbs) return minAbs;
        if (value < 0f && value > -minAbs) return -minAbs;
        if (value == 0f) return fallbackSignValue < 0f ? -minAbs : minAbs;
        return value;
    }

    private static float absMax(float a, float b) {
        return Math.abs(a) >= Math.abs(b) ? a : b;
    }

    private void applyRotate(int entityId, float mx, float my) {
        TransformComponent t = mT.get(entityId);
        if (t == null) return;

        float cx = t.x + t.originX;
        float cy = t.y + t.originY;

        float delta = signedAngleDelta(cx, cy, ctx.lastMouseX(), ctx.lastMouseY(), mx, my);

        t.rotationRad += delta;

        ctx.setLastMouse(mx, my);

        if (dirty != null) dirty.geometry(entityId, GeometryDirty.ROTATION);
    }

    private float signedAngleDelta(float cx, float cy,
                                   float x0, float y0,
                                   float x1, float y1) {
        float a0 = (float) Math.atan2(y0 - cy, x0 - cx);
        float a1 = (float) Math.atan2(y1 - cy, x1 - cx);
        float d = a1 - a0;

        while (d > Math.PI) d -= (float) (Math.PI * 2f);
        while (d < -Math.PI) d += (float) (Math.PI * 2f);

        return d;
    }

    private Integer findTopmostObbHit(float mouseX, float mouseY) {
        EntitySubscription sub = world.getAspectSubscriptionManager().get(
                Aspect.all(OrientedBoundsComponent.class, VisibilityComponent.class)
                        .exclude(PointLightComponent.class, ConeLightComponent.class)
        );
        IntBag bag = sub.getEntities();
        int[] data = bag.getData();

        float tolWorld = PICK_TOLERANCE_PX * HandleHelper.worldUnitsPerPixel(worldCam);

        int bestEntity = -1;
        int bestLayerIndex = Integer.MIN_VALUE;
        int bestZIndex = Integer.MIN_VALUE;

        for (int i = 0, n = bag.size(); i < n; i++) {
            int e = data[i];

            if (!isSelectableInViewport(e)) continue;

            OrientedBoundsComponent b = mOBB.get(e);
            if (b == null) continue;

            float[] obb = computeOBBWorldCorners(e);
            if (obb == null) continue;

            if (!OrientedBoundsHelper.contains(obb, mouseX, mouseY, tolWorld)) continue;

            int layerIndex = (mEntityIndex != null && mEntityIndex.has(e)) ? mEntityIndex.get(e).getLayerIndex() : 0;
            int z = (mEntityIndex != null && mEntityIndex.has(e)) ? mEntityIndex.get(e).getZIndex() : 0;

            if (isBetterHit(bestEntity, bestLayerIndex, bestZIndex, e, layerIndex, z)) {
                bestEntity = e;
                bestLayerIndex = layerIndex;
                bestZIndex = z;
            }
        }

        return bestEntity;
    }

    private Integer findTopmostJointHit(float mouseX, float mouseY) {
        if (!isJointPickingEnabled()) return -1;

        EntitySubscription sub = world.getAspectSubscriptionManager().get(
                Aspect.all(PhysicsJointComponent.class)
        );
        IntBag bag = sub.getEntities();
        int[] data = bag.getData();

        float tolWorld = JOINT_PICK_TOL_PX * HandleHelper.worldUnitsPerPixel(worldCam);
        float tol2 = tolWorld * tolWorld;
        int focusedBodyEid = physicsSelectionService.getFocusedBodyEid();

        int best = -1;
        int bestLayer = Integer.MIN_VALUE;
        int bestZ = Integer.MIN_VALUE;

        for (int i = 0, n = bag.size(); i < n; i++) {
            int jEid = data[i];

            PhysicsJointComponent base = mJointBase.getSafe(jEid, null);
            if (base == null) continue;
            if (!isJointVisibleForPicking(base, focusedBodyEid)) continue;

            boolean hit;

            if (base.type == PhysicsJointComponent.TYPE_PULLEY) {
                Vector2 groundA = tmpA;
                Vector2 anchorA = tmpB;
                Vector2 groundB = new Vector2();
                Vector2 anchorB = new Vector2();

                if (!computePulleyJointForPicking(jEid, base, groundA, anchorA, groundB, anchorB)) continue;

                float d2a = pointSegmentDst2(mouseX, mouseY, groundA.x, groundA.y, anchorA.x, anchorA.y);
                float d2b = pointSegmentDst2(mouseX, mouseY, groundB.x, groundB.y, anchorB.x, anchorB.y);
                float d2top = pointSegmentDst2(mouseX, mouseY, groundA.x, groundA.y, groundB.x, groundB.y);

                boolean hitPoint =
                        Math.abs(mouseX - groundA.x) <= tolWorld && Math.abs(mouseY - groundA.y) <= tolWorld
                                || Math.abs(mouseX - anchorA.x) <= tolWorld && Math.abs(mouseY - anchorA.y) <= tolWorld
                                || Math.abs(mouseX - groundB.x) <= tolWorld && Math.abs(mouseY - groundB.y) <= tolWorld
                                || Math.abs(mouseX - anchorB.x) <= tolWorld && Math.abs(mouseY - anchorB.y) <= tolWorld;

                hit = hitPoint || d2a <= tol2 || d2b <= tol2 || d2top <= tol2;

            } else if (base.type == PhysicsJointComponent.TYPE_DISTANCE
                    || base.type == PhysicsJointComponent.TYPE_PRISMATIC
                    || base.type == PhysicsJointComponent.TYPE_MOTOR
                    || base.type == PhysicsJointComponent.TYPE_GEAR) {
                boolean ok;
                if (base.type == PhysicsJointComponent.TYPE_GEAR) {
                    ok = computeGearJointLineForPicking(jEid, base, tmpA, tmpB);
                } else {
                    ok = computeJointLineForPicking(jEid, base, tmpA, tmpB);
                }
                if (!ok) continue;
                float d2 = pointSegmentDst2(mouseX, mouseY, tmpA.x, tmpA.y, tmpB.x, tmpB.y);
                hit = d2 <= tol2;
            } else if (base.type == PhysicsJointComponent.TYPE_REVOLUTE
                    || base.type == PhysicsJointComponent.TYPE_WHEEL
                    || base.type == PhysicsJointComponent.TYPE_FRICTION
                    || base.type == PhysicsJointComponent.TYPE_WELD) {
                if (!computeJointPivotForPicking(jEid, base, tmpA)) continue;
                hit = Math.abs(mouseX - tmpA.x) <= tolWorld && Math.abs(mouseY - tmpA.y) <= tolWorld;
            } else {
                continue;
            }

            if (!hit) continue;

            int layer = Math.max(layerOf(base.aEid), layerOf(base.bEid));
            int z = Math.max(zOf(base.aEid), zOf(base.bEid));
            if (!isBetterHit(best, bestLayer, bestZ, jEid, layer, z)) continue;

            best = jEid;
            bestLayer = layer;
            bestZ = z;
        }

        return best;
    }

    private boolean isJointVisibleForPicking(PhysicsJointComponent base, int focusedBodyEid) {
        if (base == null) return false;
        if (!isExplicitPhysicsEditMode()
                && focusedBodyEid >= 0 && !jointTouchesBody(base, focusedBodyEid)) {
            return false;
        }
        return isSelectableInViewport(base.aEid) || isSelectableInViewport(base.bEid);
    }

    private boolean jointTouchesBody(PhysicsJointComponent base, int bodyEid) {
        return base != null && bodyEid >= 0 && (base.aEid == bodyEid || base.bEid == bodyEid);
    }

    private boolean computeJointLineForPicking(int jointEid, PhysicsJointComponent base, Vector2 outA, Vector2 outB) {
        if (physicsService == null || base == null) return false;

        if (base.type == PhysicsJointComponent.TYPE_DISTANCE) {
            if (!physicsService.computeDistanceJointEndpointsWU(jointEid, outA, outB)) return false;
            applyDisplayOffset(base.aEid, outA);
            applyDisplayOffset(base.bEid, outB);
            return true;
        }

        if (base.type == PhysicsJointComponent.TYPE_MOTOR) {
            return computeMotorJointLineForPicking(jointEid, base, outA, outB);
        }

        if (base.type == PhysicsJointComponent.TYPE_PRISMATIC) {
            if (!physicsService.computePrismaticJointGizmoWU(jointEid, outA, outB)) return false;
            applyDisplayOffset(base.aEid, outA);
            applyDisplayOffset(base.aEid, outB);
            return true;
        }

        return false;
    }

    private boolean computeJointPivotForPicking(int jointEid, PhysicsJointComponent base, Vector2 outPivot) {
        if (physicsService == null || base == null) return false;

        boolean ok;
        if (base.type == PhysicsJointComponent.TYPE_REVOLUTE) {
            ok = physicsService.computeRevoluteJointPivotWU(jointEid, outPivot);
            if (ok) applyDisplayOffset(base.aEid, outPivot);
            return ok;
        }
        if (base.type == PhysicsJointComponent.TYPE_WHEEL) {
            ok = physicsService.computeWheelJointPivotWU(jointEid, outPivot);
            if (ok) applyDisplayOffset(base.aEid, outPivot);
            return ok;
        }
        if (base.type == PhysicsJointComponent.TYPE_PRISMATIC) {
            ok = physicsService.computePrismaticJointPivotWU(jointEid, outPivot);
            if (ok) applyDisplayOffset(base.aEid, outPivot);
            return ok;
        }
        if (base.type == PhysicsJointComponent.TYPE_FRICTION || base.type == PhysicsJointComponent.TYPE_WELD) {
            ok = physicsService.computeAnchorWorldWU(base.aEid, base.anchorAx, base.anchorAy, outPivot);
            if (ok) applyDisplayOffset(base.aEid, outPivot);
            return ok;
        }
        return false;
    }

    private Integer findTopmostLightHit(float mouseX, float mouseY) {
        EntitySubscription sub = world.getAspectSubscriptionManager().get(
                Aspect.all(TransformComponent.class)
                        .one(PointLightComponent.class, ConeLightComponent.class)
        );
        IntBag bag = sub.getEntities();
        int[] data = bag.getData();

        float wpp = HandleHelper.worldUnitsPerPixel(worldCam);
        float halfWorld = (LIGHT_ICON_SIZE_PX * 0.5f + LIGHT_PICK_TOL_PX) * wpp;

        int bestEntity = -1;
        int bestLayerIndex = Integer.MIN_VALUE;
        int bestZIndex = Integer.MIN_VALUE;

        for (int i = 0, n = bag.size(); i < n; i++) {
            int e = data[i];
            if (!isSelectableInViewport(e)) continue;

            TransformComponent t = mT.getSafe(e, null);
            if (t == null) continue;

            // Studio picking uses logical editing space.
            // Parallax is preview/runtime-only and must not affect light picking.
            float cx = t.x;
            float cy = t.y;

            if (!HandleHelper.insideSquare(mouseX, mouseY, cx, cy, halfWorld)) continue;

            int layerIndex = (mEntityIndex != null && mEntityIndex.has(e)) ? mEntityIndex.get(e).getLayerIndex() : 0;
            int z = (mEntityIndex != null && mEntityIndex.has(e)) ? mEntityIndex.get(e).getZIndex() : 0;

            if (isBetterHit(bestEntity, bestLayerIndex, bestZIndex, e, layerIndex, z)) {
                bestEntity = e;
                bestLayerIndex = layerIndex;
                bestZIndex = z;
            }
        }

        return bestEntity;
    }

    static boolean isBetterHit(int bestEntity, int bestLayer, int bestZ, int candidate, int layer, int z) {
        return bestEntity == -1
                || layer > bestLayer
                || (layer == bestLayer && z > bestZ)
                || (layer == bestLayer && z == bestZ && candidate > bestEntity);
    }

    private int layerOf(int e) {
        return (mEntityIndex != null && mEntityIndex.has(e)) ? mEntityIndex.get(e).getLayerIndex() : 0;
    }

    private int zOf(int e) {
        return (mEntityIndex != null && mEntityIndex.has(e)) ? mEntityIndex.get(e).getZIndex() : 0;
    }

    private void applyDisplayOffset(int entityId, Vector2 p) {
        // See applyDisplayOffset(int, float[], int): Studio picking stays in logical
        // world space even if preview/runtime display offsets exist.
    }

    private void removeDisplayOffset(int entityId, Vector2 p) {
        // No-op for the same reason as applyDisplayOffset: mouse/edit coordinates are
        // already logical Studio world coordinates, not runtime display coordinates.
    }

    private static float pointSegmentDst2(float px, float py, float ax, float ay, float bx, float by) {
        float abx = bx - ax, aby = by - ay;
        float apx = px - ax, apy = py - ay;

        float abLen2 = abx * abx + aby * aby;
        if (abLen2 <= 1e-12f) {
            float dx = px - ax, dy = py - ay;
            return dx * dx + dy * dy;
        }

        float t = (apx * abx + apy * aby) / abLen2;
        if (t < 0f) t = 0f;
        else if (t > 1f) t = 1f;

        float cx = ax + abx * t;
        float cy = ay + aby * t;
        float dx = px - cx, dy = py - cy;
        return dx * dx + dy * dy;
    }

    private InputManipulationContext.Handle detectHandleHover(int entityId, float mx, float my) {
        if (isLightEntity(entityId)) return InputManipulationContext.Handle.NONE;
        float[] obb = computeOBBWorldCorners(entityId);
        if (obb == null) return InputManipulationContext.Handle.NONE;

        float halfWorld = HandleHelper.pxToWorld(
                worldCam,
                (GizmoDrawHelper.HANDLE_SIZE_PX * 0.5f + HOVER_TOLER_PX)
        );

        if (HandleHelper.insideSquare(mx, my, HandleLayout.swX(obb), HandleLayout.swY(obb), halfWorld))
            return InputManipulationContext.Handle.SW;
        if (HandleHelper.insideSquare(mx, my, HandleLayout.seX(obb), HandleLayout.seY(obb), halfWorld))
            return InputManipulationContext.Handle.SE;
        if (HandleHelper.insideSquare(mx, my, HandleLayout.neX(obb), HandleLayout.neY(obb), halfWorld))
            return InputManipulationContext.Handle.NE;
        if (HandleHelper.insideSquare(mx, my, HandleLayout.nwX(obb), HandleLayout.nwY(obb), halfWorld))
            return InputManipulationContext.Handle.NW;

        if (HandleHelper.insideSquare(mx, my, HandleLayout.midSX(obb), HandleLayout.midSY(obb), halfWorld))
            return InputManipulationContext.Handle.S;
        if (HandleHelper.insideSquare(mx, my, HandleLayout.midEX(obb), HandleLayout.midEY(obb), halfWorld))
            return InputManipulationContext.Handle.E;
        if (HandleHelper.insideSquare(mx, my, HandleLayout.midNX(obb), HandleLayout.midNY(obb), halfWorld))
            return InputManipulationContext.Handle.N;
        if (HandleHelper.insideSquare(mx, my, HandleLayout.midWX(obb), HandleLayout.midWY(obb), halfWorld))
            return InputManipulationContext.Handle.W;

        float rotateOffsetWorld = HandleHelper.pxToWorld(worldCam, GizmoDrawHelper.ROTATE_OFFSET_PX);
        HandleLayout.rotateHandle(obb, rotateOffsetWorld, tmp2);

        if (HandleHelper.insideSquare(mx, my, tmp2[0], tmp2[1], halfWorld)) {
            return InputManipulationContext.Handle.ROTATE;
        }

        return InputManipulationContext.Handle.NONE;
    }

    private boolean tryBeginLightRadiusDrag(float mx, float my, int entityId) {
        if (!isLightEntity(entityId)) return false;
        if (!isSelectableInViewport(entityId)) return false;

        TransformComponent t = mT.getSafe(entityId, null);
        if (t == null) return false;

        computeLightRadiusHandleWorld(entityId, t, tmp2Vec);
        float halfWorld = HandleHelper.pxToWorld(worldCam, GizmoDrawHelper.HANDLE_SIZE_PX * 0.5f + HOVER_TOLER_PX);
        if (!HandleHelper.insideSquare(mx, my, tmp2Vec.x, tmp2Vec.y, halfWorld)) return false;

        lightRadiusEntityId = entityId;
        lightRadiusBefore = readLightRadius(entityId);
        lightRadiusCurrent = lightRadiusBefore;
        lightDragIsCone = mConeLight != null && mConeLight.has(entityId);
        TransformComponent transform = mT.getSafe(entityId, null);
        lightRotationBeforeRad = transform != null ? transform.rotationRad : 0f;
        lightRotationCurrentRad = lightRotationBeforeRad;
        lightRadiusDragActive = true;
        return true;
    }

    private void onLightRadiusDragging(float mx, float my, boolean leftDown, boolean leftReleased) {
        int entityId = lightRadiusEntityId;
        TransformComponent t = mT.getSafe(entityId, null);
        if (entityId < 0 || t == null || !world.getEntityManager().isActive(entityId)) {
            lightRadiusDragActive = false;
            return;
        }

        float newRadius = EditLightRadiusCommand.clamp(Vector2.dst(t.x, t.y, mx, my));
        float newRotationRad = lightDragIsCone ? (float) Math.atan2(my - t.y, mx - t.x) : lightRotationCurrentRad;
        applyLightOverlayLive(entityId, newRadius, newRotationRad, lightDragIsCone);
        lightRadiusCurrent = newRadius;
        lightRotationCurrentRad = newRotationRad;

        boolean shouldEnd = leftReleased || !leftDown;
        if (!shouldEnd) return;

        float after = EditLightRadiusCommand.clamp(lightRadiusCurrent);
        applyLightOverlayLive(entityId, lightRadiusBefore, lightRotationBeforeRad, lightDragIsCone);
        EditLightRadiusCommand cmd = lightDragIsCone
                ? new EditLightRadiusCommand(
                world,
                historyIds,
                entityId,
                lightRadiusBefore,
                after,
                lightRotationBeforeRad,
                lightRotationCurrentRad
        )
                : new EditLightRadiusCommand(world, historyIds, entityId, lightRadiusBefore, after);
        if (!cmd.isNoop()) historyManager.execute(cmd);
        lightRadiusDragActive = false;
        lightRadiusEntityId = -1;
        lightRadiusBefore = 0f;
        lightRadiusCurrent = 0f;
        lightRotationBeforeRad = 0f;
        lightRotationCurrentRad = 0f;
        lightDragIsCone = false;
        resetPressState();
    }

    private void applyLightOverlayLive(int entityId, float radius, float rotationRad, boolean editRotation) {
        EditLightRadiusCommand.applyLightOverlayValues(
                world,
                entityId,
                radius,
                rotationRad,
                editRotation,
                dirty
        );
    }

    private float readLightRadius(int entityId) {
        PointLightComponent point = mPointLight != null ? mPointLight.getSafe(entityId, null) : null;
        if (point != null) return point.radius;
        ConeLightComponent cone = mConeLight != null ? mConeLight.getSafe(entityId, null) : null;
        return cone != null ? cone.radius : 0f;
    }

    private void computeLightRadiusHandleWorld(int entityId, TransformComponent t, Vector2 out) {
        float radius = readLightRadius(entityId);
        if (mConeLight != null && mConeLight.has(entityId)) {
            float angle = t.rotationRad;
            out.set(t.x + MathUtils.cos(angle) * radius, t.y + MathUtils.sin(angle) * radius);
        } else {
            out.set(t.x + radius, t.y);
        }

        // No applyDisplayOffset here.
        // Studio editing space is logical; parallax is preview/runtime-only.
    }

    private boolean isLightEntity(int entityId) {
        return (mPointLight != null && mPointLight.has(entityId))
                || (mConeLight != null && mConeLight.has(entityId));
    }

    private float[] computeOBBWorldCorners(int e) {
        OrientedBoundsComponent b = mOBB.getSafe(e, null);
        if (b == null) return null;

        OrientedBoundsHelper.toCorners(b, tmpCorners);
        applyDisplayOffset(e, tmpCorners);
        return tmpCorners;
    }

    private void applyDisplayOffset(int entityId, float[] corners) {
        applyDisplayOffset(entityId, corners, corners != null ? corners.length / 2 : 0);
    }

    private SceneMeta currentSceneMeta() {
        ProjectConfig cfg = ProjectConfig.getInstance();
        return cfg != null ? cfg.getCurrentSceneMeta() : null;
    }

    private void updateCursorForHover(InputManipulationContext.Handle hovered,
                                      boolean dragging,
                                      InputManipulationContext.Mode mode,
                                      InputManipulationContext.Handle activeHandle) {
        if (gizmoSystem != null) {
            gizmoSystem.clearCursor();
        }

        float objectRotationRad = 0f;
        IntArray sel = selectionService.getSelectionSnapshot();
        if (sel.size == 1 && mT.has(sel.get(0))) {
            objectRotationRad = mT.get(sel.get(0)).rotationRad;
        }

        if (dragging) {
            if (!osCursorHidden) {
                setCursor(Cursor.SystemCursor.None);
                osCursorHidden = true;
            }

            if (mode == InputManipulationContext.Mode.HANDLE_RESIZE && gizmoSystem != null) {
                float axisAngle = handleAxisAngle(activeHandle);
                Vector2 cursorPos = tmpMouseWorld;

                if (sel.size == 1) {
                    computeHandleWorldPosition(sel.get(0), activeHandle, tmp2Vec);
                    cursorPos = tmp2Vec;
                }
                gizmoSystem.setCursor(CursorKind.RESIZE, objectRotationRad + axisAngle, cursorPos);
            }

            return;
        }

        boolean hasCustomCursor = false;

        if (hoveredPolygonVertexIndex >= 0) {
            if (gizmoSystem != null) {
                gizmoSystem.setCursor(
                        CursorKind.RESIZE,
                        0f,
                        tmpMouseWorld
                );
                hasCustomCursor = true;
            }
        } else if (hovered == InputManipulationContext.Handle.ROTATE) {
            if (gizmoSystem != null) {
                gizmoSystem.setCursor(CursorKind.ROTATE, objectRotationRad, tmpMouseWorld);
                hasCustomCursor = true;
            }
        } else if (hovered != InputManipulationContext.Handle.NONE) {
            if (gizmoSystem != null) {
                float axisAngle = handleAxisAngle(hovered);
                gizmoSystem.setCursor(CursorKind.RESIZE, objectRotationRad + axisAngle, tmpMouseWorld);
                hasCustomCursor = true;
            }
        }

        if (hasCustomCursor) {
            if (!osCursorHidden) {
                setCursor(Cursor.SystemCursor.None);
                osCursorHidden = true;
            }
        } else {
            if (osCursorHidden) {
                setCursor(Cursor.SystemCursor.Arrow);
                osCursorHidden = false;
            }
        }
    }

    private void setCursor(Cursor.SystemCursor c) {
        if (lastCursor != c) {
            Gdx.graphics.setSystemCursor(c);
            lastCursor = c;
        }
    }

    private static float handleAxisAngle(InputManipulationContext.Handle h) {
        return switch (h) {
            case E, W -> 0f;
            case N, S -> (float) Math.PI * 0.5f;
            case NE, SW -> (float) Math.PI * 0.25f;
            case NW, SE -> (float) -Math.PI * 0.25f;
            default -> 0f;
        };
    }

    private void computeHandleWorldPosition(int entityId,
                                            InputManipulationContext.Handle handle,
                                            Vector2 out) {
        float[] obb = computeOBBWorldCorners(entityId);
        if (obb == null) {
            out.set(tmpMouseWorld);
            return;
        }

        switch (handle) {
            case N -> out.set(HandleLayout.midNX(obb), HandleLayout.midNY(obb));
            case S -> out.set(HandleLayout.midSX(obb), HandleLayout.midSY(obb));
            case E -> out.set(HandleLayout.midEX(obb), HandleLayout.midEY(obb));
            case W -> out.set(HandleLayout.midWX(obb), HandleLayout.midWY(obb));
            case NE -> out.set(HandleLayout.neX(obb), HandleLayout.neY(obb));
            case NW -> out.set(HandleLayout.nwX(obb), HandleLayout.nwY(obb));
            case SE -> out.set(HandleLayout.seX(obb), HandleLayout.seY(obb));
            case SW -> out.set(HandleLayout.swX(obb), HandleLayout.swY(obb));
            default -> out.set(tmpMouseWorld);
        }
    }
}
