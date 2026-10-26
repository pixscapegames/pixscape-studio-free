package games.pixscape.studio.service;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.artemis.managers.WorldSerializationManager;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Files;
import com.badlogic.gdx.backends.headless.HeadlessFiles;
import com.badlogic.gdx.utils.JsonReader;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.configuration.SceneMeta;
import games.pixscape.studio.scene.SceneEditorContext;
import games.pixscape.studio.service.tiled.TiledAllocatorService;
import games.pixscape.studio.ui.main.WorldCanvas;
import org.junit.*;
import org.junit.rules.TemporaryFolder;

import java.lang.reflect.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.Assert.*;

/** Runs the real save-target resolution and disk write without a graphical canvas. */
public class SceneSaveContextIsolationTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private ProjectConfig cfg, previousConfig;
    private Files previousFiles;
    private WorldCanvas canvas;
    private SceneService service;
    private final List<SceneEditorContext> contexts = new ArrayList<>();
    private Path sceneFile, backupFile, atlasInput;

    @Before public void setup() throws Exception {
        previousFiles = Gdx.files;
        previousConfig = ProjectConfig.getInstance();
        Gdx.files = new HeadlessFiles();
        cfg = new ProjectConfig();
        cfg.projectFileName = "test";
        cfg.projectDirectoryPath = temporary.newFolder("studio").getAbsolutePath();
        cfg.exportRootPathDir = temporary.newFolder("export").getAbsolutePath();
        cfg.createSceneMeta("Scene A");
        ProjectConfig.setInstance(cfg);
        canvas = allocate(WorldCanvas.class);
        service = allocate(SceneService.class);
        field(service, "canvas", canvas);
        sceneFile = Path.of(cfg.projectDirectoryPath, "scenes", cfg.getCurrentSceneMeta().getFile());
        java.nio.file.Files.createDirectories(sceneFile.getParent());
        backupFile = sceneFile.resolveSibling(sceneFile.getFileName()+".bak");
        atlasInput = Path.of(cfg.projectDirectoryPath,"atlases","input","scene1","keep.png");
        java.nio.file.Files.createDirectories(atlasInput.getParent());
        java.nio.file.Files.writeString(sceneFile,"original scene");
        java.nio.file.Files.writeString(backupFile,"original backup");
        java.nio.file.Files.writeString(atlasInput,"original atlas input");
    }

    @After public void cleanup() {
        for (SceneEditorContext context : contexts) if (!context.isDisposed()) context.dispose();
        ProjectConfig.setInstance(previousConfig);
        Gdx.files = previousFiles;
    }

    @Test public void gameObjectCannotOverwriteSceneOrBackupOnRepeatedSave() throws Exception {
        attach(context(null, new SceneMeta()));
        for(int i=0;i<2;i++) {
            assertThrows(IllegalStateException.class, service::saveProjectAndCurrentScene);
            unchanged();
        }
    }

    @Test public void previewSaveReportsFailureInsteadOfLaunchingFromAssetContext() throws Exception {
        attach(context(null, new SceneMeta()));
        AtomicBoolean success = new AtomicBoolean();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        service.saveProjectAndCurrentSceneWithProgress(null, () -> success.set(true), failure::set);
        assertFalse(success.get());
        assertTrue(failure.get() instanceof IllegalStateException);
        unchanged();
    }

    @Test public void validSceneWithGameObjectLayerNameCanBeSavedTwice() throws Exception {
        SceneEditorContext source = attachScene();
        source.layerService().addLayerTop("Game Object");
        source.world().process();
        Object plan = call("prepareSaveExecutionPlan");
        call("savePlannedScene", plan);
        assertEquals(1, new JsonReader().parse(java.nio.file.Files.readString(sceneFile)).get("entities").size);
        assertEquals("original scene",java.nio.file.Files.readString(backupFile));
        call("savePlannedScene", plan);
        assertEquals(java.nio.file.Files.readString(sceneFile),java.nio.file.Files.readString(backupFile));
    }

    @Test public void legitimateEmptySceneIsNotMistakenForAssetContext() throws Exception {
        attachScene();
        call("savePlannedScene",call("prepareSaveExecutionPlan"));
        var entities = new JsonReader().parse(java.nio.file.Files.readString(sceneFile)).get("entities");
        assertTrue(entities == null || entities.size == 0);
    }

    @Test public void switchToAssetDuringSavePreservesSceneBackupAndAtlasInputs() throws Exception {
        attachScene();
        Object plan = call("prepareSaveExecutionPlan");
        attach(context(null,new SceneMeta()));
        assertThrows(IllegalStateException.class, () -> call("syncSceneAtlasInputForSave",plan));
        assertThrows(IllegalStateException.class, () -> call("savePlannedScene",plan));
        unchanged();
    }

    @Test public void switchToAnotherSceneCannotRedirectPendingSave() throws Exception {
        attachScene();
        Object plan = call("prepareSaveExecutionPlan");
        cfg.createSceneMeta("Scene B");
        attachScene();
        assertThrows(IllegalStateException.class, () -> call("savePlannedScene",plan));
        unchanged();
    }

    @Test public void switchAwayAndBackStillInvalidatesPendingSave() throws Exception {
        SceneEditorContext source = attachScene();
        Object plan = call("prepareSaveExecutionPlan");
        attach(context(null,new SceneMeta()));
        attach(source);
        assertThrows(IllegalStateException.class, () -> call("savePlannedScene",plan));
        unchanged();
    }

    @Test public void closingAndReopeningSameSceneRejectsOldPlan() throws Exception {
        SceneEditorContext source = attachScene();
        Object plan = call("prepareSaveExecutionPlan");
        source.dispose();
        attachScene();
        assertThrows(IllegalStateException.class, () -> call("savePlannedScene",plan));
        unchanged();
    }

    @Test public void replacedContextGenerationRejectsOldPlan() throws Exception {
        SceneEditorContext source = attachScene();
        Object plan = call("prepareSaveExecutionPlan");
        source.resetForSceneReplacement(1);
        source.bindSceneIdentity(cfg.canonicalSceneTagCurrent());
        assertThrows(IllegalStateException.class, () -> call("savePlannedScene",plan));
        unchanged();
    }

    @Test public void destinationChangeCannotWriteOldOrNewFile() throws Exception {
        attachScene();
        Object plan = call("prepareSaveExecutionPlan");
        cfg.getCurrentSceneMeta().file="other.json";
        assertThrows(IllegalStateException.class, () -> call("savePlannedScene",plan));
        assertFalse(java.nio.file.Files.exists(sceneFile.resolveSibling("other.json")));
        unchanged();
    }

    @Test public void projectChangeRejectsDelayedExportStepBeforeAction() throws Exception {
        attachScene();
        Object plan = call("prepareSaveExecutionPlan");
        AtomicBoolean mutated = new AtomicBoolean();
        SaveProgressRunner.Step step=(SaveProgressRunner.Step)call("saveStep",plan,.9f,"Export",(Runnable)()->mutated.set(true));
        ProjectConfig.setInstance(new ProjectConfig());
        assertThrows(IllegalStateException.class, ()->step.action().run((p,m)->{},()->{},e->fail()));
        assertFalse(mutated.get());
        unchanged();
    }

    @Test public void staleAtlasCallbackFailsBeforePublishingOrSchedulingAgain() throws Exception {
        SceneEditorContext source = attachScene();
        Object plan = call("prepareSaveExecutionPlan");
        source.dispose();
        AtomicBoolean done=new AtomicBoolean();
        AtomicReference<Throwable> failure=new AtomicReference<>();
        // No atlas service or Gdx.app: reaching publication/rescheduling would fail this assertion.
        call("waitForAsyncPackCompletion",plan,"scene1",1L,
                (SaveProgressRunner.ProgressHandle)(p,m)->{},(Runnable)()->done.set(true),
                (Consumer<Throwable>)failure::set);
        assertFalse(done.get());
        assertTrue(failure.get() instanceof IllegalStateException);
        unchanged();
    }

    @Test public void completionCannotLaunchPreviewForAnotherProject() throws Exception {
        attachScene();
        Object plan=call("prepareSaveExecutionPlan");
        ProjectConfig.setInstance(new ProjectConfig());
        AtomicBoolean launched=new AtomicBoolean();
        AtomicReference<Throwable> failure=new AtomicReference<>();
        call("completePlannedSave",plan,(Runnable)()->launched.set(true),(Consumer<Throwable>)failure::set);
        assertFalse(launched.get());
        assertTrue(failure.get() instanceof IllegalStateException);
        unchanged();
    }

    private SceneEditorContext attachScene() throws Exception {
        return attach(context(cfg.canonicalSceneTagCurrent(),cfg.getCurrentSceneMeta()));
    }
    private SceneEditorContext context(String identity, SceneMeta meta) {
        SceneEditorContext c=new SceneEditorContext(identity,new StudioEditingModeService());
        c.initializeWorld(new World(new WorldConfiguration().setSystem(new WorldSerializationManager())),
                new TiledAllocatorService(),meta);
        contexts.add(c);return c;
    }
    private SceneEditorContext attach(SceneEditorContext c) throws Exception {
        SceneEditorContext old=canvas.getAttachedSceneContext();if(old!=null)old.detached();
        field(canvas,"sceneEditorContext",c);c.attached();return c;
    }
    private void unchanged() throws Exception {
        assertEquals("original scene",java.nio.file.Files.readString(sceneFile));
        assertEquals("original backup",java.nio.file.Files.readString(backupFile));
        assertEquals("original atlas input",java.nio.file.Files.readString(atlasInput));
    }
    private Object call(String name,Object... args) throws Exception {
        for(Method m:SceneService.class.getDeclaredMethods())if(m.getName().equals(name)&&m.getParameterCount()==args.length){
            m.setAccessible(true);
            try{return m.invoke(service,args);}catch(InvocationTargetException e){
                if(e.getCause() instanceof Exception cause)throw cause;
                if(e.getCause() instanceof Error cause)throw cause;
                throw e;
            }
        }
        throw new NoSuchMethodException(name);
    }
    private static void field(Object o,String name,Object value)throws Exception {
        Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);f.set(o,value);
    }
    // Only the graphical shell is bypassed; contexts, Worlds, serialization and files are real.
    private static <T> T allocate(Class<T> type)throws Exception {
        Field f=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);
        return type.cast(((sun.misc.Unsafe)f.get(null)).allocateInstance(type));
    }
}
