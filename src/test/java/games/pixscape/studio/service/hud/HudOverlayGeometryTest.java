package games.pixscape.studio.service.hud;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.ui.Cell;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import org.junit.Test;
import org.junit.BeforeClass;

import static org.junit.Assert.assertEquals;

public class HudOverlayGeometryTest {
    @BeforeClass public static void bootGdx() {
        if (Gdx.app == null) new HeadlessApplication(new ApplicationAdapter() {},
                new HeadlessApplicationConfiguration());
    }


    @Test public void simpleOneByOneCellUsesFullAllocation() {
        Fixture f = fixture(100f, 80f);
        Cell<Actor> cell = f.table.add(actor()).size(20f, 10f);
        assertRect(cell(f, cell), 40f, 35f, 20f, 10f);
    }

    @Test public void tablePaddingOffsetsCenteredContent() {
        Fixture f = fixture(100f, 80f);
        f.table.pad(5f, 7f, 9f, 3f);
        Cell<Actor> cell = f.table.add(actor()).size(20f, 10f);
        assertRect(cell(f, cell), 42f, 37f, 20f, 10f);
    }

    @Test public void leftAlignmentUsesLeftPadding() {
        Fixture f = fixture(100f, 80f);
        f.table.padLeft(7f).left();
        assertRect(cell(f, f.table.add(actor()).size(20f, 10f)), 7f, 35f, 20f, 10f);
    }

    @Test public void centeredAlignmentCentersContent() {
        Fixture f = fixture(101f, 81f);
        assertRect(cell(f, f.table.add(actor()).size(20f, 10f)), 40.5f, 35.5f, 20f, 10f);
    }

    @Test public void rightAlignmentUsesRightPadding() {
        Fixture f = fixture(100f, 80f);
        f.table.padRight(6f).right();
        assertRect(cell(f, f.table.add(actor()).size(20f, 10f)), 74f, 35f, 20f, 10f);
    }

    @Test public void topAndBottomAlignmentUseVerticalPadding() {
        Fixture top = fixture(100f, 80f);
        top.table.padTop(4f).top();
        assertRect(cell(top, top.table.add(actor()).size(20f, 10f)), 40f, 66f, 20f, 10f);

        Fixture bottom = fixture(100f, 80f);
        bottom.table.padBottom(6f).bottom();
        assertRect(cell(bottom, bottom.table.add(actor()).size(20f, 10f)), 40f, 6f, 20f, 10f);
    }

    @Test public void unequalRowsRespectTopBasedRowIndexes() {
        Fixture f = fixture(100f, 100f);
        f.table.left().bottom();
        Cell<Actor> top = f.table.add(actor()).size(20f, 10f);
        f.table.row();
        Cell<Actor> bottom = f.table.add(actor()).size(20f, 30f);
        assertRect(cell(f, top), 0f, 30f, 20f, 10f);
        assertRect(cell(f, bottom), 0f, 0f, 20f, 30f);
    }

    @Test public void unequalColumnsAccumulateTheirCalculatedWidths() {
        Fixture f = fixture(100f, 80f);
        f.table.left().bottom();
        Cell<Actor> first = f.table.add(actor()).size(20f, 10f);
        Cell<Actor> second = f.table.add(actor()).size(40f, 10f);
        assertRect(cell(f, first), 0f, 0f, 20f, 10f);
        assertRect(cell(f, second), 20f, 0f, 40f, 10f);
    }

    @Test public void colspanSumsAllCoveredColumnWidths() {
        Fixture f = fixture(100f, 80f);
        f.table.left().bottom();
        Cell<Actor> spanning = f.table.add(actor()).colspan(2).height(10f);
        f.table.row();
        f.table.add(actor()).size(20f, 12f);
        f.table.add(actor()).size(30f, 12f);
        assertRect(cell(f, spanning), 0f, 12f, 50f, 10f);
    }

    @Test public void expandDistributesAvailableWidthIntoCalculatedColumns() {
        Fixture f = fixture(100f, 40f);
        f.table.left().bottom();
        Cell<Actor> first = f.table.add(actor()).width(10f).height(10f).expandX();
        Cell<Actor> second = f.table.add(actor()).width(10f).height(10f).expandX();
        assertRect(cell(f, first), 0f, 0f, 50f, 10f);
        assertRect(cell(f, second), 50f, 0f, 50f, 10f);
    }

    @Test public void fractionalColumnWidthsAreNotTruncated() {
        Fixture f = fixture(100f, 40f);
        f.table.setRound(false);
        f.table.left().bottom();
        f.table.add(actor()).size(10.25f, 7.5f);
        Cell<Actor> second = f.table.add(actor()).size(20.5f, 7.5f);
        assertRect(cell(f, second), 10.25f, 0f, 20.5f, 7.5f);
    }

    @Test public void nestedCoordinateSpacesConvertIntoOverlayLocalSpace() {
        Fixture f = fixture(80f, 50f);
        f.container.setPosition(50f, 60f);
        f.table.setPosition(10f, 20f);
        f.table.left().bottom();
        f.overlay.setPosition(5f, 7f);
        Cell<Actor> cell = f.table.add(actor()).size(20f, 10f);
        assertRect(cell(f, cell), 55f, 73f, 20f, 10f);
    }

    @Test public void scaledParentTransformsAllActorCorners() {
        Fixture f = fixture(80f, 50f);
        Actor actor = actor();
        actor.setBounds(4f, 5f, 10f, 20f);
        f.container.setPosition(3f, 7f);
        f.container.setScale(2f, 3f);
        f.container.addActor(actor);
        assertRect(HudOverlayGeometry.actorBoundsInOverlay(actor, f.overlay, new Rectangle()),
                11f, 22f, 20f, 60f);
    }

    @Test public void rotatedActorUsesFourCornerAabb() {
        Fixture f = fixture(80f, 50f);
        Actor actor = actor();
        actor.setBounds(10f, 20f, 10f, 20f);
        actor.setOrigin(0f, 0f);
        actor.setRotation(90f);
        f.container.addActor(actor);
        assertRect(HudOverlayGeometry.actorBoundsInOverlay(actor, f.overlay, new Rectangle()),
                -10f, 20f, 20f, 10f);
    }

    private Rectangle cell(Fixture fixture, Cell<?> cell) {
        return HudOverlayGeometry.cellBoundsInOverlay(cell, fixture.overlay, new Rectangle());
    }

    private Fixture fixture(float width, float height) {
        Group root = new Group();
        Group container = new Group();
        Table table = new Table();
        table.setSize(width, height);
        container.addActor(table);
        Group overlay = new Group();
        overlay.setSize(500f, 500f);
        root.addActor(container);
        root.addActor(overlay);
        return new Fixture(container, table, overlay);
    }

    private static Actor actor() { return new Actor(); }

    private static void assertRect(Rectangle actual, float x, float y, float width, float height) {
        assertEquals(x, actual.x, 0.001f);
        assertEquals(y, actual.y, 0.001f);
        assertEquals(width, actual.width, 0.001f);
        assertEquals(height, actual.height, 0.001f);
    }

    private record Fixture(Group container, Table table, Group overlay) {}
}
