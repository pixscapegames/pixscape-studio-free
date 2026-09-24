package games.pixscape.studio.service.hud;

import com.badlogic.gdx.files.FileHandle;
import games.pixscape.runtime.hud.HudScreenAsset;
import games.pixscape.runtime.hud.document.HudDocumentV1;
import games.pixscape.runtime.hud.document.HudNode;
import games.pixscape.runtime.hud.document.HudNodeKind;
import games.pixscape.studio.document.HudScreenEditorDocument;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.lang.reflect.Field;

import static org.junit.Assert.*;

public class HudEditorSessionReimportTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Test public void failedResourceReloadRetainsBoundPreviewAndMarksItStale() throws Exception {
        FileHandle project = new FileHandle(temporary.newFolder());
        HudScreenAsset asset = new HudScreenAsset();
        asset.documentId = "hud/a.json";
        asset.skinId = "skins/missing.json"; // Fails before GL resource creation.
        HudDocumentV1 document = new HudDocumentV1(new HudNode("root", HudNodeKind.GROUP));
        HudScreenEditorDocument open = new HudScreenEditorDocument("hud/a", "a", asset, document);
        HudEditorSession editor = new HudEditorSession();
        set(editor, "editorDocument", open);
        set(editor, "projectDir", project);
        set(editor, "status", HudEditorSession.Status.READY);
        set(editor, "document", document);
        set(editor, "asset", asset);

        RuntimeException failure = assertThrows(RuntimeException.class, editor::reloadResources);

        assertTrue(failure.getMessage().contains("Skin"));
        assertTrue(editor.projects(open));
        assertEquals(HudEditorSession.Status.READY, editor.status());
        assertSame(document, editor.document());
        assertTrue(editor.resourcesStale());
        editor.dispose();
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
