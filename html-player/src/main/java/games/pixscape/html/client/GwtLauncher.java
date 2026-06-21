package games.pixscape.html.client;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.backends.gwt.GwtApplication;
import com.badlogic.gdx.backends.gwt.GwtApplicationConfiguration;

public final class GwtLauncher extends GwtApplication {

    @Override
    public GwtApplicationConfiguration getConfig() {
        GwtApplicationConfiguration cfg = new GwtApplicationConfiguration(true);
        cfg.padVertical = 0;
        cfg.padHorizontal = 0;
        cfg.useGL30 = true;

        return cfg;
    }

    @Override
    public ApplicationListener createApplicationListener() {
        return new PixscapeHtmlPreviewApp();
    }

    @Override
    public void onModuleLoad() {
        super.onModuleLoad();

        com.google.gwt.dom.client.Element canvas =
                com.google.gwt.dom.client.Document.get()
                        .getElementsByTagName("canvas")
                        .getItem(0);

        if (canvas != null) {
            canvas.setAttribute("tabindex", "0");
            canvas.focus();
        }
    }
}
