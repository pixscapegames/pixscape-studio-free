package games.pixscape.studio.asset;

import com.badlogic.gdx.files.FileHandle;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class AssetMetaDatabaseIndexTest {

    @Test
    public void emptyAndAllAssetTypes_preserveOrderAndPopulateEveryIndex() {
        AssetMetaDatabase db = new AssetMetaDatabase();
        assertTrue(db.isEmpty());
        assertEquals(0, db.size());
        assertNull(db.findById(0));
        assertNull(db.findByLogicalPath(" "));
        assertNull(db.findBySourceRelPath(null));
        assertEquals(-1, db.getIdBySourceRelPath("missing"));

        AssetType[] types = AssetType.values();
        for (int i = 0; i < types.length; i++) {
            AssetMeta asset = db.registerIfAbsent(
                    types[i],
                    "logical/" + i,
                    "source/" + i,
                    AssetMeta.AssetScope.USER
            );
            assertEquals(i + 1, asset.id());
            assertSame(asset, db.assetAt(i));
            assertSame(asset, db.findById(asset.id()));
            assertSame(asset, db.findByLogicalPath(asset.logicalPath()));
            assertSame(asset, db.findBySourceRelPath(asset.sourceRelPath()));
            assertEquals(asset.id(), db.getIdBySourceRelPath(asset.sourceRelPath()));
        }

        assertFalse(db.isEmpty());
        assertEquals(types.length, db.size());
        assertEquals(types.length + 1, db.nextId());
    }

    @Test
    public void registration_reusesCompatibleAssetPromotesScopeAndIsAtomicOnCollisions() {
        AssetMetaDatabase db = new AssetMetaDatabase();
        AssetMeta first = db.registerIfAbsent(
                AssetType.IMAGE,
                "images/hero",
                null,
                AssetMeta.AssetScope.INTERNAL
        );

        AssetMeta reused = db.registerIfAbsent(
                AssetType.IMAGE,
                "images/hero",
                "orig/images/hero.png",
                AssetMeta.AssetScope.USER
        );
        assertSame(first, reused);
        assertEquals(AssetMeta.AssetScope.USER, first.scope);
        assertEquals("orig/images/hero.png", first.sourceRelPath());
        assertSame(first, db.findBySourceRelPath(first.sourceRelPath()));

        int size = db.size();
        int nextId = db.nextId();
        expectFailure("type collision", () -> db.registerIfAbsent(
                AssetType.ANIMATION,
                "images/hero",
                first.sourceRelPath(),
                AssetMeta.AssetScope.USER
        ));
        expectFailure("source collision", () -> db.registerIfAbsent(
                AssetType.IMAGE,
                "images/hero",
                "orig/images/other.png",
                AssetMeta.AssetScope.USER
        ));
        expectFailure("Duplicate sourceRelPath", () -> db.registerIfAbsent(
                AssetType.IMAGE,
                "images/other",
                first.sourceRelPath(),
                AssetMeta.AssetScope.USER
        ));

        assertEquals(size, db.size());
        assertEquals(nextId, db.nextId());
        assertSame(first, db.findByLogicalPath("images/hero"));
        assertNull(db.findByLogicalPath("images/other"));
        assertNull(db.findBySourceRelPath("orig/images/other.png"));
    }

    @Test
    public void identityMutation_updatesIndexesAndKeepsOldStateOnFailure() {
        AssetMetaDatabase db = new AssetMetaDatabase();
        AssetMeta first = image(db, "images/first", "orig/first.png");
        AssetMeta second = image(db, "images/second", "orig/second.png");

        assertTrue(db.updateLogicalPath(first.id(), "images/renamed"));
        assertNull(db.findByLogicalPath("images/first"));
        assertSame(first, db.findByLogicalPath("images/renamed"));

        assertTrue(db.updateSourceRelPath(first.id(), "orig/renamed.png"));
        assertNull(db.findBySourceRelPath("orig/first.png"));
        assertSame(first, db.findBySourceRelPath("orig/renamed.png"));
        assertFalse(db.updateIdentity(
                first.id(),
                first.logicalPath(),
                first.sourceRelPath()
        ));

        expectFailure("Duplicate logicalPath", () -> db.updateIdentity(
                first.id(),
                second.logicalPath(),
                "orig/new.png"
        ));
        assertEquals("images/renamed", first.logicalPath());
        assertEquals("orig/renamed.png", first.sourceRelPath());
        assertSame(first, db.findByLogicalPath("images/renamed"));
        assertSame(first, db.findBySourceRelPath("orig/renamed.png"));
        assertNull(db.findBySourceRelPath("orig/new.png"));

        expectFailure("Duplicate sourceRelPath", () -> db.updateSourceRelPath(
                first.id(),
                second.sourceRelPath()
        ));
        assertEquals("orig/renamed.png", first.sourceRelPath());

        assertTrue(db.updateSourceRelPath(first.id(), null));
        assertNull(first.sourceRelPath());
        assertNull(db.findBySourceRelPath("orig/renamed.png"));
    }

    @Test
    public void removal_keepsCollectionAndAllIndexesCoherent() {
        AssetMetaDatabase db = new AssetMetaDatabase();
        AssetMeta keep = image(db, "images/keep", "orig/keep.png");
        AssetMeta prefixOne = image(db, "images/group/one", "orig/one.png");
        AssetMeta prefixTwo = image(db, "images/group/two", "orig/two.png");
        AssetMeta byLogical = image(db, "images/by-logical", "orig/by-logical.png");

        assertTrue(db.removeById(prefixOne.id()));
        assertNull(db.findById(prefixOne.id()));
        assertNull(db.findByLogicalPath(prefixOne.logicalPath()));
        assertNull(db.findBySourceRelPath(prefixOne.sourceRelPath()));
        assertFalse(db.removeById(prefixOne.id()));

        assertTrue(db.removeByLogicalPath(byLogical.logicalPath()));
        assertFalse(db.removeByLogicalPath("missing"));
        assertEquals(1, db.removeByLogicalPathPrefix("images/group/"));
        assertEquals(0, db.removeByLogicalPathPrefix("images/group/"));

        assertEquals(1, db.size());
        assertSame(keep, db.assetAt(0));
        assertSame(keep, db.findById(keep.id()));
        assertNull(db.findById(prefixTwo.id()));
        assertNull(db.findByLogicalPath(prefixTwo.logicalPath()));
        assertNull(db.findBySourceRelPath(prefixTwo.sourceRelPath()));
    }

    @Test
    public void atlasTilesMayShareTheirTilesetSourceWithDeterministicPrimaryLookup() {
        AssetMetaDatabase db = new AssetMetaDatabase();
        AssetMeta tileset = db.registerIfAbsent(
                AssetType.TILESET,
                "tiles/terrain",
                "orig/terrain.png",
                AssetMeta.AssetScope.USER
        );
        AssetMeta firstTile = db.registerIfAbsent(
                AssetType.TILE,
                "tiles/terrain/0",
                "orig/terrain.png",
                AssetMeta.AssetScope.USER
        );
        AssetMeta secondTile = db.registerIfAbsent(
                AssetType.TILE,
                "tiles/terrain/1",
                "orig/terrain.png",
                AssetMeta.AssetScope.USER
        );

        assertSame(tileset, db.findBySourceRelPath("orig/terrain.png"));
        assertTrue(db.removeById(tileset.id()));
        assertSame(firstTile, db.findBySourceRelPath("orig/terrain.png"));
        assertTrue(db.removeById(firstTile.id()));
        assertSame(secondTile, db.findBySourceRelPath("orig/terrain.png"));
    }

    @Test
    public void saveLoad_preservesShapeRebuildsIndexesAndNormalizesNextId() throws Exception {
        AssetMetaDatabase db = new AssetMetaDatabase();
        AssetMeta image = image(db, "images/hero", "orig/hero.png");
        AnimationAssetMeta animation = (AnimationAssetMeta) db.registerIfAbsent(
                AssetType.ANIMATION,
                "animations/hero",
                "orig/animations/hero",
                AssetMeta.AssetScope.USER
        );
        animation.clips = null;

        Path path = Files.createTempFile("asset-index-roundtrip", ".json");
        FileHandle file = new FileHandle(path.toFile());
        db.save(file);

        String json = Files.readString(path);
        assertTrue(json.contains("\"version\": 3"));
        assertTrue(json.contains("\"nextId\": 3"));
        assertTrue(json.contains("\"assets\": ["));
        assertTrue(json.contains("\"class\": \"games.pixscape.studio.asset.ImageAssetMeta\""));

        AssetMetaDatabase loaded = AssetMetaDatabase.load(file);
        assertEquals(2, loaded.size());
        assertEquals(2, loaded.indexBuildAssetVisits());
        assertEquals(0, loaded.fullCollectionLookupScans());
        assertEquals(image.id(), loaded.findByLogicalPath("images/hero").id());
        assertSame(
                loaded.findById(image.id()),
                loaded.findBySourceRelPath("orig/hero.png")
        );
        assertTrue(((AnimationAssetMeta) loaded.assetAt(1)).clips != null);

        Files.writeString(path, """
                {
                  "version": 3,
                  "nextId": 1,
                  "assets": [
                    { "id": 8, "type": "image", "logicalPath": "images/eight",
                      "sourceRelPath": "orig/eight.png", "scope": "USER" }
                  ]
                }
                """);
        AssetMetaDatabase normalized = AssetMetaDatabase.load(file);
        assertEquals(9, normalized.nextId());
        assertEquals(9, normalized.allocateNextId());
        assertEquals(10, normalized.registerIfAbsent(
                AssetType.IMAGE,
                "images/ten",
                "orig/ten.png",
                AssetMeta.AssetScope.USER
        ).id());
    }

    @Test
    public void invalidSerializedCatalogs_areRejectedWithPreciseDiagnostics() throws Exception {
        expectInvalidAsset(
                asset(1, "image", "same", "one") + ","
                        + asset(1, "image", "other", "two"),
                "Duplicate asset ID 1", "same", "other"
        );
        expectInvalidAsset(
                asset(1, "image", "same", "one") + ","
                        + asset(2, "image", "same", "two"),
                "Duplicate logicalPath 'same'"
        );
        expectInvalidAsset(
                asset(1, "image", "one", "same") + ","
                        + asset(2, "image", "two", "same"),
                "Duplicate sourceRelPath 'same'"
        );
        expectInvalidAsset("null", "null asset");
        expectInvalidAsset(asset(0, "image", "zero", "zero"), "ID must be > 0");
        expectInvalidAsset(asset(-2, "image", "negative", "negative"), "ID must be > 0");
        expectInvalidAsset(
                asset(Integer.MAX_VALUE, "image", "overflow", "overflow"),
                "allocation overflow", "Integer.MAX_VALUE"
        );
        expectInvalidAsset(asset(1, "image", " ", "source"), "logicalPath");
        expectInvalidAsset("""
                {
                  "class": "games.pixscape.studio.asset.AnimationAssetMeta",
                  "id": 1,
                  "type": "IMAGE",
                  "logicalPath": "images/mismatch",
                  "sourceRelPath": "orig/mismatch.png",
                  "scope": "USER"
                }
                """, "concrete type mismatch", "AnimationAssetMeta", "IMAGE");
    }

    @Test
    public void replaceState_isTransactionalAndDoesNotShareTheSourceCollection() {
        AssetMetaDatabase current = new AssetMetaDatabase();
        AssetMeta old = image(current, "images/old", "orig/old.png");

        AssetMetaDatabase restored = new AssetMetaDatabase();
        AssetMeta replacement = image(restored, "images/new", "orig/new.png");
        current.replaceStateFrom(restored);

        assertNull(current.findByLogicalPath(old.logicalPath()));
        assertSame(replacement, current.findById(replacement.id()));
        restored.removeById(replacement.id());
        assertSame(replacement, current.findById(replacement.id()));
        assertEquals(1, current.size());

        AssetMetaDatabase invalid = new AssetMetaDatabase();
        AssetMeta invalidAsset = image(invalid, "images/invalid", "orig/invalid.png");
        invalidAsset.scope = null;
        expectFailure("scope must not be null", () -> current.replaceStateFrom(invalid));

        assertEquals(1, current.size());
        assertSame(replacement, current.findById(replacement.id()));
        assertNull(current.findByLogicalPath("images/invalid"));
    }

    @Test
    public void fiveThousandAssetsAndSixHundredThousandLookups_doNotScanTheCollection()
            throws Exception {
        int assetCount = 5_000;
        StringBuilder json = new StringBuilder(assetCount * 120);
        json.append("{\"version\":3,\"nextId\":1,\"assets\":[");
        for (int i = 1; i <= assetCount; i++) {
            if (i > 1) json.append(',');
            json.append(asset(i, "image", "images/" + i, "orig/" + i + ".png"));
        }
        json.append("]}");

        Path path = Files.createTempFile("asset-index-volume", ".json");
        Files.writeString(path, json);
        AssetMetaDatabase db = AssetMetaDatabase.load(new FileHandle(path.toFile()));
        assertEquals(assetCount, db.indexBuildAssetVisits());
        assertEquals(assetCount + 1, db.nextId());

        for (int i = 0; i < 100_000; i++) {
            int present = (i % assetCount) + 1;
            int absent = assetCount + present;
            assertSame(db.assetAt(present - 1), db.findById(present));
            assertNull(db.findById(absent));
            assertSame(db.assetAt(present - 1), db.findByLogicalPath("images/" + present));
            assertNull(db.findByLogicalPath("missing/" + absent));
            assertSame(db.assetAt(present - 1), db.findBySourceRelPath("orig/" + present + ".png"));
            assertNull(db.findBySourceRelPath("missing/" + absent + ".png"));
        }

        assertEquals(assetCount, db.indexBuildAssetVisits());
        assertEquals(0, db.fullCollectionLookupScans());
    }

    private static AssetMeta image(AssetMetaDatabase db,
                                   String logicalPath,
                                   String sourceRelPath) {
        return db.registerIfAbsent(
                AssetType.IMAGE,
                logicalPath,
                sourceRelPath,
                AssetMeta.AssetScope.USER
        );
    }

    private static String asset(int id,
                                String type,
                                String logicalPath,
                                String sourceRelPath) {
        return "{\"id\":" + id
                + ",\"type\":\"" + type
                + "\",\"logicalPath\":\"" + logicalPath
                + "\",\"sourceRelPath\":\"" + sourceRelPath
                + "\",\"scope\":\"USER\"}";
    }

    private static void expectInvalidAsset(String assets,
                                           String... expectedMessages) throws Exception {
        Path path = Files.createTempFile("asset-index-invalid", ".json");
        Files.writeString(path, "{\"version\":3,\"nextId\":1,\"assets\":[" + assets + "]}");
        expectFailure(expectedMessages,
                () -> AssetMetaDatabase.load(new FileHandle(path.toFile())));
    }

    private static void expectFailure(String expectedMessage, ThrowingRunnable runnable) {
        expectFailure(new String[]{expectedMessage}, runnable);
    }

    private static void expectFailure(String[] expectedMessages, ThrowingRunnable runnable) {
        try {
            runnable.run();
            fail("Expected failure containing: " + String.join(", ", expectedMessages));
        } catch (Throwable failure) {
            StringBuilder messages = new StringBuilder();
            for (Throwable current = failure; current != null; current = current.getCause()) {
                if (current.getMessage() != null) {
                    messages.append(current.getMessage()).append('\n');
                }
            }
            for (String expected : expectedMessages) {
                assertTrue(
                        "Expected <" + expected + "> in <" + messages + ">",
                        messages.toString().contains(expected)
                );
            }
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
