package games.pixscape.studio.ui.property.entityproperties.physics;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Array;
import com.kotcrab.vis.ui.util.InputValidator;
import com.kotcrab.vis.ui.widget.*;
import games.pixscape.runtime.component.DimensionsComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.physics.FixtureDefData;
import games.pixscape.runtime.component.physics.FixtureIdSequence;
import games.pixscape.runtime.component.physics.PhysicsFixturesComponent;
import games.pixscape.runtime.render.PhysicsDirtyBits;
import games.pixscape.studio.component.physics.AuthoredPolygonData;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.configuration.SceneMeta;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.history.commands.*;
import games.pixscape.studio.service.physics.PhysicsPolygonAuthoringService;
import games.pixscape.studio.service.physics.PhysicsSelectionService;
import games.pixscape.studio.ui.config.CommonLayout;
import games.pixscape.studio.ui.property.entityproperties.EntityPropertiesContext;
import games.pixscape.studio.ui.widget.CollapsibleVisTable;
import games.pixscape.studio.ui.widget.FloatField;
import games.pixscape.studio.ui.widget.IntField;
import games.pixscape.studio.ui.widget.UiBinders;

import java.util.function.Consumer;

public final class FixturesPanel extends CollapsibleWidget {

    private static final float MIN_SHAPE_HALF_M = 0.001f;
    private static final Array<String> SHAPES = Array.with("Box", "Circle", "Polygon");

    private final EntityPropertiesContext ctx;
    private final PhysicsPolygonAuthoringService polygonAuthoringService;

    private final VisTable root = new VisTable(true);
    private final CollapsibleVisTable detailsBlock = new CollapsibleVisTable(true);

    private final VisSelectBox<String> shapeBox = new VisSelectBox<>();
    private final VisCheckBox sensorBox = new VisCheckBox("Sensor");

    private final FloatField densityField;
    private final FloatField frictionField;
    private final FloatField restitutionField;

    private final FloatField widthWUField;
    private final FloatField heightWUField;
    private final FloatField diameterWUField;

    private final VisTextButton autoSizeBtn = new VisTextButton("Auto size");
    private final VisTextButton duplicateFixtureBtn = new VisTextButton("Duplicate");
    private final VisTextButton deleteFixtureBtn = new VisTextButton("Delete");

    private final FloatField offsetXWUField;
    private final FloatField offsetYWUField;

    private final IntField categoryBitsField;
    private final IntField maskBitsField;
    private final IntField groupIndexField;

    private final CollapsibleVisTable boxSizeBlock = new CollapsibleVisTable(true);
    private final CollapsibleVisTable circleSizeBlock = new CollapsibleVisTable(true);
    private final CollapsibleVisTable offsetsBlock = new CollapsibleVisTable(true);

    private final UiBinders.SelectBoxBinder<String> shapeBinder;
    private final UiBinders.CheckBoxBinder sensorBinder;

    private int entityId = -1;
    private boolean internalRefresh = false;
    private long lastSelectedFixtureId = PhysicsSelectionService.NO_FIXTURE;
    private int lastFixtureCount = -1;

    private final int MY_TAG = EventFlow.tag(this);

    public FixturesPanel(EntityPropertiesContext ctx) {
        super();
        this.ctx = ctx;
        this.polygonAuthoringService = ctx.physicsPolygonAuthoringService;
        autoSizeBtn.setColor(CommonLayout.BUTTON_COLOR);
        duplicateFixtureBtn.setColor(CommonLayout.BUTTON_COLOR);
        deleteFixtureBtn.setColor(CommonLayout.BUTTON_COLOR);

        widthWUField = new FloatField(ctx.world, this::readWidthWU, this::hasActiveFixture).setDisplayDecimals(2);
        widthWUField.setApplier((eid, v) -> {
            applyFixtureEdit(eid, PhysicsDirtyBits.FIXTURE, false, snapshot -> {
                if (!isBoxShape(snapshot.shapeType)) return;
                float ppm = resolvePixelsPerMeter();
                float wM = wuToM(Math.abs(v), ppm);
                snapshot.halfW = clampMin(wM * 0.5f, MIN_SHAPE_HALF_M);
            });
            refreshShapeUi(eid);
        });

        heightWUField = new FloatField(ctx.world, this::readHeightWU, this::hasActiveFixture).setDisplayDecimals(2);
        heightWUField.setApplier((eid, v) -> {
            applyFixtureEdit(eid, PhysicsDirtyBits.FIXTURE, false, snapshot -> {
                if (!isBoxShape(snapshot.shapeType)) return;
                float ppm = resolvePixelsPerMeter();
                float hM = wuToM(Math.abs(v), ppm);
                snapshot.halfH = clampMin(hM * 0.5f, MIN_SHAPE_HALF_M);
            });
            refreshShapeUi(eid);
        });

        diameterWUField = new FloatField(ctx.world, this::readDiameterWU, this::hasActiveFixture).setDisplayDecimals(2);
        diameterWUField.setApplier((eid, v) -> {
            applyFixtureEdit(eid, PhysicsDirtyBits.FIXTURE, false, snapshot -> {
                if (!isCircleShape(snapshot.shapeType)) return;
                float ppm = resolvePixelsPerMeter();
                float dM = wuToM(Math.abs(v), ppm);
                snapshot.radius = clampMin(dM * 0.5f, MIN_SHAPE_HALF_M);
            });
            refreshShapeUi(eid);
        });

        offsetXWUField = new FloatField(ctx.world, this::readOffsetXWU, this::hasActiveFixture).setDisplayDecimals(2);
        offsetXWUField.setApplier((eid, v) -> {
            applyFixtureEdit(eid, PhysicsDirtyBits.FIXTURE, false, snapshot -> {
                float ppm = resolvePixelsPerMeter();
                snapshot.offsetX = wuToM(v, ppm);
            });
        });

        offsetYWUField = new FloatField(ctx.world, this::readOffsetYWU, this::hasActiveFixture).setDisplayDecimals(2);
        offsetYWUField.setApplier((eid, v) -> {
            applyFixtureEdit(eid, PhysicsDirtyBits.FIXTURE, false, snapshot -> {
                float ppm = resolvePixelsPerMeter();
                snapshot.offsetY = wuToM(v, ppm);
            });
        });

        densityField = new FloatField(ctx.world, (int e) -> {
            FixtureDefData f = activeFixture(e);
            return (f != null) ? f.density : 0f;
        }, this::hasActiveFixture).setDisplayDecimals(3);
        densityField.setApplier((eid, v) -> {
            applyFixtureEdit(eid, PhysicsDirtyBits.FIXTURE, false, snapshot -> snapshot.density = v);
        });

        frictionField = new FloatField(ctx.world, (int e) -> {
            FixtureDefData f = activeFixture(e);
            return (f != null) ? f.friction : 0f;
        }, this::hasActiveFixture).setDisplayDecimals(3);
        frictionField.setApplier((eid, v) -> {
            applyFixtureEdit(eid, PhysicsDirtyBits.FIXTURE, false, snapshot -> snapshot.friction = v);
        });

        restitutionField = new FloatField(ctx.world, (int e) -> {
            FixtureDefData f = activeFixture(e);
            return (f != null) ? f.restitution : 0f;
        }, this::hasActiveFixture).setDisplayDecimals(3);
        restitutionField.setApplier((eid, v) -> {
            applyFixtureEdit(eid, PhysicsDirtyBits.FIXTURE, false, snapshot -> snapshot.restitution = v);
        });

        categoryBitsField = newHexIntField((int e) -> {
            FixtureDefData f = activeFixture(e);
            return (f != null) ? (((int) f.categoryBits) & 0xFFFF) : 0;
        });
        categoryBitsField.setApplier((eid, v) -> {
            applyFixtureEdit(eid, PhysicsDirtyBits.FILTER, false, snapshot -> {
                snapshot.categoryBits = toShortClampedUnsigned(v);
            });
        });

        maskBitsField = newHexIntField((int e) -> {
            FixtureDefData f = activeFixture(e);
            return (f != null) ? (((int) f.maskBits) & 0xFFFF) : 0;
        });
        maskBitsField.setApplier((eid, v) -> {
            applyFixtureEdit(eid, PhysicsDirtyBits.FILTER, false, snapshot -> {
                snapshot.maskBits = toShortClampedUnsigned(v);
            });
        });

        groupIndexField = new IntField(ctx.world, (int e) -> {
            FixtureDefData f = activeFixture(e);
            return (f != null) ? (int) f.groupIndex : 0;
        }, this::hasActiveFixture);
        groupIndexField.setApplier((eid, v) -> {
            applyFixtureEdit(eid, PhysicsDirtyBits.FILTER, false, snapshot -> {
                snapshot.groupIndex = toShortClampedSigned(v);
            });
        });

        setTable(root);
        root.left().top();
        root.defaults().left().pad(1);

        root.add(new VisLabel("SHAPE")).center().row();

        VisTable d = detailsBlock.content();
        d.left().top().padTop(5);
        d.defaults().left().pad(1);

        d.add(new VisLabel("Type")).width(CommonLayout.LABEL_WIDTH).left();
        VisTable shape = new VisTable(true);
        shape.add(shapeBox).width(CommonLayout.FIELD_WIDTH).left();
        shape.add(autoSizeBtn).left();
        d.add(shape).colspan(2).left().row();

        d.add(sensorBox).left().colspan(2).row();

        VisTable boxTable = boxSizeBlock.content();
        boxTable.left().top();
        boxTable.defaults().left().pad(1);
        boxTable.add(new VisLabel("Width (WU)")).width(CommonLayout.LABEL_WIDTH).left();
        boxTable.add(widthWUField).left().row();
        boxTable.add(new VisLabel("Height (WU)")).width(CommonLayout.LABEL_WIDTH).left();
        boxTable.add(heightWUField).left().row();
        d.add(boxSizeBlock).colspan(2).left().growX().row();

        VisTable circleTable = circleSizeBlock.content();
        circleTable.left().top();
        circleTable.defaults().left().pad(1);
        circleTable.add(new VisLabel("Diameter (WU)")).width(CommonLayout.LABEL_WIDTH).left();
        circleTable.add(diameterWUField).left().row();
        d.add(circleSizeBlock).colspan(2).left().growX().row();

        VisTable off = offsetsBlock.content();
        off.left().top();
        off.defaults().left().pad(1);
        off.add(new VisLabel("Offset X (WU)")).width(CommonLayout.LABEL_WIDTH).left();
        off.add(offsetXWUField).left().row();
        off.add(new VisLabel("Offset Y (WU)")).width(CommonLayout.LABEL_WIDTH).left();
        off.add(offsetYWUField).left().row();
        d.add(offsetsBlock).colspan(2).left().growX().row();

        d.addSeparator().colspan(2).growX().padTop(2).padBottom(2).row();
        d.add(new VisLabel("Density")).width(CommonLayout.LABEL_WIDTH).left();
        d.add(densityField).left().row();
        d.add(new VisLabel("Friction")).width(CommonLayout.LABEL_WIDTH).left();
        d.add(frictionField).left().row();
        d.add(new VisLabel("Restitution")).width(CommonLayout.LABEL_WIDTH).left();
        d.add(restitutionField).left().row();

        d.addSeparator().colspan(2).growX().padTop(2).padBottom(2).row();
        d.add(new VisLabel("Category bits")).width(CommonLayout.LABEL_WIDTH).left();
        d.add(categoryBitsField).left().row();
        d.add(new VisLabel("Mask bits")).width(CommonLayout.LABEL_WIDTH).left();
        d.add(maskBitsField).left().row();
        d.add(new VisLabel("Group index")).width(CommonLayout.LABEL_WIDTH).left();
        d.add(groupIndexField).left().row();

        root.add(detailsBlock).growX().left().row();
        detailsBlock.show(false);

        VisTable actions = new VisTable(true);
        actions.defaults().left().padRight(4f);
        actions.add(duplicateFixtureBtn).left();
        actions.add(deleteFixtureBtn).left();
        root.add(actions).padTop(30).center().row();


        shapeBinder = new UiBinders.SelectBoxBinder<>(
                ctx.world,
                shapeBox,
                this::hasActiveFixture,
                (int e) -> {
                    FixtureDefData f = activeFixture(e);
                    return SHAPES.get(clamp((f != null) ? f.shapeType : FixtureDefData.SHAPE_BOX, SHAPES.size - 1));
                },
                (eid, before, after) -> {
                    int idx = SHAPES.indexOf(after, false);
                    if (idx < 0) idx = FixtureDefData.SHAPE_BOX;
                    final int targetShape = idx;
                    applyFixtureEdit(eid, PhysicsDirtyBits.FIXTURE, true, snapshot -> {
                        int prevType = snapshot.shapeType;
                        snapshot.shapeType = targetShape;
                        if (targetShape == FixtureDefData.SHAPE_POLYGON) {
                            seedDefaultPolygon(snapshot, prevType);
                        }
                    });
                    refreshShapeUi(eid);
                }
        );
        shapeBinder.setItems(SHAPES);

        sensorBinder = new UiBinders.CheckBoxBinder(
                ctx.world,
                sensorBox,
                this::hasActiveFixture,
                (int e) -> {
                    FixtureDefData f = activeFixture(e);
                    return f != null && f.isSensor;
                },
                (eid, v) -> {
                    applyFixtureEdit(eid, PhysicsDirtyBits.FIXTURE, false, snapshot -> snapshot.isSensor = v);
                }
        );

        duplicateFixtureBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (internalRefresh || !canDuplicateActiveFixture(entityId)) return;
                executeCommand(new DuplicateFixtureCommand(
                        ctx.world,
                        ctx.history.historyIds(),
                        ctx.physicsSelectionService,
                        entityId,
                        ctx.physicsSelectionService.getSelectedFixtureId()
                ));
                refreshFromModel(entityId);
                event.handle();
            }
        });

        deleteFixtureBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (internalRefresh || !canDeleteActiveFixture(entityId)) return;

                long fixtureId = ctx.physicsSelectionService.getSelectedFixtureId();
                if (fixtureId <= 0L) return;

                AuthoredPolygonData authored =
                        polygonAuthoringService.findByGeneratedFixtureId(entityId, fixtureId);

                if (authored != null) {
                    DeleteAuthoredPolygonCommand cmd = new DeleteAuthoredPolygonCommand(
                            ctx.world,
                            ctx.history.historyIds(),
                            ctx.physicsSelectionService,
                            entityId,
                            authored.authoringId
                    );

                    if (!cmd.isNoop()) {
                        ctx.history.execute(cmd);
                        refreshFromModel(entityId);
                    }

                    event.handle();
                    return;
                }

                executeCommand(new DeleteFixtureCommand(
                        ctx.world,
                        ctx.history.historyIds(),
                        ctx.physicsSelectionService,
                        entityId,
                        fixtureId
                ));

                refreshFromModel(entityId);
                event.handle();
            }
        });

        autoSizeBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (internalRefresh || !hasActiveFixture(entityId)) return;
                autoSizeFromSprite(entityId);
                refreshFromModel(entityId);
                event.handle();
            }
        });
    }

    public void setEntityId(int entityId) {
        this.entityId = entityId;
        refreshFromModel(entityId);
    }

    public boolean hasSelectedFixture() {
        return activeFixture(entityId) != null;
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        if (entityId < 0 || internalRefresh) return;

        long fixtureId = resolveSelectedFixtureIdForPanel(entityId);
        int fixtureCount = countFixtures(entityId);
        if (fixtureId != lastSelectedFixtureId || fixtureCount != lastFixtureCount) {
            refreshFromModel(entityId);
        }
    }

    public void refreshNow() {
        refreshFromModel(entityId);
    }

    private void refreshFromModel(int eid) {
        internalRefresh = true;
        try {
            shapeBinder.setEntityId(eid);
            sensorBinder.setEntityId(eid);
            rebindFields(eid);

            FixtureDefData active = activeFixture(eid);
            boolean hasActive = active != null;

            detailsBlock.show(hasActive);

            lastSelectedFixtureId = resolveSelectedFixtureIdForPanel(eid);
            lastFixtureCount = countFixtures(eid);
            updateActionButtons(hasActive);

            if (!hasActive) {
                boxSizeBlock.show(false);
                circleSizeBlock.show(false);
                offsetsBlock.show(false);
                autoSizeBtn.setVisible(false);
                autoSizeBtn.setDisabled(true);
                return;
            }

            refreshShapeUi(eid);
        } finally {
            internalRefresh = false;
        }
        invalidateHierarchy();
    }

    private void rebindFields(int eid) {
        widthWUField.setEntityId(eid);
        heightWUField.setEntityId(eid);
        diameterWUField.setEntityId(eid);
        offsetXWUField.setEntityId(eid);
        offsetYWUField.setEntityId(eid);

        densityField.setEntityId(eid);
        frictionField.setEntityId(eid);
        restitutionField.setEntityId(eid);

        categoryBitsField.setEntityId(eid);
        maskBitsField.setEntityId(eid);
        groupIndexField.setEntityId(eid);
    }

    private void refreshShapeUi(int eid) {
        internalRefresh = true;
        try {
            FixtureDefData f = activeFixture(eid);
            if (f == null) {
                boxSizeBlock.show(false);
                circleSizeBlock.show(false);
                offsetsBlock.show(false);
                autoSizeBtn.setVisible(false);
                autoSizeBtn.setDisabled(true);
                return;
            }

            if (isBox(f)) {
                boxSizeBlock.show(true);
                circleSizeBlock.show(false);
                offsetsBlock.show(true);

                autoSizeBtn.setVisible(true);
                autoSizeBtn.setDisabled(false);
            } else if (isCircle(f)) {
                boxSizeBlock.show(false);
                circleSizeBlock.show(true);
                offsetsBlock.show(true);

                autoSizeBtn.setVisible(true);
                autoSizeBtn.setDisabled(false);
            } else {
                boxSizeBlock.show(false);
                circleSizeBlock.show(false);
                offsetsBlock.show(true);

                // Polygon edition is now canvas-only.
                autoSizeBtn.setVisible(false);
                autoSizeBtn.setDisabled(true);
            }

            widthWUField.refreshFromModel();
            heightWUField.refreshFromModel();
            diameterWUField.refreshFromModel();
            offsetXWUField.refreshFromModel();
            offsetYWUField.refreshFromModel();
        } finally {
            internalRefresh = false;
        }
        invalidateHierarchy();
    }

    private void updateActionButtons(boolean hasActive) {
        duplicateFixtureBtn.setDisabled(!canDuplicateActiveFixture(entityId));
        deleteFixtureBtn.setDisabled(!canDeleteActiveFixture(entityId));
        duplicateFixtureBtn.setVisible(hasActive);
        deleteFixtureBtn.setVisible(hasActive);
    }

    private boolean canDuplicateActiveFixture(int eid) {
        return hasActiveFixture(eid) && !isActiveGeneratedFixture(eid);
    }

    private boolean canDeleteActiveFixture(int eid) {
        return hasActiveFixture(eid);
    }

    private int countFixtures(int eid) {
        PhysicsFixturesComponent fixtures = ctx.mPhysFixtures.getSafe(eid, null);
        return (fixtures != null) ? fixtures.fixtures.size : 0;
    }

    private boolean hasValidPolygon(FixtureDefData f) {
        return f != null
                && f.polyVerts != null
                && f.polyCount >= 3
                && f.polyVerts.length >= f.polyCount * 2;
    }

    private void seedDefaultPolygon(FixtureDefData f, int previousShapeType) {
        if (f == null || hasValidPolygon(f)) return;

        float hx;
        float hy;

        if (previousShapeType == FixtureDefData.SHAPE_CIRCLE) {
            float r = Math.max(MIN_SHAPE_HALF_M, f.radius);
            hx = r;
            hy = r;
        } else {
            hx = Math.max(MIN_SHAPE_HALF_M, f.halfW);
            hy = Math.max(MIN_SHAPE_HALF_M, f.halfH);
        }

        f.polyCount = 4;
        f.polyVerts = new float[]{
                -hx, -hy,
                hx, -hy,
                hx, hy,
                -hx, hy
        };
    }

    private boolean isActiveGeneratedFixture(int eid) {
        long fixtureId = ctx.physicsSelectionService.getSelectedFixtureId();
        return fixtureId > 0L && polygonAuthoringService.isGeneratedFixture(eid, fixtureId);
    }

    private void executeCommand(Command command) {
        if (command instanceof HistoryManager.SupportsNoop supportsNoop && supportsNoop.isNoop()) {
            return;
        }

        ctx.history.execute(command);
    }

    private void applyFixtureEdit(int eid,
                                  int dirtyMask,
                                  boolean publishStructureChanged,
                                  Consumer<FixtureDefData> edit) {
        FixtureDefData current = activeFixture(eid);
        if (current == null || edit == null) return;

        FixtureDefData afterData = current.copy();
        edit.accept(afterData);

        EditFixtureCommand command = new EditFixtureCommand(
                ctx.world,
                ctx.history.historyIds(),
                ctx.physicsSelectionService,
                eid,
                current.fixtureId,
                EditFixtureCommand.Snapshot.capture(current),
                EditFixtureCommand.Snapshot.capture(afterData),
                dirtyMask,
                publishStructureChanged
        );
        executeCommand(command);
    }

    private void markPhysicsDirty(int eid, int mask) {
        if (eid < 0) return;
        if (ctx.dirtyTracker != null) ctx.dirtyTracker.physics(eid, mask);
    }

    private boolean hasPhysics(int eid) {
        return eid >= 0
                && ctx.mPhysBody.has(eid)
                && ctx.mPhysFixtures.has(eid)
                && ctx.mPhysFixtures.get(eid).hasFixtures();
    }

    private boolean hasActiveFixture(int eid) {
        return activeFixture(eid) != null;
    }

    private long resolveSelectedFixtureIdForPanel(int eid) {
        FixtureDefData active = activeFixture(eid);
        return active != null ? active.fixtureId : PhysicsSelectionService.NO_FIXTURE;
    }

    private FixtureDefData activeFixture(int eid) {
        if (!hasPhysics(eid)) return null;

        long fixtureId = ctx.physicsSelectionService.getSelectedFixtureId();
        if (fixtureId <= 0L) return null;

        PhysicsFixturesComponent fixtures = ctx.mPhysFixtures.getSafe(eid, null);
        if (fixtures == null || !fixtures.hasFixtures()) return null;

        for (int i = 0, n = fixtures.fixtures.size; i < n; i++) {
            FixtureDefData f = fixtures.fixtures.get(i);
            if (f == null) continue;
            FixtureIdSequence.i().ensure(f);
            if (f.fixtureId == fixtureId) return f;
        }
        return null;
    }

    private static boolean isBox(FixtureDefData f) {
        return f != null && isBoxShape(f.shapeType);
    }

    private static boolean isCircle(FixtureDefData f) {
        return f != null && isCircleShape(f.shapeType);
    }

    private static boolean isBoxShape(int shapeType) {
        return shapeType == FixtureDefData.SHAPE_BOX;
    }

    private static boolean isCircleShape(int shapeType) {
        return shapeType == FixtureDefData.SHAPE_CIRCLE;
    }

    private float readWidthWU(int eid) {
        FixtureDefData f = activeFixture(eid);
        if (!isBox(f)) return 0f;
        float ppm = resolvePixelsPerMeter();
        return mToWu(2f * Math.max(MIN_SHAPE_HALF_M, f.halfW), ppm);
    }

    private float readHeightWU(int eid) {
        FixtureDefData f = activeFixture(eid);
        if (!isBox(f)) return 0f;
        float ppm = resolvePixelsPerMeter();
        return mToWu(2f * Math.max(MIN_SHAPE_HALF_M, f.halfH), ppm);
    }

    private float readDiameterWU(int eid) {
        FixtureDefData f = activeFixture(eid);
        if (!isCircle(f)) return 0f;
        float ppm = resolvePixelsPerMeter();
        return mToWu(2f * Math.max(MIN_SHAPE_HALF_M, f.radius), ppm);
    }

    private float readOffsetXWU(int eid) {
        FixtureDefData f = activeFixture(eid);
        if (f == null) return 0f;
        return mToWu(f.offsetX, resolvePixelsPerMeter());
    }

    private float readOffsetYWU(int eid) {
        FixtureDefData f = activeFixture(eid);
        if (f == null) return 0f;
        return mToWu(f.offsetY, resolvePixelsPerMeter());
    }

    private void autoSizeFromSprite(int eid) {
        FixtureDefData f = activeFixture(eid);
        if (f == null) return;
        if (!ctx.mTransform.has(eid) || !ctx.mDimensions.has(eid)) return;

        TransformComponent t = ctx.mTransform.get(eid);
        DimensionsComponent d = ctx.mDimensions.get(eid);
        if (t == null || d == null) return;

        float ppm = resolvePixelsPerMeter();
        float worldWwu = Math.abs(d.width * t.scaleX);
        float worldHwu = Math.abs(d.height * t.scaleY);
        float wM = clampMin(wuToM(worldWwu, ppm), 2f * MIN_SHAPE_HALF_M);
        float hM = clampMin(wuToM(worldHwu, ppm), 2f * MIN_SHAPE_HALF_M);

        applyFixtureEdit(eid, PhysicsDirtyBits.FIXTURE, false, snapshot -> {
            if (isBoxShape(snapshot.shapeType)) {
                snapshot.halfW = clampMin(wM * 0.5f, MIN_SHAPE_HALF_M);
                snapshot.halfH = clampMin(hM * 0.5f, MIN_SHAPE_HALF_M);
            } else if (isCircleShape(snapshot.shapeType)) {
                float dM = Math.max(2f * MIN_SHAPE_HALF_M, Math.min(wM, hM));
                snapshot.radius = clampMin(dM * 0.5f, MIN_SHAPE_HALF_M);
            }
        });
    }

    private IntField newHexIntField(java.util.function.IntFunction<Integer> reader) {
        InputValidator validator = input -> parseIntDecOrHex(input) != null;

        com.kotcrab.vis.ui.widget.VisTextField.TextFieldFilter filter = (tf, c) -> {
            if (Character.isDigit(c)) return true;
            if (c == '-' && tf.getCursorPosition() == 0 && !tf.getText().startsWith("-")) return true;
            if (c == 'x' || c == 'X') return true;
            return (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
        };

        return new IntField(ctx.world, reader, this::hasActiveFixture, validator, filter) {
            @Override
            protected Integer parse(String text) {
                return parseIntDecOrHex(text);
            }
        };
    }

    private static Integer parseIntDecOrHex(String s) {
        try {
            if (s == null) return null;
            String t = s.trim();
            if (t.isEmpty() || "-".equals(t)) return null;

            boolean neg = t.startsWith("-");
            String u = neg ? t.substring(1) : t;

            int value;
            if (u.startsWith("0x") || u.startsWith("0X")) {
                String hex = u.substring(2);
                if (hex.isEmpty()) return null;
                value = Integer.parseUnsignedInt(hex, 16);
            } else {
                value = Integer.parseInt(u);
            }
            return neg ? -value : value;
        } catch (Exception ignore) {
            return null;
        }
    }

    private float resolvePixelsPerMeter() {
        ProjectConfig cfg = ProjectConfig.getInstance();
        if (cfg == null) return 100f;
        SceneMeta meta = cfg.getCurrentSceneMeta();
        if (meta == null) return 100f;
        float ppm = meta.pixelsPerMeter;
        return (ppm > 0f) ? ppm : 100f;
    }

    private static float wuToM(float wu, float ppm) {
        return wu / ((ppm > 0f) ? ppm : 100f);
    }

    private static float mToWu(float m, float ppm) {
        return m * ((ppm > 0f) ? ppm : 100f);
    }

    private static float clampMin(float v, float min) {
        return Math.max(v, min);
    }

    private static int clamp(int v, int max) {
        return Math.max(0, Math.min(max, v));
    }

    private static short toShortClampedUnsigned(int v) {
        int clamped = Math.max(0, Math.min(0xFFFF, v));
        return (short) (clamped & 0xFFFF);
    }

    private static short toShortClampedSigned(int v) {
        int clamped = Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, v));
        return (short) clamped;
    }
}
