package games.pixscape.studio.service.runtimeavailability;

import com.badlogic.gdx.files.FileHandle;
import games.pixscape.runtime.hud.HudTextureProfile;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

/** Physical pack identity. Authored layout, Skin styles, and provenance do not affect these inputs. */
final class SceneHudPackFingerprint {
    private SceneHudPackFingerprint() {}

    static String of(FileHandle project, SceneHudPackInputPlan plan) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            HudTextureProfile profile = HudTextureProfile.forId(HudTextureProfile.DEFAULT_ID);
            add(digest, "scene-hud-physical-v1;fixed-page;duplicate-padding;edge-padding;combine-subdirectories");
            add(digest, profile.id());
            add(digest, profile.pageWidth()); add(digest, profile.pageHeight());
            add(digest, profile.minFilter().name()); add(digest, profile.magFilter().name());
            add(digest, profile.uWrap().name()); add(digest, profile.vWrap().name());
            add(digest, profile.useMipMaps() ? 1 : 0); add(digest, profile.outputFormat().name());
            Map<String, byte[]> sourceBytes = new HashMap<>();
            // The materializer also uses this canonical order for its entry names.
            for (var entry : plan.entries()) {
                add(digest, entry.key().name()); add(digest, entry.key().index());
                add(digest, entry.materialKind().name());
                if (entry.sourcePath() != null) {
                    byte[] bytes = sourceBytes.get(entry.sourcePath());
                    if (bytes == null) {
                        bytes = Files.readAllBytes(SceneHudBuildStamp.source(project, entry.sourcePath()));
                        sourceBytes.put(entry.sourcePath(), bytes);
                    }
                    add(digest, bytes);
                }
                var region = entry.atlasRegion();
                if (region != null) {
                    add(digest, region.sourcePageName()); add(digest, region.pagePremultipliedAlpha() ? 1 : 0);
                    add(digest, region.left()); add(digest, region.top());
                    add(digest, region.width()); add(digest, region.height());
                    add(digest, region.degrees()); add(digest, region.rotate() ? 1 : 0);
                    add(digest, region.flip() ? 1 : 0);
                    add(digest, region.originalWidth()); add(digest, region.originalHeight());
                    add(digest, Float.floatToIntBits(region.offsetX()));
                    add(digest, Float.floatToIntBits(region.offsetY()));
                    for (var value : new TreeMap<>(region.values()).entrySet()) {
                        add(digest, value.getKey());
                        add(digest, value.getValue().size());
                        for (int number : value.getValue()) add(digest, number);
                    }
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException failure) {
            throw new IllegalStateException("Unable to fingerprint Scene HUD pack inputs", failure);
        }
    }

    private static void add(MessageDigest digest, String value) { add(digest, value.getBytes(StandardCharsets.UTF_8)); }
    private static void add(MessageDigest digest, int value) {
        add(digest, ByteBuffer.allocate(Integer.BYTES).putInt(value).array());
    }
    private static void add(MessageDigest digest, byte[] value) {
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(value.length).array());
        digest.update(value);
    }
}
