package games.pixscape.studio.service.runtimeavailability;

import games.pixscape.runtime.hud.HudScreenAsset;
import games.pixscape.runtime.hud.HudScreenAssetId;
import games.pixscape.runtime.hud.document.HudDocumentCodec;
import games.pixscape.runtime.hud.document.HudDocumentV1;

import java.util.List;
import java.util.Objects;

/** Immutable logical authored-resource closure for the selected HUD roots of one Scene. */
public final class SceneHudDependencyClosure {
    public enum FileKind {
        SKIN_JSON,
        SKIN_ATLAS,
        SKIN_ATLAS_PAGE,
        BITMAP_FONT_DESCRIPTOR,
        BITMAP_FONT_PAGE
    }

    public record FileDependency(FileKind kind, String projectRelativePath) {
        public FileDependency {
            Objects.requireNonNull(kind, "kind");
            if (projectRelativePath == null || projectRelativePath.isBlank()) {
                throw new IllegalArgumentException("Project-relative dependency path is required.");
            }
        }
    }

    /** One stable project Image identity and its authored source file. */
    public record ImageDependency(int assetId, String logicalPath, String resourceName,
                                  String sourceRelPath) {
        public ImageDependency {
            if (assetId <= 0) throw new IllegalArgumentException("Image asset ID must be positive.");
            if (resourceName == null || resourceName.isBlank()) {
                throw new IllegalArgumentException("Image resource name is required.");
            }
            if (sourceRelPath == null || sourceRelPath.isBlank()) {
                throw new IllegalArgumentException("Image source path is required.");
            }
        }
    }

    /** One selected authored Skin and the files needed to reconstruct it without GL discovery. */
    public record SkinDependency(String skinId, List<FileDependency> files) {
        public SkinDependency {
            if (skinId == null || skinId.isBlank()) throw new IllegalArgumentException("Skin ID is required.");
            files = List.copyOf(files);
        }
    }

    /** One standalone bitmap-font Asset and its descriptor/page source closure. */
    public record FontDependency(int assetId, String logicalPath, String descriptorPath,
                                 List<FileDependency> files, List<String> hudRoots) {
        public FontDependency {
            if (assetId <= 0) throw new IllegalArgumentException("Font asset ID must be positive.");
            if (descriptorPath == null || descriptorPath.isBlank()) {
                throw new IllegalArgumentException("Font descriptor path is required.");
            }
            files = List.copyOf(files);
            hudRoots = List.copyOf(hudRoots);
        }
    }

    /** Immutable snapshot of the selected asset/document state and its logical resource categories. */
    public record SelectedHudScreen(String screenId, HudScreenAsset asset, HudDocumentV1 document,
                                    boolean requiresSkin, boolean requiresAtlas,
                                    boolean requiresBuiltInLabelStyle) {
        public SelectedHudScreen(String screenId, HudScreenAsset asset, HudDocumentV1 document,
                                 boolean requiresSkin, boolean requiresAtlas) {
            this(screenId, asset, document, requiresSkin, requiresAtlas, false);
        }

        public SelectedHudScreen {
            screenId = HudScreenAssetId.normalize(screenId);
            asset = copyAsset(Objects.requireNonNull(asset, "asset"));
            document = copyDocument(document);
        }

        @Override public HudScreenAsset asset() { return copyAsset(asset); }
        @Override public HudDocumentV1 document() { return copyDocument(document); }
    }

    private final List<String> rootIds;
    private final List<SelectedHudScreen> selectedHudScreens;
    private final List<ImageDependency> imageDependencies;
    private final List<SkinDependency> skinDependencies;
    private final List<FontDependency> fontDependencies;

    public SceneHudDependencyClosure(List<SelectedHudScreen> selectedHudScreens,
                                     List<ImageDependency> imageDependencies,
                                     List<SkinDependency> skinDependencies) {
        this(selectedHudScreens, imageDependencies, skinDependencies, List.of());
    }

    public SceneHudDependencyClosure(List<SelectedHudScreen> selectedHudScreens,
                                     List<ImageDependency> imageDependencies,
                                     List<SkinDependency> skinDependencies,
                                     List<FontDependency> fontDependencies) {
        this.selectedHudScreens = List.copyOf(selectedHudScreens);
        this.rootIds = this.selectedHudScreens.stream().map(SelectedHudScreen::screenId).toList();
        this.imageDependencies = List.copyOf(imageDependencies);
        this.skinDependencies = List.copyOf(skinDependencies);
        this.fontDependencies = List.copyOf(fontDependencies);
    }

    public static SceneHudDependencyClosure empty() {
        return new SceneHudDependencyClosure(List.of(), List.of(), List.of());
    }

    public List<String> rootIds() { return rootIds; }
    public List<SelectedHudScreen> selectedHudScreens() { return selectedHudScreens; }
    public List<ImageDependency> imageDependencies() { return imageDependencies; }
    public List<SkinDependency> skinDependencies() { return skinDependencies; }
    public List<FontDependency> fontDependencies() { return fontDependencies; }

    private static HudScreenAsset copyAsset(HudScreenAsset source) {
        HudScreenAsset copy = new HudScreenAsset();
        copy.schemaVersion = source.schemaVersion;
        copy.referenceWidth = source.referenceWidth;
        copy.referenceHeight = source.referenceHeight;
        copy.documentId = source.documentId;
        copy.skinId = source.skinId;
        copy.atlasId = source.atlasId;
        copy.textureProfileId = source.textureProfileId;
        return copy;
    }

    private static HudDocumentV1 copyDocument(HudDocumentV1 source) {
        return source == null ? null : new HudDocumentCodec().read(new HudDocumentCodec().write(source));
    }
}
