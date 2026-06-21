package games.pixscape.studio.ui.docking;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Window;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3WindowConfiguration;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import com.kotcrab.vis.ui.widget.VisSplitPane;
import com.kotcrab.vis.ui.widget.VisTable;
import games.pixscape.studio.PixscapeStudioApplication;
import games.pixscape.studio.ui.GenericWindow;
import games.pixscape.studio.ui.GenericWindowListener;
import games.pixscape.studio.ui.main.RulerActor;
import games.pixscape.studio.ui.main.StudioApplicationAdapter;

public final class DockManager {

    // ------------------------------------------------------------------------
    // Layout containers
    // ------------------------------------------------------------------------

    private final VisTable root = new VisTable(true);

    // zones
    private final VisTable left = new VisTable();
    private final VisTable center = new VisTable(); // placeholder
    private final VisTable bottom = new VisTable();

    private final VisTable rightTop = new VisTable();
    private final VisTable rightBottom = new VisTable();

    // splits
    private final VisSplitPane rightSplit;   // top / bottom
    private final VisSplitPane topSplit;     // left / center / right
    private final VisSplitPane rootSplit;    // top / bottom

    // state
    private boolean hasLeft = false;
    private boolean hasRight = false;
    private boolean hasBottom = false;

    private final RulerActor rulerLeft;
    private final RulerActor rulerTop;
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
        root.setTouchable(Touchable.childrenOnly);

        rightSplit = new VisSplitPane(rightTop, rightBottom, true);
        topSplit = new VisSplitPane(left, center, false);
        rootSplit = new VisSplitPane(topSplit, bottom, true);

        rightSplit.setTouchable(Touchable.childrenOnly);
        topSplit.setTouchable(Touchable.childrenOnly);
        rootSplit.setTouchable(Touchable.childrenOnly);

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

        if (panel.getDockMode() == DockablePanel.DockMode.WINDOW_ONLY) {
            Lwjgl3Window w = panelToWindow.remove(panel);
            if (w != null) {
                w.closeWindow();
            }
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
        updateFlags();
        rebuildLayout();
        notifyVisibilityChanged(panel, false);
    }

    public void dock(DockablePanel panel, DockSlot slot) {
        if (panel.getDockMode() == DockablePanel.DockMode.WINDOW_ONLY) {
            return;
        }
        Lwjgl3Window floatingWindow = panelToWindow.get(panel);
        if (containsPanel(floatingWindow)) {
            floatingWindow.setVisible(false);
        }

        panel.remove();
        panel.setFillParent(false);
        panel.setVisible(true);
        panel.setHeaderVisible(true);

        switch (slot) {
            case LEFT -> {
                left.clearChildren();
                left.add(panel).grow();
            }
            case RIGHT_TOP -> {
                rightTop.clearChildren();
                rightTop.add(panel).grow();
            }
            case RIGHT_BOTTOM -> {
                rightBottom.clearChildren();
                rightBottom.add(panel).grow();
            }
            case BOTTOM -> {
                bottom.clearChildren();
                bottom.add(panel).grow();
            }
        }

        updateFlags();
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
        Lwjgl3Window existingWindow = panelToWindow.get(panel);
        if (existingWindow != null) {
            panel.remove();
            updateFlags();
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
        updateFlags();
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

    private void updateFlags() {
        hasLeft = left.hasChildren();
        hasRight = rightTop.hasChildren() || rightBottom.hasChildren();
        hasBottom = bottom.hasChildren();
    }

    private void rebuildLayout() {
        root.clearChildren();

        // --- top area (left / center / right)
        VisTable topArea = new VisTable(false);
        topArea.setTouchable(Touchable.childrenOnly);

        if (hasLeft) {
            topArea.add(left).width(280f).growY();
        }
        VisTable rulers = new VisTable(true);
        rulers.setTouchable(Touchable.childrenOnly);
        rulers.add(rulerTop).height(RulerActor.TOP_HEIGHT).growX().colspan(2).row();
        rulers.add(rulerLeft).width(RulerActor.LEFT_WIDTH).left().growY();
        rulers.add().expand().grow();
        rulers.toBack();

        topArea.add(rulers).grow(); // topArea.add().expand().grow(); // center (WorldCanvas behind)

        if (hasRight) {
            topArea.add(rightSplit).width(342f).growY();
        }

        if (hasBottom) {
            rootSplit.setFirstWidget(topArea);
            rootSplit.setSecondWidget(bottom);
            root.add(rootSplit).grow();
        } else {
            root.add(topArea).grow();
        }
        left.toFront();
        root.invalidateHierarchy();
    }

    public void setRulersVisible(boolean visible) {
        rulerLeft.setVisible(visible);
        rulerTop.setVisible(visible);
    }
}
