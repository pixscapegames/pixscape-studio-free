package games.pixscape.studio.service;

import com.badlogic.gdx.files.FileHandle;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import games.pixscape.studio.service.runtimeavailability.SceneHudRuntimePreparationService;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BitmapFontReimportFlowContractTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Test public void projectGuardAcceptsOnlyTheCapturedProjectDirectory() throws Exception {
        FileHandle original = new FileHandle(temporary.newFolder("original"));
        FileHandle other = new FileHandle(temporary.newFolder("other"));

        SceneService.requireSameProject(original, original.child("."));
        org.junit.Assert.assertThrows(IllegalStateException.class,
                () -> SceneService.requireSameProject(original, other));
    }

    @Test public void fontContextActionUsesExplicitReimportWithCapturedAssetAndProject() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/games/pixscape/studio/ui/asset/AssetsThumbsView.java"),
                StandardCharsets.UTF_8);
        String menu = methodBody(source, "private void showAssetContextMenu(");
        String chooser = methodBody(source, "private void showReimportFontChooser(");

        assertTrue(menu.contains("node.kind == AssetNode.Kind.SKIN || node.kind == AssetNode.Kind.FONT"));
        assertTrue(menu.contains("showReimportFontChooser(node)"));
        assertTrue(chooser.contains("int assetId = node.assetId"));
        assertTrue(chooser.contains("String sourceRelPath ="));
        assertTrue(chooser.contains("reimportBitmapFontAsset("));
        assertTrue(chooser.contains("assetId, sourceRelPath, projectDir, files.first()"));
        assertFalse(chooser.contains("importNew("));
    }

    @Test public void skinContextActionAlsoCapturesAssetSourceAndProject() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/games/pixscape/studio/ui/asset/AssetsThumbsView.java"),
                StandardCharsets.UTF_8);
        String chooser = methodBody(source, "private void showReimportSkinChooser(");
        assertTrue(chooser.contains("int assetId = node.assetId"));
        assertTrue(chooser.contains("String sourceRelPath ="));
        assertTrue(chooser.contains("reimportScene2dSkinAsset("));
        assertTrue(chooser.contains("assetId, sourceRelPath, projectDir, files.first()"));
    }

    @Test public void successfulReimportPublishesCatalogThenTargetsHudUsage() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/games/pixscape/studio/service/SceneService.java"),
                StandardCharsets.UTF_8);
        String body = methodBody(source, "public void reimportBitmapFontAsset(");

        assertOrdered(body,
                "requireSameProject(expectedProjectDir, projectDir)",
                ".reimport(assetId, descriptor, projectDir)",
                "assetMetaDatabase.save(assetsFile)",
                "canvas.publishAssetMetaDatabase(assetMetaDatabase)",
                ".invalidateReimportedFont(assetId)",
                "refreshAssetsPanel()");
        assertTrue(body.contains("original.type() != AssetType.FONT"));
        assertTrue(body.contains("!Objects.equals(original.sourceRelPath(), expectedSourceRelPath)"));
    }

    @Test public void skinReimportChecksCapturedContextBeforePublishingOrInvalidating() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/games/pixscape/studio/service/SceneService.java"),
                StandardCharsets.UTF_8);
        String body = methodBody(source, "public void reimportScene2dSkinAsset(");
        assertOrdered(body,
                "requireSameProject(expectedProjectDir, projectDir)",
                "original.type() != AssetType.SKIN",
                ".reimport(assetId, descriptor, projectDir)",
                "assetMetaDatabase.save(assetsFile)",
                "canvas.publishAssetMetaDatabase(assetMetaDatabase)",
                ".invalidateReimportedSkin(original.sourceRelPath())");
    }

    @Test public void authoringReloadIsLimitedToTheActiveUsedHud() {
        var impact = new SceneHudRuntimePreparationService.ReimportImpact(
                Set.of("hud/used"), Set.of("hud/uncertain"), Set.of("sceneA"), Set.of());
        AtomicInteger reloads = new AtomicInteger(), stale = new AtomicInteger();
        Runnable reload = reloads::incrementAndGet, markStale = stale::incrementAndGet;

        SceneService.refreshHudAfterReimport(impact, "hud/unrelated", reload, markStale);
        assertTrue(reloads.get() == 0 && stale.get() == 0);
        SceneService.refreshHudAfterReimport(impact, "hud/used", reload, markStale);
        assertTrue(reloads.get() == 1 && stale.get() == 0);
        SceneService.refreshHudAfterReimport(impact, "hud/uncertain", reload, markStale);
        assertTrue(reloads.get() == 1 && stale.get() == 1);
    }

    private static String methodBody(String source, String signature) {
        int signatureIndex = source.indexOf(signature);
        if (signatureIndex < 0) throw new AssertionError("Method signature not found: " + signature);
        int bodyStart = source.indexOf('{', signatureIndex);
        int depth = 0;
        for (int index = bodyStart; index < source.length(); index++) {
            char character = source.charAt(index);
            if (character == '{') depth++;
            else if (character == '}' && --depth == 0) {
                return source.substring(bodyStart + 1, index);
            }
        }
        throw new AssertionError("Method body end not found: " + signature);
    }

    private static void assertOrdered(String body, String... fragments) {
        int previous = -1;
        for (String fragment : fragments) {
            int current = body.indexOf(fragment);
            assertTrue("Missing or out-of-order fragment: " + fragment,
                    current > previous);
            previous = current;
        }
    }
}
