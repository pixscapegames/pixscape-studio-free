package games.pixscape.studio.service.hud;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import games.pixscape.runtime.hud.*;
import games.pixscape.runtime.hud.document.*;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class HudScreenAssetAuthoringServiceTest {
    @BeforeClass public static void bootGdx() {
        if (Gdx.app == null) new HeadlessApplication(new ApplicationAdapter() {},
                new HeadlessApplicationConfiguration());
    }

    @Rule public final TemporaryFolder temporary = new TemporaryFolder();
    private final HudScreenAssetAuthoringService service = new HudScreenAssetAuthoringService();

    @Test
    public void createsLoadableScreenAndRootDocument() throws Exception {
        FileHandle root = new FileHandle(temporary.newFolder());
        String id = service.create(root, "inventory");

        Assert.assertEquals("hud/inventory", id);
        HudScreenAsset asset = new HudScreenAssetLoader().load(root, id);
        Assert.assertEquals(1, asset.schemaVersion);
        Assert.assertEquals("hud/inventory.json", asset.documentId);
        Assert.assertTrue(root.child("hud/inventory.hudscreen").exists());
        Assert.assertTrue(root.child(asset.documentId).exists());
    }

    @Test
    public void rejectsDuplicateAndInvalidInputWithoutReplacingAsset() throws Exception {
        FileHandle root = new FileHandle(temporary.newFolder());
        service.create(root, "pause");
        String original = root.child("hud/pause.hudscreen").readString("UTF-8");

        IllegalArgumentException duplicate = Assert.assertThrows(IllegalArgumentException.class,
                () -> service.create(root, "pause"));
        Assert.assertTrue(duplicate.getMessage().contains("already exists"));
        Assert.assertEquals(original, root.child("hud/pause.hudscreen").readString("UTF-8"));

        Assert.assertThrows(IllegalArgumentException.class,
                () -> service.create(root, ""));
    }

    @Test
    public void appearsInBrowserAndReopensWithItsRootDocument() throws Exception {
        FileHandle root = new FileHandle(temporary.newFolder());
        String id = service.create(root, "character");

        var visible = HudScreenAssetBrowser.scan(root);
        Assert.assertEquals(1, visible.size);
        Assert.assertEquals(id, visible.first().path);

        var loaded = new HudDocumentPersistenceService().load(root, "character");
        Assert.assertEquals("hud/character.json", loaded.asset().documentId);
        Assert.assertNotNull(loaded.document());
        Assert.assertEquals("root", loaded.document().root.id);
    }

    @Test
    public void initialCellExpandsWithSurfaceButKeepsAnExplicitWidgetSize() throws Exception {
        FileHandle root = new FileHandle(temporary.newFolder());
        String id = service.create(root, "single-cell");
        HudDocumentPersistenceService persistence = new HudDocumentPersistenceService();
        var loaded = persistence.load(root, id);
        HudDocumentEditSession edit = new HudDocumentEditSession(loaded.asset(), loaded.document());
        edit.edit("Add anchored widget", candidate -> {
            var cell = candidate.root.table.rows.get(0).cells.get(0);
            HudNode widget = new HudNode("widget", HudNodeKind.GROUP);
            widget.actor.width = 20f;
            widget.actor.height = 10f;
            cell.content = widget;
            cell.constraints.horizontalAlign = HudHorizontalAlign.RIGHT;
            cell.constraints.verticalAlign = HudVerticalAlign.TOP;
            return candidate;
        });
        persistence.save(root, edit);
        edit.close();
        var reopened = persistence.load(root, id);
        var cell = reopened.document().root.table.rows.get(0).cells.get(0);
        Assert.assertTrue(cell.constraints.expandX);
        Assert.assertTrue(cell.constraints.expandY);
        Assert.assertFalse(cell.constraints.fillX);
        Assert.assertFalse(cell.constraints.fillY);

        var validation = new HudDocumentValidator().validate(reopened.document());
        Assert.assertTrue(validation.issues().toString(), validation.isValid());
        HudResources resources = HudResources.prepareStandalone(reopened.asset(), root,
                HudResourceRequirements.from(validation.validatedDocument()));
        try {
            var hud = new HudMaterializer().materialize(validation.validatedDocument(),
                    resources.select(null));
            try {
                Table surface = (Table) hud.root();
                surface.setBounds(0f, 0f, 320f, 200f);
                surface.validate();
                Assert.assertEquals(300f, hud.actor("widget").getX(), 0f);
                Assert.assertEquals(190f, hud.actor("widget").getY(), 0f);
                surface.setBounds(0f, 0f, 640f, 300f);
                surface.invalidateHierarchy();
                surface.validate();
                Assert.assertEquals(620f, hud.actor("widget").getX(), 0f);
                Assert.assertEquals(290f, hud.actor("widget").getY(), 0f);
                Assert.assertEquals(20f, hud.actor("widget").getWidth(), 0f);
                Assert.assertEquals(10f, hud.actor("widget").getHeight(), 0f);
            } finally {
                hud.dispose();
            }
        } finally {
            resources.dispose();
        }
    }

    @Test
    public void newlyCreatedScreenKeepsAdaptiveCellLayoutAfterSaveAndReopen() throws Exception {
        FileHandle root = new FileHandle(temporary.newFolder());
        String id = service.create(root, "adaptive");
        HudDocumentPersistenceService persistence = new HudDocumentPersistenceService();
        var loaded = persistence.load(root, id);
        HudDocumentEditSession edit = new HudDocumentEditSession(loaded.asset(), loaded.document());
        edit.edit("Add HUD content", candidate -> {
            candidate.root.table = HudLayoutAuthoring.newTableLayout(candidate.root, 1, 2, false);
            var anchoredCell = candidate.root.table.rows.get(0).cells.get(0);
            HudNode anchored = new HudNode("anchored", HudNodeKind.GROUP);
            anchored.actor.width = 20f;
            anchored.actor.height = 10f;
            anchoredCell.content = anchored;
            anchoredCell.constraints.expandX = true;
            anchoredCell.constraints.horizontalAlign = HudHorizontalAlign.RIGHT;
            anchoredCell.constraints.verticalAlign = HudVerticalAlign.TOP;

            var stretchCell = candidate.root.table.rows.get(0).cells.get(1);
            stretchCell.content = new HudNode("stretched", HudNodeKind.STACK);
            stretchCell.constraints.expandX = true;
            stretchCell.constraints.expandY = true;
            stretchCell.constraints.fillX = true;
            stretchCell.constraints.fillY = true;
            return candidate;
        });
        persistence.save(root, edit);
        var reopened = persistence.load(root, id).document();
        var validation = new HudDocumentValidator().validate(reopened);
        Assert.assertTrue(validation.issues().toString(), validation.isValid());
        HudResources resources = HudResources.prepareStandalone(loaded.asset(), root,
                HudResourceRequirements.from(validation.validatedDocument()));
        try {
            var hud = new HudMaterializer().materialize(validation.validatedDocument(),
                    resources.select(null));
            try {
                Table surface = (Table) hud.root();
                surface.setBounds(0f, 0f, 320f, 200f);
                surface.validate();
                float firstX = hud.actor("anchored").getX();
                float firstWidth = hud.actor("stretched").getWidth();
                Assert.assertEquals(320f, surface.getWidth(), 0f);
                Assert.assertEquals(200f, surface.getHeight(), 0f);
                Assert.assertEquals(20f, hud.actor("anchored").getWidth(), 0f);

                surface.setBounds(0f, 0f, 640f, 300f);
                surface.invalidateHierarchy();
                surface.validate();
                Assert.assertEquals(640f, surface.getWidth(), 0f);
                Assert.assertEquals(300f, surface.getHeight(), 0f);
                Assert.assertTrue(hud.actor("anchored").getX() > firstX);
                Assert.assertEquals(20f, hud.actor("anchored").getWidth(), 0f);
                Assert.assertTrue(hud.actor("stretched").getWidth() > firstWidth);
            } finally {
                hud.dispose();
            }
        } finally {
            resources.dispose();
            edit.close();
        }
    }
}
