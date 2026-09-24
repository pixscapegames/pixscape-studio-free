package games.pixscape.studio.service.runtimeavailability;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData;
import games.pixscape.runtime.hud.HudScreenAsset;
import games.pixscape.runtime.hud.document.HudDocumentV1;
import games.pixscape.runtime.hud.document.HudNode;
import games.pixscape.runtime.hud.document.HudNodeKind;
import games.pixscape.runtime.hud.document.HudChild;
import games.pixscape.runtime.hud.document.HudFreePlacement;
import games.pixscape.runtime.hud.document.HudImageData;
import games.pixscape.runtime.hud.document.HudImageSource;
import games.pixscape.runtime.hud.document.HudLabelData;
import games.pixscape.runtime.hud.document.HudSliderData;
import games.pixscape.runtime.hud.document.HudProgressBarData;
import games.pixscape.runtime.hud.document.HudTooltipData;
import games.pixscape.studio.asset.AssetMeta;
import games.pixscape.studio.asset.AssetMetaDatabase;
import games.pixscape.studio.asset.AssetType;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.configuration.SceneMeta;
import games.pixscape.studio.document.EditorDocumentManager;
import games.pixscape.studio.document.HudScreenEditorDocument;
import games.pixscape.studio.io.AtomicDirectoryPublication;
import games.pixscape.studio.service.atlas.AsyncAtlasRepackCoordinator;
import games.pixscape.studio.service.atlas.AtlasPackingService;
import games.pixscape.studio.service.atlas.ControlledAtlasExecutor;
import games.pixscape.studio.service.asset.BitmapFontAssetImportService;
import games.pixscape.studio.service.asset.Scene2dSkinAssetImportService;
import games.pixscape.studio.service.hud.HudDocumentPersistenceService;
import games.pixscape.studio.service.hud.HudLayoutAuthoring;
import java.io.File;
import java.io.IOException;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class SceneHudRuntimePreparationServiceTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @BeforeClass public static void bootGdx() {
        if (Gdx.files == null) new HeadlessApplication(new ApplicationAdapter() {}, new HeadlessApplicationConfiguration());
    }

    @Test public void authoredEditsRecomposeWithoutReadingSourcesOrPackingAgain() throws Exception {
        Fixture f = fixture();
        try (var service = f.service()) {
            service.invalidateScene("sceneA"); f.complete(service);
            assertEquals(1, f.packs.get());
            long initial = service.compositionGeneration("sceneA");
            long resources = service.resourceGeneration("sceneA");
            FileHandle source = f.root.child(f.first.sourceRelPath());
            byte[] saved = source.readBytes();
            assertTrue(source.delete()); // A missing source makes any accidental resource read fail.
            try {
                f.a.editSession().edit("Move", document -> {
                    document.root.children.get(0).free.offsetX = 20f; return document;
                });
                f.a.editSession().edit("Resize and hide", document -> {
                    document.root.children.get(0).node.visible = false;
                    document.root.children.get(0).free.offsetY = 12f; return document;
                });
                assertEquals(1, f.packs.get());
                assertEquals(1, service.generation("sceneA"));
                assertTrue(service.compositionGeneration("sceneA") > initial);
                assertEquals(resources, service.resourceGeneration("sceneA"));
                assertTrue(f.errors.isEmpty());
                assertTrue(f.a.editSession().undo());
                assertTrue(f.a.editSession().redo());
                assertEquals(1, f.packs.get());
            } finally { source.writeBytes(saved, false); }
            service.invalidateScene("sceneA"); f.complete(service);
            assertEquals(1, f.packs.get());
            f.noCandidates();
        }
    }

    @Test public void sameInputsKeepPendingRunningReadyAndPublishedPack() throws Exception {
        Fixture f = fixture(); Blocker blocker = new Blocker();
        f.packer = (input, output, name, profile) -> {
            blocker.blockIgnoringInterrupt(); AtlasPackingService.packHud(input, output, name, profile);
        };
        try (var service = f.service()) {
            service.invalidateScene("sceneA");
            service.invalidateScene("sceneA"); // Pending.
            assertEquals(1, service.generation("sceneA"));
            service.update(); Thread worker = f.executors.get(0).startNext();
            try {
                blocker.awaitStarted();
                f.a.editSession().edit("B", document -> {
                    document.root.children.get(0).free.offsetX = 3f; return document;
                });
                service.invalidateScene("sceneA"); // Running, same projected inputs.
                assertEquals(1, service.generation("sceneA"));
                blocker.release(); ControlledAtlasExecutor.join(worker);
                service.invalidateScene("sceneA"); // Ready.
                assertEquals(1, service.generation("sceneA"));
                service.update();
                assertEquals(1, f.packs.get());
                assertTrue(service.compositionGeneration("sceneA") > 0L);
                service.invalidateScene("sceneA"); // Published.
                f.complete(service);
                assertEquals(1, f.packs.get());
                assertEquals(1, service.generation("sceneA"));
                assertTrue(f.errors.isEmpty()); f.noCandidates();
            } finally { blocker.release(); ControlledAtlasExecutor.join(worker); }
        }
    }

    @Test public void duplicateReferenceAndUndoReuseAtlasButNewReferenceAndReimportRepack() throws Exception {
        Fixture f = fixture();
        try (var service = f.service()) {
            service.invalidateScene("sceneA"); f.complete(service);
            f.add(f.a, f.firstName()); f.complete(service);
            assertEquals(1, f.packs.get());
            assertTrue(f.a.editSession().undo()); f.complete(service);
            assertEquals(1, f.packs.get());
            f.add(f.a, f.secondName()); f.complete(service);
            assertEquals(2, f.packs.get());
            f.a.editSession().edit("Remove reference", document -> {
                document.root.children.remove(1); return document;
            });
            f.complete(service); assertEquals(3, f.packs.get());
            FileHandle reimported = f.root.child(f.first.sourceRelPath());
            int originalLength = reimported.readBytes().length;
            png(reimported, 0xff0000ff);
            assertEquals("Reimport fixture changes pixels without changing the file size",
                    originalLength, reimported.readBytes().length);
            service.invalidateHudScreen("hud/a"); f.complete(service);
            assertEquals("Same Asset ID and path with different pixels must repack", 4, f.packs.get());
            f.noCandidates();
        }
    }

    @Test public void sharedHudRecomposesBothScenesAndPacksPhysicalChangesIndependently() throws Exception {
        Fixture f = fixture(); f.sceneB.defaultHudScreenId = "hud/a";
        try (var service = f.service()) {
            service.invalidateScene("sceneA"); service.invalidateScene("sceneB"); f.complete(service);
            assertEquals(2, f.packs.get());
            long a = service.compositionGeneration("sceneA"), b = service.compositionGeneration("sceneB");
            f.a.editSession().edit("Hide", document -> { document.root.visible = false; return document; });
            assertTrue(service.compositionGeneration("sceneA") > a);
            assertTrue(service.compositionGeneration("sceneB") > b);
            f.complete(service); assertEquals(2, f.packs.get());
            png(f.root.child(f.first.sourceRelPath()), 0xff0000ff);
            service.invalidateHudScreen("hud/a"); f.complete(service);
            assertEquals(4, f.packs.get());
            assertEquals(2, service.publishedGeneration("sceneA"));
            assertEquals(2, service.publishedGeneration("sceneB")); f.noCandidates();
        }
    }

    @Test public void textWithSameBitmapFontAndSliderValuesOnlyRecompose() throws Exception {
        Fixture f = fixture();
        AssetMeta font = f.database.registerIfAbsent(AssetType.FONT, "fonts/test",
                "orig/fonts/" + f.database.nextId() + "/test.fnt", AssetMeta.AssetScope.USER);
        FileHandle descriptor = f.root.child(font.sourceRelPath());
        descriptor.parent().mkdirs();
        descriptor.writeString("info face=\"test\" size=16 bold=0 italic=0 charset=\"\" unicode=0 stretchH=100 smooth=1 aa=1 padding=0,0,0,0 spacing=0,0\n"
                + "common lineHeight=16 base=12 scaleW=4 scaleH=4 pages=1 packed=0\n"
                + "page id=0 file=\"page.png\"\nchars count=1\n"
                + "char id=65 x=0 y=0 width=1 height=1 xoffset=0 yoffset=0 xadvance=1 page=0 chnl=0\n"
                + "kernings count=0\n", false);
        png(descriptor.parent().child("page.png"), 0xffffffff);
        f.a.editSession().edit("Widgets", document -> {
            HudNode label = new HudNode("label", HudNodeKind.LABEL);
            label.label = new HudLabelData(); label.label.text = "A"; label.label.fontAssetId = font.id();
            document.root.children.add(HudChild.free(label, new HudFreePlacement()));
            HudNode slider = new HudNode("slider", HudNodeKind.SLIDER);
            slider.slider = new HudSliderData();
            document.root.children.add(HudChild.free(slider, new HudFreePlacement()));
            HudNode progress = new HudNode("progress", HudNodeKind.PROGRESS_BAR);
            progress.progressBar = new HudProgressBarData();
            document.root.children.add(HudChild.free(progress, new HudFreePlacement()));
            return document;
        });
        try (var service = f.service()) {
            service.invalidateScene("sceneA"); f.complete(service);
            assertTrue(f.errors.toString(), f.errors.isEmpty());
            assertEquals(1, f.packs.get());
            long before = service.compositionGeneration("sceneA");
            f.a.editSession().edit("Values and text", document -> {
                document.root.children.get(1).node.label.text = "B";
                document.root.children.get(2).node.slider.value = 75f;
                document.root.children.get(3).node.progressBar.value = 25f;
                return document;
            });
            f.complete(service);
            assertEquals(1, f.packs.get());
            assertTrue(service.compositionGeneration("sceneA") > before);
            assertTrue(f.a.editSession().undo()); f.complete(service);
            assertEquals(1, f.packs.get());
            f.noCandidates();
        }
    }

    @Test public void logicalSkinChangeRecomposesWithoutChangingPhysicalPack() throws Exception {
        Fixture f = fixture();
        FileHandle skin = f.root.child("skins/game.json");
        skin.parent().mkdirs(); skin.writeString("{}", false);
        png(skin.parent().child("page.png"), 0xff00ffff);
        skin.parent().child("game.atlas").writeString("page.png\nsize: 4, 4\nformat: RGBA8888\nfilter: Linear, Linear\nrepeat: none\n"
                + "panel\n  bounds: 0, 0, 4, 4\n  index: -1\n"
                + "other\n  bounds: 0, 0, 1, 1\n  index: -1\n", false);
        f.a.editSession().editSkin("Skin", "skins/game.json");
        f.a.editSession().edit("Drawable", document -> {
            HudNode node = new HudNode("drawable", HudNodeKind.IMAGE);
            node.image = new HudImageData(); node.image.source = HudImageSource.DRAWABLE;
            node.image.resourceName = "panel";
            document.root.children.add(HudChild.free(node, new HudFreePlacement()));
            return document;
        });
        try (var service = f.service()) {
            service.invalidateScene("sceneA"); f.complete(service);
            assertTrue(f.errors.toString(), f.errors.isEmpty());
            assertEquals(1, f.packs.get());
            long before = service.compositionGeneration("sceneA");
            long beforeResources = service.resourceGeneration("sceneA");
            skin.writeString("{\"com.badlogic.gdx.graphics.Color\":{\"accent\":{\"r\":1,\"g\":0,\"b\":0,\"a\":1}}}", false);
            service.invalidateHudScreen("hud/a"); f.complete(service);
            assertEquals(1, f.packs.get());
            assertTrue("before=" + before + " after=" + service.compositionGeneration("sceneA")
                    + " published=" + service.publishedGeneration("sceneA") + " errors=" + f.errors,
                    service.compositionGeneration("sceneA") > before);
            assertTrue(service.resourceGeneration("sceneA") > beforeResources);
            long logical = service.compositionGeneration("sceneA");
            long logicalResources = service.resourceGeneration("sceneA");
            f.a.editSession().edit("Use another existing drawable", document -> {
                document.root.children.get(1).node.image.resourceName = "other";
                return document;
            });
            f.complete(service);
            assertEquals(1, f.packs.get());
            assertTrue(service.compositionGeneration("sceneA") > logical);
            assertEquals(logicalResources, service.resourceGeneration("sceneA"));
            assertTrue(f.errors.isEmpty()); f.noCandidates();
        }
    }

    @Test public void unrelatedFontAndSkinReimportsDoNotInvalidateScenesOrAuthoring() throws Exception {
        Fixture f = fixture();
        AssetMeta font = importFont(f, "unrelated-font", 0xffffffff);
        AssetMeta skin = importSkin(f, "unrelated-skin", "{}");
        try (var service = f.service()) {
            service.invalidateScene("sceneA"); service.invalidateScene("sceneB"); f.complete(service);
            int packs = f.packs.get(), a = (int) service.generation("sceneA"), b = (int) service.generation("sceneB");
            FileHandle unrelatedImage = f.root.child(f.first.sourceRelPath());
            byte[] imageBytes = unrelatedImage.readBytes();
            assertTrue(unrelatedImage.delete()); // Usage lookup must not rescan unrelated source bytes.
            try {
                new BitmapFontAssetImportService(f.database).reimport(font.id(),
                        fontBundle("unrelated-font-new", 0xff0000ff), f.root);
                var fontImpact = service.invalidateReimportedFont(font.id());
                new Scene2dSkinAssetImportService(f.database).reimport(skin.id(),
                        skinBundle("unrelated-skin-new", "{\"com.badlogic.gdx.graphics.Color\":{\"accent\":{\"r\":1,\"g\":0,\"b\":0,\"a\":1}}}"), f.root);
                var skinImpact = service.invalidateReimportedSkin(skin.sourceRelPath());
                f.complete(service);
                assertTrue(fontImpact.openScreensToReload().isEmpty());
                assertTrue(fontImpact.invalidatedScenes().isEmpty());
                assertTrue(skinImpact.openScreensToReload().isEmpty());
                assertTrue(skinImpact.invalidatedScenes().isEmpty());
                assertEquals(a, service.generation("sceneA")); assertEquals(b, service.generation("sceneB"));
                assertEquals(packs, f.packs.get()); assertTrue(f.errors.toString(), f.errors.isEmpty()); f.noCandidates();
            } finally { unrelatedImage.writeBytes(imageBytes, false); }
        }
    }

    @Test public void fontReimportTargetsOnlyUsingSceneAndDetectsTooltipAndUnsavedReferences() throws Exception {
        Fixture f = fixture();
        AssetMeta font = importFont(f, "used-font", 0xffffffff);
        f.a.editSession().edit("Tooltip Font", document -> {
            document.root.tooltip = new HudTooltipData();
            document.root.tooltip.fontAssetId = font.id();
            return document;
        });
        try (var service = f.service()) {
            service.invalidateScene("sceneA"); service.invalidateScene("sceneB"); f.complete(service);
            assertEquals(2, f.packs.get());
            String originalPath = font.sourceRelPath();
            FileHandle originalPage = f.root.child(originalPath).sibling("page.png");
            byte[] originalPixels = originalPage.readBytes();
            AssetMeta reimported = new BitmapFontAssetImportService(f.database).reimport(font.id(),
                    fontBundle("used-font-new", 0xff0000ff), f.root);
            assertEquals(font.id(), reimported.id());
            assertEquals(originalPath, reimported.sourceRelPath());
            assertFalse(java.util.Arrays.equals(originalPixels, originalPage.readBytes()));
            var impact = service.invalidateReimportedFont(font.id());
            assertEquals(java.util.Set.of("sceneA"), impact.invalidatedScenes());
            assertEquals(java.util.Set.of("hud/a"), impact.openScreensToReload());
            assertEquals(2, service.generation("sceneA")); assertEquals(1, service.generation("sceneB"));
            f.complete(service);
            assertEquals(3, f.packs.get()); assertTrue(f.errors.toString(), f.errors.isEmpty()); f.noCandidates();
        }
    }

    @Test public void sharedHudAndRepeatedFontUsesInvalidateEachSceneOnce() throws Exception {
        Fixture f = fixture(); f.sceneB.defaultHudScreenId = "hud/a";
        AssetMeta font = importFont(f, "shared-font", 0xffffffff);
        f.a.editSession().edit("Repeated Font", document -> {
            document.root.tooltip = new HudTooltipData(); document.root.tooltip.fontAssetId = font.id();
            HudNode label = new HudNode("font-label", HudNodeKind.LABEL);
            label.label = new HudLabelData(); label.label.text = "A"; label.label.fontAssetId = font.id();
            document.root.children.add(HudChild.free(label, new HudFreePlacement()));
            return document;
        });
        try (var service = f.service()) {
            service.invalidateScene("sceneA"); service.invalidateScene("sceneB"); f.complete(service);
            new BitmapFontAssetImportService(f.database).reimport(font.id(),
                    fontBundle("shared-font-new", 0xff0000ff), f.root);
            var impact = service.invalidateReimportedFont(font.id());
            assertEquals(java.util.Set.of("sceneA", "sceneB"), impact.invalidatedScenes());
            assertEquals(2, service.generation("sceneA")); assertEquals(2, service.generation("sceneB"));
            f.complete(service);
            assertEquals(4, f.packs.get()); assertTrue(f.errors.toString(), f.errors.isEmpty()); f.noCandidates();
        }
    }

    @Test public void openUnsavedRemovalOverridesSavedFontButClosedHudUsesPersistedReference() throws Exception {
        Fixture f = fixture(); AssetMeta font = importFont(f, "saved-font", 0xffffffff);
        f.a.editSession().edit("Saved Font", document -> {
            document.root.tooltip = new HudTooltipData(); document.root.tooltip.fontAssetId = font.id();
            return document;
        });
        f.persistence.save(f.root, f.a);
        f.a.editSession().edit("Remove Font In Memory", document -> {
            document.root.tooltip = null; return document;
        });
        try (var service = f.service()) {
            var openImpact = service.invalidateReimportedFont(font.id());
            assertTrue(openImpact.invalidatedScenes().isEmpty());
            assertTrue(openImpact.openScreensToReload().isEmpty());
            assertEquals(0, f.packs.get());

            f.manager.closeNow(f.a.key());
            var closedImpact = service.invalidateReimportedFont(font.id());
            assertEquals(java.util.Set.of("sceneA"), closedImpact.invalidatedScenes());
            assertTrue(closedImpact.openScreensToReload().isEmpty());
            f.complete(service);
            assertEquals(1, f.packs.get()); assertTrue(f.errors.toString(), f.errors.isEmpty()); f.noCandidates();
        }
    }

    @Test public void unassociatedOpenHudReloadsAuthoringWithoutScenePack() throws Exception {
        Fixture f = fixture(); AssetMeta font = importFont(f, "standalone-font", 0xffffffff);
        HudScreenEditorDocument standalone = f.open("standalone");
        standalone.editSession().edit("Font", document -> {
            document.root.tooltip = new HudTooltipData(); document.root.tooltip.fontAssetId = font.id();
            return document;
        });
        try (var service = f.service()) {
            new BitmapFontAssetImportService(f.database).reimport(font.id(),
                    fontBundle("standalone-font-new", 0xff0000ff), f.root);
            var impact = service.invalidateReimportedFont(font.id());
            assertEquals(java.util.Set.of("hud/standalone"), impact.openScreensToReload());
            assertTrue(impact.invalidatedScenes().isEmpty());
            f.complete(service); assertEquals(0, f.packs.get()); f.noCandidates();
        }
    }

    @Test public void logicalSkinReimportReloadsUsedHudWithoutPhysicalPack() throws Exception {
        Fixture f = fixture(); AssetMeta skin = importSkin(f, "used-skin", "{}");
        f.a.editSession().editSkin("Skin", skin.sourceRelPath());
        f.a.editSession().edit("Drawable", document -> {
            HudNode image = new HudNode("skin-drawable", HudNodeKind.IMAGE);
            image.image = new HudImageData(); image.image.source = HudImageSource.DRAWABLE;
            image.image.resourceName = "panel";
            document.root.children.add(HudChild.free(image, new HudFreePlacement()));
            return document;
        });
        try (var service = f.service()) {
            service.invalidateScene("sceneA"); service.invalidateScene("sceneB"); f.complete(service);
            long resources = service.resourceGeneration("sceneA");
            AssetMeta reimported = new Scene2dSkinAssetImportService(f.database).reimport(skin.id(),
                    skinBundle("used-skin-new", "{\"com.badlogic.gdx.graphics.Color\":{\"accent\":{\"r\":1,\"g\":0,\"b\":0,\"a\":1}}}"), f.root);
            assertEquals(skin.id(), reimported.id());
            assertEquals(skin.sourceRelPath(), reimported.sourceRelPath());
            var impact = service.invalidateReimportedSkin(skin.sourceRelPath());
            assertEquals(java.util.Set.of("sceneA"), impact.invalidatedScenes());
            assertEquals(java.util.Set.of("hud/a"), impact.openScreensToReload());
            f.complete(service);
            assertEquals(2, f.packs.get()); // One initial pack per Scene; Skin JSON is logical only.
            assertTrue(service.resourceGeneration("sceneA") > resources);
            assertEquals(1, service.generation("sceneB"));
            assertTrue(f.errors.toString(), f.errors.isEmpty()); f.noCandidates();
        }
    }

    @Test public void associatedButUnusedSkinRefreshesAuthoringOnly() throws Exception {
        Fixture f = fixture(); AssetMeta skin = importSkin(f, "authoring-skin", "{}");
        f.a.editSession().editSkin("Skin", skin.sourceRelPath());
        try (var service = f.service()) {
            service.invalidateScene("sceneA"); f.complete(service);
            int packs = f.packs.get(); long generation = service.generation("sceneA");
            new Scene2dSkinAssetImportService(f.database).reimport(skin.id(),
                    skinBundle("authoring-skin-new", "{\"com.badlogic.gdx.graphics.Color\":{\"accent\":{\"r\":1,\"g\":0,\"b\":0,\"a\":1}}}"), f.root);
            var impact = service.invalidateReimportedSkin(skin.sourceRelPath());
            assertEquals(java.util.Set.of("hud/a"), impact.openScreensToReload());
            assertTrue(impact.invalidatedScenes().isEmpty());
            assertEquals(generation, service.generation("sceneA"));
            f.complete(service); assertEquals(packs, f.packs.get()); f.noCandidates();
        }
    }

    @Test public void failedFontReimportKeepsPublishedHudAndDoesNotInvalidate() throws Exception {
        Fixture f = fixture(); AssetMeta font = importFont(f, "stable-font", 0xffffffff);
        f.a.editSession().edit("Font", document -> {
            document.root.tooltip = new HudTooltipData(); document.root.tooltip.fontAssetId = font.id();
            return document;
        });
        try (var service = f.service()) {
            service.invalidateScene("sceneA"); f.complete(service);
            String descriptor = f.descriptor("sceneA");
            FileHandle invalid = new FileHandle(temporary.newFolder("invalid-font")).child("font.fnt");
            invalid.writeString("not a bitmap font", false);
            assertThrows(RuntimeException.class, () -> new BitmapFontAssetImportService(f.database)
                    .reimport(font.id(), invalid, f.root));
            service.update();
            assertEquals(1, service.generation("sceneA"));
            assertEquals(1, f.packs.get());
            assertEquals(descriptor, f.descriptor("sceneA"));
            assertTrue(f.errors.isEmpty()); f.noCandidates();
        }
    }

    @Test public void unresolvedSavedHudReportsUncertaintyAndRetainsLastPublishedAtlas() throws Exception {
        Fixture f = fixture(); AssetMeta font = importFont(f, "uncertain-font", 0xffffffff);
        f.a.editSession().edit("Font", document -> {
            document.root.tooltip = new HudTooltipData(); document.root.tooltip.fontAssetId = font.id();
            return document;
        });
        f.persistence.save(f.root, f.a);
        try (var service = f.service()) {
            service.invalidateScene("sceneA"); f.complete(service);
            String descriptor = f.descriptor("sceneA");
            f.manager.closeNow(f.a.key());
            FileHandle saved = f.root.child("hud/a.json");
            byte[] valid = saved.readBytes(); saved.writeString("not JSON", false);
            new BitmapFontAssetImportService(f.database).reimport(font.id(),
                    fontBundle("uncertain-font-new", 0xff0000ff), f.root);
            var unresolved = service.invalidateReimportedFont(font.id());
            assertEquals(java.util.Set.of("sceneA"), unresolved.uncertainScenes());
            assertTrue(unresolved.invalidatedScenes().isEmpty());
            assertEquals(1, service.generation("sceneA"));
            assertEquals(descriptor, f.descriptor("sceneA"));
            assertFalse(f.errors.isEmpty());

            saved.writeBytes(valid, false);
            var recovered = service.invalidateReimportedFont(font.id());
            assertEquals(java.util.Set.of("sceneA"), recovered.invalidatedScenes());
            f.complete(service);
            assertEquals(2, f.packs.get()); assertTrue(f.live("sceneA").exists()); f.noCandidates();
        }
    }

    @Test public void staleProjectReimportNotificationDoesNotTouchNewSession() throws Exception {
        Fixture f = fixture(); AssetMeta font = importFont(f, "project-font", 0xffffffff);
        try (var service = f.service()) {
            service.onProjectChanging();
            var oldNotification = service.invalidateReimportedFont(font.id());
            assertTrue(oldNotification.invalidatedScenes().isEmpty());
            f.manager.clear(); f.switchProject(); service.bindProject(f.root);
            assertEquals(0, service.generation("sceneA")); assertEquals(0, f.packs.get());
        }
    }

    @Test public void scenesHaveIndependentKeysOutputsAndRebuildingADoesNotTouchB() throws Exception {
        Fixture f = fixture();
        try (var service = f.service()) {
            service.invalidateScene("sceneA"); service.invalidateScene("sceneB");
            assertEquals(2, f.executors.size());
            f.complete(service);
            assertTrue(f.descriptor("sceneA").contains(f.firstName()));
            assertTrue(f.descriptor("sceneB").contains(f.secondName()));
            byte[] b = f.live("sceneB").child("hud.png").readBytes();
            String descriptorB = f.descriptor("sceneB");
            f.add(f.a, f.secondName());
            assertEquals(2, service.generation("sceneA"));
            assertEquals(1, service.generation("sceneB"));
            f.complete(service);
            assertTrue(f.descriptor("sceneA").contains(f.secondName()));
            assertEquals(descriptorB, f.descriptor("sceneB"));
            assertArrayEquals(b, f.live("sceneB").child("hud.png").readBytes());
            f.noCandidates();
        }
    }

    @Test public void ensureRequestDeduplicatesPendingWorkAndPublishesObservableGeneration()
            throws Exception {
        Fixture f = fixture();
        try (var service = f.service()) {
            service.ensureSceneRequested("sceneA");
            service.ensureSceneRequested("sceneA");
            assertEquals(1, service.generation("sceneA"));
            assertEquals(0L, service.publishedGeneration("sceneA"));

            f.complete(service);
            assertEquals(1L, service.publishedGeneration("sceneA"));

            service.ensureSceneRequested("sceneA");
            assertEquals(1, service.generation("sceneA"));
        }
    }

    @Test public void openingAndActivatingNeverRequestsButInactiveSelectedPublicationDoes() throws Exception {
        Fixture f = fixture();
        try (var service = f.service()) {
            var unrelated = f.open("unrelated");
            f.manager.activate(unrelated.key());
            assertEquals(0, service.generation("sceneA"));
            assertEquals(0, f.executors.size());
            f.add(unrelated, f.firstName());
            assertEquals(0, service.generation("sceneA"));
            f.add(f.a, f.secondName()); // A is inactive, yet selected by Scene A.
            assertEquals(1, service.generation("sceneA"));
            assertEquals(0, service.generation("sceneB"));
            f.manager.activate(f.a.key()); f.manager.activate(unrelated.key());
            assertEquals(1, service.generation("sceneA"));
            f.complete(service);
            assertTrue(f.descriptor("sceneA").contains(f.secondName()));
            assertFalse(f.live("sceneB").exists());
        }
    }

    @Test public void blockedSceneADoesNotBlockIndependentSceneBPreparationOrPublication() throws Exception {
        Fixture f = fixture(); Blocker blocker = new Blocker();
        f.packer = (input, output, name, profile) -> {
            if (output.path().contains("sceneA-")) blocker.blockIgnoringInterrupt();
            AtlasPackingService.packHud(input, output, name, profile);
        };
        try (var service = f.service()) {
            service.invalidateScene("sceneA"); service.invalidateScene("sceneB"); service.update();
            Thread a = f.executors.get(0).startNext();
            try {
                blocker.awaitStarted(); f.executors.get(1).runNext(); service.update();
                assertFalse(f.live("sceneA").exists()); assertTrue(f.live("sceneB").exists());
                blocker.release(); ControlledAtlasExecutor.join(a); service.update();
                assertTrue(f.live("sceneA").exists()); f.noCandidates();
            } finally { blocker.release(); ControlledAtlasExecutor.join(a); }
        }
    }

    @Test public void databaseChangesAfterRequestDoNotChangeTheWorkerOwnedSnapshot() throws Exception {
        Fixture f = fixture();
        try (var service = f.service()) {
            service.invalidateScene("sceneA"); service.update();
            assertTrue(f.database.removeById(f.first.id()));
            f.executors.get(0).runNext();
            assertEquals("Worker resolved its captured Image metadata, not the removed live entry", 1, f.packs.get());
            service.update();
            assertFalse(f.live("sceneA").exists()); assertEquals(1, f.errors.size()); f.noCandidates();
        }
    }

    @Test public void rootFirstOpenOverrideExcludesUnrelatedAndWorkerUsesCapturedEditorAndDatabase() throws Exception {
        Fixture f = fixture();
        f.persistence.save(f.root, f.a); // Disk A contains only the first image.
        f.add(f.a, f.secondName());
        var unrelated = f.open("unrelated");
        f.add(unrelated, "missing__a999"); // Invalid unrelated dependency must never enter the worker.
        AtomicReference<String> candidate = new AtomicReference<>();
        f.packer = (input, output, name, profile) -> {
            assertEquals("controlled-atlas-worker", Thread.currentThread().getName());
            AtlasPackingService.packHud(input, output, name, profile);
            candidate.set(output.child("hud.atlas").readString());
        };
        try (var service = f.service()) {
            service.invalidateScene("sceneA");
            service.update();
            // Edit after launch: the old worker must use its captured two-image snapshot.
            assertTrue(f.a.editSession().undo());
            service.update();
            // Cancelled task doesn't run. The new worker sees the newest immutable one-image state.
            f.executors.get(0).runNext(); f.executors.get(0).runNext(); service.update();
            assertNotNull(candidate.get());
            assertTrue(f.descriptor("sceneA").contains(f.firstName()));
            assertFalse(f.descriptor("sceneA").contains(f.secondName()));
            assertFalse(f.descriptor("sceneA").contains("missing"));
            f.noCandidates();
        }
    }

    @Test public void selectedOpenOverrideActuallyWinsOverSavedContentAndDoesNotSaveOrMutateHistoryBindings() throws Exception {
        Fixture f = fixture();
        f.persistence.save(f.root, f.a);
        byte[] asset = f.root.child("hud/a" + HudScreenAsset.EXTENSION).readBytes();
        byte[] doc = f.root.child(f.a.asset().documentId).readBytes();
        f.add(f.a, f.secondName());
        String atlasId = f.a.asset().atlasId, profileId = f.a.asset().textureProfileId;
        int history = f.a.editSession().historySize();
        try (var service = f.service()) {
            service.invalidateScene("sceneA"); f.complete(service);
            assertTrue(f.descriptor("sceneA").contains(f.firstName()));
            assertTrue(f.descriptor("sceneA").contains(f.secondName()));
            assertEquals(atlasId, f.a.asset().atlasId);
            assertEquals(profileId, f.a.asset().textureProfileId);
            assertEquals(history, f.a.editSession().historySize());
            assertTrue(f.a.isDirty());
            assertArrayEquals(asset, f.root.child("hud/a" + HudScreenAsset.EXTENSION).readBytes());
            assertArrayEquals(doc, f.root.child(f.a.asset().documentId).readBytes());
        }
    }

    @Test public void lateNonCooperativeA1CannotPublishAndA2WinsWithAllOldCandidatesCleaned() throws Exception {
        Fixture f = fixture();
        Blocker blocker = new Blocker();
        List<FileHandle> built = new ArrayList<>();
        f.packer = (input, output, name, profile) -> {
            synchronized (built) { built.add(output); }
            if (f.packs.get() == 1) blocker.blockIgnoringInterrupt();
            AtlasPackingService.packHud(input, output, name, profile);
        };
        try (var service = f.service()) {
            service.invalidateScene("sceneA"); service.update();
            Thread old = f.executors.get(0).startNext();
            try {
                blocker.awaitStarted();
                f.add(f.a, f.secondName()); service.update();
                f.executors.get(0).runNext(); service.update(); // New result is accepted before old finishes.
                assertTrue(f.descriptor("sceneA").contains(f.secondName()));
                String accepted = f.descriptor("sceneA");
                blocker.release(); ControlledAtlasExecutor.join(old); service.update();
                assertEquals(accepted, f.descriptor("sceneA"));
                assertEquals(2, service.generation("sceneA"));
                assertEquals(1, f.publications.get());
                for (var directory : built) assertFalse(directory.exists());
                f.noCandidates();
            } finally { blocker.release(); ControlledAtlasExecutor.join(old); }
        }
    }

    @Test public void unnotifiedSourceContentChangeAtSameTimestampRejectsReadyAndRequestsFreshBytes() throws Exception {
        Fixture f = fixture();
        try (var service = f.service()) {
            service.invalidateScene("sceneA"); service.update(); f.executors.get(0).runNext();
            FileHandle image = f.root.child(f.first.sourceRelPath());
            FileTime mtime = Files.getLastModifiedTime(image.file().toPath());
            png(image, 0xffff0000);
            Files.setLastModifiedTime(image.file().toPath(), mtime);
            service.update();
            assertFalse(f.live("sceneA").exists());
            assertEquals(2, service.generation("sceneA"));
            assertTrue(service.isPending("sceneA"));
            f.complete(service);
            assertTrue(f.live("sceneA").exists());
            assertEquals(1, f.publications.get());
            f.noCandidates();
        }
    }

    @Test public void rejectedCopiedBDoesNotDeduplicateFreshRequestForRestoredA() throws Exception {
        Fixture f = fixture();
        try (var service = f.service()) {
            FileHandle source = f.root.child(f.first.sourceRelPath());
            byte[] original = source.readBytes();
            service.invalidateScene("sceneA"); // Request captures A.
            png(source, 0xffff0000); // Worker will copy B without another invalidation.
            service.update(); f.executors.get(0).runNext(); // Prepared B is ready.
            source.writeBytes(original, false); // Current inputs are A again before apply.

            service.update(); // Reject B, then request A; no stale A request may suppress it.
            assertEquals(2, service.generation("sceneA"));
            assertTrue(service.isPending("sceneA"));
            assertFalse(f.live("sceneA").exists());
            f.complete(service);

            assertEquals(2, f.packs.get());
            assertEquals(1, f.publications.get());
            assertEquals(2L, service.publishedGeneration("sceneA"));
            assertFalse(service.isPending("sceneA"));
            assertTrue(f.descriptor("sceneA").contains(f.firstName()));
            assertTrue(f.errors.isEmpty()); f.noCandidates();
        }
    }

    @Test public void missingPublishedAtlasIsRebuiltAndPublishedEvenWithIdenticalInputs() throws Exception {
        Fixture f = fixture();
        try (var service = f.service()) {
            service.invalidateScene("sceneA"); f.complete(service);
            FileHandle descriptor = f.live("sceneA").child(SceneHudEnvironmentPaths.ATLAS_FILE);
            assertTrue(descriptor.exists());
            assertTrue(descriptor.delete()); // Only the generated descriptor is missing.

            service.invalidateScene("sceneA"); f.complete(service);
            assertEquals(2, f.packs.get());
            assertEquals(2, f.publications.get());
            assertTrue("The rebuilt atlas must be published, not merely marked ready", descriptor.exists());
            assertFalse(new TextureAtlasData(descriptor, f.live("sceneA"), false).getRegions().isEmpty());
            assertFalse(service.isPending("sceneA"));

            service.invalidateScene("sceneA"); f.complete(service);
            assertEquals(2, f.packs.get());
            assertTrue(f.errors.isEmpty()); f.noCandidates();
        }
    }

    @Test public void dependencyBytesArePrivateBeforePackingEvenWhenOriginalChangesMidBuild() throws Exception {
        Fixture f = fixture();
        Blocker blocker = new Blocker();
        AtomicReference<byte[]> packedInput = new AtomicReference<>();
        byte[] original = f.root.child(f.first.sourceRelPath()).readBytes();
        f.packer = (input, output, name, profile) -> {
            if (f.packs.get() == 1) {
                blocker.blockIgnoringInterrupt();
                packedInput.set(input.child("entry-000000.png").readBytes());
            }
            AtlasPackingService.packHud(input, output, name, profile);
        };
        try (var service = f.service()) {
            service.invalidateScene("sceneA"); service.update(); Thread old = f.executors.get(0).startNext();
            try {
                blocker.awaitStarted();
                png(f.root.child(f.first.sourceRelPath()), 0xff0000ff);
                blocker.release(); ControlledAtlasExecutor.join(old); service.update();
                assertArrayEquals(original, packedInput.get());
                assertFalse(f.live("sceneA").exists());
                assertEquals(2, service.generation("sceneA"));
                f.complete(service); assertTrue(f.live("sceneA").exists()); f.noCandidates();
            } finally { blocker.release(); ControlledAtlasExecutor.join(old); }
        }
    }

    @Test public void workerNeverReadsLaterEditorStateAndReadyMismatchResubmitsSameProjectOnly() throws Exception {
        Fixture f = fixture();
        Blocker blocker = new Blocker();
        f.packer = (input, output, name, profile) -> {
            if (f.packs.get() == 1) blocker.blockIgnoringInterrupt();
            AtlasPackingService.packHud(input, output, name, profile);
        };
        try (var service = f.service()) {
            service.invalidateScene("sceneA"); service.update(); Thread old = f.executors.get(0).startNext();
            try {
                blocker.awaitStarted();
                // Metadata changes have a public seam, but simulate a caller that has not notified yet.
                f.sceneA.defaultHudScreenId = "hud/b";
                blocker.release(); ControlledAtlasExecutor.join(old); service.update();
                assertEquals(2, service.generation("sceneA"));
                assertFalse(f.live("sceneA").exists());
                f.complete(service);
                assertTrue(f.descriptor("sceneA").contains(f.secondName()));
                assertFalse(f.descriptor("sceneA").contains(f.firstName()));
                f.noCandidates();
            } finally { blocker.release(); ControlledAtlasExecutor.join(old); }
        }
    }

    @Test public void removingLastRootRetiresWithoutPackingAndReAddingBuildsFresh() throws Exception {
        Fixture f = fixture();
        try (var service = f.service()) {
            service.invalidateScene("sceneA"); f.complete(service);
            assertEquals(1, f.packs.get());
            f.sceneA.defaultHudScreenId = null; service.invalidateScene("sceneA"); f.complete(service);
            assertFalse(f.live("sceneA").exists());
            assertEquals("No white-only pack", 1, f.packs.get());
            assertFalse(service.isPending("sceneA"));
            f.sceneA.defaultHudScreenId = "hud/a"; service.invalidateScene("sceneA"); f.complete(service);
            assertTrue(f.live("sceneA").exists());
            assertEquals(2, f.packs.get()); assertEquals(3, service.generation("sceneA")); f.noCandidates();
        }
    }

    @Test public void staleEmptyStateCannotRetireNewestNonEmptyOutput() throws Exception {
        Fixture f = fixture();
        try (var service = f.service()) {
            service.invalidateScene("sceneA"); f.complete(service);
            f.sceneA.defaultHudScreenId = null; service.invalidateScene("sceneA"); service.update(); f.executors.get(0).runNext();
            f.sceneA.defaultHudScreenId = "hud/a"; // No invalidation: apply must still detect mismatch.
            service.update();
            assertTrue(f.live("sceneA").exists());
            assertEquals(1, f.packs.get());
            assertEquals(1L, service.publishedGeneration("sceneA"));
            f.complete(service); f.noCandidates();
        }
    }

    @Test public void readyProjectAIsDisposedWithoutResubmitAndGenuineProjectBRequestWorks() throws Exception {
        Fixture f = fixture(); FileHandle projectA = f.root;
        try (var service = f.service()) {
            service.invalidateScene("sceneA"); service.update(); f.executors.get(0).runNext();
            assertFalse(f.live("sceneA").exists());
            service.onProjectChanging(); f.manager.clear(); f.switchProject(); service.bindProject(f.root); service.update();
            assertEquals(0, service.generation("sceneA")); assertEquals(0, f.publications.get());
            noCandidates(projectA); assertFalse(SceneHudEnvironmentPaths.liveDirectory(projectA, "sceneA").exists());
            f.a = f.open("a"); f.add(f.a, f.firstName());
            assertEquals(1, service.generation("sceneA")); f.complete(service);
            assertTrue(f.live("sceneA").exists()); f.noCandidates();
        }
    }

    @Test public void lateOldProjectWorkerCannotPublishOrStampResubmitIntoNewProject() throws Exception {
        Fixture f = fixture(); FileHandle projectA = f.root; Blocker blocker = new Blocker();
        f.packer = (input, output, name, profile) -> {
            if (f.packs.get() == 1) blocker.blockIgnoringInterrupt();
            AtlasPackingService.packHud(input, output, name, profile);
        };
        try (var service = f.service()) {
            service.invalidateScene("sceneA"); service.update(); Thread old = f.executors.get(0).startNext();
            try {
                blocker.awaitStarted(); service.onProjectChanging(); f.manager.clear(); f.switchProject(); service.bindProject(f.root);
                blocker.release(); ControlledAtlasExecutor.join(old); service.update();
                assertEquals(0, service.generation("sceneA")); assertEquals(0, f.publications.get());
                assertFalse(f.live("sceneA").exists()); noCandidates(projectA);
                f.a = f.open("a"); f.add(f.a, f.firstName()); f.complete(service);
                assertTrue(f.live("sceneA").exists()); f.noCandidates();
            } finally { blocker.release(); ControlledAtlasExecutor.join(old); }
        }
    }

    @Test public void sameCanonicalProjectRootRebindStillInvalidatesPreviousEpoch() throws Exception {
        Fixture f = fixture();
        try (var service = f.service()) {
            service.invalidateScene("sceneA"); service.update(); f.executors.get(0).runNext();
            service.bindProject(new FileHandle(f.root.file().getCanonicalPath())); service.update();
            assertEquals(0, service.generation("sceneA")); assertFalse(f.live("sceneA").exists()); f.noCandidates();
            service.invalidateScene("sceneA"); f.complete(service); assertTrue(f.live("sceneA").exists());
        }
    }

    @Test public void canonicalProjectIdentityIsResolvedOncePerUnchangedSession() throws Exception {
        Fixture f = fixture();
        AtomicInteger canonicalizations = new AtomicInteger();
        FileHandle countedRoot = new FileHandle(new CountingCanonicalFile(f.root.file(), canonicalizations));
        try (var service = f.service(countedRoot)) {
            assertEquals(1, canonicalizations.get());
            service.ensureSceneRequested("sceneA");
            service.publishedGeneration("sceneA");
            service.update();
            service.invalidateHudScreen("hud/a");
            service.update();
            assertEquals("The bound identity must be reused by update/invalidation calls", 1, canonicalizations.get());
        }
    }

    @Test public void failedProjectIdentityResolutionLeavesNoPreviousSessionActive() throws Exception {
        Fixture f = fixture();
        try (var service = f.service()) {
            service.invalidateScene("sceneA");
            f.complete(service);
            assertTrue(f.live("sceneA").exists());

            FileHandle failingRoot = new FileHandle(new FailingCanonicalFile(f.root.file()));
            try {
                service.bindProject(failingRoot);
                fail("Expected canonical project identity resolution to fail");
            } catch (IllegalStateException expected) {
                assertTrue(expected.getMessage().contains("Unable to identify Scene HUD project"));
            }

            service.invalidateScene("sceneA");
            service.update();
            assertEquals(0, service.generation("sceneA"));
            assertFalse(service.isPending("sceneA"));
        }
    }

    @Test public void dependencyClosureFailurePreservesOldLive() throws Exception {
        Fixture f = fixture();
        try (var service = f.service()) {
            service.invalidateScene("sceneA"); f.complete(service); String old = f.descriptor("sceneA");
            f.add(f.a, "missing__a999"); f.complete(service);
            assertEquals(old, f.descriptor("sceneA")); assertEquals(1, f.errors.size()); f.noCandidates();
        }
    }

    @Test public void projectionCollisionPreservesOldLive() throws Exception {
        Fixture f = fixture();
        try (var service = f.service()) {
            service.invalidateScene("sceneA"); f.complete(service); String old = f.descriptor("sceneA");
            FileHandle skin = f.root.child("skins/collision.json"); skin.parent().mkdirs(); skin.writeString("{}", false);
            png(skin.parent().child("page.png"), 0xff00ffff);
            skin.parent().child("collision.atlas").writeString("page.png\nsize: 4, 4\nformat: RGBA8888\nfilter: Linear, Linear\nrepeat: none\n"
                    + "legitimate_0\n  bounds: 0, 0, 1, 1\n  index: -1\nlegitimate\n  bounds: 1, 0, 1, 1\n  index: 0\n", false);
            HudScreenAsset asset = new HudScreenAsset();
            asset.documentId = "hud/collision.json";
            asset.skinId = "skins/collision.json";
            HudDocumentV1 document = new HudDocumentV1(new HudNode("root", HudNodeKind.GROUP));
            HudLayoutAuthoring.addImage(document, "root", HudLayoutAuthoring.nextImageId(document), "drawable");
            document.root.children.get(0).node.image.source = games.pixscape.runtime.hud.document.HudImageSource.DRAWABLE;
            f.manager.openHudScreen(new HudScreenEditorDocument("hud/collision", "collision", asset, document));
            f.sceneA.defaultHudScreenId = "hud/collision";
            service.invalidateScene("sceneA"); f.complete(service);
            assertEquals(old, f.descriptor("sceneA")); assertEquals(1, f.errors.size());
            assertTrue(rootCause(f.errors.get(0)).getMessage().contains("Skin registration key collision")); f.noCandidates();
        }
    }

    @Test public void materializationFailurePreservesOldLive() throws Exception {
        Fixture f = fixture();
        try (var service = f.service()) {
            service.invalidateScene("sceneA"); f.complete(service); String old = f.descriptor("sceneA");
            f.root.child(f.first.sourceRelPath()).writeString("not an image", false);
            service.invalidateScene("sceneA"); f.complete(service);
            assertEquals(old, f.descriptor("sceneA")); assertEquals(1, f.errors.size()); f.noCandidates();
        }
    }

    @Test public void builderFailurePreservesOldLive() throws Exception {
        Fixture f = fixture();
        try (var service = f.service()) {
            service.invalidateScene("sceneA"); f.complete(service); String old = f.descriptor("sceneA");
            assertEquals(1L, service.publishedGeneration("sceneA"));
            f.failBuilder = true;
            png(f.root.child(f.first.sourceRelPath()), 0xff0000ff);
            service.invalidateScene("sceneA"); f.complete(service);
            assertEquals(old, f.descriptor("sceneA")); assertEquals(1, f.errors.size()); f.noCandidates();
            assertEquals(1L, service.publishedGeneration("sceneA"));
        }
    }

    @Test public void postMovePublicationFailureRestoresOldCompleteLive() throws Exception {
        Fixture f = fixture();
        try (var service = f.service()) {
            service.invalidateScene("sceneA"); f.complete(service); String old = f.descriptor("sceneA");
            byte[] oldPage = f.live("sceneA").child("hud.png").readBytes();
            f.failPublication = true; f.add(f.a, f.secondName()); f.complete(service);
            assertEquals(old, f.descriptor("sceneA")); assertArrayEquals(oldPage, f.live("sceneA").child("hud.png").readBytes());
            assertEquals(1, f.errors.size()); f.noCandidates();
        }
    }

    @Test public void shutdownDiscardsQueuedAndReadyDetachesWithoutClosingDocumentsOrDeletingLive() throws Exception {
        Fixture f = fixture();
        var service = f.service();
        service.invalidateScene("sceneA"); f.complete(service); String old = f.descriptor("sceneA");
        f.add(f.a, f.secondName()); service.invalidateScene("sceneB"); service.update(); f.executors.get(0).runNext();
        service.close(); service.close();
        assertEquals(old, f.descriptor("sceneA")); assertFalse(f.live("sceneB").exists()); f.noCandidates();
        for (var executor : f.executors) assertEquals(0, executor.queued());
        int history = f.a.editSession().historySize(); f.add(f.a, f.secondName());
        assertEquals(history + 1, f.a.editSession().historySize());
        assertEquals(2, f.manager.documents().size()); assertEquals(0, service.generation("sceneA"));
        f.open("after-close"); service.invalidateScene("sceneA"); service.update(); assertEquals(0, service.generation("sceneA"));
    }

    @Test public void shutdownLateWorkerEventuallyCleansAllOwnedDirectoriesAndNeverPublishes() throws Exception {
        Fixture f = fixture(); Blocker blocker = new Blocker();
        f.packer = (input, output, name, profile) -> { blocker.blockIgnoringInterrupt(); AtlasPackingService.packHud(input, output, name, profile); };
        var service = f.service(); service.invalidateScene("sceneA"); service.update(); Thread old = f.executors.get(0).startNext();
        try {
            blocker.awaitStarted(); service.close(); blocker.release(); ControlledAtlasExecutor.join(old);
            assertFalse(f.live("sceneA").exists()); assertEquals(0, f.publications.get()); f.noCandidates();
        } finally { service.close(); blocker.release(); ControlledAtlasExecutor.join(old); }
    }

    @Test public void dirtySelectedCloseInvalidatesSavedFallbackButCleanUnrelatedCloseDoesNot() throws Exception {
        Fixture f = fixture(); f.persistence.save(f.root, f.a);
        try (var service = f.service()) {
            f.add(f.a, f.secondName()); f.complete(service); assertTrue(f.descriptor("sceneA").contains(f.secondName()));
            f.manager.closeNow(f.a.key()); f.complete(service);
            assertFalse(f.descriptor("sceneA").contains(f.secondName()));
            assertTrue(f.descriptor("sceneA").contains(f.firstName()));
            var unrelated = f.open("unrelated"); f.manager.closeNow(unrelated.key());
            assertEquals(2, service.generation("sceneA")); f.noCandidates();
        }
    }

    @Test public void serviceRejectsOffStudioThreadInvalidationAndUnsafeTags() throws Exception {
        Fixture f = fixture();
        try (var service = f.service()) {
            AtomicReference<Throwable> error = new AtomicReference<>();
            Thread caller = new Thread(() -> { try { service.invalidateScene("sceneA"); } catch (Throwable failure) { error.set(failure); } });
            caller.start(); ControlledAtlasExecutor.join(caller);
            assertTrue(error.get().getMessage().contains("Studio thread"));
            service.invalidateScene("../sceneA");
            assertEquals(1, f.errors.size()); assertEquals(0, f.executors.size());
        }
    }

    @Test public void ambiguousCaseInsensitiveSceneTagsNeverReachPublication() throws Exception {
        Fixture f = fixture(); f.cfg.getScenesMap().put("duplicate", new SceneMeta("duplicate", "SCENEA.json"));
        try (var service = f.service()) {
            service.invalidateScene("sceneA");
            assertEquals(1, f.errors.size()); assertEquals(0, f.executors.size()); assertFalse(f.live("sceneA").exists());
        }
    }

    private Fixture fixture() throws Exception {
        Fixture f = new Fixture(new FileHandle(temporary.newFolder()));
        f.first = image(f.root, f.database, "first"); f.second = image(f.root, f.database, "second");
        f.a = f.open("a"); f.b = f.open("b"); f.add(f.a, f.firstName()); f.add(f.b, f.secondName());
        return f;
    }
    private AssetMeta importFont(Fixture f, String folder, int color) throws Exception {
        return new BitmapFontAssetImportService(f.database).importNew(fontBundle(folder, color), f.root);
    }
    private FileHandle fontBundle(String folder, int color) throws Exception {
        FileHandle external = new FileHandle(temporary.newFolder(folder));
        png(external.child("page.png"), color);
        FileHandle descriptor = external.child("font.fnt");
        descriptor.writeString("info face=\"test\" size=16 bold=0 italic=0 charset=\"\" unicode=0 stretchH=100 smooth=1 aa=1 padding=0,0,0,0 spacing=0,0\n"
                + "common lineHeight=16 base=12 scaleW=4 scaleH=4 pages=1 packed=0\n"
                + "page id=0 file=\"page.png\"\nchars count=1\n"
                + "char id=65 x=0 y=0 width=1 height=1 xoffset=0 yoffset=0 xadvance=1 page=0 chnl=0\n"
                + "kernings count=0\n", false);
        return descriptor;
    }
    private AssetMeta importSkin(Fixture f, String folder, String json) throws Exception {
        return new Scene2dSkinAssetImportService(f.database).importNew(skinBundle(folder, json), f.root);
    }
    private FileHandle skinBundle(String folder, String json) throws Exception {
        FileHandle external = new FileHandle(temporary.newFolder(folder));
        FileHandle descriptor = external.child("game.json");
        descriptor.writeString(json, false);
        return descriptor;
    }
    private static AssetMeta image(FileHandle root, AssetMetaDatabase database, String name) throws Exception {
        int id = database.nextId();
        AssetMeta meta = database.registerIfAbsent(AssetType.IMAGE, "images/" + name,
                "orig/images/" + name + "__a" + id + ".png", AssetMeta.AssetScope.USER);
        png(root.child(meta.sourceRelPath()), 0xff00ffff); return meta;
    }
    private static void png(FileHandle path, int color) throws Exception {
        path.parent().mkdirs(); BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < 4; x++) for (int y = 0; y < 4; y++) image.setRGB(x, y, color);
        ImageIO.write(image, "png", path.file());
    }
    private static Throwable rootCause(Throwable failure) { while (failure.getCause() != null) failure = failure.getCause(); return failure; }
    private static void noCandidates(FileHandle project) throws Exception {
        if (!project.child("atlases").exists()) return;
        try (var paths = Files.walk(project.child("atlases").file().toPath())) {
            assertFalse(paths.anyMatch(path -> !path.getFileName().toString().equals(".tmp")
                    && path.toString().contains(".tmp") || path.getFileName().toString().startsWith(".backup-")
                    || path.getFileName().toString().startsWith(".retired-")));
        }
    }
    private final class Fixture {
        FileHandle root;
        final ProjectConfig cfg = new ProjectConfig();
        final SceneMeta sceneA = new SceneMeta("A", "sceneA.json"), sceneB = new SceneMeta("B", "sceneB.json");
        final EditorDocumentManager manager = new EditorDocumentManager();
        final AssetMetaDatabase database = new AssetMetaDatabase();
        final HudDocumentPersistenceService persistence = new HudDocumentPersistenceService();
        final List<ControlledAtlasExecutor> executors = new ArrayList<>();
        final List<RuntimeException> errors = new ArrayList<>();
        final AtomicInteger packs = new AtomicInteger(), publications = new AtomicInteger();
        AssetMeta first, second;
        HudScreenEditorDocument a, b;
        SceneHudAtlasBuilder.Packer packer = AtlasPackingService::packHud;
        boolean failBuilder, failPublication;
        Fixture(FileHandle root) {
            this.root = root; sceneA.defaultHudScreenId = "hud/a"; sceneB.defaultHudScreenId = "hud/b";
            cfg.getScenesMap().put("A", sceneA); cfg.getScenesMap().put("B", sceneB);
        }
        HudScreenEditorDocument open(String name) {
            HudScreenAsset asset = new HudScreenAsset();
            asset.documentId = "hud/" + name + ".json";
            return manager.openHudScreen(new HudScreenEditorDocument("hud/" + name, name, asset,
                    new HudDocumentV1(new HudNode("root", HudNodeKind.GROUP))));
        }
        void add(HudScreenEditorDocument document, String resource) {
            document.editSession().edit("Add Image", candidate -> {
                HudLayoutAuthoring.addImage(candidate, "root", HudLayoutAuthoring.nextImageId(candidate), resource); return candidate;
            });
        }
        String firstName() { return new FileHandle(first.sourceRelPath()).nameWithoutExtension(); }
        String secondName() { return new FileHandle(second.sourceRelPath()).nameWithoutExtension(); }
        SceneHudRuntimePreparationService service() { return service(root); }
        SceneHudRuntimePreparationService service(FileHandle boundRoot) {
            var builder = new SceneHudAtlasBuilder((input, output, name, profile) -> {
                packs.incrementAndGet(); if (failBuilder) throw new IllegalStateException("injected builder failure");
                packer.pack(input, output, name, profile);
            }, SceneHudAtlasBuilder::writeDescriptor);
            var service = new SceneHudRuntimePreparationService(() -> root, () -> cfg, () -> database, manager, persistence,
                    builder, (candidate, live) -> {
                        publications.incrementAndGet(); AtomicDirectoryPublication.move(candidate, live);
                        if (failPublication) throw new IllegalStateException("injected post-move publication failure");
                    }, runner -> {
                        var executor = new ControlledAtlasExecutor(); executors.add(executor);
                        return new AsyncAtlasRepackCoordinator<>(runner, executor, () -> 0L, 0L);
                    });
            service.setErrorHandler(errors::add); service.bindProject(boundRoot); return service;
        }
        void complete(SceneHudRuntimePreparationService service) throws Exception {
            service.update(); for (var executor : List.copyOf(executors)) while (executor.queued() > 0) executor.runNext(); service.update();
        }
        FileHandle live(String tag) { return SceneHudEnvironmentPaths.liveDirectory(root, tag); }
        String descriptor(String tag) { return live(tag).child("hud.atlas").readString(); }
        void noCandidates() throws Exception { SceneHudRuntimePreparationServiceTest.noCandidates(root); }
        void switchProject() throws Exception {
            root = new FileHandle(temporary.newFolder()); png(root.child(first.sourceRelPath()), 0xffff0000); png(root.child(second.sourceRelPath()), 0xff0000ff);
        }
    }
    private static final class CountingCanonicalFile extends File {
        private final AtomicInteger canonicalizations;
        CountingCanonicalFile(File delegate, AtomicInteger canonicalizations) {
            super(delegate.getPath());
            this.canonicalizations = canonicalizations;
        }
        @Override public String getCanonicalPath() throws IOException {
            canonicalizations.incrementAndGet();
            return super.getCanonicalPath();
        }
    }
    private static final class FailingCanonicalFile extends File {
        FailingCanonicalFile(File delegate) { super(delegate.getPath()); }
        @Override public String getCanonicalPath() throws IOException { throw new IOException("injected canonicalization failure"); }
    }
    private static final class Blocker {
        final CountDownLatch started = new CountDownLatch(1), finished = new CountDownLatch(1);
        void blockIgnoringInterrupt() {
            started.countDown(); boolean done = false;
            while (!done) try { done = finished.await(10, TimeUnit.SECONDS); if (!done) throw new AssertionError("Unreleased worker"); }
            catch (InterruptedException ignored) { /* Deliberately non-cooperative late completion. */ }
            Thread.interrupted();
        }
        void awaitStarted() throws InterruptedException { assertTrue(started.await(10, TimeUnit.SECONDS)); }
        void release() { finished.countDown(); }
    }
}
