package games.pixscape.studio.ui.tree;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup;
import com.kotcrab.vis.ui.widget.VisScrollPane;
import games.pixscape.studio.event.GetScrollListener;
import games.pixscape.studio.event.LoseScroolListener;

/** Small stateless collection of the scrolling conventions shared by Studio trees. */
public final class StudioTreeUiSupport {
    private StudioTreeUiSupport() {
    }

    public static VisScrollPane createScrollPane(Actor tree) {
        VisScrollPane scroller = new VisScrollPane(tree);
        scroller.setScrollingDisabled(false, false);
        scroller.setFadeScrollBars(false);
        scroller.setSmoothScrolling(true);
        scroller.addListener(new GetScrollListener(scroller));
        scroller.addListener(new LoseScroolListener());
        return scroller;
    }

    public static void scrollToNode(VisScrollPane scroller, WidgetGroup tree, Actor row) {
        scrollToNode(scroller, tree, row, false);
    }

    public static void scrollToNode(VisScrollPane scroller, WidgetGroup tree, Actor row,
                                    boolean revealHorizontalPosition) {
        if (scroller == null || tree == null || row == null) return;
        tree.validate();
        scroller.layout();
        scroller.scrollTo(revealHorizontalPosition ? row.getX() : 0f,
                row.getY(), row.getWidth(), row.getHeight(), false, true);
        scroller.updateVisualScroll();
    }
}
