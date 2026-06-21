package games.pixscape.studio.system;

import com.artemis.BaseSystem;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.physics.box2d.Box2DDebugRenderer;
import games.pixscape.runtime.service.Box2dWorldService;
import games.pixscape.studio.event.EventFlow;

public final class Box2dDebugRenderSystem extends BaseSystem {

    private final Box2DDebugRenderer debug = new Box2DDebugRenderer();
    private Box2dWorldService box2d;
    private final OrthographicCamera camera;

    private boolean showFixtures = false;

    private final Matrix4 tmp = new Matrix4();

    public Box2dDebugRenderSystem(Box2dWorldService box2d, OrthographicCamera camera) {
        this.box2d = box2d;
        this.camera = camera;

        EventFlow.i().subscribe(EventFlow.SceneShowFixturesChanged.class, e -> showFixtures = e.enabled());
    }

    @Override
    protected void processSystem() {
        if (!showFixtures) return;
        if (box2d == null || box2d.world == null) return;

        // camera px -> box2d m
        tmp.set(camera.combined).scale(1f / box2d.ppm, 1f / box2d.ppm, 1f);
        debug.render(box2d.world, tmp);
    }

    @Override
    protected void dispose() {
        debug.dispose();
    }

    public void setBox2d(Box2dWorldService box2d) {
        this.box2d = box2d;
    }
}
