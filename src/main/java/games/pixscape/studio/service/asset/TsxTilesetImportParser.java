package games.pixscape.studio.service.asset;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.XmlReader;
import games.pixscape.studio.importer.tmx.TmxFileResolver;
import games.pixscape.studio.io.StudioFs;

import java.util.ArrayList;
import java.util.List;

public final class TsxTilesetImportParser {

    public static final String TILE_ANIMATION_EMPTY = "TMX_TILE_ANIMATION_EMPTY";
    public static final String TILE_ANIMATION_TILE_ID_OUT_OF_RANGE = "TMX_TILE_ANIMATION_TILE_ID_OUT_OF_RANGE";
    public static final String TILE_ANIMATION_FRAME_TILE_ID_OUT_OF_RANGE = "TMX_TILE_ANIMATION_FRAME_TILE_ID_OUT_OF_RANGE";
    public static final String TILE_ANIMATION_FRAME_DURATION_INVALID = "TMX_TILE_ANIMATION_FRAME_DURATION_INVALID";

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

        int tileCount = intAttribute(tileset, "tilecount", 0);
        int columns = intAttribute(tileset, "columns", 0);
        List<TsxTilesetDescriptor.ImageCollectionTile> imageCollectionTiles =
                parseImageCollectionTiles(tileset, tsxFile);
        int effectiveTileCount = Math.max(tileCount, imageCollectionTileCount(imageCollectionTiles));

        XmlReader.Element image = tileset.getChildByName("image");
        if (image == null) {
            if (!imageCollectionTiles.isEmpty()) {
                return new TsxTilesetDescriptor(
                        tileset.getAttribute("name", null),
                        tsxFile,
                        null,
                        null,
                        0,
                        0,
                        tileWidth,
                        tileHeight,
                        spacing,
                        margin,
                        effectiveTileCount,
                        columns,
                        imageCollectionTiles,
                        parseTileAnimationsForStandalone(tileset, effectiveTileCount)
                );
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
                effectiveTileCount,
                columns,
                imageCollectionTiles,
                parseTileAnimationsForStandalone(tileset, effectiveTileCount)
        );
    }

    private static List<TsxTilesetDescriptor.TileAnimation> parseTileAnimationsForStandalone(XmlReader.Element tileset,
                                                                                             int tileCount) {
        List<String> failures = new ArrayList<>();
        List<TsxTilesetDescriptor.TileAnimation> animations = parseTileAnimations(
                tileset,
                tileCount,
                (code, message, location) -> failures.add(message)
        );
        if (!failures.isEmpty()) {
            throw new IllegalArgumentException(failures.get(0));
        }
        return animations;
    }

    public static List<TsxTilesetDescriptor.TileAnimation> parseTileAnimations(XmlReader.Element tileset,
                                                                               int tileCount,
                                                                               TileAnimationDiagnosticSink diagnostics) {
        List<TsxTilesetDescriptor.TileAnimation> animations = new ArrayList<>();
        if (tileset == null) {
            return animations;
        }

        for (int i = 0; i < tileset.getChildCount(); i++) {
            XmlReader.Element tile = tileset.getChild(i);
            if (!"tile".equals(tile.getName())) continue;

            XmlReader.Element animation = tile.getChildByName("animation");
            if (animation == null) continue;

            int baseLocalTileId = intAttribute(tile, "id", -1);
            String baseLocation = "tile " + baseLocalTileId;
            boolean valid = true;

            if (!isValidLocalTileId(baseLocalTileId, tileCount)) {
                report(
                        diagnostics,
                        TILE_ANIMATION_TILE_ID_OUT_OF_RANGE,
                        "Tile animation base tile id is outside the tileset tile range: " + baseLocalTileId,
                        baseLocation
                );
                valid = false;
            }

            if (animation.getChildCount() == 0) {
                report(
                        diagnostics,
                        TILE_ANIMATION_EMPTY,
                        "Tile animation has no frames: tile " + baseLocalTileId,
                        baseLocation
                );
                valid = false;
            }

            List<TsxTilesetDescriptor.Frame> frames = new ArrayList<>();
            for (int f = 0; f < animation.getChildCount(); f++) {
                XmlReader.Element frame = animation.getChild(f);
                if (!"frame".equals(frame.getName())) continue;

                int frameLocalTileId = intAttribute(frame, "tileid", -1);
                int durationMs = intAttribute(frame, "duration", 0);
                String frameLocation = "tile " + baseLocalTileId + " animation frame " + f;

                if (!isValidLocalTileId(frameLocalTileId, tileCount)) {
                    report(
                            diagnostics,
                            TILE_ANIMATION_FRAME_TILE_ID_OUT_OF_RANGE,
                            "Tile animation frame tile id is outside the tileset tile range: " + frameLocalTileId,
                            frameLocation
                    );
                    valid = false;
                }

                if (durationMs <= 0) {
                    report(
                            diagnostics,
                            TILE_ANIMATION_FRAME_DURATION_INVALID,
                            "Tile animation frame duration must be > 0 ms: tile "
                                    + baseLocalTileId + ", frame " + f,
                            frameLocation
                    );
                    valid = false;
                }

                frames.add(new TsxTilesetDescriptor.Frame(frameLocalTileId, durationMs));
            }

            if (frames.isEmpty() && animation.getChildCount() > 0) {
                report(
                        diagnostics,
                        TILE_ANIMATION_EMPTY,
                        "Tile animation has no frame elements: tile " + baseLocalTileId,
                        baseLocation
                );
                valid = false;
            }

            if (valid) {
                animations.add(new TsxTilesetDescriptor.TileAnimation(baseLocalTileId, frames));
            }
        }

        return animations;
    }

    private List<TsxTilesetDescriptor.ImageCollectionTile> parseImageCollectionTiles(XmlReader.Element tileset,
                                                                                     FileHandle tsxFile) {
        List<TsxTilesetDescriptor.ImageCollectionTile> tiles = new ArrayList<>();
        for (int i = 0; i < tileset.getChildCount(); i++) {
            XmlReader.Element child = tileset.getChild(i);
            if ("tile".equals(child.getName()) && child.getChildByName("image") != null) {
                int localTileId = intAttribute(child, "id", -1);
                XmlReader.Element image = child.getChildByName("image");
                String imageSource = image.getAttribute("source", null);
                if (localTileId < 0) {
                    throw new IllegalArgumentException("TSX image collection tile id is invalid.");
                }
                if (imageSource == null || imageSource.isBlank()) {
                    throw new IllegalArgumentException("TSX image collection tile image is missing.");
                }
                FileHandle imageFile = fileResolver.resolveRelative(tsxFile, imageSource);
                if (imageFile == null || !imageFile.exists() || imageFile.isDirectory()) {
                    throw new IllegalArgumentException("TSX image collection tile image is missing: " + imageSource);
                }
                if (!isPng(imageFile)) {
                    throw new IllegalArgumentException("TSX image collection tile image format is not supported: " + imageSource);
                }
                tiles.add(new TsxTilesetDescriptor.ImageCollectionTile(
                        localTileId,
                        imageFile,
                        imageSource,
                        intAttribute(image, "width", 0),
                        intAttribute(image, "height", 0)
                ));
            }
        }
        return tiles;
    }

    private static int imageCollectionTileCount(List<TsxTilesetDescriptor.ImageCollectionTile> tiles) {
        int max = 0;
        if (tiles == null) return max;
        for (TsxTilesetDescriptor.ImageCollectionTile tile : tiles) {
            if (tile == null) continue;
            max = Math.max(max, tile.localTileId() + 1);
        }
        return max;
    }

    private static boolean isValidLocalTileId(int localTileId, int tileCount) {
        return localTileId >= 0 && (tileCount <= 0 || localTileId < tileCount);
    }

    private static void report(TileAnimationDiagnosticSink diagnostics,
                               String code,
                               String message,
                               String location) {
        if (diagnostics != null) {
            diagnostics.report(code, message, location);
        }
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

    @FunctionalInterface
    public interface TileAnimationDiagnosticSink {
        void report(String code, String message, String location);
    }
}
