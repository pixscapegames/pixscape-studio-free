package games.pixscape.studio.asset;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.ObjectMap;
import games.pixscape.runtime.component.AnimationComponent;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

public class AssetMetaDatabaseIndexTest {

    @Test
    public void emptyAndAllAssetTypes_preserveOrderAndPopulateEveryIndex() {
        AssetMetaDatabase db = new AssetMetaDatabase();
        assertTrue(db.isEmpty());
        assertEquals(0, db.size());
        assertNull(db.findById(0));
        assertNull(db.findByLogicalPath(" "));
        assertEquals(0, db.sourceOwnerCount(null));
        assertEquals(0, db.sourceOwnerCount("missing"));
        expectFailure("ownerCount=0", () -> db.sourceOwnerAt("missing", 0));

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
            assertEquals(1, db.sourceOwnerCount(asset.sourceRelPath()));
            assertSame(asset, db.sourceOwnerAt(asset.sourceRelPath(), 0));
            assertSame(asset, db.findUniqueBySourceRelPath(asset.sourceRelPath()));
            assertSame(asset, db.findUniqueBySourceRelPath(
                    asset.sourceRelPath(),
                    asset.type()
            ));
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
        assertSame(first, db.findUniqueBySourceRelPath(
                first.sourceRelPath(),
                AssetType.IMAGE
        ));

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
        assertNull(db.findUniqueBySourceRelPath(
                "orig/images/other.png",
                AssetType.IMAGE
        ));
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
        assertEquals(0, db.sourceOwnerCount("orig/first.png"));
        assertSame(first, db.findUniqueBySourceRelPath(
                "orig/renamed.png",
                AssetType.IMAGE
        ));
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
        assertSame(first, db.findUniqueBySourceRelPath("orig/renamed.png"));
        assertEquals(0, db.sourceOwnerCount("orig/new.png"));

        expectFailure("Duplicate sourceRelPath", () -> db.updateSourceRelPath(
                first.id(),
                second.sourceRelPath()
        ));
        assertEquals("orig/renamed.png", first.sourceRelPath());

        assertTrue(db.updateSourceRelPath(first.id(), null));
        assertNull(first.sourceRelPath());
        assertEquals(0, db.sourceOwnerCount("orig/renamed.png"));
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
        assertEquals(0, db.sourceOwnerCount(prefixOne.sourceRelPath()));
        assertFalse(db.removeById(prefixOne.id()));

        assertTrue(db.removeByLogicalPath(byLogical.logicalPath()));
        assertFalse(db.removeByLogicalPath("missing"));
        assertEquals(1, db.removeByLogicalPathPrefix("images/group/"));
        assertEquals(0, db.removeByLogicalPathPrefix("images/group/"));

        assertEquals(1, db.size());
        assertSame(keep, db.assetAt(0));
        assertSame(keep, db.findById(keep.id()));
        assertNull(db.findById(prefixTwo.id()));
        assertEquals(0, db.sourceOwnerCount(prefixTwo.sourceRelPath()));
    }

    @Test
    public void sharedSource_usesExplicitOwnersSortedByAssetId() {
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

        assertEquals(3, db.sourceOwnerCount("orig/terrain.png"));
        assertSame(tileset, db.sourceOwnerAt("orig/terrain.png", 0));
        assertSame(firstTile, db.sourceOwnerAt("orig/terrain.png", 1));
        assertSame(secondTile, db.sourceOwnerAt("orig/terrain.png", 2));
        expectFailure(
                new String[]{"orig/terrain.png", "ownerCount=3",
                        "id=1", "TILESET", "id=2", "TILE", "id=3"},
                () -> db.findUniqueBySourceRelPath("orig/terrain.png")
        );
        assertSame(tileset, db.findUniqueBySourceRelPath(
                "orig/terrain.png",
                AssetType.TILESET
        ));
        expectFailure(
                new String[]{"orig/terrain.png", "ownerCount=2", "id=2", "id=3"},
                () -> db.findUniqueBySourceRelPath(
                        "orig/terrain.png",
                        AssetType.TILE
                )
        );
        assertNull(db.findUniqueBySourceRelPath(
                "orig/terrain.png",
                AssetType.IMAGE
        ));
    }

    @Test
    public void lateTilesetSourceMutation_preservesCanonicalOwnerOrderAcrossRoundTrip()
            throws Exception {
        AssetMetaDatabase db = new AssetMetaDatabase();
        AssetMeta tileset = db.registerIfAbsent(
                AssetType.TILESET,
                "tiles/terrain",
                null,
                AssetMeta.AssetScope.USER
        );
        AssetMeta tile = db.registerIfAbsent(
                AssetType.TILE,
                "tiles/terrain/0",
                "orig/sheet.png",
                AssetMeta.AssetScope.USER
        );

        assertTrue(db.updateSourceRelPath(tileset.id(), "orig/sheet.png"));
        assertEquals(2, db.sourceOwnerCount("orig/sheet.png"));
        assertSame(tileset, db.sourceOwnerAt("orig/sheet.png", 0));
        assertSame(tile, db.sourceOwnerAt("orig/sheet.png", 1));
        assertSame(tileset, db.findUniqueBySourceRelPath(
                "orig/sheet.png",
                AssetType.TILESET
        ));

        Path path = Files.createTempFile("asset-index-late-tileset", ".json");
        FileHandle file = new FileHandle(path.toFile());
        db.save(file);
        AssetMetaDatabase loaded = AssetMetaDatabase.load(file);

        assertEquals(2, loaded.sourceOwnerCount("orig/sheet.png"));
        assertEquals(tileset.id(), loaded.sourceOwnerAt("orig/sheet.png", 0).id());
        assertEquals(tile.id(), loaded.sourceOwnerAt("orig/sheet.png", 1).id());
    }

    @Test
    public void sourceMutation_movesExactlyOnceBetweenCanonicalBuckets() throws Exception {
        AssetMetaDatabase db = new AssetMetaDatabase();
        AssetMeta tileset = db.registerIfAbsent(
                AssetType.TILESET,
                "tiles/terrain",
                "orig/b.png",
                AssetMeta.AssetScope.USER
        );
        AssetMeta moving = db.registerIfAbsent(
                AssetType.TILE,
                "tiles/terrain/0",
                "orig/a.png",
                AssetMeta.AssetScope.USER
        );
        AssetMeta last = db.registerIfAbsent(
                AssetType.TILE,
                "tiles/terrain/1",
                "orig/b.png",
                AssetMeta.AssetScope.USER
        );

        assertTrue(db.updateSourceRelPath(moving.id(), "orig/b.png"));
        assertEquals(0, db.sourceOwnerCount("orig/a.png"));
        assertEquals(3, db.sourceOwnerCount("orig/b.png"));
        assertSame(tileset, db.sourceOwnerAt("orig/b.png", 0));
        assertSame(moving, db.sourceOwnerAt("orig/b.png", 1));
        assertSame(last, db.sourceOwnerAt("orig/b.png", 2));

        Path path = Files.createTempFile("asset-index-bucket-move", ".json");
        db.save(new FileHandle(path.toFile()));
        AssetMetaDatabase loaded =
                AssetMetaDatabase.load(new FileHandle(path.toFile()));
        assertEquals(3, loaded.sourceOwnerCount("orig/b.png"));
        assertEquals(moving.id(), loaded.sourceOwnerAt("orig/b.png", 1).id());
    }

    @Test
    public void replaceState_deepCopiesEveryMutableSubtypeAndBothDirectionsAreIsolated() {
        AssetMetaDatabase current = new AssetMetaDatabase();
        AssetMeta old = image(current, "images/old", "orig/old.png");

        AssetMetaDatabase restored = representativeDatabase();
        int restoredNextId = restored.nextId();
        current.replaceStateFrom(restored);

        assertNull(current.findByLogicalPath(old.logicalPath()));
        assertEquals(restored.size(), current.size());
        assertEquals(restoredNextId, current.nextId());
        assertEquals(restored.version(), current.version());
        for (int i = 0; i < restored.size(); i++) {
            assertNotSame(restored.assetAt(i), current.assetAt(i));
        }

        ImageAssetMeta restoredImage = (ImageAssetMeta) restored.assetAt(0);
        AnimationAssetMeta restoredAnimation = (AnimationAssetMeta) restored.assetAt(1);
        TilesetAssetMeta restoredTileset = (TilesetAssetMeta) restored.assetAt(2);
        TileAssetMeta restoredTile = (TileAssetMeta) restored.assetAt(3);
        AnimationAssetMeta currentAnimation = (AnimationAssetMeta) current.assetAt(1);
        assertNotSame(restoredAnimation.clips, currentAnimation.clips);
        assertNotSame(
                restoredAnimation.clips.get("run"),
                currentAnimation.clips.get("run")
        );

        restored.updateIdentity(
                restoredImage.id(),
                "images/restored-renamed",
                "orig/restored-renamed.png"
        );
        restoredImage.scope = AssetMeta.AssetScope.INTERNAL;
        restoredAnimation.frameCount = 99;
        restoredAnimation.clips.get("run").end = 99;
        restoredAnimation.clips.put("extra", new AnimationComponent.Clip(7, 8));
        restoredTileset.tileWidth = 999;
        restoredTile.sheetIndex = 999;

        ImageAssetMeta currentImage = (ImageAssetMeta) current.assetAt(0);
        TilesetAssetMeta currentTileset = (TilesetAssetMeta) current.assetAt(2);
        TileAssetMeta currentTile = (TileAssetMeta) current.assetAt(3);
        assertEquals("images/hero", currentImage.logicalPath());
        assertEquals("orig/hero.png", currentImage.sourceRelPath());
        assertEquals(AssetMeta.AssetScope.USER, currentImage.scope);
        assertEquals(8, currentAnimation.frameCount);
        assertEquals(5, currentAnimation.clips.get("run").end);
        assertFalse(currentAnimation.clips.containsKey("extra"));
        assertEquals(16, currentTileset.tileWidth);
        assertEquals(3, currentTile.sheetIndex);
        assertSame(currentImage, current.findByLogicalPath("images/hero"));
        assertSame(currentImage, current.findUniqueBySourceRelPath(
                "orig/hero.png",
                AssetType.IMAGE
        ));

        current.updateIdentity(
                currentImage.id(),
                "images/current-renamed",
                "orig/current-renamed.png"
        );
        assertEquals("images/restored-renamed", restoredImage.logicalPath());
        assertEquals("orig/restored-renamed.png", restoredImage.sourceRelPath());
    }

    @Test
    public void invalidReplacement_keepsCurrentCollectionInstancesIndexesAndAllocator() {
        AssetMetaDatabase current = representativeDatabase();
        AssetMeta[] before = new AssetMeta[current.size()];
        for (int i = 0; i < current.size(); i++) before[i] = current.assetAt(i);
        int beforeNextId = current.nextId();
        int beforeVersion = current.version();

        AssetMetaDatabase invalid = new AssetMetaDatabase();
        AssetMeta invalidAsset = image(invalid, "images/invalid", "orig/invalid.png");
        invalidAsset.scope = null;
        expectFailure("scope must not be null", () -> current.replaceStateFrom(invalid));

        assertEquals(before.length, current.size());
        assertEquals(beforeNextId, current.nextId());
        assertEquals(beforeVersion, current.version());
        for (int i = 0; i < before.length; i++) {
            assertSame(before[i], current.assetAt(i));
            assertSame(before[i], current.findById(before[i].id()));
            assertSame(before[i], current.findByLogicalPath(before[i].logicalPath()));
        }
        assertNull(current.findByLogicalPath("images/invalid"));
    }

    @Test
    public void canonicalJson_isClassIndependentAndRoundTripsEverySubtype() throws Exception {
        AssetMetaDatabase db = representativeDatabase();
        db.registerIfAbsent(
                AssetType.PARTICLE,
                "particles/fire",
                "orig/effects/fire.p",
                AssetMeta.AssetScope.USER
        );
        db.registerIfAbsent(
                AssetType.TILE,
                "tiles/terrain/1",
                "orig/sheet.png",
                AssetMeta.AssetScope.USER
        );

        Path path = Files.createTempFile("asset-index-canonical", ".json");
        FileHandle file = new FileHandle(path.toFile());
        db.save(file);
        String json = Files.readString(path);

        assertTrue(json.contains("\"type\": \"image\""));
        assertTrue(json.contains("\"type\": \"animation\""));
        assertTrue(json.contains("\"type\": \"particle\""));
        assertTrue(json.contains("\"type\": \"tileset\""));
        assertTrue(json.contains("\"type\": \"tile\""));
        assertFalse(json.contains("\"class\""));
        assertFalse(json.contains("games.pixscape.studio.asset"));
        assertFalse(json.contains("\"IMAGE\""));
        assertFalse(json.contains("\"ANIMATION\""));

        AssetMetaDatabase loaded = AssetMetaDatabase.load(file);
        assertTrue(loaded.assetAt(0) instanceof ImageAssetMeta);
        assertTrue(loaded.assetAt(1) instanceof AnimationAssetMeta);
        assertTrue(loaded.assetAt(2) instanceof TilesetAssetMeta);
        assertTrue(loaded.assetAt(3) instanceof TileAssetMeta);
        assertTrue(loaded.assetAt(4) instanceof ParticleAssetMeta);
        assertTrue(loaded.assetAt(5) instanceof TileAssetMeta);
        assertEquals(3, loaded.sourceOwnerCount("orig/sheet.png"));

        Files.writeString(path, """
                {
                  "version": 3,
                  "nextId": 2,
                  "assets": [{
                    "class": "ignored.java.ClassName",
                    "id": 1,
                    "type": "image",
                    "logicalPath": "images/class-is-ignored",
                    "sourceRelPath": "orig/class-is-ignored.png",
                    "scope": "USER"
                  }]
                }
                """);
        assertTrue(AssetMetaDatabase.load(file).assetAt(0) instanceof ImageAssetMeta);
    }

    @Test
    public void load_normalizesNextIdAndRejectsInvalidCatalogs() throws Exception {
        Path path = Files.createTempFile("asset-index-next-id", ".json");
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
        AssetMetaDatabase normalized =
                AssetMetaDatabase.load(new FileHandle(path.toFile()));
        assertEquals(9, normalized.nextId());
        assertEquals(9, normalized.allocateNextId());
        assertEquals(10, normalized.registerIfAbsent(
                AssetType.IMAGE,
                "images/ten",
                "orig/ten.png",
                AssetMeta.AssetScope.USER
        ).id());

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
    }

    @Test
    public void fiveThousandAssetsAndSixHundredThousandLookups_keepStableIndexes()
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
        AssetMetaDatabase db =
                AssetMetaDatabase.load(new FileHandle(path.toFile()));
        assertEquals(assetCount, db.indexBuildAssetVisits());
        assertEquals(assetCount + 1, db.nextId());

        for (int i = 0; i < 100_000; i++) {
            int present = (i % assetCount) + 1;
            int absent = assetCount + present;
            AssetMeta expected = db.assetAt(present - 1);
            assertSame(expected, db.findById(present));
            assertNull(db.findById(absent));
            assertSame(expected, db.findByLogicalPath("images/" + present));
            assertNull(db.findByLogicalPath("missing/" + absent));
            assertSame(expected, db.findUniqueBySourceRelPath(
                    "orig/" + present + ".png"
            ));
            assertNull(db.findUniqueBySourceRelPath(
                    "missing/" + absent + ".png"
            ));
        }

        assertEquals(assetCount, db.indexBuildAssetVisits());
        assertEquals(assetCount, db.size());
    }

    private static AssetMetaDatabase representativeDatabase() {
        AssetMetaDatabase db = new AssetMetaDatabase();
        image(db, "images/hero", "orig/hero.png");

        AnimationAssetMeta animation = (AnimationAssetMeta) db.registerIfAbsent(
                AssetType.ANIMATION,
                "animations/hero",
                "orig/animations/hero",
                AssetMeta.AssetScope.USER
        );
        animation.frameCount = 8;
        animation.fps = 12f;
        animation.currentClip = "run";
        animation.clips = new ObjectMap<>();
        AnimationComponent.Clip run = new AnimationComponent.Clip(2, 5);
        run.flipX = true;
        animation.clips.put("run", run);
        animation.clips.put("idle", new AnimationComponent.Clip(0, 1));

        TilesetAssetMeta tileset = (TilesetAssetMeta) db.registerIfAbsent(
                AssetType.TILESET,
                "tiles/terrain",
                "orig/sheet.png",
                AssetMeta.AssetScope.USER
        );
        tileset.imageWidth = 64;
        tileset.imageHeight = 32;
        tileset.tileWidth = 16;
        tileset.tileHeight = 16;
        tileset.columns = 4;
        tileset.rows = 2;
        tileset.spacing = 1;
        tileset.margin = 2;
        tileset.referenceCellWidth = 32;
        tileset.referenceCellHeight = 24;
        tileset.projection = SceneMetaRuntime.TiledProjection.ISO;
        tileset.anchor = TilesetAnchor.BOTTOM_CENTER;
        tileset.offsetX = 3;
        tileset.offsetY = -4;
        tileset.renderSize = TilesetRenderSize.NATIVE;

        TileAssetMeta tile = (TileAssetMeta) db.registerIfAbsent(
                AssetType.TILE,
                "tiles/terrain/0",
                "orig/sheet.png",
                AssetMeta.AssetScope.USER
        );
        tile.tilesetId = tileset.id();
        tile.sheetIndex = 3;
        tile.cellX = 1;
        tile.cellY = 1;
        return db;
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

    private static void expectFailure(String expectedMessage,
                                      ThrowingRunnable runnable) {
        expectFailure(new String[]{expectedMessage}, runnable);
    }

    private static void expectFailure(String[] expectedMessages,
                                      ThrowingRunnable runnable) {
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
