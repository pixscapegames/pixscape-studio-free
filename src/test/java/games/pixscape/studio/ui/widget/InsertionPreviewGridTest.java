package games.pixscape.studio.ui.widget;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.utils.Array;
import org.junit.Test;

import static org.junit.Assert.*;

public class InsertionPreviewGridTest {
    @Test
    public void internalMoveRemovesSourceSlotsAndUsesRemainingSequenceGaps() {
        var grid = new InsertionPreviewGrid(84, 94);
        var cells = cells(5);
        grid.setItems(cells, null);
        layout(grid, 168);
        grid.beginMove(new int[]{1, 3});
        var ghosts = cells(2);
        grid.showInsertion(3, ghosts);
        layout(grid, 168);
        assertFalse(cells.get(1).isVisible());
        assertFalse(cells.get(3).isVisible());
        assertEquals(cells.get(0).getY(), cells.get(2).getY(), 0);
        assertEquals(cells.get(4).getY(), ghosts.get(0).getY(), 0);
        assertEquals(3, grid.insertionAt(ghosts.get(1).getX() + 20, ghosts.get(1).getY() + 20));
        layout(grid, 420);
        assertSame(grid, cells.get(1).getParent());
        assertFalse(cells.get(1).isVisible());
        assertEquals(3, grid.insertionAt(ghosts.get(0).getX() + 20, ghosts.get(0).getY() + 20));
        grid.clearInsertion();
        grid.endMove();
        layout(grid, 168);
        for (Actor cell : cells) assertTrue(cell.isVisible());
        assertEquals(5, grid.getChildren().size);
    }

    @Test
    public void wrappingResizeKeepsActorsAndSourceOrder() {
        var grid = new InsertionPreviewGrid(84, 94);
        Array<Actor> cells = cells(7);
        grid.setItems(cells, null);
        layout(grid, 420);
        assertEquals(5, grid.columns());
        assertEquals(188, grid.getHeight(), 0);
        assertEquals(cells.get(0).getY(), cells.get(4).getY(), 0);
        assertTrue(cells.get(5).getY() < cells.get(4).getY());
        layout(grid, 210);
        assertEquals(2, grid.columns());
        assertEquals(376, grid.getHeight(), 0);
        for (int i = 0; i < cells.size; i++) {
            assertSame(grid, cells.get(i).getParent());
            assertSame(cells.get(i), grid.getChildren().get(i));
            assertEquals(2 + (i % 2) * 84, cells.get(i).getX(), 0);
        }
    }

    @Test
    public void multipleGhostsStayStableAtWrapAndIncompleteLastRow() {
        for (int columns : new int[]{2, 3, 5})
            for (int count : new int[]{1, 2, 4}) {
                var grid = new InsertionPreviewGrid(84, 94);
                Array<Actor> cells = cells(7);
                grid.setItems(cells, null);
                layout(grid, columns * 84);
                for (int insertion = 0; insertion <= cells.size; insertion++) {
                    grid.clearInsertion();
                    layout(grid, columns * 84);
                    float x = (insertion % columns) * 84 + 5;
                    float fromTop = (insertion / columns) * 94 + 20;
                    assertEquals(insertion, grid.insertionAt(x, grid.getHeight() - fromTop));
                    var ghosts = cells(count);
                    grid.showInsertion(insertion, ghosts);
                    layout(grid, columns * 84);
                    for (int tick = 0; tick < 10; tick++) {
                        int destination = grid.insertionAt(x, grid.getHeight() - fromTop);
                        assertEquals(insertion, destination);
                        grid.showInsertion(destination, ghosts);
                        layout(grid, columns * 84);
                    }
                    for (int i = 0; i < count; i++) {
                        assertEquals(insertion, grid.insertionAt(ghosts.get(i).getX() + 10, ghosts.get(i).getY() + 30));
                        assertEquals(.4f, ghosts.get(i).getColor().a, 0);
                    }
                    assertEquals(7, grid.insertionAt(columns * 84 - 2, 1));
                    grid.clearInsertion();
                    layout(grid, columns * 84);
                    for (Actor ghost : ghosts) assertNull(ghost.getParent());
                    assertEquals(7, grid.getChildren().size);
                }
            }
    }

    @Test
    public void emptyDropSpaceIsCompactAndReplacedByGhosts() {
        var grid = new InsertionPreviewGrid(84, 94);
        var message = new Actor();
        grid.setItems(new Array<>(), message);
        layout(grid, 260);
        assertEquals(40, grid.getHeight(), 0);
        assertTrue(message.isVisible());
        grid.showInsertion(0, cells(4));
        layout(grid, 260);
        assertEquals(188, grid.getHeight(), 0);
        assertFalse(message.isVisible());
        grid.clearInsertion();
        layout(grid, 260);
        assertTrue(message.isVisible());
        grid.setItems(new Array<>(), null);
        layout(grid, 260);
        assertEquals(0, grid.getHeight(), 0);
    }

    private static Array<Actor> cells(int count) {
        Array<Actor> result = new Array<>();
        for (int i = 0; i < count; i++) result.add(new Actor());
        return result;
    }

    private static void layout(InsertionPreviewGrid grid, float width) {
        grid.setWidth(width);
        grid.setHeight(grid.getPrefHeight());
        grid.validate();
    }
}
