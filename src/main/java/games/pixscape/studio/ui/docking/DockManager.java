package games.pixscape.studio.ui.docking;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Window;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3WindowConfiguration;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Stack;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import com.kotcrab.vis.ui.widget.VisTable;
import games.pixscape.studio.PixscapeStudioApplication;
import games.pixscape.studio.ui.GenericWindow;
import games.pixscape.studio.ui.GenericWindowListener;
import games.pixscape.studio.ui.main.RulerActor;
import games.pixscape.studio.ui.main.StudioApplicationAdapter;
import games.pixscape.studio.ui.widget.CanvasModeIndicator;
import games.pixscape.studio.ui.widget.DockSplitPane;

public final class DockManager {

    // ------------------------------------------------------------------------
    // Layout containers
    // ------------------------------------------------------------------------

    private final VisTable root = new VisTable(true);

    // zones
    private final VisTable left = new VisTable();
    private final Stack centerStack = new Stack();
    private final Actor centerHost;
    private final VisTable rulersAndCanvasPlaceholder = new VisTable(true);
    private final VisTable modeOverlay = new VisTable();
    private final CanvasModeIndicator modeIndicator;
    private final VisTable bottom = new VisTable();

    private final VisTable rightTop = new VisTable();
    private final VisTable rightBottom = new VisTable();

    // splits
    private final DockSplitPane rightSplit;       // right top / right bottom
    private final DockSplitPane centerRightSplit; // center / right
    private final DockSplitPane topSplit;         // left / center and right
    private final DockSplitPane rootSplit;        // top / bottom
    private int layoutMask = -1;

    private final RulerActor rulerLeft;
    private final RulerActor rulerTop;
    private boolean rulersVisible = true;
    private float modeIndicatorTopPadding;
    // misc
    private final Array<DockablePanel> panels = new Array<>();
    private final Array<DockListener> listeners = new Array<>();
    private final Array<Lwjgl3Window> floatingWindows = new Array<>();

    private final ObjectMap<DockablePanel, Lwjgl3Window> panelToWindow = new ObjectMap<>();

    private final StudioApplicationAdapter app;

    // ------------------------------------------------------------------------
    // Ctor
    // ------------------------------------------------------------------------

    public DockManager(StudioApplicationAdapter app, RulerActor rulerLeft, RulerActor rulerTop) {
        this.app = app;
        this.rulerLeft = rulerLeft;
        this.rulerTop = rulerTop;
        this.modeIndicator = new CanvasModeIndicator(app.getCanvas().getStudioEditingModeService());
        root.setTouchable(Touchable.childrenOnly);

        centerStack.setTouchable(Touchable.childrenOnly);
        rulersAndCanvasPlaceholder.setTouchable(Touchable.childrenOnly);
        modeOverlay.setTouchable(Touchable.disabled);
        centerStack.add(rulersAndCanvasPlaceholder);
        centerStack.add(modeOverlay);
        rebuildCenterOverlay();
        centerHost = app.createDockCenterHost(centerStack);

        rightSplit = new DockSplitPane(null, null, true);
        centerRightSplit = new DockSplitPane(null, null, false);
        topSplit = new DockSplitPane(null, null, false);
        rootSplit = new DockSplitPane(null, null, true);
        topSplit.setSplitAmount(0.24f);
        centerRightSplit.setSplitAmount(0.625f);
        rightSplit.setSplitAmount(0.7f);
        rootSplit.setSplitAmount(0.80f);

        rebuildLayout();
    }

    // ------------------------------------------------------------------------
    // Listener
    // ------------------------------------------------------------------------

    public interface DockListener {
        void onPanelVisibilityChanged(DockablePanel panel, boolean visible);
    }

    public void addListener(DockListener l) {
        listeners.add(l);
    }

    private void notifyVisibilityChanged(DockablePanel panel, boolean visible) {
        for (DockListener l : listeners) {
            l.onPanelVisibilityChanged(panel, visible);
        }
    }

    // ------------------------------------------------------------------------
    // API publique
    // ------------------------------------------------------------------------

    public VisTable getRoot() {
        return root;
    }

    public void register(DockablePanel panel, DockSlot slot, boolean docked) {
        panels.add(panel);
        panel.attachDockManager(this, slot);

        if (panel.getDockMode() == DockablePanel.DockMode.WINDOW_ONLY) {
            panel.setVisible(false);

            if (panel.isOpenOnRegister()) {
                undock(panel);   // opens the window
            }
            return;
        }

        // comportement dockable classique
        if (docked) {
            dock(panel, slot);
        } else {
            panel.setVisible(false);
        }
    }

    public Array<DockablePanel> getPanels() {
        return panels;
    }

    // ------------------------------------------------------------------------
    // Docking
    // ------------------------------------------------------------------------

    public void show(DockablePanel panel) {
        if (panel.getDockMode() == DockablePanel.DockMode.WINDOW_ONLY) {
            undock(panel);
            return;
        }

        dock(panel, panel.getDefaultSlot());
    }

    public void hide(DockablePanel panel) {
        cancelInput(panel);

        if (panel.getDockMode() == DockablePanel.DockMode.WINDOW_ONLY) {
            Lwjgl3Window w = panelToWindow.get(panel);
            if (w != null) {
                GenericWindow genericWindow = genericWindow(w);
                if (genericWindow != null) {
                    genericWindow.detachPanel();
                }
                w.setVisible(false);
            }
            panel.remove();
            panel.setFillParent(false);
            panel.setVisible(false);
            notifyVisibilityChanged(panel, false);
            return;
        }

        // comportement dockable classique
        Lwjgl3Window floatingWindow = panelToWindow.get(panel);
        if (containsPanel(floatingWindow)) {
            floatingWindow.setVisible(false);
        }

        panel.remove();
        panel.setFillParent(false);
        panel.setVisible(false);
        rebuildLayout();
        notifyVisibilityChanged(panel, false);
    }

    public void dock(DockablePanel panel, DockSlot slot) {
        if (panel.getDockMode() == DockablePanel.DockMode.WINDOW_ONLY) {
            return;
        }
        cancelInput(panel);
        Lwjgl3Window floatingWindow = panelToWindow.get(panel);
        if (containsPanel(floatingWindow)) {
            floatingWindow.setVisible(false);
        }

        panel.remove();
        panel.setFillParent(false);
        panel.setVisible(true);
        panel.setHeaderVisible(true);

        VisTable target = switch (slot) {
            case LEFT -> left;
            case RIGHT_TOP -> rightTop;
            case RIGHT_BOTTOM -> rightBottom;
            case BOTTOM -> bottom;
        };
        cancelInput(target);
        target.clearChildren();
        target.add(panel).grow();
        rebuildLayout();
        notifyVisibilityChanged(panel, true);
    }

    public void dockToDefault(DockablePanel panel) {
        dock(panel, panel.getDefaultSlot());
    }

    // ------------------------------------------------------------------------
    // Undock (floating window)
    // ------------------------------------------------------------------------

    public void undock(DockablePanel panel) {
        cancelInput(panel);
        Lwjgl3Window existingWindow = panelToWindow.get(panel);
        if (existingWindow != null) {
            panel.remove();
            rebuildLayout();

            panel.setVisible(true);
            panel.setHeaderVisible(false);
            GenericWindow existingGenericWindow = genericWindow(existingWindow);
            if (existingGenericWindow != null) {
                existingGenericWindow.attachPanel();
            }
            existingWindow.setVisible(true);
            existingWindow.restoreWindow();
            existingWindow.focusWindow();
            notifyVisibilityChanged(panel, true);
            return;
        }

        panel.remove();
        rebuildLayout();

        panel.setVisible(true);
        panel.setHeaderVisible(false);

        Lwjgl3Application lwjgl3App = (Lwjgl3Application) Gdx.app;
        Lwjgl3WindowConfiguration cfg = new Lwjgl3WindowConfiguration();
        cfg.setTitle(panel.getTitleText());

        // 🔑 CUSTOM SIZE
        cfg.setWindowedMode(
                panel.getPreferredWindowWidth(),
                panel.getPreferredWindowHeight()
        );

        cfg.setWindowListener(new GenericWindowListener(panel, app));
        cfg.setWindowIcon(PixscapeStudioApplication.PIXSCAPE_ICON);
        cfg.useVsync(true);

        GenericWindow window = new GenericWindow(panel);
        Lwjgl3Window lw = lwjgl3App.newWindow(window, cfg);
        floatingWindows.add(lw);
        panelToWindow.put(panel, lw);
        notifyVisibilityChanged(panel, true);
    }

    public void onFloatingWindowClosed(Lwjgl3Window window) {
        onFloatingWindowClosed(window, true);
    }

    public void onFloatingWindowClosed(Lwjgl3Window window, boolean notifyHidden) {
        floatingWindows.removeValue(window, true);

        DockablePanel toRemove = null;
        for (ObjectMap.Entry<DockablePanel, Lwjgl3Window> e : panelToWindow.entries()) {
            if (e.value == window) {
                toRemove = e.key;
                break;
            }
        }

        if (toRemove != null) {
            panelToWindow.remove(toRemove);

            if (notifyHidden) {
                notifyVisibilityChanged(toRemove, false);
            }
        }
    }

    public void closeAllFloatingWindows() {
        for (Lwjgl3Window w : floatingWindows) {
            if (w == null) continue;
            if (w.getWindowListener() instanceof GenericWindowListener listener) {
                listener.forceClose();
            }
            w.closeWindow();
        }
        floatingWindows.clear();
        panelToWindow.clear();
    }

    public void dispose() {
        closeAllFloatingWindows();
        modeIndicator.dispose();
    }

    private static GenericWindow genericWindow(Lwjgl3Window window) {
        if (window == null) return null;
        if (window.getListener() instanceof GenericWindow genericWindow) {
            return genericWindow;
        }
        return null;
    }

    private static boolean containsPanel(Lwjgl3Window window) {
        GenericWindow genericWindow = genericWindow(window);
        return genericWindow != null && genericWindow.containsPanel();
    }

    // ------------------------------------------------------------------------
    // Layout rebuild
    // ------------------------------------------------------------------------

    private void rebuildLayout() {
        int mask = (left.hasChildren() ? 1 : 0) | (rightTop.hasChildren() ? 2 : 0)
                | (rightBottom.hasChildren() ? 4 : 0) | (bottom.hasChildren() ? 8 : 0);
        if (mask == layoutMask) {
            root.invalidateHierarchy();
            return;
        }
        // Cancel before detaching: Stage.unfocus(parent) does not cancel descendant listeners.
        cancelInput(root);
        root.clearChildren();
        // Clear through VisUI's setters, not clearChildren(): its private widget references
        // must be released before any branch is attached elsewhere. Ratios remain untouched.
        rootSplit.setWidgets(null, null);
        topSplit.setWidgets(null, null);
        centerRightSplit.setWidgets(null, null);
        rightSplit.setWidgets(null, null);

        Actor right = compose(rightSplit, occupied(rightTop), occupied(rightBottom));
        Actor centerRight = compose(centerRightSplit, centerHost, right);
        Actor top = compose(topSplit, occupied(left), centerRight);
        Actor branch = compose(rootSplit, top, occupied(bottom));
        if (branch != null) root.add(branch).grow().minSize(0, 0);
        layoutMask = mask;
        root.invalidateHierarchy();
    }

    private static Actor occupied(VisTable slot) {
        return slot.hasChildren() ? slot : null;
    }

    private static Actor compose(DockSplitPane split, Actor first, Actor second) {
        if (first == null) return second;
        if (second == null) return first;
        split.setWidgets(first, second);
        return split;
    }

    private static void cancelInput(Actor actor) {
        if (actor.getStage() == null) return;
        actor.getStage().cancelTouchFocus(actor);
        if (actor instanceof Group group) {
            for (Actor child : group.getChildren()) cancelInput(child);
        }
    }

    /**
     * Ends separator gestures on Escape or native-window focus loss.
     */
    public boolean cancelResize() {
        // Non-short-circuit OR cancels every split, including nested simultaneous gestures.
        return rootSplit.cancelResize() | topSplit.cancelResize()
                | centerRightSplit.cancelResize() | rightSplit.cancelResize();
    }

    public void setRulersVisible(boolean visible) {
        rulersVisible = visible;
        rulerLeft.setVisible(visible);
        rulerTop.setVisible(visible);
        rebuildCenterOverlay();
        root.invalidateHierarchy();
    }

    private void rebuildCenterOverlay() {
        rulersAndCanvasPlaceholder.clearChildren();
        if (rulersVisible) {
            rulersAndCanvasPlaceholder.add(rulerTop)
                    .height(RulerActor.TOP_HEIGHT).growX().colspan(2).row();
            rulersAndCanvasPlaceholder.add(rulerLeft)
                    .width(RulerActor.LEFT_WIDTH).left().growY();
            rulersAndCanvasPlaceholder.add().expand().grow();
        } else {
            rulersAndCanvasPlaceholder.add().expand().grow();
        }

        updateModeIndicatorTopPadding();
        modeOverlay.clearChildren();
        modeOverlay.top();
        if (rulersVisible) {
            modeOverlay.add().width(RulerActor.LEFT_WIDTH);
        }
        modeOverlay.add(modeIndicator)
                .expandX().top().padTop(modeIndicatorTopPadding);
    }

    private void updateModeIndicatorTopPadding() {
        modeIndicatorTopPadding = (rulersVisible ? RulerActor.TOP_HEIGHT : 0f) + 5f;
    }

    CanvasModeIndicator getModeIndicator() {
        return modeIndicator;
    }

    public Stack getCenterStack() {
        return centerStack;
    }

    float getModeIndicatorTopPadding() {
        return modeIndicatorTopPadding;
    }

    public boolean isRulersVisible() {
        return rulersVisible;
    }
}
