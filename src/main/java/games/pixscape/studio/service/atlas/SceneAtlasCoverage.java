package games.pixscape.studio.service.atlas;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/** Checks that a published atlas contains every current scene input, including animation frames. */
public final class SceneAtlasCoverage {
    private SceneAtlasCoverage() {
    }

    public static boolean coversInput(FileHandle inputDir, FileHandle atlasFile) {
        try {
            return missingInput(inputDir, atlasFile) == null;
        } catch (RuntimeException invalidAtlas) {
            return false;
        }
    }

    public static void requireComplete(FileHandle inputDir, FileHandle atlasFile) {
        String missing = missingInput(inputDir, atlasFile);
        if (missing != null) {
            throw new IllegalStateException("Scene atlas omits input region '" + missing
                    + "': " + atlasFile.path());
        }
    }

    private static String missingInput(FileHandle inputDir, FileHandle atlasFile) {
        if (inputDir == null || !inputDir.exists() || atlasFile == null || !atlasFile.exists()) {
            return "<atlas or input directory missing>";
        }
        TextureAtlasData data = new TextureAtlasData(atlasFile, atlasFile.parent(), false);
        Set<String> packed = new HashSet<>();
        for (TextureAtlasData.Region region : data.getRegions()) {
            packed.add(key(region.name, region.index));
        }
        return missingInDirectory(inputDir, "", packed);
    }

    private static String missingInDirectory(FileHandle directory, String prefix, Set<String> packed) {
        for (FileHandle child : directory.list()) {
            if (child.isDirectory()) {
                String missing = missingInDirectory(child, prefix + child.name() + '/', packed);
                if (missing != null) return missing;
            } else if (child.extension().equalsIgnoreCase("png")) {
                String name = child.nameWithoutExtension();
                int index = -1;
                int separator = name.lastIndexOf('_');
                if (separator >= 0 && separator + 1 < name.length()) {
                    String suffix = name.substring(separator + 1);
                    if (suffix.matches("[0-9]+")) {
                        index = Integer.parseInt(suffix);
                        name = name.substring(0, separator);
                    }
                }
                if (!packed.contains(key(prefix + name, index)) && !isFullyTransparent(child)) {
                    return prefix + child.name();
                }
            }
        }
        return null;
    }

    private static String key(String name, int index) {
        return name + '#' + index;
    }

    private static boolean isFullyTransparent(FileHandle imageFile) {
        try {
            BufferedImage image = ImageIO.read(imageFile.file());
            if (image == null || !image.getColorModel().hasAlpha()) return false;
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    if ((image.getRGB(x, y) >>> 24) != 0) return false;
                }
            }
            return true;
        } catch (IOException unreadable) {
            return false;
        }
    }
}
