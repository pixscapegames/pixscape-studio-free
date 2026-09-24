package games.pixscape.studio.service.runtimeavailability;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Json;
import games.pixscape.runtime.hud.document.HudDocumentCodec;
import games.pixscape.studio.io.AtomicDirectoryPublication;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;
import java.util.TreeSet;

/** Logical closure plus source bytes, not editor membership or filesystem timestamps. */
final class SceneHudBuildStamp {
    private SceneHudBuildStamp() {}

    static String of(FileHandle project, SceneHudDependencyClosure closure) {
        try {
            MessageDigest hash = MessageDigest.getInstance("SHA-256");
            add(hash, "scene-hud-v1");
            for (var selected : closure.selectedHudScreens()) {
                add(hash, selected.screenId());
                add(hash, new Json().toJson(selected.asset()));
                add(hash, new HudDocumentCodec().write(selected.document()));
            }
            for (var image : closure.imageDependencies()) add(hash, image.toString());
            for (var skin : closure.skinDependencies()) add(hash, skin.toString());
            for (var font : closure.fontDependencies()) add(hash, font.toString());
            for (String relative : files(closure)) {
                add(hash, relative);
                add(hash, Files.readAllBytes(source(project, relative)));
            }
            return HexFormat.of().formatHex(hash.digest());
        } catch (IOException | NoSuchAlgorithmException failure) {
            throw new IllegalStateException("Unable to stamp Scene HUD dependencies", failure);
        }
    }

    /** Recollect against this private source tree before projection: all later readers use these bytes. */
    static void copySources(FileHandle project, SceneHudDependencyClosure closure, FileHandle snapshot) {
        try {
            Files.createDirectories(snapshot.file().toPath());
            for (String relative : files(closure)) {
                Path source = source(project, relative);
                Path target = snapshot.file().toPath().toAbsolutePath().normalize().resolve(relative).normalize();
                if (!target.startsWith(snapshot.file().toPath().toAbsolutePath().normalize()))
                    throw new IllegalArgumentException("Scene HUD snapshot source escapes its directory: " + relative);
                Files.createDirectories(target.getParent());
                Files.copy(source, target);
            }
        } catch (IOException failure) {
            throw new IllegalStateException("Unable to snapshot Scene HUD dependency sources", failure);
        }
    }

    private static Set<String> files(SceneHudDependencyClosure closure) {
        Set<String> paths = new TreeSet<>();
        for (var image : closure.imageDependencies()) paths.add(image.sourceRelPath());
        for (var skin : closure.skinDependencies()) for (var file : skin.files()) paths.add(file.projectRelativePath());
        for (var font : closure.fontDependencies()) for (var file : font.files()) paths.add(file.projectRelativePath());
        return paths;
    }

    static Path source(FileHandle project, String relative) throws IOException {
        Path root = project.file().toPath().toAbsolutePath().normalize();
        Path path = root.resolve(relative).normalize();
        AtomicDirectoryPublication.rejectSymlinks(path);
        if (!path.startsWith(root) || !path.toRealPath().startsWith(root.toRealPath()) || !Files.isRegularFile(path))
            throw new IllegalArgumentException("Unsafe Scene HUD dependency source: " + relative);
        return path;
    }

    private static void add(MessageDigest hash, String value) { add(hash, value.getBytes(StandardCharsets.UTF_8)); }
    private static void add(MessageDigest hash, byte[] bytes) {
        hash.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        hash.update(bytes);
    }
}
