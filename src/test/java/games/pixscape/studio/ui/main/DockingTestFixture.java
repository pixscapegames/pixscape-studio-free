package games.pixscape.studio.ui.main;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.kotcrab.vis.ui.widget.VisSplitPane;
import com.kotcrab.vis.ui.widget.VisTable;
import games.pixscape.studio.document.EditorDocumentKey;
import games.pixscape.studio.document.EditorDocumentManager;
import games.pixscape.studio.scene.SceneEditorContext;
import games.pixscape.studio.service.StudioEditingModeService;
import games.pixscape.studio.ui.document.EditorDocumentHost;
import games.pixscape.studio.ui.docking.DockManager;
import games.pixscape.studio.ui.docking.DockSlot;
import games.pixscape.studio.ui.docking.DockablePanel;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.function.Consumer;

/** Minimal headless Studio shell shared by docking lifecycle tests. */
public final class DockingTestFixture implements AutoCloseable {
    public final DockManager manager;
    public final EditorDocumentManager documents;
    public final EditorDocumentKey scene;
    public final EditorDocumentKey hud;
    public final EditorDocumentHost editorHost;
    public final TestPanel items;
    public final TestPanel properties;
    public final TestPanel layers;
    public final TestPanel widgets;
    public final TestPanel assets;
    public final VisTable left;
    public final VisTable rightTop;
    public final VisTable rightBottom;
    public final VisTable bottom;
    public final VisSplitPane rightSplit;
    public final VisTable shell;
    public final Actor menu;
    public final Stage studioStage = stage();
    public final Stage floatingStage = stage();
    final DocumentDockPanelCoordinator coordinator;

    private DockingTestFixture(boolean coordinate, Consumer<Runnable> deferUi) throws Exception {
        StudioEditingModeService modes = new StudioEditingModeService();
        documents = new EditorDocumentManager();
        EditorDocumentHost[] host = new EditorDocumentHost[1];
        manager = manager(modes, center -> {
            host[0] = new EditorDocumentHost(documents, center);
            return host[0];
        });
        editorHost = host[0];

        items = new TestPanel("Items");
        properties = new TestPanel("Properties");
        layers = new TestPanel("Layers");
        widgets = new TestPanel("Widgets");
        assets = new TestPanel("Assets");
        manager.register(items, DockSlot.LEFT, true);
        manager.register(properties, DockSlot.RIGHT_TOP, true);
        manager.register(layers, DockSlot.RIGHT_BOTTOM, true);
        manager.register(widgets, DockSlot.RIGHT_BOTTOM, false);
        manager.register(assets, DockSlot.BOTTOM, true);
        coordinator = coordinate
                ? new DocumentDockPanelCoordinator(
                        documents, manager, layers, widgets, () -> studioStage, deferUi)
                : null;

        shell = new VisTable();
        menu = new Actor();
        menu.setTouchable(Touchable.enabled);
        shell.add(menu).growX().height(32f).row();
        shell.add(manager.getRoot()).grow();

        scene = documents.openScene(
                "scene-a", "Scene A", new SceneEditorContext("scene-a", modes)).key();
        hud = documents.openHudScreen("hud/main", "HUD").key();
        left = field(manager, "left", VisTable.class);
        rightTop = field(manager, "rightTop", VisTable.class);
        rightBottom = field(manager, "rightBottom", VisTable.class);
        bottom = field(manager, "bottom", VisTable.class);
        rightSplit = field(manager, "rightSplit", VisSplitPane.class);
        resize(1200f, 800f);
    }

    public static DockingTestFixture create() throws Exception {
        return new DockingTestFixture(false, Runnable::run);
    }

    static DockingTestFixture createCoordinated(Consumer<Runnable> deferUi) throws Exception {
        return new DockingTestFixture(true, deferUi);
    }

    public void resize(float width, float height) {
        shell.setSize(width, height);
        shell.invalidateHierarchy();
        shell.validate();
    }

    /** Establishes the coordinator's Stage-based floating classification; not a native-window test. */
    void placeOnFloatingStage(DockablePanel panel) {
        panel.remove();
        panel.setFillParent(true);
        panel.setVisible(true);
        floatingStage.addActor(panel);
    }

    @Override public void close() {
        manager.dispose();
        floatingStage.dispose();
        studioStage.dispose();
    }

    public static int countIdentity(Actor root, Actor target) {
        int count = root == target ? 1 : 0;
        if (root instanceof com.badlogic.gdx.scenes.scene2d.Group group) {
            for (Actor child : group.getChildren()) count += countIdentity(child, target);
        }
        return count;
    }

    public static <T> T field(Object target, String name, Class<T> type)
            throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return type.cast(field.get(target));
    }

    private static DockManager manager(StudioEditingModeService modes,
                                       java.util.function.Function<Actor, Actor> centerHostFactory)
            throws Exception {
        Unsafe unsafe = unsafe();
        StudioApplicationAdapter app = new StudioApplicationAdapter() {
            @Override public Actor createDockCenterHost(Actor centerContent) {
                return centerHostFactory.apply(centerContent);
            }
        };
        WorldCanvas canvas = (WorldCanvas) unsafe.allocateInstance(WorldCanvas.class);
        setObject(unsafe, canvas, "studioEditingModeService", modes);
        setObject(unsafe, app, "canvas", canvas);
        return new DockManager(
                app,
                new RulerActor(RulerActor.Orientation.LEFT, null, null, null, null),
                new RulerActor(RulerActor.Orientation.TOP, null, null, null, null));
    }

    private static Unsafe unsafe() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe) field.get(null);
    }

    private static void setObject(Unsafe unsafe, Object target, String name, Object value)
            throws Exception {
        Field field = declaredField(target.getClass(), name);
        unsafe.putObject(target, unsafe.objectFieldOffset(field), value);
    }

    private static Field declaredField(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                // Continue through the anonymous test adapter's superclass chain.
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static Stage stage() {
        Batch batch = (Batch) Proxy.newProxyInstance(
                Batch.class.getClassLoader(), new Class[]{Batch.class},
                (proxy, method, args) -> defaultValue(method.getReturnType()));
        return new Stage(new ScreenViewport(), batch);
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0f;
        if (type == double.class) return 0d;
        if (type == char.class) return '\0';
        return null;
    }

    public static final class TestPanel extends DockablePanel {
        public TestPanel(String title) { super(title); }
    }
}
