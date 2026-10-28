package games.pixscape.studio.service.asset;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import games.pixscape.studio.asset.AssetMeta;
import games.pixscape.studio.asset.AssetMetaDatabase;
import games.pixscape.studio.asset.AssetType;
import games.pixscape.studio.io.StudioFs;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.*;

public class BitmapFontAssetImportServiceTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @BeforeClass public static void bootGdx() {
        if (Gdx.files == null) new HeadlessApplication(new ApplicationAdapter() {},
                new HeadlessApplicationConfiguration());
    }

    @Test public void importsMultipageBundleAndPersistsStableIdentity() throws Exception {
        FileHandle external = new FileHandle(temporary.newFolder("external"));
        writePng(external.child("page0.png"));
        writePng(external.child("nested/page1.png"));
        FileHandle descriptor = external.child("default.fnt");
        descriptor.writeString(descriptor("page0.png", "nested/page1.png"), false, "UTF-8");
        FileHandle project = new FileHandle(temporary.newFolder("project"));
        AssetMetaDatabase database = new AssetMetaDatabase();

        AssetMeta imported = new BitmapFontAssetImportService(database)
                .importNew(descriptor, project);
        database.save(project.child(StudioFs.FILE_ASSETS_JSON));
        AssetMeta restored = AssetMetaDatabase.load(project.child(StudioFs.FILE_ASSETS_JSON))
                .findById(imported.id());

        assertEquals(AssetType.FONT, restored.type());
        assertEquals(imported.logicalPath(), restored.logicalPath());
        assertTrue(project.child(restored.sourceRelPath()).exists());
        assertTrue(project.child(StudioFs.DIR_ORIG_FONTS + "/" + imported.id()
                + "/nested/page1.png").exists());
    }

    @Test public void missingPageDoesNotPublishAssetOrBundle() throws Exception {
        FileHandle external = new FileHandle(temporary.newFolder("external-missing"));
        FileHandle descriptor = external.child("broken.fnt");
        descriptor.writeString(descriptor("missing.png"), false, "UTF-8");
        FileHandle project = new FileHandle(temporary.newFolder("project-missing"));
        AssetMetaDatabase database = new AssetMetaDatabase();

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> new BitmapFontAssetImportService(database).importNew(descriptor, project));

        assertTrue(failure.getMessage().contains("missing"));
        assertEquals(0, database.size());
        FileHandle root = project.child(StudioFs.DIR_ORIG_FONTS);
        assertTrue(!root.exists() || root.list().length == 0);
    }

    @Test public void homonymousDescriptorsBecomeDistinctAssetsWithoutOverwrite() throws Exception {
        FileHandle firstRoot = new FileHandle(temporary.newFolder("first"));
        FileHandle secondRoot = new FileHandle(temporary.newFolder("second"));
        writePng(firstRoot.child("page.png"));
        writePng(secondRoot.child("page.png"));
        firstRoot.child("default.fnt").writeString(descriptor("page.png"), false, "UTF-8");
        secondRoot.child("default.fnt").writeString(descriptor("page.png"), false, "UTF-8");
        FileHandle project = new FileHandle(temporary.newFolder("homonyms"));
        AssetMetaDatabase database = new AssetMetaDatabase();
        BitmapFontAssetImportService importer = new BitmapFontAssetImportService(database);

        AssetMeta first = importer.importNew(firstRoot.child("default.fnt"), project);
        AssetMeta second = importer.importNew(secondRoot.child("default.fnt"), project);

        assertNotEquals(first.id(), second.id());
        assertNotEquals(first.logicalPath(), second.logicalPath());
        assertTrue(project.child(first.sourceRelPath()).exists());
        assertTrue(project.child(second.sourceRelPath()).exists());
    }

    @Test public void explicitReimportPreservesAssetIdentityAndReplacesTheWholeBundle() throws Exception {
        FileHandle firstRoot = new FileHandle(temporary.newFolder("reimport-first"));
        writePng(firstRoot.child("old.png"));
        firstRoot.child("font.fnt").writeString(descriptor("old.png"), false, "UTF-8");
        FileHandle replacementRoot = new FileHandle(temporary.newFolder("reimport-replacement"));
        writePng(replacementRoot.child("nested/new.png"));
        replacementRoot.child("renamed.fnt").writeString(descriptor("nested/new.png"), false, "UTF-8");
        FileHandle project = new FileHandle(temporary.newFolder("reimport-project"));
        AssetMetaDatabase database = new AssetMetaDatabase();
        BitmapFontAssetImportService importer = new BitmapFontAssetImportService(database);
        AssetMeta original = importer.importNew(firstRoot.child("font.fnt"), project);

        AssetMeta reimported = importer.reimport(original.id(), replacementRoot.child("renamed.fnt"), project);

        assertEquals(original.id(), reimported.id());
        assertEquals(original.logicalPath(), reimported.logicalPath());
        assertEquals(1, database.size());
        assertFalse(project.child(StudioFs.DIR_ORIG_FONTS + "/" + original.id() + "/old.png").exists());
        assertTrue(project.child(database.findById(original.id()).sourceRelPath()).exists());
        assertTrue(project.child(StudioFs.DIR_ORIG_FONTS + "/" + original.id()
                + "/nested/new.png").exists());
        database.save(project.child(StudioFs.FILE_ASSETS_JSON));
        AssetMeta restored = AssetMetaDatabase.load(project.child(StudioFs.FILE_ASSETS_JSON))
                .findById(original.id());
        assertEquals(original.id(), restored.id());
        assertEquals(original.logicalPath(), restored.logicalPath());
        assertEquals(StudioFs.DIR_ORIG_FONTS + "/" + original.id() + "/renamed.fnt",
                restored.sourceRelPath());
    }

    @Test public void invalidReimportKeepsThePublishedBundleAndCatalogEntry() throws Exception {
        FileHandle firstRoot = new FileHandle(temporary.newFolder("stable-first"));
        writePng(firstRoot.child("old.png"));
        firstRoot.child("font.fnt").writeString(descriptor("old.png"), false, "UTF-8");
        FileHandle invalidRoot = new FileHandle(temporary.newFolder("stable-invalid"));
        invalidRoot.child("broken.fnt").writeString(descriptor("missing.png"), false, "UTF-8");
        FileHandle project = new FileHandle(temporary.newFolder("stable-project"));
        AssetMetaDatabase database = new AssetMetaDatabase();
        BitmapFontAssetImportService importer = new BitmapFontAssetImportService(database);
        AssetMeta original = importer.importNew(firstRoot.child("font.fnt"), project);

        assertThrows(IllegalArgumentException.class,
                () -> importer.reimport(original.id(), invalidRoot.child("broken.fnt"), project));

        AssetMeta retained = database.findById(original.id());
        assertEquals(original.logicalPath(), retained.logicalPath());
        assertEquals(StudioFs.DIR_ORIG_FONTS + "/" + original.id() + "/font.fnt",
                retained.sourceRelPath());
        assertTrue(project.child(retained.sourceRelPath()).exists());
        assertTrue(project.child(StudioFs.DIR_ORIG_FONTS + "/" + original.id() + "/old.png").exists());
    }

    private static void writePng(FileHandle file) {
        file.parent().mkdirs();
        Pixmap pixmap = new Pixmap(2, 2, Pixmap.Format.RGBA8888);
        try {
            pixmap.setColor(1f, 1f, 1f, 1f);
            pixmap.fill();
            PixmapIO.writePNG(file, pixmap);
        } finally {
            pixmap.dispose();
        }
    }

    private static String descriptor(String... pages) {
        StringBuilder out = new StringBuilder("info face=\"test\" size=16 bold=0 italic=0 charset=\"\" unicode=0 stretchH=100 smooth=1 aa=1 padding=0,0,0,0 spacing=1,1\n")
                .append("common lineHeight=16 base=12 scaleW=2 scaleH=2 pages=")
                .append(pages.length).append(" packed=0\n");
        for (int i = 0; i < pages.length; i++) out.append("page id=").append(i)
                .append(" file=\"").append(pages[i]).append("\"\n");
        out.append("chars count=1\nchar id=65 x=0 y=0 width=1 height=1 xoffset=0 yoffset=0 xadvance=1 page=0 chnl=0\n");
        return out.toString();
    }
}
