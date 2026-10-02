package games.pixscape.studio.service.atlas;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Validates a scene atlas against a stable snapshot of its input files. */
public final class SceneAtlasCoverage {
    private SceneAtlasCoverage() {}

    public record FileStamp(String path, long length, long modified) {
        static FileStamp of(FileHandle file) {
            return file.exists() && !file.isDirectory()
                    ? new FileStamp(file.path(), file.length(), file.lastModified()) : null;
        }
    }

    public record InputFile(String relativePath, FileStamp stamp) {}

    public record InputSnapshot(FileHandle directory, List<InputFile> files) {
        public boolean sameFiles(InputSnapshot other) {
            return other != null && files.equals(other.files);
        }
    }

    /** Region coverage and file stamps from one successful descriptor parse. */
    public record Validation(InputSnapshot input, FileStamp atlas, List<FileStamp> pages) {
        public boolean stillCurrent(InputSnapshot currentInput, FileHandle currentAtlas) {
            if (!input.sameFiles(currentInput) || !atlas.equals(FileStamp.of(currentAtlas))) return false;
            for (FileStamp page : pages) {
                if (!page.equals(FileStamp.of(new FileHandle(page.path())))) return false;
            }
            return true;
        }

        public Validation at(FileHandle newAtlas) {
            List<FileStamp> copiedPages = new ArrayList<>(pages.size());
            for (FileStamp page : pages) {
                FileStamp copied = FileStamp.of(newAtlas.parent().child(new FileHandle(page.path()).name()));
                if (copied == null || copied.length() <= 0) {
                    throw new IllegalStateException("Published atlas page is missing: " + page.path());
                }
                copiedPages.add(copied);
            }
            FileStamp descriptor = FileStamp.of(newAtlas);
            if (descriptor == null || descriptor.length() <= 0) {
                throw new IllegalStateException("Published atlas descriptor is missing: " + newAtlas.path());
            }
            return new Validation(input, descriptor, copiedPages);
        }
    }

    public static InputSnapshot snapshot(FileHandle inputDir) {
        if (inputDir == null || !inputDir.exists() || !inputDir.isDirectory()) {
            throw new IllegalStateException("Scene atlas input directory is missing: "
                    + (inputDir == null ? "null" : inputDir.path()));
        }
        List<InputFile> files = new ArrayList<>();
        collect(inputDir, "", files);
        files.sort((a, b) -> a.relativePath().compareTo(b.relativePath()));
        return new InputSnapshot(inputDir, List.copyOf(files));
    }

    private static void collect(FileHandle directory, String prefix, List<InputFile> files) {
        for (FileHandle child : directory.list()) {
            if (child.isDirectory()) collect(child, prefix + child.name() + '/', files);
            else if (child.extension().equalsIgnoreCase("png")) {
                files.add(new InputFile(prefix + child.name(), FileStamp.of(child)));
            }
        }
    }

    public static boolean coversInput(FileHandle inputDir, FileHandle atlasFile) {
        try {
            validate(snapshot(inputDir), atlasFile);
            return true;
        } catch (RuntimeException invalidAtlas) {
            return false;
        }
    }

    public static Validation validate(InputSnapshot input, FileHandle atlasFile) {
        FileStamp descriptor = FileStamp.of(atlasFile);
        if (descriptor == null || descriptor.length() <= 0) {
            throw new IllegalStateException("Scene atlas descriptor is missing: " + atlasFile.path());
        }
        TextureAtlasData data = new TextureAtlasData(atlasFile, atlasFile.parent(), false);
        List<FileStamp> pages = new ArrayList<>();
        for (TextureAtlasData.Page page : data.getPages()) {
            FileStamp stamp = FileStamp.of(page.textureFile);
            if (stamp == null || stamp.length() <= 0) {
                throw new IllegalStateException("Scene atlas page is missing: " + page.textureFile.path());
            }
            pages.add(stamp);
        }
        if (pages.isEmpty()) throw new IllegalStateException("Scene atlas has no pages: " + atlasFile.path());
        Set<String> packed = new HashSet<>();
        for (TextureAtlasData.Region region : data.getRegions()) packed.add(key(region.name, region.index));
        for (InputFile inputFile : input.files()) {
            String relative = inputFile.relativePath();
            String name = relative.substring(0, relative.length() - 4);
            int index = -1;
            int separator = name.lastIndexOf('_');
            if (separator >= 0 && separator + 1 < name.length()) {
                String suffix = name.substring(separator + 1);
                if (suffix.matches("[0-9]+")) {
                    index = Integer.parseInt(suffix);
                    name = name.substring(0, separator);
                }
            }
            if (!packed.contains(key(name, index))
                    && !isFullyTransparent(input.directory().child(relative))) {
                throw new IllegalStateException("Scene atlas omits input region '" + relative
                        + "': " + atlasFile.path());
            }
        }
        return new Validation(input, descriptor, List.copyOf(pages));
    }

    private static String key(String name, int index) { return name + '#' + index; }

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
