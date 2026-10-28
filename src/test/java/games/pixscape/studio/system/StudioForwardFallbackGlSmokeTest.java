package games.pixscape.studio.system;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import org.junit.Test;

public class StudioForwardFallbackGlSmokeTest {
    @Test public void standaloneForwardMatchesPackedForEveryRenderDomain() {
        final Throwable[] failure={null};
        Lwjgl3ApplicationConfiguration config=new Lwjgl3ApplicationConfiguration();
        config.setInitialVisible(false);config.setWindowedMode(64,32);config.disableAudio(true);
        config.setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL30,3,3);
        new Lwjgl3Application(new ApplicationAdapter(){@Override public void create(){
            try{StudioForwardFallbackGlCheck.run();}catch(Throwable error){failure[0]=error;}finally{Gdx.app.exit();}
        }},config);
        if(failure[0]!=null)throw new AssertionError("Studio Forward fallback pixel oracle failed",failure[0]);
    }
}
