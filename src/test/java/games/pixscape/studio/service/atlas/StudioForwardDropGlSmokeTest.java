package games.pixscape.studio.service.atlas;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import org.junit.Test;

public class StudioForwardDropGlSmokeTest {
    @Test public void realCanvasDropsWhilePackingIsBlocked() {
        final Throwable[] failure={null};
        Lwjgl3ApplicationConfiguration config=new Lwjgl3ApplicationConfiguration();
        config.setInitialVisible(false);config.setWindowedMode(960,640);config.disableAudio(true);
        config.setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL30,3,3);
        new Lwjgl3Application(new ApplicationAdapter(){
            StudioForwardDropGlCheck check;
            @Override public void create(){
                try{check=new StudioForwardDropGlCheck();check.run();}
                catch(Throwable error){failure[0]=error;}finally{Gdx.app.exit();}
            }
            @Override public void dispose(){
                try{if(check!=null)check.close();}
                catch(Exception error){if(failure[0]==null)failure[0]=error;else failure[0].addSuppressed(error);}
            }
        },config);
        if(failure[0]!=null)throw new AssertionError("Studio blocked packing canvas failed",failure[0]);
    }
}
