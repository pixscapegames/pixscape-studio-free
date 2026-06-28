package games.pixscape.studio.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Window;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3WindowListener;
import games.pixscape.studio.ui.docking.DockablePanel;
import games.pixscape.studio.ui.main.StudioApplicationAdapter;

public class GenericWindowListener implements Lwjgl3WindowListener {
    private final DockablePanel panel;
    private Lwjgl3Window window;
    private final StudioApplicationAdapter app;
    private boolean forceClose;

    public GenericWindowListener(DockablePanel p, StudioApplicationAdapter app) {
        this.panel = p;
        this.app = app;
    }

    @Override
    public void created(Lwjgl3Window window) {
        this.window = window;
        // Undocked panels are separate windows, but they must not be OS-level
        // floating/always-on-top windows or they will interfere with Alt+Tab.
    }

    @Override
    public void filesDropped(String[] files) {
        if (app != null) {
            Gdx.app.postRunnable(() -> app.onFilesDropped(files));
        }
    }

    public void forceClose() {
        forceClose = true;
    }

    @Override
    public void iconified(boolean isIconified) {
        if (!isIconified) {
            return;
        }

        if (panel.getDockMode() == DockablePanel.DockMode.WINDOW_ONLY) {
            // simple close
            panel.getDockManager().onFloatingWindowClosed(window);
            window.closeWindow();
            return;
        }

        // comportement dockable classique
        panel.setHeaderVisible(true);
        panel.getDockManager().dockToDefault(panel);
        window.setVisible(false);
    }

    @Override
    public void maximized(boolean isMaximized) {
    }

    @Override
    public void focusLost() {
    }

    @Override
    public void focusGained() {
    }

    @Override
    public boolean closeRequested() {
        if (forceClose) {
            panel.getDockManager().onFloatingWindowClosed(window);
            return true;
        }

        if (panel.getDockMode() == DockablePanel.DockMode.WINDOW_ONLY) {
            panel.getDockManager().onFloatingWindowClosed(window);
            return true;
        }

        panel.setHeaderVisible(true);
        panel.getDockManager().hide(panel);
        window.setVisible(false);
        return false;
    }

    @Override
    public void refreshRequested() {
    }
}
