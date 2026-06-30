package games.pixscape.studio.importer.tmx;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.XmlReader;
import games.pixscape.studio.io.StudioFs;
import games.pixscape.studio.service.asset.TsxTilesetDescriptor;
import games.pixscape.studio.service.asset.TsxTilesetImportParser;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

public final class TmxPreflightService {

    private final TmxFileResolver fileResolver;

    public TmxPreflightService() {
        this(new TmxFileResolver());
    }

    TmxPreflightService(TmxFileResolver fileResolver) {
        this.fileResolver = fileResolver;
    }

    public TmxPreflightReport analyze(TmxPreflightRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request is null");
        }

        FileHandle tmxFile = request.tmxFile();
        String sourcePath = pathOf(tmxFile);
        AnalysisState state = new AnalysisState(sourcePath);

        if (tmxFile == null || !tmxFile.exists() || tmxFile.isDirectory()) {
            state.blocking("TMX_MISSING", "TMX file is missing.", sourcePath);
            return state.toReport();
        }

        XmlReader.Element map;
        try {
            map = new XmlReader().parse(tmxFile);
        } catch (RuntimeException ex) {
            state.blocking("TMX_INVALID_XML", "TMX XML could not be parsed: " + ex.getMessage(), sourcePath);
            return state.toReport();
        }

        if (!"map".equals(map.getName())) {
            state.blocking("TMX_INVALID_XML", "Root element is not a TMX map.", sourcePath);
            return state.toReport();
        }

        TmxMapInfo mapInfo = readMapInfo(map, state);
        state.mapInfo = mapInfo;

        if (map.hasAttribute("parallaxoriginx") || map.hasAttribute("parallaxoriginy")) {
            state.warning("TMX_MAP_PARALLAX_ORIGIN_IGNORED", "Map parallax origin is detected but Pixscape has no equivalent.", "map");
        }
        warnIgnoredProperties(map, state, "map");
        readTilesets(map, tmxFile, mapInfo, state);
        state.tilesets.sort(Comparator.comparingInt(TmxTilesetInfo::firstGid));

        LayerContext rootContext = new LayerContext("", true, 1f, 0f, 0f, 1f, 1f);
        readLayers(map, rootContext, state);

        return state.toReport();
    }

    private TmxMapInfo readMapInfo(XmlReader.Element map, AnalysisState state) {
        String orientation = map.getAttribute("orientation", "");
        int width = intAttribute(map, "width", -1);
        int height = intAttribute(map, "height", -1);
        int tileWidth = intAttribute(map, "tilewidth", -1);
        int tileHeight = intAttribute(map, "tileheight", -1);
        boolean infinite = "1".equals(map.getAttribute("infinite", "0"));

        if (width <= 0 || height <= 0) {
            state.blocking("TMX_INVALID_MAP_DIMENSIONS", "Map width and height must be positive.", "map");
        }
        if (tileWidth <= 0 || tileHeight <= 0) {
            state.blocking("TMX_INVALID_TILE_DIMENSIONS", "Map tile width and tile height must be positive.", "map");
        }
        if (infinite) {
            state.blocking("TMX_INFINITE_MAP", "Infinite TMX maps are not part of the first import scope.", "map");
        }

        if (!"orthogonal".equals(orientation) && !"isometric".equals(orientation)) {
            String message = switch (orientation) {
                case "hexagonal" -> "Hexagonal TMX maps are not supported by the first import scope.";
                case "staggered" -> "Staggered TMX maps are not supported by the first import scope.";
                default -> "Unsupported TMX orientation: " + orientation;
            };
            state.blocking("TMX_UNSUPPORTED_ORIENTATION", message, "map");
        }

        return new TmxMapInfo(orientation, width, height, tileWidth, tileHeight, infinite);
    }

    private void readTilesets(XmlReader.Element map,
                              FileHandle tmxFile,
                              TmxMapInfo mapInfo,
                              AnalysisState state) {
        for (int i = 0; i < map.getChildCount(); i++) {
            XmlReader.Element child = map.getChild(i);
            if (!"tileset".equals(child.getName())) continue;

            int firstGid = intAttribute(child, "firstgid", -1);
            if (firstGid <= 0) {
                state.blocking("TMX_INVALID_TILESET_FIRSTGID", "Tileset firstgid must be positive.", "tileset");
            }

            String source = child.getAttribute("source", null);
            if (source != null && !source.isBlank()) {
                readExternalTileset(firstGid, source, tmxFile, mapInfo, state);
            } else {
                state.tilesets.add(readTilesetElement(firstGid, null, false, child, tmxFile, mapInfo, state));
            }
        }
    }

    private void readExternalTileset(int firstGid,
                                     String source,
                                     FileHandle tmxFile,
                                     TmxMapInfo mapInfo,
                                     AnalysisState state) {
        FileHandle tsxFile = fileResolver.resolveRelative(tmxFile, source);
        String tsxPath = pathOf(tsxFile);
        if (tsxFile == null || !tsxFile.exists() || tsxFile.isDirectory()) {
            state.blocking("TMX_TSX_MISSING", "External TSX file is missing: " + source, source);
            state.tilesets.add(new TmxTilesetInfo(firstGid, tsxPath, null, 0, 0, 0, 0, 0, 0,
                    null, 0, 0, null, false, true, Collections.emptyList()));
            return;
        }

        XmlReader.Element tileset;
        try {
            tileset = new XmlReader().parse(tsxFile);
        } catch (RuntimeException ex) {
            state.blocking("TMX_TSX_INVALID_XML", "External TSX XML could not be parsed: " + ex.getMessage(), tsxPath);
            state.tilesets.add(new TmxTilesetInfo(firstGid, tsxPath, null, 0, 0, 0, 0, 0, 0,
                    null, 0, 0, null, false, true, Collections.emptyList()));
            return;
        }

        state.tilesets.add(readTilesetElement(firstGid, tsxPath, true, tileset, tsxFile, mapInfo, state));
    }

    private TmxTilesetInfo readTilesetElement(int firstGid,
                                              String sourcePath,
                                              boolean external,
                                              XmlReader.Element tileset,
                                              FileHandle declaringFile,
                                              TmxMapInfo mapInfo,
                                              AnalysisState state) {
        String name = tileset.getAttribute("name", null);
        int tileWidth = intAttribute(tileset, "tilewidth", 0);
        int tileHeight = intAttribute(tileset, "tileheight", 0);
        int tileCount = intAttribute(tileset, "tilecount", 0);
        int columns = intAttribute(tileset, "columns", 0);
        int spacing = intAttribute(tileset, "spacing", 0);
        int margin = intAttribute(tileset, "margin", 0);

        if (tileWidth > 0 && mapInfo.tileWidth() > 0 && tileWidth != mapInfo.tileWidth()
                || tileHeight > 0 && mapInfo.tileHeight() > 0 && tileHeight != mapInfo.tileHeight()) {
            state.blocking(
                    "TMX_TILESET_TILE_SIZE_INCOMPATIBLE",
                    "Tileset tile size is incompatible with the map tile size for the first import scope.",
                    name
            );
        }

        XmlReader.Element tileOffset = tileset.getChildByName("tileoffset");
        if (tileOffset != null) {
            int x = intAttribute(tileOffset, "x", 0);
            int y = intAttribute(tileOffset, "y", 0);
            if (x != 0 || y != 0) {
                state.blocking("TMX_TILEOFFSET_UNSUPPORTED", "Non-zero tileset tileoffset is not supported.", name);
            }
        }

        boolean hasPerTileImages = false;
        for (int i = 0; i < tileset.getChildCount(); i++) {
            XmlReader.Element child = tileset.getChild(i);
            if (!"tile".equals(child.getName())) continue;
            if (child.getChildByName("image") != null) {
                hasPerTileImages = true;
            }
            warnIgnoredProperties(child, state, "tile");
        }
        if (hasPerTileImages) {
            state.blocking("TMX_IMAGE_COLLECTION_TILESET", "Image collection tilesets are not supported.", name);
        }

        List<TsxTilesetDescriptor.TileAnimation> tileAnimations = TsxTilesetImportParser.parseTileAnimations(
                tileset,
                tileCount,
                (code, message, location) -> state.blocking(code, message, location)
        );

        warnIgnoredProperties(tileset, state, "tileset");

        XmlReader.Element image = tileset.getChildByName("image");
        String imageSource = image != null ? image.getAttribute("source", null) : null;
        int imageWidth = image != null ? intAttribute(image, "width", 0) : 0;
        int imageHeight = image != null ? intAttribute(image, "height", 0) : 0;
        FileHandle resolvedImage = imageSource != null ? fileResolver.resolveRelative(declaringFile, imageSource) : null;
        boolean imageExists = resolvedImage != null && resolvedImage.exists() && !resolvedImage.isDirectory();

        if (imageSource == null || imageSource.isBlank()) {
            state.blocking("TMX_TILESET_IMAGE_MISSING", "Tileset does not declare a single image source.", name);
        } else if (!imageExists) {
            state.blocking("TMX_TILESET_IMAGE_MISSING", "Tileset image is missing: " + imageSource, imageSource);
        }

        return new TmxTilesetInfo(
                firstGid,
                sourcePath,
                name,
                tileWidth,
                tileHeight,
                tileCount,
                columns,
                spacing,
                margin,
                imageSource,
                imageWidth,
                imageHeight,
                pathOf(resolvedImage),
                imageExists,
                external,
                tileAnimations
        );
    }

    private void readLayers(XmlReader.Element parent, LayerContext context, AnalysisState state) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            XmlReader.Element child = parent.getChild(i);
            switch (child.getName()) {
                case "group" -> readGroup(child, context, state);
                case "layer" -> readTileLayer(child, context, state);
                case "objectgroup" -> readGenericLayer(child, context, state, TmxLayerKind.OBJECT);
                case "imagelayer" -> readImageLayer(child, context, state);
                default -> {
                }
            }
        }
    }

    private void readGroup(XmlReader.Element group, LayerContext parent, AnalysisState state) {
        String rawName = group.getAttribute("name", "Group");
        String path = parent.namePrefix().isBlank() ? rawName : parent.namePrefix() + "/" + rawName;
        boolean visible = parent.visible() && intAttribute(group, "visible", 1) != 0;
        float opacity = parent.opacity() * floatAttribute(group, "opacity", 1f);
        float offsetX = parent.offsetX() + floatAttribute(group, "offsetx", 0f);
        float offsetY = parent.offsetY() + floatAttribute(group, "offsety", 0f);
        float parallaxX = parent.parallaxX() * floatAttribute(group, "parallaxx", 1f);
        float parallaxY = parent.parallaxY() * floatAttribute(group, "parallaxy", 1f);
        warnIgnoredProperties(group, state, path);
        readLayers(group, new LayerContext(path, visible, opacity, offsetX, offsetY, parallaxX, parallaxY), state);
    }

    private void readGenericLayer(XmlReader.Element layer,
                                  LayerContext context,
                                  AnalysisState state,
                                  TmxLayerKind kind) {
        String name = layerName(layer, context);
        boolean visible = context.visible() && intAttribute(layer, "visible", 1) != 0;
        float opacity = context.opacity() * floatAttribute(layer, "opacity", 1f);
        float offsetX = context.offsetX() + floatAttribute(layer, "offsetx", 0f);
        float offsetY = context.offsetY() + floatAttribute(layer, "offsety", 0f);
        float parallaxX = context.parallaxX() * floatAttribute(layer, "parallaxx", 1f);
        float parallaxY = context.parallaxY() * floatAttribute(layer, "parallaxy", 1f);

        state.layers.add(new TmxGenericLayerInfo(kind, name, visible, opacity, offsetX, offsetY, parallaxX, parallaxY));
        if (kind == TmxLayerKind.OBJECT) {
            state.warning("TMX_OBJECT_LAYER_OUT_OF_SCOPE", "Object layers are not part of the first import scope yet.", name);
        }
        warnIgnoredLayerAttributes(layer, state, name, opacity);
    }

    private void warnIgnoredImageLayerAttributes(XmlReader.Element layer,
                                                 AnalysisState state,
                                                 String location) {
        warnIgnoredProperties(layer, state, location);
        if (layer.hasAttribute("blendmode")) {
            state.warning("TMX_LAYER_BLENDMODE_IGNORED", "Layer blend mode is detected but ignored by preflight.", location);
        }
    }

    private void readImageLayer(XmlReader.Element layer, LayerContext context, AnalysisState state) {
        String name = layerName(layer, context);
        String originalName = layer.getAttribute("name", layer.getName());
        boolean visible = context.visible() && intAttribute(layer, "visible", 1) != 0;
        float opacity = context.opacity() * floatAttribute(layer, "opacity", 1f);
        float offsetX = context.offsetX() + floatAttribute(layer, "offsetx", 0f);
        float offsetY = context.offsetY() + floatAttribute(layer, "offsety", 0f);
        float parallaxX = context.parallaxX() * floatAttribute(layer, "parallaxx", 1f);
        float parallaxY = context.parallaxY() * floatAttribute(layer, "parallaxy", 1f);
        float x = floatAttribute(layer, "x", 0f);
        float y = floatAttribute(layer, "y", 0f);

        XmlReader.Element image = layer.getChildByName("image");
        String imageSource = image != null ? image.getAttribute("source", null) : null;
        int imageWidth = image != null ? intAttribute(image, "width", 0) : 0;
        int imageHeight = image != null ? intAttribute(image, "height", 0) : 0;
        FileHandle resolvedImage = imageSource != null ? fileResolver.resolveRelative(new FileHandle(state.sourcePath), imageSource) : null;
        boolean imageExists = resolvedImage != null && resolvedImage.exists() && !resolvedImage.isDirectory();

        if (image == null) {
            state.blocking("TMX_IMAGE_LAYER_IMAGE_MISSING", "Image layer has no image element.", name);
        } else if (imageSource == null || imageSource.isBlank()) {
            state.blocking("TMX_IMAGE_LAYER_SOURCE_MISSING", "Image layer image source is missing.", name);
        } else if (!imageExists) {
            state.blocking("TMX_IMAGE_LAYER_IMAGE_MISSING", "Image layer image is missing: " + imageSource, imageSource);
        } else if (!StudioFs.isImageFile(imageSource)) {
            state.blocking("TMX_IMAGE_LAYER_IMAGE_UNSUPPORTED", "Image layer image format is not supported: " + imageSource, imageSource);
        }

        state.layers.add(new TmxImageLayerInfo(
                name,
                originalName,
                visible,
                opacity,
                offsetX,
                offsetY,
                parallaxX,
                parallaxY,
                x,
                y,
                imageSource,
                imageWidth,
                imageHeight,
                pathOf(resolvedImage),
                imageExists
        ));

        if (layer.hasAttribute("repeatx") || layer.hasAttribute("repeaty")) {
            state.warning("TMX_IMAGE_LAYER_REPEAT_UNSUPPORTED", "Image layer repeatx/repeaty is detected but not supported yet.", name);
        }
        if (layer.hasAttribute("tintcolor")) {
            state.warning("TMX_IMAGE_LAYER_TINT_IGNORED", "Image layer tint color is detected but ignored.", name);
        }
        if (image != null && image.hasAttribute("trans")) {
            state.warning("TMX_IMAGE_LAYER_TRANSPARENT_COLOR_IGNORED", "Image layer transparent color is detected but ignored.", name);
        }
        warnIgnoredImageLayerAttributes(layer, state, name);
    }

    private void readTileLayer(XmlReader.Element layer, LayerContext context, AnalysisState state) {
        String name = layerName(layer, context);
        boolean visible = context.visible() && intAttribute(layer, "visible", 1) != 0;
        float opacity = context.opacity() * floatAttribute(layer, "opacity", 1f);
        float offsetX = context.offsetX() + floatAttribute(layer, "offsetx", 0f);
        float offsetY = context.offsetY() + floatAttribute(layer, "offsety", 0f);
        float parallaxX = context.parallaxX() * floatAttribute(layer, "parallaxx", 1f);
        float parallaxY = context.parallaxY() * floatAttribute(layer, "parallaxy", 1f);
        int width = intAttribute(layer, "width", state.mapInfo != null ? state.mapInfo.width() : 0);
        int height = intAttribute(layer, "height", state.mapInfo != null ? state.mapInfo.height() : 0);
        XmlReader.Element data = layer.getChildByName("data");

        int nonEmpty = 0;
        boolean hasTransformFlags = false;
        List<TmxTileCellInfo> cells = new ArrayList<>();
        String encoding = data != null ? data.getAttribute("encoding", null) : null;
        String compression = data != null ? data.getAttribute("compression", null) : null;

        if (width <= 0 || height <= 0) {
            state.blocking("TMX_INVALID_LAYER_DIMENSIONS", "Tile layer width and height must be positive.", name);
        }

        int[] gids = null;
        if (data == null) {
            state.blocking("TMX_LAYER_DATA_MISSING", "Tile layer has no data element.", name);
        } else {
            try {
                gids = decodeLayerData(data, width, height);
            } catch (LayerDataException ex) {
                state.blocking(ex.code, ex.getMessage(), name);
            }
        }

        if (gids != null) {
            for (int i = 0; i < gids.length; i++) {
                int rawGid = gids[i];
                TmxGidSupport.DecodedGid decoded = TmxGidSupport.decode(rawGid);
                int cleanGid = decoded.cleanGid;
                if (decoded.isEmpty()) continue;
                nonEmpty++;
                hasTransformFlags |= TmxGidSupport.hasTransformFlags(rawGid);
                TmxTilesetInfo tileset = resolveTileset(cleanGid, state.tilesets);
                if (tileset == null) {
                    state.blocking("TMX_GID_UNRESOLVED", "Tile layer references a GID that does not resolve to any tileset: " + cleanGid, name);
                }
                int tilesetFirstGid = tileset != null ? tileset.firstGid() : -1;
                int localTileId = tileset != null ? cleanGid - tileset.firstGid() : -1;
                cells.add(new TmxTileCellInfo(
                        i % Math.max(width, 1),
                        i / Math.max(width, 1),
                        rawGid,
                        cleanGid,
                        tilesetFirstGid,
                        localTileId,
                        TmxGidSupport.hasTransformFlags(rawGid),
                        decoded.flipH,
                        decoded.flipV,
                        decoded.flipD,
                        decoded.hex120
                ));
            }
        }

        state.tileLayerCount++;
        state.requiredTiledCells += Math.max(0, width) * (long) Math.max(0, height);
        state.nonEmptyTileCount += nonEmpty;
        state.layers.add(new TmxTileLayerInfo(
                name,
                layer.getAttribute("name", layer.getName()),
                visible,
                opacity,
                offsetX,
                offsetY,
                parallaxX,
                parallaxY,
                width,
                height,
                encoding,
                compression,
                nonEmpty,
                hasTransformFlags,
                cells
        ));
        warnIgnoredLayerAttributes(layer, state, name, opacity);
    }

    private int[] decodeLayerData(XmlReader.Element data, int width, int height) throws LayerDataException {
        String encoding = data.getAttribute("encoding", null);
        String compression = data.getAttribute("compression", null);
        int expected = width * height;

        if (encoding == null || encoding.isBlank()) {
            throw new LayerDataException("TMX_XML_TILE_DATA_UNSUPPORTED", "XML/unencoded tile data is not supported.");
        }

        if ("csv".equals(encoding)) {
            if (compression != null && !compression.isBlank()) {
                throw new LayerDataException("TMX_LAYER_COMPRESSION_UNSUPPORTED", "CSV tile data must not declare compression.");
            }
            return decodeCsv(data.getText(), expected);
        }

        if (!"base64".equals(encoding)) {
            throw new LayerDataException("TMX_LAYER_ENCODING_UNSUPPORTED", "Unsupported tile data encoding: " + encoding);
        }

        if (compression != null && !compression.isBlank()
                && !"gzip".equals(compression)
                && !"zlib".equals(compression)) {
            throw new LayerDataException("TMX_LAYER_COMPRESSION_UNSUPPORTED", "Unsupported tile data compression: " + compression);
        }

        try {
            byte[] bytes = Base64.getMimeDecoder().decode(data.getText() == null ? "" : data.getText());
            if ("gzip".equals(compression)) {
                bytes = readAll(new GZIPInputStream(new ByteArrayInputStream(bytes)));
            } else if ("zlib".equals(compression)) {
                bytes = readAll(new InflaterInputStream(new ByteArrayInputStream(bytes)));
            }
            if (bytes.length < expected * 4) {
                throw new LayerDataException("TMX_LAYER_DATA_MALFORMED", "Tile data has fewer GIDs than expected.");
            }
            int[] gids = new int[expected];
            ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
            for (int i = 0; i < expected; i++) {
                gids[i] = buffer.getInt();
            }
            return gids;
        } catch (IllegalArgumentException | IOException ex) {
            throw new LayerDataException("TMX_LAYER_DATA_MALFORMED", "Malformed tile data: " + ex.getMessage());
        }
    }

    private int[] decodeCsv(String text, int expected) throws LayerDataException {
        String[] raw = (text == null ? "" : text.trim()).split("\\s*,\\s*");
        if (raw.length != expected) {
            throw new LayerDataException("TMX_LAYER_DATA_MALFORMED", "CSV tile data cell count does not match layer dimensions.");
        }
        int[] gids = new int[expected];
        for (int i = 0; i < raw.length; i++) {
            try {
                gids[i] = (int) Long.parseLong(raw[i].trim());
            } catch (NumberFormatException ex) {
                throw new LayerDataException("TMX_LAYER_DATA_MALFORMED", "CSV tile data contains a non-numeric GID.");
            }
        }
        return gids;
    }

    private static byte[] readAll(java.io.InputStream input) throws IOException {
        try (input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private static TmxTilesetInfo resolveTileset(int cleanGid, List<TmxTilesetInfo> tilesets) {
        TmxTilesetInfo resolved = null;
        for (TmxTilesetInfo tileset : tilesets) {
            if (tileset.firstGid() <= cleanGid) {
                resolved = tileset;
            } else {
                break;
            }
        }
        if (resolved == null || !resolved.containsCleanGid(cleanGid)) {
            return null;
        }
        return resolved;
    }

    private void warnIgnoredLayerAttributes(XmlReader.Element layer,
                                            AnalysisState state,
                                            String location,
                                            float opacity) {
        warnIgnoredProperties(layer, state, location);
        if (Math.abs(opacity - 1f) > 0.0001f) {
            state.warning("TMX_LAYER_OPACITY_IGNORED", "Layer opacity differs from 1.0 and may be ignored by the first import scope.", location);
        }
        if (layer.hasAttribute("tintcolor")) {
            state.warning("TMX_LAYER_TINT_IGNORED", "Layer tint color is detected but ignored by preflight.", location);
        }
        if (layer.hasAttribute("blendmode")) {
            state.warning("TMX_LAYER_BLENDMODE_IGNORED", "Layer blend mode is detected but ignored by preflight.", location);
        }
    }

    private static void warnIgnoredProperties(XmlReader.Element element, AnalysisState state, String location) {
        if (element != null && element.getChildByName("properties") != null) {
            state.warning("TMX_CUSTOM_PROPERTIES_IGNORED", "Custom properties are detected but ignored by preflight.", location);
        }
    }

    private static int intAttribute(XmlReader.Element element, String name, int defaultValue) {
        try {
            return element.getIntAttribute(name, defaultValue);
        } catch (RuntimeException ex) {
            return defaultValue;
        }
    }

    private static float floatAttribute(XmlReader.Element element, String name, float defaultValue) {
        try {
            return element.getFloatAttribute(name, defaultValue);
        } catch (RuntimeException ex) {
            return defaultValue;
        }
    }

    private static String layerName(XmlReader.Element layer, LayerContext context) {
        String rawName = layer.getAttribute("name", layer.getName());
        return context.namePrefix().isBlank() ? rawName : context.namePrefix() + "/" + rawName;
    }

    private static String pathOf(FileHandle file) {
        if (file == null) return null;
        try {
            return file.file().toPath().toAbsolutePath().normalize().toString();
        } catch (RuntimeException ex) {
            return file.path();
        }
    }

    private record LayerContext(String namePrefix,
                                boolean visible,
                                float opacity,
                                float offsetX,
                                float offsetY,
                                float parallaxX,
                                float parallaxY) {
    }

    private static final class AnalysisState {
        private final String sourcePath;
        private TmxMapInfo mapInfo = new TmxMapInfo("", 0, 0, 0, 0, false);
        private final List<TmxTilesetInfo> tilesets = new ArrayList<>();
        private final List<TmxLayerInfo> layers = new ArrayList<>();
        private final List<TmxDiagnostic> diagnostics = new ArrayList<>();
        private int tileLayerCount;
        private long requiredTiledCells;
        private long nonEmptyTileCount;

        private AnalysisState(String sourcePath) {
            this.sourcePath = sourcePath;
        }

        private void blocking(String code, String message, String location) {
            diagnostics.add(new TmxDiagnostic(TmxDiagnosticSeverity.BLOCKING, code, message, location));
        }

        private void warning(String code, String message, String location) {
            diagnostics.add(new TmxDiagnostic(TmxDiagnosticSeverity.WARNING, code, message, location));
        }

        private TmxPreflightReport toReport() {
            return new TmxPreflightReport(
                    sourcePath,
                    mapInfo,
                    tilesets,
                    layers,
                    diagnostics,
                    tileLayerCount,
                    requiredTiledCells,
                    nonEmptyTileCount
            );
        }
    }

    private static final class LayerDataException extends Exception {
        private final String code;

        private LayerDataException(String code, String message) {
            super(message);
            this.code = code;
        }
    }
}
