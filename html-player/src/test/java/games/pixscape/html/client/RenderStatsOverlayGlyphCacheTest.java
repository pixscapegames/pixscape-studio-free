package games.pixscape.html.client;

import com.badlogic.gdx.*;
import com.badlogic.gdx.backends.headless.*;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.*;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import games.pixscape.runtime.render.batch.performance.RenderStats;
import org.junit.*;
import java.lang.reflect.Proxy;
import static org.junit.Assert.*;

public class RenderStatsOverlayGlyphCacheTest {
    private static HeadlessApplication app;
    private CountingData data;
    private Stage stage;
    private RenderStatsOverlay overlay;
    private int draws;

    @BeforeClass public static void initialize() {
        HeadlessApplicationConfiguration cfg = new HeadlessApplicationConfiguration();
        cfg.updatesPerSecond = -1;
        app = new HeadlessApplication(new ApplicationAdapter() {}, cfg);
        Gdx.gl = Gdx.gl20 = (GL20) Proxy.newProxyInstance(GL20.class.getClassLoader(),
                new Class<?>[]{GL20.class}, (p,m,a) -> defaultValue(m.getReturnType()));
    }
    @AfterClass public static void shutdown() { app.exit(); }
    @Before public void create() {
        data = new CountingData();
        BitmapFont font = new BitmapFont(data, (TextureRegion)null, true);
        Batch batch = (Batch) Proxy.newProxyInstance(Batch.class.getClassLoader(),
                new Class<?>[]{Batch.class}, (p,m,a) -> {
                    if (m.getName().equals("draw")) draws++;
                    return defaultValue(m.getReturnType());
                });
        stage = new Stage(new ScreenViewport(), batch);
        stage.getViewport().setWorldSize(1280,720);
        overlay = new RenderStatsOverlay(stage, new RenderStats(), font);
    }
    @After public void dispose() { if (overlay != null) overlay.dispose(); if (stage != null) stage.dispose(); }

    @Test public void hiddenStatisticsReuseHelpWithoutLayingOutText() {
        int layouts = data.calls;
        for(int i=0;i<100;i++) overlay.render(i*10_000_000L);
        assertEquals(layouts,data.calls);
        assertEquals(100,draws);
    }
    @Test public void visibleStatisticsReuseGlyphsUntilRefreshDeadline() {
        overlay.setEnabled(true);
        overlay.render(0);
        int layouts=data.calls;
        assertTrue(layouts>1);
        overlay.setFrameTimes(12,14,15,20);
        for(int i=1;i<25;i++) overlay.render(i*10_000_000L);
        assertEquals(layouts,data.calls);
        overlay.render(250_000_000L);
        assertTrue(data.calls>layouts);
    }
    @Test public void resizeInvalidatesVisibleLayoutImmediately() {
        overlay.setEnabled(true); overlay.render(0);
        int layouts=data.calls;
        stage.getViewport().setWorldSize(1280,900);
        overlay.render(1);
        assertTrue(data.calls>layouts);
    }
    @Test public void hidingStopsStatsWorkAndReopeningRefreshes() {
        overlay.setEnabled(true); overlay.render(0);
        overlay.setEnabled(false);
        int layouts=data.calls;
        stage.getViewport().setWorldSize(1280,900);
        overlay.render(1_000_000_000L);
        assertEquals(layouts,data.calls);
        overlay.setEnabled(true); overlay.render(1_000_000_001L);
        assertTrue(data.calls>layouts);
    }
    private static class CountingData extends BitmapFont.BitmapFontData {
        int calls;
        CountingData() { super(Gdx.files.classpath("com/badlogic/gdx/utils/lsans-15.fnt"),false); }
        @Override public void getGlyphs(GlyphLayout.GlyphRun run, CharSequence text,
                int start,int end,BitmapFont.Glyph last) {
            calls++; super.getGlyphs(run,text,start,end,last);
        }
    }
    private static Object defaultValue(Class<?> type) {
        if(type==boolean.class)return false;
        if(type==int.class)return 0;
        if(type==float.class)return 0f;
        if(type==long.class)return 0L;
        return null;
    }
}
