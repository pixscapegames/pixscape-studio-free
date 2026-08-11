package games.pixscape.html.client;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.backends.gwt.GwtApplication;
import com.badlogic.gdx.backends.gwt.GwtApplicationConfiguration;
import com.badlogic.gdx.backends.gwt.preloader.Preloader.PreloaderCallback;
import com.badlogic.gdx.backends.gwt.preloader.Preloader.PreloaderState;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.user.client.ui.InlineHTML;
import com.google.gwt.user.client.ui.SimplePanel;

public final class GwtLauncher extends GwtApplication {

    private SimplePanel preloader;

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
    public PreloaderCallback getPreloaderCallback() {
        preloader = new SimplePanel();
        preloader.setStyleName("pixscape-preloader-meter");
        final InlineHTML fill = new InlineHTML();
        fill.setStyleName("pixscape-preloader-meter-fill");
        preloader.add(fill);
        getRootPanel().add(preloader);

        return new PreloaderCallback() {
            @Override
            public void error(String file) {
                System.out.println("Unable to preload: " + file);
            }

            @Override
            public void update(PreloaderState state) {
                fill.getElement().getStyle().setWidth(
                        100f * PixscapeHtmlPreviewApp.HTML_PRELOAD_SHARE * state.getProgress(), Unit.PCT);
            }
        };
    }

    @Override
    public void onModuleLoad() {
        setLoadingListener(new LoadingListener() {
            @Override
            public void beforeSetup() {
            }

            @Override
            public void afterSetup() {
                if (preloader != null) {
                    preloader.removeFromParent();
                    preloader = null;
                }
                focusCanvas();
            }
        });
        super.onModuleLoad();
    }

    private void focusCanvas() {
        Element canvas = getCanvasElement();
        if (canvas != null) {
            canvas.setAttribute("tabindex", "0");
            canvas.focus();
        }
    }
}
