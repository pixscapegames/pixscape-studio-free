package games.pixscape.studio.ui.widget;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.kotcrab.vis.ui.VisUI;

import java.lang.reflect.Proxy;

public final class VisUiTestBootstrap {

    private static HeadlessApplication app;
    private static int refCount = 0;

    private VisUiTestBootstrap() {
    }

    public static synchronized void loadSkin() {
        if (refCount++ > 0) return;

        if (app == null) {
            app = new HeadlessApplication(new ApplicationAdapter() {}, new HeadlessApplicationConfiguration());
        }

        if (Gdx.graphics == null) {
            Gdx.graphics = createGraphicsStub();
        }

        if (Gdx.gl == null) {
            GL20 gl20 = createNoOpGl20();
            Gdx.gl = gl20;
            Gdx.gl20 = gl20;
        }

        if (!VisUI.isLoaded()) {
            VisUI.load(new Skin(Gdx.files.internal("assets/ui/skin/uiskin.json")));
        }
    }

    public static synchronized void unloadSkin() {
        if (refCount == 0) return;
        if (--refCount > 0) return;

        if (VisUI.isLoaded()) {
            VisUI.dispose();
        }

        Gdx.gl = null;
        Gdx.gl20 = null;
        Gdx.graphics = null;

        if (app != null) {
            app.exit();
            app = null;
        }
    }

    private static GL20 createNoOpGl20() {
        return (GL20) Proxy.newProxyInstance(
                GL20.class.getClassLoader(),
                new Class[]{GL20.class},
                (proxy, method, args) -> defaultValue(method.getReturnType(), method.getName())
        );
    }

    private static Graphics createGraphicsStub() {
        return (Graphics) Proxy.newProxyInstance(
                Graphics.class.getClassLoader(),
                new Class[]{Graphics.class},
                (proxy, method, args) -> {
                    return switch (method.getName()) {
                        case "getWidth", "getBackBufferWidth" -> 16;
                        case "getHeight", "getBackBufferHeight" -> 16;
                        case "getDeltaTime" -> 1f / 60f;
                        case "getFramesPerSecond" -> 60;
                        case "supportsDisplayModeChange", "isFullscreen", "isGL30Available" -> false;
                        default -> defaultValue(method.getReturnType(), method.getName());
                    };
                }
        );
    }

    private static Object defaultValue(Class<?> returnType, String methodName) {
        if (returnType == Void.TYPE) return null;
        if (returnType == Boolean.TYPE) return false;
        if (returnType == Byte.TYPE) return (byte) 0;
        if (returnType == Short.TYPE) return (short) 0;
        if (returnType == Integer.TYPE) return 0;
        if (returnType == Long.TYPE) return 0L;
        if (returnType == Float.TYPE) return 0f;
        if (returnType == Double.TYPE) return 0d;
        if (returnType == Character.TYPE) return '\0';
        return null;
    }
}
