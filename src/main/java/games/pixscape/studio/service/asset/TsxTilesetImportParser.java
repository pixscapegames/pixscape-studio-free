package games.pixscape.studio.service.asset;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.XmlReader;
import games.pixscape.studio.importer.tmx.TmxFileResolver;
import games.pixscape.studio.io.StudioFs;

public final class TsxTilesetImportParser {

    private final TmxFileResolver fileResolver;

    public TsxTilesetImportParser() {
        this(new TmxFileResolver());
    }

    TsxTilesetImportParser(TmxFileResolver fileResolver) {
        this.fileResolver = fileResolver;
    }

    public TsxTilesetDescriptor parse(FileHandle tsxFile) {
        if (tsxFile == null || !tsxFile.exists() || tsxFile.isDirectory()) {
            throw new IllegalArgumentException("TSX file is invalid.");
        }

        XmlReader.Element tileset;
        try {
            tileset = new XmlReader().parse(tsxFile);
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("TSX file is invalid.");
        }

        if (tileset == null || !"tileset".equals(tileset.getName())) {
            throw new IllegalArgumentException("TSX file is invalid.");
        }

        int tileWidth = intAttribute(tileset, "tilewidth", 0);
        int tileHeight = intAttribute(tileset, "tileheight", 0);
        int spacing = intAttribute(tileset, "spacing", 0);
        int margin = intAttribute(tileset, "margin", 0);
        if (tileWidth <= 0 || tileHeight <= 0) {
            throw new IllegalArgumentException("TSX tile width and tile height must be positive.");
        }
        if (spacing < 0 || margin < 0) {
            throw new IllegalArgumentException("TSX spacing and margin must be non-negative.");
        }

        XmlReader.Element image = tileset.getChildByName("image");
        if (image == null) {
            if (hasPerTileImages(tileset)) {
                throw new IllegalArgumentException("Image collection TSX tilesets are not supported yet.");
            }
            throw new IllegalArgumentException("TSX tileset image is missing.");
        }

        String imageSource = image.getAttribute("source", null);
        if (imageSource == null || imageSource.isBlank()) {
            throw new IllegalArgumentException("TSX tileset image is missing.");
        }

        FileHandle imageFile = fileResolver.resolveRelative(tsxFile, imageSource);
        if (imageFile == null || !imageFile.exists() || imageFile.isDirectory()) {
            throw new IllegalArgumentException("TSX tileset image is missing: " + imageSource);
        }
        if (!isPng(imageFile)) {
            throw new IllegalArgumentException("TSX tileset image format is not supported: " + imageSource);
        }

        return new TsxTilesetDescriptor(
                tileset.getAttribute("name", null),
                tsxFile,
                imageFile,
                imageSource,
                intAttribute(image, "width", 0),
                intAttribute(image, "height", 0),
                tileWidth,
                tileHeight,
                spacing,
                margin,
                intAttribute(tileset, "tilecount", 0),
                intAttribute(tileset, "columns", 0)
        );
    }

    private boolean hasPerTileImages(XmlReader.Element tileset) {
        for (int i = 0; i < tileset.getChildCount(); i++) {
            XmlReader.Element child = tileset.getChild(i);
            if ("tile".equals(child.getName()) && child.getChildByName("image") != null) {
                return true;
            }
        }
        return false;
    }

    private static int intAttribute(XmlReader.Element element, String name, int defaultValue) {
        try {
            return element.getIntAttribute(name, defaultValue);
        } catch (RuntimeException ignored) {
            return defaultValue;
        }
    }

    private static boolean isPng(FileHandle file) {
        return file != null && StudioFs.EXT_PNG.substring(1).equalsIgnoreCase(file.extension());
    }
}
