package games.pixscape.studio.system;

import com.artemis.Aspect;
import com.artemis.BaseSystem;
import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.component.light.ConeLightComponent;
import games.pixscape.runtime.component.light.PointLightComponent;
import games.pixscape.runtime.component.physics.*;
import games.pixscape.runtime.helper.OrientedBoundsHelper;
import games.pixscape.runtime.render.RenderStateSOA;
import games.pixscape.runtime.render.TiledMapRenderState;
import games.pixscape.runtime.service.PhysicsService;
import games.pixscape.runtime.service.TextureRegistry;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import games.pixscape.studio.component.physics.AuthoredPolygonData;
import games.pixscape.studio.component.physics.ConvexPolygonPartData;
import games.pixscape.studio.component.physics.PhysicsAuthoringComponent;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.configuration.SceneMeta;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.helper.*;
import games.pixscape.studio.input.InputState;
import games.pixscape.studio.service.CoordSpaces;
import games.pixscape.studio.service.LayerService;
import games.pixscape.studio.service.SelectionService;
import games.pixscape.studio.service.physics.PhysicsSelectionService;
import games.pixscape.studio.service.physics.PolygonDrawSession;
import games.pixscape.studio.service.spatial.SpatialBlockPlacementTarget;
import games.pixscape.studio.service.spatial.SpatialBlockProjection;
import games.pixscape.studio.service.spatial.SpatialBlockSelectionService;
import games.pixscape.studio.service.spatial.SpatialTileSelectionService;
import games.pixscape.studio.service.tiled.TiledPreviewService;
import games.pixscape.studio.service.tiled.TiledVisualCoverage;
import games.pixscape.studio.ui.config.CommonLayout;

@All({TransformComponent.class, DimensionsComponent.class})
public final class GizmoSystem extends BaseSystem {

    private final StudioDrawContext ctx;
    private final InputState inputState;
    private final CoordSpaces coordSpaces;
    private final RenderStateSOA renderState;
    private final TiledMapRenderState tiledState;

    private LayerService layerService;
    private SelectionService selectionService;
    private PhysicsService physicsService;
    private final PolygonDrawSession polygonDrawSession;
    private final PhysicsSelectionService physicsSelectionService;
    private final SpatialBlockSelectionService spatialBlockSelectionService;
    private final SpatialTileSelectionService spatialTileSelectionService;
    private final TiledPreviewService tiledPreviewService;
    private final FixtureDefData tmpAuthoringFixture = new FixtureDefData();

    private boolean lassoVisible = false;
    private float lassoX0, lassoY0, lassoX1, lassoY1;

    private boolean tiledOverlayEnabled = false;
    private float tiledWidth;
    private float tiledHeight;
    private final Vector2 tmpMouseWorld = new Vector2();

    private boolean tiledRectPreview = false;
    private TiledMapLayerData tiledRectPreviewMap;
    private int tiledRectMinGX;
    private int tiledRectMinGY;
    private int tiledRectMaxGX;
    private int tiledRectMaxGY;

    private boolean rectPreviewVisible = false;
    private float rectPreviewX0;
    private float rectPreviewY0;
    private float rectPreviewX1;
    private float rectPreviewY1;

    private final Vector2 tmpA = new Vector2();
    private final Vector2 tmpB = new Vector2();
    private CursorKind cursorKind = CursorKind.NONE;
    private float cursorAngleRad = 0f;
    private final Vector2 cursorWorld = new Vector2();
    private boolean entityGizmoEnabled = true;

    private ComponentMapper<VisibilityComponent> mVis;
    private ComponentMapper<OrientedBoundsComponent> mOBB;
    private ComponentMapper<EntityIndexComponent> mEntityIndex;
    private ComponentMapper<TransformComponent> mT;
    private ComponentMapper<PhysicsFixturesComponent> mFixDefs;
    private ComponentMapper<PhysicsJointComponent> mJoint;
    private ComponentMapper<PhysicsWheelJointComponent> mWheel;
    private ComponentMapper<PhysicsMotorJointComponent> mMotor;
    private ComponentMapper<PhysicsPulleyJointComponent> mPulley;
    private ComponentMapper<PhysicsGearJointComponent> mGear;
    private ComponentMapper<PointLightComponent> mPointLight;
    private ComponentMapper<ConeLightComponent> mConeLight;
    private ComponentMapper<PhysicsAuthoringComponent> mPhysicsAuthoring;
    private ComponentMapper<SpatialBlocksComponent> mSpatialBlocks;
    private ComponentMapper<TiledLayerComponent> mTiledLayer;

    private int[] selected = new int[0];
    private final float[] tmpCorners = new float[8];
    private final Vector2 tmpFixtureCenter = new Vector2();
    private float[] tmpFixtureVerts = new float[32];
    private final float[] tmpSpatialBlockBaseVerts = new float[8];
    private final float[] tmpSpatialBlockTopVerts = new float[8];
    private final float[] tmpSpatialTileTintVerts = new float[20];

    public GizmoSystem(StudioDrawContext worldCtx,
                       InputState inputState,
                       CoordSpaces coordSpaces,
                       RenderStateSOA renderState,
                       TiledMapRenderState tiledState,
                       PhysicsSelectionService physicsSelectionService,
                       SpatialBlockSelectionService spatialBlockSelectionService,
                       SpatialTileSelectionService spatialTileSelectionService,
                       TiledPreviewService tiledPreviewService,
                       PolygonDrawSession polygonDrawSession) {
        this.ctx = worldCtx;
        this.renderState = renderState;
        this.tiledState = tiledState;
        this.inputState = inputState;
        this.coordSpaces = coordSpaces;
        this.physicsSelectionService = physicsSelectionService;
        this.spatialBlockSelectionService = spatialBlockSelectionService;
        this.spatialTileSelectionService = spatialTileSelectionService;
        this.tiledPreviewService = tiledPreviewService;
        this.polygonDrawSession = polygonDrawSession;

        EventFlow.i().subscribe(EventFlow.SelectionChanged.class, evt -> selected = evt.ids().toArray());
    }

    public void setEntityGizmoEnabled(boolean enabled) {
        this.entityGizmoEnabled = enabled;
    }

    public void setLayerService(LayerService layerService) {
        this.layerService = layerService;
    }

    public void setSelectionService(SelectionService selectionService) {
        this.selectionService = selectionService;
    }

    public void setPhysicsService(PhysicsService physicsService) {
        this.physicsService = physicsService;
    }

    @Override
    protected void processSystem() {
        boolean fixtureOverlayVisible = hasFixtureOverlayWork();
        boolean jointOverlayVisible = hasJointOverlayWork();
        boolean hoveredEntityVisible = hasHoveredEntityWork();
        boolean polygonDrawVisible = polygonDrawSession != null && polygonDrawSession.isActive();
        boolean spatialBlockVisible = hasSpatialBlockOverlayWork();
        boolean spatialTileSelectionVisible = hasSpatialTileSelectionWork();

        if (!lassoVisible
                && !rectPreviewVisible
                && selected.length == 0
                && cursorKind == CursorKind.NONE
                && !tiledOverlayEnabled
                && !fixtureOverlayVisible
                && !jointOverlayVisible
                && !spatialBlockVisible
                && !spatialTileSelectionVisible
                && !hoveredEntityVisible
                && !polygonDrawVisible) {
            return;
        }

        ctx.batch.setProjectionMatrix(ctx.cam.combined);
        ctx.batch.begin();

        try {
            if (lassoVisible) {
                GizmoDrawHelper.drawLasso(ctx, lassoX0, lassoY0, lassoX1, lassoY1);
            }

            if (rectPreviewVisible) {
                if (tiledRectPreview && tiledRectPreviewMap != null) {
                    GizmoDrawHelper.drawTiledRectPreview(
                            ctx,
                            tiledRectPreviewMap,
                            tiledRectMinGX,
                            tiledRectMinGY,
                            tiledRectMaxGX,
                            tiledRectMaxGY
                    );
                } else {
                    float minX = Math.min(rectPreviewX0, rectPreviewX1);
                    float maxX = Math.max(rectPreviewX0, rectPreviewX1);
                    float minY = Math.min(rectPreviewY0, rectPreviewY1);
                    float maxY = Math.max(rectPreviewY0, rectPreviewY1);

                    GizmoDrawHelper.drawRectWorld(ctx, minX, minY, maxX, maxY);
                }
            }

            if (tiledOverlayEnabled
                    && (polygonDrawSession == null || !polygonDrawSession.isActive())
                    && !hasSpatialBlockOverlayWork()
                    && (spatialTileSelectionService == null || !spatialTileSelectionService.hasSelection())) {
                readMouseWorld(tmpMouseWorld);

                int layerEntityId = selectionService != null ? selectionService.getActivelayerId() : -1;
                TiledLayerComponent tiled = layerEntityId >= 0
                        ? world.getMapper(TiledLayerComponent.class).getSafe(layerEntityId, null)
                        : null;

                if (tiled != null && tiled.data != null) {
                    TiledMapLayerData map = tiled.data;

                    int gx = map.worldToTileX(tmpMouseWorld.x, tmpMouseWorld.y);
                    int gy = map.worldToTileY(tmpMouseWorld.x, tmpMouseWorld.y);

                    if (map.isInside(gx, gy)) {
                        TiledVisualCoverage.Coverage coverage = activePreviewCoverage(map, gx, gy);
                        SpatialBlockPlacementTarget target = SpatialBlockPlacementTarget.fromWorld(
                                map,
                                layerEntityId,
                                tmpMouseWorld.x,
                                tmpMouseWorld.y,
                                coverage,
                                false
                        );
                        GizmoDrawHelper.drawTiledOverlay(ctx, map, target);
                    }
                }
            }

            boolean physicsEditMode = isExplicitPhysicsEditMode();

            drawFixtureOverlays();
            drawSelectedFixtureHandles();
            drawJointOverlays();
            drawSelectedJointGizmos();
            drawSpatialTileSelection();
            drawSelectedSpatialBlockLinkedTiles();
            drawSpatialBlockOverlays();
            drawHoveredEntityGizmo(physicsEditMode);
            drawPolygonDrawSession();

            if (entityGizmoEnabled && !physicsEditMode) {
                for (int e : selected) {
                    if (mJoint.has(e)) continue;

                    if (mOBB.has(e)) {
                        if (isLightEntity(e)) continue;
                        if (!isEntityVisibleForGizmo(e)) continue;

                        float[] obb = computeOBBWorldCorners(e);
                        if (obb == null) continue;

                        GizmoDrawHelper.drawDashedObb(ctx, obb);

                        if (selected.length == 1) {
                            GizmoDrawHelper.drawHandlesObb(ctx, obb);
                        }
                    }
                }
            }

            CursorDrawHelper.draw(ctx, cursorWorld, cursorKind, cursorAngleRad);
        } finally {
            ctx.batch.end();
        }
    }

    private boolean hasHoveredEntityWork() {
        if (selectionService == null) return false;

        int hoveredEntityId = selectionService.getHoveredEntityId();
        if (hoveredEntityId < 0) return false;
        if (isSelectedEntity(hoveredEntityId)) return false;
        if (mJoint.has(hoveredEntityId)) return false;
        if (isLightEntity(hoveredEntityId)) return false;
        if (!mOBB.has(hoveredEntityId)) return false;

        return isEntityVisibleForGizmo(hoveredEntityId);
    }

    private boolean hasFixtureOverlayWork() {
        return isFixtureOverlayVisible() && physicsService != null;
    }

    private boolean hasJointOverlayWork() {
        return isJointOverlayVisible() && physicsService != null && physicsService.isAvailable();
    }

    private boolean hasSpatialBlockOverlayWork() {
        return spatialBlockSelectionService != null && spatialBlockSelectionService.isEditingActive();
    }

    private boolean hasSpatialTileSelectionWork() {
        return spatialTileSelectionService != null && spatialTileSelectionService.hasSelection();
    }

    private void drawSpatialBlockOverlays() {
        if (!hasSpatialBlockOverlayWork()) return;

        int layerEntityId = spatialBlockSelectionService.getEditingLayerEntityId();
        SpatialBlocksComponent component = mSpatialBlocks.getSafe(layerEntityId, null);
        TiledLayerComponent tiled = mTiledLayer.getSafe(layerEntityId, null);
        if (component == null || component.blocks == null || tiled == null || tiled.data == null) return;

        int selectedBlockId = spatialBlockSelectionService.getSelectedBlockId();
        int hoveredBlockId = spatialBlockSelectionService.getHoveredBlockId();

        for (int i = 0, n = component.blocks.size; i < n; i++) {
            SpatialBlockData block = component.blocks.get(i);
            if (block == null) continue;

            SpatialBlockProjection.projectBaseFootprint(tiled.data, block, tmpSpatialBlockBaseVerts);
            SpatialBlockProjection.projectTopFootprint(tiled.data, block, tmpSpatialBlockTopVerts);
            boolean selected = block.id == selectedBlockId;
            boolean hovered = block.id == hoveredBlockId;

            if (selected) {
                ctx.drawer.setColor(CommonLayout.SELECTION_HIGHLIGHT_COLOR);
            } else if (hovered) {
                ctx.drawer.setColor(Color.WHITE);
            } else if (block.enabled) {
                ctx.drawer.setColor(0.25f, 1f, 0.65f, 0.85f);
            } else {
                ctx.drawer.setColor(0.55f, 0.55f, 0.55f, 0.65f);
            }

            float lineW = ctx.pxToWorld(selected ? 2.5f : hovered ? 2f : 1.25f);
            drawBlockVolume(tmpSpatialBlockBaseVerts, tmpSpatialBlockTopVerts, lineW);
            if (selected) {
                GizmoDrawHelper.drawShapeVertices(ctx, tmpSpatialBlockBaseVerts, 4);
                drawBlockHeightHandle(tmpSpatialBlockTopVerts);
            }
        }
    }

    private void drawSpatialTileSelection() {
        if (!hasSpatialTileSelectionWork()) return;
        if (spatialBlockSelectionService == null || !spatialBlockSelectionService.isEditingActive()) return;

        int layerEntityId = spatialTileSelectionService.getLayerEntityId();
        if (layerEntityId != spatialBlockSelectionService.getEditingLayerEntityId()) return;
        if (!isLayerEntityVisible(layerEntityId)) return;

        TiledLayerComponent tiled = mTiledLayer.getSafe(layerEntityId, null);
        if (tiled == null || tiled.data == null) {
            spatialTileSelectionService.clear();
            return;
        }

        boolean validSelection = spatialTileSelectionService.canCreateSpatialBlock(tiled.data);
        drawSpatialTileSelectionTint(tiled.data, spatialTileSelectionService.isDragging(), validSelection);
    }

    private void drawSelectedSpatialBlockLinkedTiles() {
        if (spatialBlockSelectionService == null || !spatialBlockSelectionService.hasSelectedBlock()) return;
        if (hasSpatialTileSelectionWork()) return;

        int layerEntityId = spatialBlockSelectionService.getEditingLayerEntityId();
        if (!isLayerEntityVisible(layerEntityId)) return;

        SpatialBlocksComponent component = mSpatialBlocks.getSafe(layerEntityId, null);
        TiledLayerComponent tiled = mTiledLayer.getSafe(layerEntityId, null);
        if (component == null || component.blocks == null || tiled == null || tiled.data == null) return;

        SpatialBlockData block = spatialBlockById(component, spatialBlockSelectionService.getSelectedBlockId());
        if (block == null || !block.hasLinkedTileRefs()) return;

        drawSpatialBlockLinkedTileTint(tiled.data, block);
    }

    private SpatialBlockData spatialBlockById(SpatialBlocksComponent component, int blockId) {
        if (component == null || component.blocks == null || blockId <= 0) return null;
        for (int i = 0, n = component.blocks.size; i < n; i++) {
            SpatialBlockData block = component.blocks.get(i);
            if (block != null && block.id == blockId) return block;
        }
        return null;
    }

    private void drawSpatialTileSelectionTint(TiledMapLayerData map, boolean dragging, boolean validSelection) {
        if (map == null || renderState == null) return;

        float colorPacked = Color.toFloatBits(
                validSelection ? 0.05f : 1f,
                validSelection ? 0.92f : 0.12f,
                validSelection ? 1f : 0.08f,
                dragging ? 0.42f : 0.5f
        );

        drawSpatialTileSelectionTintRange(map, colorPacked);
    }

    private void drawSpatialTileSelectionTintRange(TiledMapLayerData map, float colorPacked) {
        for (int gy = spatialTileSelectionService.getMinGy(); gy <= spatialTileSelectionService.getMaxGy(); gy++) {
            for (int gx = spatialTileSelectionService.getMinGx(); gx <= spatialTileSelectionService.getMaxGx(); gx++) {
                if (!map.isInside(gx, gy)) continue;
                if (map.getTile(gx, gy) <= 0) continue;
                drawSpatialTileTintCell(map, gx, gy, colorPacked);
            }
        }
    }

    private void drawSpatialBlockLinkedTileTint(TiledMapLayerData map, SpatialBlockData block) {
        if (map == null || block == null || block.linkedTileRefs == null || renderState == null) return;

        float colorPacked = Color.toFloatBits(0.05f, 0.92f, 1f, 0.5f);
        for (int i = 0, n = block.linkedTileRefs.size; i < n; i++) {
            SpatialBlockData.LinkedTileRef ref = block.linkedTileRefs.get(i);
            if (ref == null || !map.isInside(ref.gx, ref.gy)) continue;
            if (map.getTile(ref.gx, ref.gy) <= 0) continue;
            drawSpatialTileTintCell(map, ref.gx, ref.gy, colorPacked);
        }
    }

    private void drawSpatialTileTintCell(TiledMapLayerData map, int gx, int gy, float colorPacked) {
        if (drawSpatialTileTintRef(map.tiledRenderRefForTile(gx, gy), colorPacked)) {
            return;
        }
        drawSpatialTileTintSlot(map.slotForTile(gx, gy), colorPacked);
    }

    private boolean drawSpatialTileTintRef(int tiledRenderRef, float colorPacked) {
        if (!isRenderableSpatialTileRef(tiledRenderRef)) return false;

        Texture texture = TextureRegistry.getByHandle(tiledState.textureHandle[tiledRenderRef]);
        if (texture == null) return false;

        buildTintVerticesFromTiledRef(tiledRenderRef, colorPacked, tmpSpatialTileTintVerts);
        ctx.batch.draw(texture, tmpSpatialTileTintVerts, 0, 20);
        return true;
    }

    private void drawSpatialTileTintSlot(int slot, float colorPacked) {
        if (!isRenderableSpatialTileSlot(slot)) return;

        Texture texture = TextureRegistry.getByHandle(renderState.textureHandle[slot]);
        if (texture == null) return;

        buildTintVertices(slot, colorPacked, tmpSpatialTileTintVerts);
        ctx.batch.draw(texture, tmpSpatialTileTintVerts, 0, 20);
    }

    private boolean isRenderableSpatialTileSlot(int slot) {
        return renderState != null
                && slot >= 0
                && slot < renderState.textureHandle.length
                && renderState.enabled[slot]
                && renderState.visible[slot]
                && renderState.kind[slot] == RenderStateSOA.KIND_SPRITE
                && renderState.textureHandle[slot] != 0;
    }

    private boolean isRenderableSpatialTileRef(int tiledRenderRef) {
        return tiledState != null
                && tiledState.isRenderableRef(tiledRenderRef);
    }

    private void buildTintVerticesFromTiledRef(int tiledRenderRef, float colorPacked, float[] out) {
        out[0] = tiledState.x1[tiledRenderRef];
        out[1] = tiledState.y1[tiledRenderRef];
        out[2] = colorPacked;
        out[3] = tiledState.u1[tiledRenderRef];
        out[4] = tiledState.v2[tiledRenderRef];

        out[5] = tiledState.x2[tiledRenderRef];
        out[6] = tiledState.y2[tiledRenderRef];
        out[7] = colorPacked;
        out[8] = tiledState.u1[tiledRenderRef];
        out[9] = tiledState.v1[tiledRenderRef];

        out[10] = tiledState.x3[tiledRenderRef];
        out[11] = tiledState.y3[tiledRenderRef];
        out[12] = colorPacked;
        out[13] = tiledState.u2[tiledRenderRef];
        out[14] = tiledState.v1[tiledRenderRef];

        out[15] = tiledState.x4[tiledRenderRef];
        out[16] = tiledState.y4[tiledRenderRef];
        out[17] = colorPacked;
        out[18] = tiledState.u2[tiledRenderRef];
        out[19] = tiledState.v2[tiledRenderRef];
    }

    private void buildTintVertices(int slot, float colorPacked, float[] out) {
        out[0] = renderState.x1[slot];
        out[1] = renderState.y1[slot];
        out[2] = colorPacked;
        out[3] = renderState.u1[slot];
        out[4] = renderState.v2[slot];

        out[5] = renderState.x2[slot];
        out[6] = renderState.y2[slot];
        out[7] = colorPacked;
        out[8] = renderState.u1[slot];
        out[9] = renderState.v1[slot];

        out[10] = renderState.x3[slot];
        out[11] = renderState.y3[slot];
        out[12] = colorPacked;
        out[13] = renderState.u2[slot];
        out[14] = renderState.v1[slot];

        out[15] = renderState.x4[slot];
        out[16] = renderState.y4[slot];
        out[17] = colorPacked;
        out[18] = renderState.u2[slot];
        out[19] = renderState.v2[slot];
    }

    private void drawBlockHeightHandle(float[] topVerts) {
        if (topVerts == null || topVerts.length < 8) return;
        float cx = (topVerts[0] + topVerts[2] + topVerts[4] + topVerts[6]) * 0.25f;
        float cy = (topVerts[1] + topVerts[3] + topVerts[5] + topVerts[7]) * 0.25f;
        GizmoDrawHelper.drawShapeVertexHandle(ctx, cx, cy);
    }

    private TiledVisualCoverage.Coverage activePreviewCoverage(TiledMapLayerData map, int fallbackGX, int fallbackGY) {
        if (tiledPreviewService != null
                && tiledPreviewService.isCoverageVisible()
                && tiledPreviewService.map() == map
                && tiledPreviewService.hasVisualSize()) {
            return TiledVisualCoverage.compute(
                    map,
                    tiledPreviewService.gx(),
                    tiledPreviewService.gy(),
                    tiledPreviewService.visualPixW(),
                    tiledPreviewService.visualPixH(),
                    tiledPreviewService.flags()
            );
        }

        return TiledVisualCoverage.compute(map, fallbackGX, fallbackGY, map.tileWidth, map.tileHeight, (byte) 0);
    }

    private void drawBlockVolume(float[] base, float[] top, float lineW) {
        drawBlockOutline(base, lineW);
        drawBlockOutline(top, lineW);
        for (int i = 0; i < 4; i++) {
            ctx.drawer.line(
                    base[i * 2],
                    base[i * 2 + 1],
                    top[i * 2],
                    top[i * 2 + 1],
                    lineW
            );
        }
    }

    private void drawBlockOutline(float[] verts, float lineW) {
        for (int i = 0; i < 4; i++) {
            int next = (i + 1) & 3;
            ctx.drawer.line(
                    verts[i * 2],
                    verts[i * 2 + 1],
                    verts[next * 2],
                    verts[next * 2 + 1],
                    lineW
            );
        }
    }

    private void drawFixtureOverlays() {
        if (!hasFixtureOverlayWork()) return;

        int focusedBodyEid = physicsSelectionService.getFocusedBodyEid();
        boolean physicsEditMode = isExplicitPhysicsEditMode();

        if (physicsEditMode) {
            drawAllFixturesExcept(focusedBodyEid);
        }

        if (isDrawableFixtureBody(focusedBodyEid)) {
            drawBodyFixtures(focusedBodyEid, true);
        }
    }

    private void drawAllFixturesExcept(int skipBodyEid) {
        IntBag bag = world.getAspectSubscriptionManager()
                .get(Aspect.all(PhysicsFixturesComponent.class))
                .getEntities();
        int[] data = bag.getData();

        for (int i = 0, n = bag.size(); i < n; i++) {
            int bodyEid = data[i];
            if (bodyEid == skipBodyEid) continue;
            if (!isDrawableFixtureBody(bodyEid)) continue;
            drawBodyFixtures(bodyEid, false);
        }
    }

    private void drawBodyFixtures(int bodyEid, boolean focusedBody) {
        if (!isDrawableFixtureBody(bodyEid)) return;

        PhysicsFixturesComponent fixtures = physicsService.getFixturesComponent(bodyEid);
        if (fixtures == null || !fixtures.hasFixtures()) return;

        physicsService.ensureFixtureIds(bodyEid);

        int hoveredBodyEid = physicsSelectionService.getHoveredBodyEid();
        long hoveredId = (hoveredBodyEid == bodyEid)
                ? physicsSelectionService.getHoveredFixtureId()
                : PhysicsSelectionService.NO_FIXTURE;

        int selectedId = focusedBody
                ? physicsSelectionService.getSelectedFixtureId()
                : PhysicsSelectionService.NO_FIXTURE;

        for (int i = 0, n = fixtures.fixtures.size; i < n; i++) {
            FixtureDefData fixture = fixtures.fixtures.get(i);
            if (fixture == null) continue;

            // Generated polygon parts are technical runtime fixtures.
            // They are represented visually through the authored polygon overlay.
            if (isGeneratedAuthoringFixture(bodyEid, fixture.fixtureId)) {
                continue;
            }

            if (shouldHideEditedPolygon(bodyEid, fixture)) {
                continue;
            }

            boolean hovered = fixture.fixtureId == hoveredId;
            boolean selected = fixture.fixtureId == selectedId;

            drawFixture(bodyEid, fixture, focusedBody, hovered, selected);
        }

        drawAuthoredPolygonOverlays(bodyEid, focusedBody, selectedId, hoveredId);
    }

    private void drawAuthoredPolygonOverlays(
            int bodyEid,
            boolean focusedBody,
            int selectedFixtureId,
            long hoveredFixtureId
    ) {
        if (mPhysicsAuthoring == null) return;

        PhysicsAuthoringComponent authoring = mPhysicsAuthoring.getSafe(bodyEid, null);
        if (authoring == null || authoring.polygons == null || authoring.polygons.size == 0) {
            return;
        }

        for (int i = 0; i < authoring.polygons.size; i++) {
            AuthoredPolygonData polygon = authoring.polygons.get(i);
            if (polygon == null) continue;

            boolean hovered = containsFixtureId(
                    polygon.generatedFixtureIds,
                    hoveredFixtureId
            );

            boolean selected = focusedBody && containsFixtureId(
                    polygon.generatedFixtureIds,
                    selectedFixtureId
            );

            boolean active = hovered || selected;

            drawAuthoredPolygonDecomposition(bodyEid, polygon, focusedBody, active);
            drawAuthoredPolygonSource(bodyEid, polygon, focusedBody, active);
        }
    }

    private boolean isGeneratedAuthoringFixture(int bodyEid, long fixtureId) {
        return findAuthoredPolygonByGeneratedFixture(bodyEid, fixtureId) != null;
    }

    private AuthoredPolygonData findAuthoredPolygonByGeneratedFixture(int bodyEid, long fixtureId) {
        if (fixtureId <= 0L) return null;
        if (mPhysicsAuthoring == null) return null;

        PhysicsAuthoringComponent authoring = mPhysicsAuthoring.getSafe(bodyEid, null);
        if (authoring == null || authoring.polygons == null) return null;

        for (int i = 0; i < authoring.polygons.size; i++) {
            AuthoredPolygonData polygon = authoring.polygons.get(i);
            if (polygon == null) continue;

            if (containsFixtureId(polygon.generatedFixtureIds, fixtureId)) {
                return polygon;
            }
        }

        return null;
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

            if (!Float.isFinite(lx) || !Float.isFinite(ly)) {
                return 0;
            }

            // Fixture local transform, still in meters.
            float fx = lx * fixtureCos - ly * fixtureSin + fixtureOffsetX;
            float fy = lx * fixtureSin + ly * fixtureCos + fixtureOffsetY;

            // Body transform, meters -> world units.
            float wx = t.x + (fx * bodyCos - fy * bodySin) * ppm;
            float wy = t.y + (fx * bodySin + fy * bodyCos) * ppm;

            out[i * 2] = wx;
            out[i * 2 + 1] = wy;
        }

        applyDisplayOffset(bodyEid, out, count);

        return count;
    }

    private void prepareTempPolygonFixture(
            float[] verts,
            int count,
            AuthoredPolygonData polygon
    ) {
        tmpAuthoringFixture.fixtureId = 0;
        tmpAuthoringFixture.shapeType = FixtureDefData.SHAPE_POLYGON;

        tmpAuthoringFixture.polyCount = count;
        tmpAuthoringFixture.polyVerts = verts;

        tmpAuthoringFixture.halfW = 0.5f;
        tmpAuthoringFixture.halfH = 0.5f;
        tmpAuthoringFixture.radius = 0.5f;

        tmpAuthoringFixture.offsetX = polygon != null ? polygon.offsetX : 0f;
        tmpAuthoringFixture.offsetY = polygon != null ? polygon.offsetY : 0f;
        tmpAuthoringFixture.angleDeg = polygon != null ? polygon.angleDeg : 0f;

        tmpAuthoringFixture.density = polygon != null ? polygon.density : 1f;
        tmpAuthoringFixture.friction = polygon != null ? polygon.friction : 0.2f;
        tmpAuthoringFixture.restitution = polygon != null ? polygon.restitution : 0f;
        tmpAuthoringFixture.isSensor = polygon != null && polygon.isSensor;

        tmpAuthoringFixture.categoryBits = polygon != null ? polygon.categoryBits : (short) 0x0001;
        tmpAuthoringFixture.maskBits = polygon != null ? polygon.maskBits : (short) 0xFFFF;
        tmpAuthoringFixture.groupIndex = polygon != null ? polygon.groupIndex : (short) 0;
    }

    private static boolean containsFixtureId(int[] ids, long fixtureId) {
        if (ids == null || fixtureId <= 0L) return false;

        for (int id : ids) {
            if (id == fixtureId) return true;
        }

        return false;
    }

    private void drawAuthoringSourcePolygon(
            float[] verts,
            int vertexCount,
            boolean focusedBody,
            boolean selected
    ) {
        if (verts == null || vertexCount < 2) return;

        if (selected) {
            ctx.drawer.setColor(CommonLayout.SELECTION_HIGHLIGHT_COLOR);
        } else if (focusedBody) {
            ctx.drawer.setColor(CommonLayout.PHYSICS_FOCUSED_BODY_COLOR);
        } else {
            ctx.drawer.setColor(CommonLayout.PHYSICS_BASE_COLOR);
        }

        float thicknessWU = ctx.wpp() * (selected ? 2.5f : 1.5f);

        for (int i = 0; i < vertexCount; i++) {
            int j = (i + 1) % vertexCount;

            float ax = verts[i * 2];
            float ay = verts[i * 2 + 1];
            float bx = verts[j * 2];
            float by = verts[j * 2 + 1];

            ctx.drawer.line(ax, ay, bx, by, thicknessWU);
        }
    }

    private void drawThinDecompositionPolygon(
            float[] verts,
            int vertexCount,
            boolean focusedBody,
            boolean selected
    ) {
        if (verts == null || vertexCount < 2) return;

        // Same visual family, but much more subtle.
        if (selected) {
            ctx.drawer.setColor(1f, 0.25f, 0.20f, 0.50f);
        } else if (focusedBody) {
            ctx.drawer.setColor(1f, 0.25f, 0.20f, 0.32f);
        } else {
            ctx.drawer.setColor(1f, 0.25f, 0.20f, 0.22f);
        }

        float thicknessWU = ctx.wpp() * (selected ? 1.0f : 0.75f);

        for (int i = 0; i < vertexCount; i++) {
            int j = (i + 1) % vertexCount;

            float ax = verts[i * 2];
            float ay = verts[i * 2 + 1];
            float bx = verts[j * 2];
            float by = verts[j * 2 + 1];

            ctx.drawer.line(ax, ay, bx, by, thicknessWU);
        }
    }

    private void drawAuthoredPolygonSource(
            int bodyEid,
            AuthoredPolygonData polygon,
            boolean focusedBody,
            boolean selected
    ) {
        if (polygon == null
                || polygon.sourceVerts == null
                || polygon.sourceCount < 3
                || polygon.sourceVerts.length < polygon.sourceCount * 2) {
            return;
        }

        int floatCount = Math.max(0, polygon.sourceCount * 2);
        ensureFixtureVertsCapacity(floatCount);

        int vertexCount = computeAuthoredPolygonVertsWU(
                bodyEid,
                polygon.sourceVerts,
                polygon.sourceCount,
                polygon,
                tmpFixtureVerts
        );

        if (vertexCount < 3) return;

        drawAuthoringSourcePolygon(tmpFixtureVerts, vertexCount, focusedBody, selected);
    }

    private void drawAuthoredPolygonDecomposition(
            int bodyEid,
            AuthoredPolygonData polygon,
            boolean focusedBody,
            boolean selected
    ) {
        if (polygon == null || polygon.convexParts == null || polygon.convexParts.size == 0) {
            return;
        }

        for (int i = 0; i < polygon.convexParts.size; i++) {
            ConvexPolygonPartData part = polygon.convexParts.get(i);

            if (part == null
                    || part.verts == null
                    || part.count < 3
                    || part.verts.length < part.count * 2) {
                continue;
            }

            int floatCount = Math.max(0, part.count * 2);
            ensureFixtureVertsCapacity(floatCount);

            int vertexCount = computeAuthoredPolygonVertsWU(
                    bodyEid,
                    part.verts,
                    part.count,
                    polygon,
                    tmpFixtureVerts
            );

            if (vertexCount < 3) continue;

            drawThinDecompositionPolygon(tmpFixtureVerts, vertexCount, focusedBody, selected);
        }
    }

    private boolean isDrawableFixtureBody(int bodyEid) {
        PhysicsFixturesComponent fixtures = mFixDefs != null ? mFixDefs.getSafe(bodyEid, null) : null;

        return bodyEid >= 0
                && physicsService != null
                && fixtures != null
                && fixtures.hasFixtures()
                && isEntityVisibleForGizmo(bodyEid);
    }

    private void drawFixture(int bodyEid,
                             FixtureDefData fixture,
                             boolean focusedBody,
                             boolean hovered,
                             boolean selected) {
        if (fixture == null) return;

        if (fixture.shapeType == FixtureDefData.SHAPE_CIRCLE) {
            if (!physicsService.computeFixtureCenterWU(bodyEid, fixture, tmpFixtureCenter)) return;

            applyDisplayOffset(bodyEid, tmpFixtureCenter);
            float radiusWU = physicsService.computeFixtureRadiusWU(fixture);

            GizmoDrawHelper.drawFixtureCircle(
                    ctx,
                    tmpFixtureCenter.x,
                    tmpFixtureCenter.y,
                    radiusWU,
                    focusedBody,
                    hovered,
                    selected,
                    fixture.isSensor
            );
            return;
        }

        int floatCount = fixture.shapeType == FixtureDefData.SHAPE_BOX
                ? 8
                : Math.max(0, fixture.polyCount * 2);

        ensureFixtureVertsCapacity(floatCount);

        int vertexCount = physicsService.computeFixtureVerticesWU(bodyEid, fixture, tmpFixtureVerts);
        if (vertexCount < 2) return;

        applyDisplayOffset(bodyEid, tmpFixtureVerts, vertexCount);

        GizmoDrawHelper.drawFixturePolygon(
                ctx,
                tmpFixtureVerts,
                vertexCount,
                focusedBody,
                hovered,
                selected,
                fixture.isSensor
        );
    }

    private void drawPolygonDrawSession() {
        if (polygonDrawSession == null || !polygonDrawSession.isActive()) return;

        Array<Vector2> pts = polygonDrawSession.points();
        int count = polygonDrawSession.pointCount();
        if (pts == null || count == 0) return;

        // validated segments
        ctx.drawer.setColor(Color.WHITE);
        for (int i = 0; i < count - 1; i++) {
            Vector2 a = pts.get(i);
            Vector2 b = pts.get(i + 1);
            GizmoDrawHelper.drawSolidLine(ctx, a.x, a.y, b.x, b.y, 1.5f);
        }

        // preview segment toward mouse if not closed
        if (!polygonDrawSession.isClosed()) {
            Vector2 last = polygonDrawSession.lastPoint();
            readMouseWorld(tmpMouseWorld);
            if (last != null) {
                GizmoDrawHelper.drawSolidLine(
                        ctx,
                        last.x, last.y,
                        tmpMouseWorld.x, tmpMouseWorld.y,
                        1.0f
                );
            }
        }

        // closing segment if closed
        if (polygonDrawSession.isClosed() && count >= 3) {
            Vector2 first = pts.first();
            Vector2 last = pts.peek();
            GizmoDrawHelper.drawSolidLine(ctx, last.x, last.y, first.x, first.y, 1.5f);
        }

        // points
        for (int i = 0; i < count; i++) {
            Vector2 p = pts.get(i);
            if (p == null) continue;

            boolean firstPoint = (i == 0);
            boolean closable = firstPoint && polygonDrawSession.canClose();

            if (closable) {
                float closeRadiusWorld = HandleHelper.pxToWorld(
                        ctx.cam,
                        GizmoDrawHelper.SHAPE_VERTEX_HANDLE_SIZE_PX * 0.5f + 2f
                );
                readMouseWorld(tmpMouseWorld);
                float dx = tmpMouseWorld.x - p.x;
                float dy = tmpMouseWorld.y - p.y;
                boolean nearClose = dx * dx + dy * dy <= closeRadiusWorld * closeRadiusWorld;

                if (nearClose) {
                    ctx.drawer.setColor(CommonLayout.SELECTION_HIGHLIGHT_COLOR);
                } else {
                    ctx.drawer.setColor(Color.WHITE);
                }
            } else {
                ctx.drawer.setColor(Color.WHITE);
            }

            GizmoDrawHelper.drawShapeVertexHandle(ctx, p.x, p.y);
        }
    }

    private boolean shouldHideEditedPolygon(int bodyEid, FixtureDefData fixture) {
        if (polygonDrawSession == null || !polygonDrawSession.isActive()) return false;
        if (!polygonDrawSession.isEditMode()) return false;
        if (fixture == null || fixture.shapeType != FixtureDefData.SHAPE_POLYGON) return false;

        return bodyEid == polygonDrawSession.getBodyEid()
                && fixture.fixtureId == polygonDrawSession.getFixtureId();
    }

    private void drawJointOverlays() {
        if (!hasJointOverlayWork()) return;
        if (!isExplicitPhysicsEditMode()) return;

        int selectedJointEid = findSelectedJointEid();
        int focusedBodyEid = physicsSelectionService.getFocusedBodyEid();
        IntBag bag = world.getAspectSubscriptionManager()
                .get(Aspect.all(PhysicsJointComponent.class))
                .getEntities();
        int[] data = bag.getData();

        for (int i = 0, n = bag.size(); i < n; i++) {
            int jointEid = data[i];
            PhysicsJointComponent base = mJoint.getSafe(jointEid, null);
            if (base == null) continue;
            // Selected joints are rendered by the selected overlay path; avoid double drawing.
            if (jointEid == selectedJointEid) continue;
            if (!isJointVisibleForOverlay(base)) continue;
            boolean related = PhysicsOverlaySelectionUtil.isJointRelatedToSelection(base, focusedBodyEid, selectedJointEid, jointEid);
            boolean hovered = physicsSelectionService.isHoveredJoint(jointEid);
            drawJointOverlay(jointEid, base, related, hovered);
        }
    }

    private int findSelectedJointEid() {
        for (int e : selected) {
            if (mJoint != null && mJoint.has(e)) return e;
        }
        return -1;
    }

    private void drawSelectedFixtureHandles() {
        int bodyEid = physicsSelectionService.getFocusedBodyEid();
        long selectedFixtureId = physicsSelectionService.getSelectedFixtureId();

        if (bodyEid < 0 || selectedFixtureId <= 0L) return;
        if (!isDrawableFixtureBody(bodyEid)) return;

        PhysicsFixturesComponent fixtures = mFixDefs != null ? mFixDefs.getSafe(bodyEid, null) : null;
        if (fixtures == null || !fixtures.hasFixtures()) return;

        physicsService.ensureFixtureIds(bodyEid);

        FixtureDefData selectedFixture = null;
        for (int i = 0, n = fixtures.fixtures.size; i < n; i++) {
            FixtureDefData fixture = fixtures.fixtures.get(i);
            if (fixture == null) continue;
            if (fixture.fixtureId == selectedFixtureId) {
                selectedFixture = fixture;
                break;
            }
        }

        if (selectedFixture == null) return;

        AuthoredPolygonData authored = findAuthoredPolygonByGeneratedFixture(bodyEid, selectedFixtureId);
        if (authored != null) {
            drawAuthoredPolygonSourceHandles(bodyEid, authored);
            return;
        }

        if (selectedFixture.shapeType == FixtureDefData.SHAPE_CIRCLE) {
            if (!physicsService.computeFixtureCenterWU(bodyEid, selectedFixture, tmpFixtureCenter)) return;
            applyDisplayOffset(bodyEid, tmpFixtureCenter);
            float radiusWU = physicsService.computeFixtureRadiusWU(selectedFixture);
            GizmoDrawHelper.drawShapeVertexHandle(ctx, tmpFixtureCenter.x + radiusWU, tmpFixtureCenter.y);
            return;
        }

        if (shouldHideEditedPolygon(bodyEid, selectedFixture)) {
            return;
        }

        int floatCount = selectedFixture.shapeType == FixtureDefData.SHAPE_BOX
                ? 8
                : Math.max(0, selectedFixture.polyCount * 2);

        ensureFixtureVertsCapacity(floatCount);

        int vertexCount = physicsService.computeFixtureVerticesWU(bodyEid, selectedFixture, tmpFixtureVerts);
        if (vertexCount < 2) return;

        applyDisplayOffset(bodyEid, tmpFixtureVerts, vertexCount);
        GizmoDrawHelper.drawShapeVertices(ctx, tmpFixtureVerts, vertexCount);
    }

    private void drawAuthoredPolygonSourceHandles(int bodyEid, AuthoredPolygonData polygon) {
        if (polygon == null
                || polygon.sourceVerts == null
                || polygon.sourceCount < 3
                || polygon.sourceVerts.length < polygon.sourceCount * 2) {
            return;
        }

        prepareTempPolygonFixture(
                polygon.sourceVerts,
                polygon.sourceCount,
                polygon
        );

        int floatCount = Math.max(0, polygon.sourceCount * 2);
        ensureFixtureVertsCapacity(floatCount);

        int vertexCount = physicsService.computeFixtureVerticesWU(
                bodyEid,
                tmpAuthoringFixture,
                tmpFixtureVerts
        );

        if (vertexCount < 3) return;

        applyDisplayOffset(bodyEid, tmpFixtureVerts, vertexCount);

        GizmoDrawHelper.drawShapeVertices(ctx, tmpFixtureVerts, vertexCount);
    }

    private boolean jointTouchesBody(PhysicsJointComponent base, int bodyEid) {
        return base != null && bodyEid >= 0 && (base.aEid == bodyEid || base.bEid == bodyEid);
    }

    private boolean isSelectedEntity(int entityId) {
        for (int e : selected) {
            if (e == entityId) return true;
        }
        return false;
    }

    private boolean isJointVisibleForOverlay(PhysicsJointComponent base) {
        if (base == null) return false;
        return isEntityVisibleForGizmo(base.aEid) || isEntityVisibleForGizmo(base.bEid);
    }

    private void drawJointOverlay(int jointEid,
                                  PhysicsJointComponent joint,
                                  boolean focusedBody,
                                  boolean hovered) {
        if (joint == null || physicsService == null) return;

        if (joint.type == PhysicsJointComponent.TYPE_GEAR) {
            boolean ok = computeGearJointGizmoWU(jointEid, joint, tmpA, tmpB);
            if (!ok) return;

            if (hovered) {
                ctx.drawer.setColor(Color.WHITE);
                GizmoDrawHelper.drawSolidLine(ctx, tmpA.x, tmpA.y, tmpB.x, tmpB.y, 2.0f);
                GizmoDrawHelper.drawHandleSquare(ctx, tmpA.x, tmpA.y);
                GizmoDrawHelper.drawHandleSquare(ctx, tmpB.x, tmpB.y);
            } else if (focusedBody) {
                ctx.drawer.setColor(CommonLayout.PHYSICS_JOINT_COLOR);
                GizmoDrawHelper.drawSolidLine(ctx, tmpA.x, tmpA.y, tmpB.x, tmpB.y, 2.0f);
                GizmoDrawHelper.drawHandleSquare(ctx, tmpA.x, tmpA.y);
                GizmoDrawHelper.drawHandleSquare(ctx, tmpB.x, tmpB.y);
            } else {
                ctx.drawer.setColor(CommonLayout.PHYSICS_JOINT_OVERLAY_COLOR);
                GizmoDrawHelper.drawSolidLine(ctx, tmpA.x, tmpA.y, tmpB.x, tmpB.y, 1.5f);
            }
            return;
        }

        if (joint.type == PhysicsJointComponent.TYPE_PULLEY) {
            Vector2 groundA = tmpA;
            Vector2 anchorA = tmpB;
            Vector2 groundB = new Vector2();
            Vector2 anchorB = new Vector2();

            boolean ok = computePulleyJointGizmoWU(jointEid, joint, groundA, anchorA, groundB, anchorB);
            if (!ok) return;

            if (hovered) {
                ctx.drawer.setColor(Color.WHITE);
                GizmoDrawHelper.drawSolidLine(ctx, groundA.x, groundA.y, anchorA.x, anchorA.y, 2.0f);
                GizmoDrawHelper.drawSolidLine(ctx, groundB.x, groundB.y, anchorB.x, anchorB.y, 2.0f);
                GizmoDrawHelper.drawSolidLine(ctx, groundA.x, groundA.y, groundB.x, groundB.y, 1.5f);
                GizmoDrawHelper.drawHandleSquare(ctx, groundA.x, groundA.y);
                GizmoDrawHelper.drawHandleSquare(ctx, anchorA.x, anchorA.y);
                GizmoDrawHelper.drawHandleSquare(ctx, groundB.x, groundB.y);
                GizmoDrawHelper.drawHandleSquare(ctx, anchorB.x, anchorB.y);
            } else if (focusedBody) {
                ctx.drawer.setColor(CommonLayout.PHYSICS_JOINT_COLOR);
                GizmoDrawHelper.drawSolidLine(ctx, groundA.x, groundA.y, anchorA.x, anchorA.y, 2.0f);
                GizmoDrawHelper.drawSolidLine(ctx, groundB.x, groundB.y, anchorB.x, anchorB.y, 2.0f);
                GizmoDrawHelper.drawSolidLine(ctx, groundA.x, groundA.y, groundB.x, groundB.y, 1.5f);
                GizmoDrawHelper.drawHandleSquare(ctx, groundA.x, groundA.y);
                GizmoDrawHelper.drawHandleSquare(ctx, anchorA.x, anchorA.y);
                GizmoDrawHelper.drawHandleSquare(ctx, groundB.x, groundB.y);
                GizmoDrawHelper.drawHandleSquare(ctx, anchorB.x, anchorB.y);
            } else {
                ctx.drawer.setColor(CommonLayout.PHYSICS_JOINT_OVERLAY_COLOR);
                GizmoDrawHelper.drawSolidLine(ctx, groundA.x, groundA.y, anchorA.x, anchorA.y, 1.5f);
                GizmoDrawHelper.drawSolidLine(ctx, groundB.x, groundB.y, anchorB.x, anchorB.y, 1.5f);
                GizmoDrawHelper.drawSolidLine(ctx, groundA.x, groundA.y, groundB.x, groundB.y, 1.0f);
            }
            return;
        }

        if (joint.type == PhysicsJointComponent.TYPE_FRICTION || joint.type == PhysicsJointComponent.TYPE_WELD) {
            boolean ok = computeJointPivotForGizmo(joint, tmpA);
            if (!ok) return;

            if (hovered) {
                ctx.drawer.setColor(Color.WHITE);
                GizmoDrawHelper.drawHandleSquare(ctx, tmpA.x, tmpA.y);
            } else if (focusedBody) {
                ctx.drawer.setColor(CommonLayout.PHYSICS_JOINT_COLOR);
                GizmoDrawHelper.drawHandleSquare(ctx, tmpA.x, tmpA.y);
            } else {
                ctx.drawer.setColor(CommonLayout.PHYSICS_JOINT_OVERLAY_COLOR);
                GizmoDrawHelper.drawHandleSquare(ctx, tmpA.x, tmpA.y);
            }
            return;
        }

        if (joint.type == PhysicsJointComponent.TYPE_WHEEL) {
            boolean ok = computeJointPivotForGizmo(joint, tmpA);
            if (!ok) return;

            if (hovered) {
                ctx.drawer.setColor(Color.WHITE);
                GizmoDrawHelper.drawHandleSquare(ctx, tmpA.x, tmpA.y);
            } else if (focusedBody) {
                ctx.drawer.setColor(CommonLayout.PHYSICS_JOINT_COLOR);
                GizmoDrawHelper.drawHandleSquare(ctx, tmpA.x, tmpA.y);
            } else {
                ctx.drawer.setColor(CommonLayout.PHYSICS_JOINT_OVERLAY_COLOR);
                GizmoDrawHelper.drawHandleSquare(ctx, tmpA.x, tmpA.y);
            }

            drawWheelAxisOverlay(jointEid, joint, hovered, focusedBody, tmpA);
            return;
        }

        if (joint.type == PhysicsJointComponent.TYPE_MOTOR) {
            boolean ok = computeMotorJointGizmoWU(jointEid, joint, tmpA, tmpB);
            if (!ok) return;

            if (hovered) {
                ctx.drawer.setColor(Color.WHITE);
                GizmoDrawHelper.drawSolidLine(ctx, tmpA.x, tmpA.y, tmpB.x, tmpB.y, 2.0f);
                GizmoDrawHelper.drawHandleSquare(ctx, tmpB.x, tmpB.y);
            } else if (focusedBody) {
                ctx.drawer.setColor(CommonLayout.PHYSICS_JOINT_COLOR);
                GizmoDrawHelper.drawSolidLine(ctx, tmpA.x, tmpA.y, tmpB.x, tmpB.y, 2.0f);
                GizmoDrawHelper.drawHandleSquare(ctx, tmpB.x, tmpB.y);
            } else {
                ctx.drawer.setColor(CommonLayout.PHYSICS_JOINT_OVERLAY_COLOR);
                GizmoDrawHelper.drawSolidLine(ctx, tmpA.x, tmpA.y, tmpB.x, tmpB.y, 1.5f);
            }
            return;
        }

        boolean ok;
        if (joint.type == PhysicsJointComponent.TYPE_PRISMATIC) {
            ok = physicsService.computePrismaticJointGizmoWU(jointEid, tmpA, tmpB);
            if (ok) {
                applyDisplayOffset(joint.aEid, tmpA);
                applyDisplayOffset(joint.aEid, tmpB);
            }
        } else {
            ok = computeJointWorldEndpoints(jointEid, tmpA, tmpB);
        }
        if (!ok) return;

        if (hovered) {
            ctx.drawer.setColor(Color.WHITE);
            GizmoDrawHelper.drawSolidLine(ctx, tmpA.x, tmpA.y, tmpB.x, tmpB.y, 2.0f);
            GizmoDrawHelper.drawHandleSquare(ctx, tmpA.x, tmpA.y);
            GizmoDrawHelper.drawHandleSquare(ctx, tmpB.x, tmpB.y);
        } else if (focusedBody) {
            ctx.drawer.setColor(CommonLayout.PHYSICS_JOINT_COLOR);
            GizmoDrawHelper.drawSolidLine(ctx, tmpA.x, tmpA.y, tmpB.x, tmpB.y, 2.0f);
            GizmoDrawHelper.drawHandleSquare(ctx, tmpA.x, tmpA.y);
            GizmoDrawHelper.drawHandleSquare(ctx, tmpB.x, tmpB.y);
        } else {
            ctx.drawer.setColor(CommonLayout.PHYSICS_JOINT_OVERLAY_COLOR);
            GizmoDrawHelper.drawSolidLine(ctx, tmpA.x, tmpA.y, tmpB.x, tmpB.y, 1.5f);
        }
    }

    private void drawSelectedJointGizmos() {
        if (physicsService == null || !physicsService.isAvailable()) return;

        for (int e : selected) {
            if (!mJoint.has(e)) continue;

            PhysicsJointComponent joint = mJoint.getSafe(e, null);
            if (joint == null) continue;

            if (joint.type == PhysicsJointComponent.TYPE_GEAR) {
                boolean ok = computeGearJointGizmoWU(e, joint, tmpA, tmpB);
                if (!ok) continue;

                ctx.drawer.setColor(CommonLayout.SELECTION_HIGHLIGHT_COLOR);
                GizmoDrawHelper.drawSolidLine(ctx, tmpA.x, tmpA.y, tmpB.x, tmpB.y, 2.5f);
                continue;
            }

            if (joint.type == PhysicsJointComponent.TYPE_PULLEY) {
                Vector2 groundA = tmpA;
                Vector2 anchorA = tmpB;
                Vector2 groundB = new Vector2();
                Vector2 anchorB = new Vector2();

                boolean ok = computePulleyJointGizmoWU(e, joint, groundA, anchorA, groundB, anchorB);
                if (!ok) continue;

                ctx.drawer.setColor(CommonLayout.SELECTION_HIGHLIGHT_COLOR);
                GizmoDrawHelper.drawSolidLine(ctx, groundA.x, groundA.y, anchorA.x, anchorA.y, 2.5f);
                GizmoDrawHelper.drawSolidLine(ctx, groundB.x, groundB.y, anchorB.x, anchorB.y, 2.5f);
                GizmoDrawHelper.drawSolidLine(ctx, groundA.x, groundA.y, groundB.x, groundB.y, 2.0f);
                continue;
            }

            if (joint.type == PhysicsJointComponent.TYPE_FRICTION || joint.type == PhysicsJointComponent.TYPE_WELD) {
                boolean ok = computeJointPivotForGizmo(joint, tmpA);
                if (!ok) continue;

                ctx.drawer.setColor(CommonLayout.SELECTION_HIGHLIGHT_COLOR);
                GizmoDrawHelper.drawHandleSquare(ctx, tmpA.x, tmpA.y);
                continue;
            }
            if (joint.type == PhysicsJointComponent.TYPE_REVOLUTE) {
                boolean ok = computeJointPivotForGizmo(joint, tmpA);
                if (!ok) continue;

                ctx.drawer.setColor(CommonLayout.SELECTION_HIGHLIGHT_COLOR);
                GizmoDrawHelper.drawHandleSquare(ctx, tmpA.x, tmpA.y);
                continue;
            }
            if (joint.type == PhysicsJointComponent.TYPE_WHEEL) {
                boolean ok = computeJointPivotForGizmo(joint, tmpA);
                if (!ok) continue;

                ctx.drawer.setColor(CommonLayout.SELECTION_HIGHLIGHT_COLOR);
                GizmoDrawHelper.drawHandleSquare(ctx, tmpA.x, tmpA.y);
                continue;
            }

            if (joint.type == PhysicsJointComponent.TYPE_MOTOR) {
                boolean ok = computeMotorJointGizmoWU(e, joint, tmpA, tmpB);
                if (!ok) continue;

                ctx.drawer.setColor(CommonLayout.SELECTION_HIGHLIGHT_COLOR);
                GizmoDrawHelper.drawSolidLine(ctx, tmpA.x, tmpA.y, tmpB.x, tmpB.y, 2.5f);
                continue;
            }

            boolean ok;
            if (joint.type == PhysicsJointComponent.TYPE_PRISMATIC) {
                ok = physicsService.computePrismaticJointGizmoWU(e, tmpA, tmpB);
                if (ok) {
                    applyDisplayOffset(joint.aEid, tmpA);
                    applyDisplayOffset(joint.aEid, tmpB);
                }
            } else {
                ok = computeJointWorldEndpoints(e, tmpA, tmpB);
            }
            if (!ok) continue;

            ctx.drawer.setColor(CommonLayout.SELECTION_HIGHLIGHT_COLOR);
            GizmoDrawHelper.drawSolidLine(ctx, tmpA.x, tmpA.y, tmpB.x, tmpB.y, 2.5f);
            if (joint.type == PhysicsJointComponent.TYPE_DISTANCE) {
                GizmoDrawHelper.drawHandleSquare(ctx, tmpA.x, tmpA.y);
                GizmoDrawHelper.drawHandleSquare(ctx, tmpB.x, tmpB.y);
            }
        }
    }

    private boolean computeJointPivotForGizmo(PhysicsJointComponent joint, Vector2 outPivot) {
        if (joint == null || physicsService == null) return false;

        int aEid = joint.aEid;
        if (aEid < 0) return false;

        boolean ok = physicsService.computeAnchorWorldWU(aEid, joint.anchorAx, joint.anchorAy, outPivot);
        if (!ok) return false;

        applyDisplayOffset(aEid, outPivot);
        return true;
    }

    private boolean computeMotorJointGizmoWU(int jointEid, PhysicsJointComponent joint, Vector2 outA, Vector2 outB) {
        if (joint == null || physicsService == null) return false;

        PhysicsMotorJointComponent motor = mMotor.getSafe(jointEid, null);
        if (motor == null) return false;

        int aEid = joint.aEid;
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

    private boolean computeWheelJointGizmoWU(int jointEid, PhysicsJointComponent joint, Vector2 outA, Vector2 outB) {
        if (joint == null || physicsService == null) return false;
        if (mWheel.getSafe(jointEid, null) == null) return false;

        int aEid = joint.aEid;
        int bEid = joint.bEid;
        if (aEid < 0 || bEid < 0 || aEid == bEid) return false;

        TransformComponent ta = mT.getSafe(aEid, null);
        TransformComponent tb = mT.getSafe(bEid, null);
        if (ta == null || tb == null) return false;

        float ppm = resolvePixelsPerMeter();

        float ax = anchorWorldX_WU(ta, joint.anchorAx, joint.anchorAy, ppm);
        float ay = anchorWorldY_WU(ta, joint.anchorAx, joint.anchorAy, ppm);
        float bx = anchorWorldX_WU(tb, joint.anchorBx, joint.anchorBy, ppm);
        float by = anchorWorldY_WU(tb, joint.anchorBx, joint.anchorBy, ppm);

        outA.set(ax, ay);
        outB.set(bx, by);

        applyDisplayOffset(aEid, outA);
        applyDisplayOffset(bEid, outB);
        return true;
    }

    private void drawWheelAxisOverlay(int jointEid,
                                      PhysicsJointComponent joint,
                                      boolean hovered,
                                      boolean focusedBody,
                                      Vector2 anchorA) {
        PhysicsWheelJointComponent wheel = mWheel.getSafe(jointEid, null);
        TransformComponent ta = mT.getSafe(joint.aEid, null);
        if (wheel == null || ta == null || anchorA == null) return;

        float axisLen = HandleHelper.pxToWorld(ctx.cam, 20f);
        float cos = MathUtils.cos(ta.rotationRad);
        float sin = MathUtils.sin(ta.rotationRad);
        float dx = wheel.axisX * cos - wheel.axisY * sin;
        float dy = wheel.axisX * sin + wheel.axisY * cos;
        float mag2 = dx * dx + dy * dy;
        if (mag2 <= 1e-6f) return;
        float invMag = 1.0f / (float) Math.sqrt(mag2);
        dx *= invMag;
        dy *= invMag;

        float half = axisLen * 0.5f;
        float x0 = anchorA.x - dx * half;
        float y0 = anchorA.y - dy * half;
        float x1 = anchorA.x + dx * half;
        float y1 = anchorA.y + dy * half;

        if (hovered) {
            ctx.drawer.setColor(Color.WHITE);
            GizmoDrawHelper.drawSolidLine(ctx, x0, y0, x1, y1, 2.0f);
        } else if (focusedBody) {
            ctx.drawer.setColor(CommonLayout.PHYSICS_JOINT_COLOR);
            GizmoDrawHelper.drawSolidLine(ctx, x0, y0, x1, y1, 2.0f);
        } else {
            ctx.drawer.setColor(CommonLayout.PHYSICS_JOINT_OVERLAY_COLOR);
            GizmoDrawHelper.drawSolidLine(ctx, x0, y0, x1, y1, 1.0f);
        }
    }

    private boolean computePulleyJointGizmoWU(int jointEid,
                                              PhysicsJointComponent joint,
                                              Vector2 outGroundA,
                                              Vector2 outAnchorA,
                                              Vector2 outGroundB,
                                              Vector2 outAnchorB) {
        if (joint == null || physicsService == null) return false;

        PhysicsPulleyJointComponent pulley = mPulley.getSafe(jointEid, null);
        if (pulley == null) return false;

        int aEid = joint.aEid;
        int bEid = joint.bEid;
        if (aEid < 0 || bEid < 0 || aEid == bEid) return false;

        TransformComponent ta = mT.getSafe(aEid, null);
        TransformComponent tb = mT.getSafe(bEid, null);
        if (ta == null || tb == null) return false;

        float ppm = resolvePixelsPerMeter();

        // Ground anchors are stored in world meters.
        outGroundA.set(pulley.groundAx * ppm, pulley.groundAy * ppm);
        outGroundB.set(pulley.groundBx * ppm, pulley.groundBy * ppm);

        float ax = anchorWorldX_WU(ta, joint.anchorAx, joint.anchorAy, ppm);
        float ay = anchorWorldY_WU(ta, joint.anchorAx, joint.anchorAy, ppm);
        float bx = anchorWorldX_WU(tb, joint.anchorBx, joint.anchorBy, ppm);
        float by = anchorWorldY_WU(tb, joint.anchorBx, joint.anchorBy, ppm);

        outAnchorA.set(ax, ay);
        outAnchorB.set(bx, by);

        applyDisplayOffset(aEid, outGroundA);
        applyDisplayOffset(aEid, outAnchorA);
        applyDisplayOffset(bEid, outGroundB);
        applyDisplayOffset(bEid, outAnchorB);

        return true;
    }

    private boolean computeGearJointGizmoWU(int jointEid, PhysicsJointComponent joint, Vector2 outA, Vector2 outB) {
        if (joint == null || physicsService == null) return false;

        PhysicsGearJointComponent gear = mGear.getSafe(jointEid, null);
        if (gear == null) return false;

        PhysicsJointComponent src1 = mJoint.getSafe(gear.joint1Eid, null);
        PhysicsJointComponent src2 = mJoint.getSafe(gear.joint2Eid, null);
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

    private void ensureFixtureVertsCapacity(int floatCount) {
        if (floatCount <= 0) return;
        if (tmpFixtureVerts.length >= floatCount) return;
        tmpFixtureVerts = new float[Math.max(floatCount, tmpFixtureVerts.length * 2)];
    }

    private boolean isFixtureOverlayVisible() {
        return isExplicitPhysicsEditMode();
    }

    private boolean isJointOverlayVisible() {
        return isExplicitPhysicsEditMode();
    }

    private boolean isExplicitPhysicsEditMode() {
        return physicsSelectionService.getFocusedBodyEid() >= 0;
    }

    private void drawHoveredEntityGizmo(boolean physicsEditMode) {
        if (!entityGizmoEnabled || physicsEditMode || selectionService == null) return;

        int hoveredEntityId = selectionService.getHoveredEntityId();
        if (hoveredEntityId < 0) return;
        if (isSelectedEntity(hoveredEntityId)) return;
        if (mJoint.has(hoveredEntityId)) return;
        if (isLightEntity(hoveredEntityId)) return;
        if (!mOBB.has(hoveredEntityId)) return;
        if (!isEntityVisibleForGizmo(hoveredEntityId)) return;

        float[] obb = computeOBBWorldCorners(hoveredEntityId);
        if (obb == null) return;

        drawHoveredObb(obb);
    }

    private void drawHoveredObb(float[] obb) {
        ctx.drawer.setColor(Color.WHITE);
        float x0 = obb[0], y0 = obb[1], x1 = obb[2], y1 = obb[3],
                x2 = obb[4], y2 = obb[5], x3 = obb[6], y3 = obb[7];
        drawHoveredLine(x0, y0, x1, y1);
        drawHoveredLine(x1, y1, x2, y2);
        drawHoveredLine(x2, y2, x3, y3);
        drawHoveredLine(x3, y3, x0, y0);
    }

    private void drawHoveredLine(float x1, float y1, float x2, float y2) {
        ShapeHelper.drawDashedLineWorld(
                ctx.drawer, ctx.cam,
                GizmoDrawHelper.thicknessPx, GizmoDrawHelper.dashPx, GizmoDrawHelper.gapPx,
                x1, y1, x2, y2,
                ctx.screenWidth()
        );
    }

    public void refreshOverlayMouse() {
        readMouseWorld(tmpMouseWorld);
    }

    public void setLassoRect(boolean visible, float x0, float y0, float x1, float y1) {
        this.lassoVisible = visible;
        if (visible) {
            this.lassoX0 = x0;
            this.lassoY0 = y0;
            this.lassoX1 = x1;
            this.lassoY1 = y1;
        }
    }

    public void showRectPreview(float x0, float y0, float x1, float y1) {
        rectPreviewVisible = true;
        tiledRectPreview = false;
        tiledRectPreviewMap = null;
        rectPreviewX0 = x0;
        rectPreviewY0 = y0;
        rectPreviewX1 = x1;
        rectPreviewY1 = y1;
    }

    public void hideRectPreview() {
        rectPreviewVisible = false;
        tiledRectPreview = false;
        tiledRectPreviewMap = null;
    }

    public void showTiledRectPreview(TiledMapLayerData map,
                                     int minGX,
                                     int minGY,
                                     int maxGX,
                                     int maxGY) {
        rectPreviewVisible = true;
        tiledRectPreview = true;
        tiledRectPreviewMap = map;
        tiledRectMinGX = minGX;
        tiledRectMinGY = minGY;
        tiledRectMaxGX = maxGX;
        tiledRectMaxGY = maxGY;
    }

    private boolean computeJointWorldEndpoints(int jointEid, Vector2 outA, Vector2 outB) {
        PhysicsJointComponent j = mJoint.getSafe(jointEid, null);
        if (j == null) return false;

        int aEid = j.aEid;
        int bEid = j.bEid;
        if (aEid < 0 || bEid < 0 || aEid == bEid) return false;

        TransformComponent ta = mT.getSafe(aEid, null);
        TransformComponent tb = mT.getSafe(bEid, null);
        if (ta == null || tb == null) return false;

        float ppm = resolvePixelsPerMeter();
        float ax = anchorWorldX_WU(ta, j.anchorAx, j.anchorAy, ppm);
        float ay = anchorWorldY_WU(ta, j.anchorAx, j.anchorAy, ppm);
        float bx = anchorWorldX_WU(tb, j.anchorBx, j.anchorBy, ppm);
        float by = anchorWorldY_WU(tb, j.anchorBx, j.anchorBy, ppm);

        outA.set(ax, ay);
        outB.set(bx, by);

        applyDisplayOffset(aEid, outA);
        applyDisplayOffset(bEid, outB);

        return true;
    }

    private static float anchorWorldX_WU(TransformComponent t, float localAx_m, float localAy_m, float ppm) {
        float cos = MathUtils.cos(t.rotationRad);
        float sin = MathUtils.sin(t.rotationRad);
        float rx_m = localAx_m * cos - localAy_m * sin;
        return t.x + rx_m * ppm;
    }

    private static float anchorWorldY_WU(TransformComponent t, float localAx_m, float localAy_m, float ppm) {
        float cos = MathUtils.cos(t.rotationRad);
        float sin = MathUtils.sin(t.rotationRad);
        float ry_m = localAx_m * sin + localAy_m * cos;
        return t.y + ry_m * ppm;
    }

    private float resolvePixelsPerMeter() {
        ProjectConfig cfg = ProjectConfig.getInstance();
        if (cfg == null) return 100f;
        SceneMeta meta = cfg.getCurrentSceneMeta();
        if (meta == null) return 100f;
        return meta.pixelsPerMeter > 0f ? meta.pixelsPerMeter : 100f;
    }

    private void applyDisplayOffset(int entityId, Vector2 p) {
        // Studio tools operate in logical world space. Runtime render offsets may already
        // be present in RenderStateSOA because Studio reuses runtime systems.
        // Applying RenderSpaceMapper offsets here would double-apply display offsets to
        // gizmos and physics handles.
    }

    private boolean isEntityVisibleForGizmo(int e) {
        if (!isLayerVisible(e)) return false;
        return !mVis.has(e) || mVis.get(e).isVisible();
    }

    private boolean isLayerVisible(int e) {
        if (mEntityIndex == null || !mEntityIndex.has(e)) return true;

        int layerIndex = mEntityIndex.get(e).getLayerIndex();
        int layerEntityId = layerService != null ? layerService.getLayerEntity(layerIndex) : -1;
        if (layerEntityId == -1) return true;

        VisibilityComponent layerVis = mVis.getSafe(layerEntityId, null);
        return layerVis == null || layerVis.isVisible();
    }

    private boolean isLayerEntityVisible(int layerEntityId) {
        if (layerEntityId < 0 || mVis == null) return false;
        VisibilityComponent layerVis = mVis.getSafe(layerEntityId, null);
        return layerVis == null || layerVis.isVisible();
    }

    private float[] computeOBBWorldCorners(int e) {
        OrientedBoundsComponent b = mOBB.get(e);
        if (b == null) return null;

        OrientedBoundsHelper.toCorners(b, tmpCorners);
        applyDisplayOffset(e, tmpCorners);
        return tmpCorners;
    }

    private void applyDisplayOffset(int entityId, float[] corners) {
        applyDisplayOffset(entityId, corners, corners != null ? corners.length / 2 : 0);
    }

    private void applyDisplayOffset(int entityId, float[] corners, int vertexCount) {
        // See applyDisplayOffset(int, Vector2): Studio gizmos are authored and drawn in
        // logical world space, regardless of runtime RenderStateSOA display offsets.
    }

    private boolean isLightEntity(int e) {
        return (mPointLight != null && mPointLight.has(e))
                || (mConeLight != null && mConeLight.has(e));
    }

    private SceneMeta currentSceneMeta() {
        ProjectConfig cfg = ProjectConfig.getInstance();
        return cfg != null ? cfg.getCurrentSceneMeta() : null;
    }

    public void setCursor(CursorKind kind, float angleRad, Vector2 mouseWorld) {
        this.cursorKind = kind;
        this.cursorAngleRad = angleRad;
        this.cursorWorld.set(mouseWorld);
    }

    public void clearCursor() {
        this.cursorKind = CursorKind.NONE;
    }

    public void enableTiledOverlay(float w, float h) {
        this.tiledOverlayEnabled = true;
        this.tiledWidth = w;
        this.tiledHeight = h;
    }

    public void disableTiledOverlay() {
        this.tiledOverlayEnabled = false;
        if (spatialBlockSelectionService != null && spatialBlockSelectionService.isEditingActive()) {
            spatialBlockSelectionService.clearPlacementTarget();
        }
    }

    private void readMouseWorld(Vector2 out) {
        coordSpaces.screenToWorld(Gdx.input.getX(), Gdx.input.getY(), out);
    }
}
