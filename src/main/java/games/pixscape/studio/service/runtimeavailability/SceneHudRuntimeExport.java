package games.pixscape.studio.service.runtimeavailability;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonWriter;
import games.pixscape.runtime.hud.HudScreenAsset;
import games.pixscape.runtime.hud.HudScreenAssetId;
import games.pixscape.runtime.hud.HudBitmapFontResource;
import games.pixscape.runtime.hud.document.HudDocumentCodec;
import games.pixscape.runtime.hud.document.HudDocumentValidator;
import games.pixscape.runtime.hud.document.HudImageSource;
import games.pixscape.runtime.hud.document.HudImageReferences;
import games.pixscape.studio.asset.AssetMetaDatabase;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.io.AtomicDirectoryPublication;
import games.pixscape.studio.io.StudioFs;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

/** Saved-state, file-only export preparation. Never consumes or publishes Studio live atlases. */
public final class SceneHudRuntimeExport {
    private final SceneHudAtlasBuilder builder;

    public SceneHudRuntimeExport() { this(new SceneHudAtlasBuilder()); }
    SceneHudRuntimeExport(SceneHudAtlasBuilder builder) { this.builder = Objects.requireNonNull(builder); }

    /** UI precondition: export reads saved files, and must not silently substitute dirty selected HUDs. */
    public static void requireSavedDocuments(ProjectConfig config, Iterable<String> dirtyScreenIds) {
        var selected = new TreeSet<String>();
        for (String name : config.getSceneNames()) selected.addAll(SceneHudRoots.collect(config.getSceneMeta(name)));
        for (String id : dirtyScreenIds) if (selected.contains(HudScreenAssetId.normalize(id)))
            throw new IllegalStateException("Save selected HUD screen '" + HudScreenAssetId.normalize(id)
                    + "' before exporting runtime.");
    }

    /** Completes every Scene build before the caller may replace its previous Runtime export. */
    public PreparedExport prepare(FileHandle project, ProjectConfig config) {
        Objects.requireNonNull(project, "project");
        Objects.requireNonNull(config, "config");
        Map<String, List<String>> scenes = new LinkedHashMap<>();
        var allRoots = new TreeSet<String>();
        var tags = new TreeSet<String>();
        var names = new TreeSet<String>();
        for (String name : config.getSceneNames()) names.add(name);
        for (String name : names) {
            var scene = config.getSceneMeta(name);
            var roots = SceneHudRoots.collect(scene);
            if (roots.isEmpty()) continue; // Not even a white-only atlas for an empty Scene.
            String tag = SceneHudEnvironmentPaths.sceneTag(config.canonicalSceneTagFor(scene));
            if (!tags.add(tag.toLowerCase(Locale.ROOT)))
                throw new IllegalArgumentException("Ambiguous Scene HUD export tag: " + tag);
            scenes.put(tag, roots);
            allRoots.addAll(roots);
        }
        if (scenes.isEmpty()) return new PreparedExport(null, List.of());

        var database = AssetMetaDatabase.load(project.child(StudioFs.FILE_ASSETS_JSON));
        var collector = new SceneHudDependencyCollector();
        var closure = collector.collect(project, database, List.copyOf(allRoots), List.of());
        String capturedStamp = SceneHudBuildStamp.of(project, closure);
        // Snapshot asset/document objects once for the whole export, not separately between Scene builds.
        var screens = closure.selectedHudScreens().stream()
                .map(screen -> new SceneHudScreenSnapshot(screen.screenId(), screen.asset(), screen.document())).toList();
        FileHandle workspace = AtomicDirectoryPublication.createCandidate(project.child(StudioFs.DIR_ATLASES)
                .child("hud-export"));
        try {
            FileHandle sources = workspace.child("sources");
            SceneHudBuildStamp.copySources(project, closure, sources);
            closure = collector.collect(sources, database, List.copyOf(allRoots), screens);
            if (!capturedStamp.equals(SceneHudBuildStamp.of(sources, closure)))
                throw new IllegalStateException("Scene HUD sources changed while capturing export; retry export.");

            FileHandle artifacts = workspace.child("artifacts");
            var paths = new TreeSet<String>();
            Json json = new Json();
            json.setUsePrototypes(false);
            json.setTypeName(null);
            json.setOutputType(JsonWriter.OutputType.json);
            for (var screen : closure.selectedHudScreens()) {
                var asset = screen.asset();
                writeArtifact(artifacts, paths, screen.screenId() + HudScreenAsset.EXTENSION,
                        json.prettyPrint(asset).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                writeArtifact(artifacts, paths, asset.documentId,
                        new HudDocumentCodec().write(screen.document()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            for (var skin : closure.skinDependencies()) for (var file : skin.files()) {
                if (file.kind() == SceneHudDependencyClosure.FileKind.SKIN_JSON
                        || file.kind() == SceneHudDependencyClosure.FileKind.BITMAP_FONT_DESCRIPTOR)
                    writeArtifact(artifacts, paths, file.projectRelativePath(),
                            sources.child(file.projectRelativePath()).readBytes());
            }
            for (var font : closure.fontDependencies()) {
                writeArtifact(artifacts, paths,
                        HudBitmapFontResource.descriptorId(font.assetId()),
                        sources.child(font.descriptorPath()).readBytes());
            }
            for (var scene : scenes.entrySet()) {
                var selected = collector.collect(sources, database, scene.getValue(), screens);
                var plan = new SceneHudPackInputProjector().project(sources, selected);
                requireDrawableSources(sources, selected, plan);
                FileHandle inputs = workspace.child("inputs").child(scene.getKey());
                var manifest = new SceneHudPackInputMaterializer().materialize(sources, inputs, plan);
                FileHandle output = SceneHudEnvironmentPaths.liveDirectory(artifacts, scene.getKey());
                String descriptorPath = "atlases/hud/" + scene.getKey() + "/hud.atlas";
                if (paths.stream().anyMatch(path -> path.startsWith("atlases/hud/" + scene.getKey() + "/")))
                    throw new IllegalArgumentException("Authored artifact overlaps generated Scene HUD output: " + scene.getKey());
                builder.build(inputs, manifest, output);
                // Descriptor membership, not guessed filenames or a recursive output-directory copy.
                var atlas = new TextureAtlasData(output.child("hud.atlas"), output, false);
                paths.add(descriptorPath);
                for (var page : atlas.getPages()) paths.add("atlases/hud/" + scene.getKey() + "/" + page.name);
            }
            return new PreparedExport(workspace, List.copyOf(paths));
        } catch (RuntimeException | Error failure) {
            try { AtomicDirectoryPublication.discardCandidate(workspace); }
            catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    private static void requireDrawableSources(FileHandle sources, SceneHudDependencyClosure closure,
                                                SceneHudPackInputPlan plan) {
        // Source atlases are optional when Skin JSON or generated font/white regions supply a
        // drawable. But silently dropping an atlas needed for an undeclared region is not success.
        var generatedKeys = plan.entries().stream().map(entry -> entry.key().displayName()).toList();
        for (var screen : closure.selectedHudScreens()) {
            if (!screen.requiresSkin()) continue;
            String skinId = screen.asset().skinId.trim().replace('\\', '/');
            var skin = closure.skinDependencies().stream().filter(value -> value.skinId().equals(skinId))
                    .findFirst().orElseThrow();
            if (skin.files().stream().anyMatch(file -> file.kind() == SceneHudDependencyClosure.FileKind.SKIN_ATLAS)) continue;
            var document = new HudDocumentValidator().validate(screen.document()).validatedDocument();
            var skinJson = new JsonReader().parse(sources.child(skinId));
            for (var node : document.nodeIndex().values()) {
                HudImageReferences.visit(node, (ignored, fieldPath, image) ->
                        requireDrawableSource(screen.screenId(), fieldPath, image,
                                generatedKeys, skinJson));
            }
        }
    }

    private static void requireDrawableSource(String screenId, String fieldPath,
                                              games.pixscape.runtime.hud.document.HudImageData image,
                                              java.util.List<String> generatedKeys, JsonValue skinJson) {
                if (image == null || image.source != HudImageSource.DRAWABLE
                        || generatedKeys.contains(image.resourceName)) return;
                boolean declared = false;
                for (var section = skinJson.child; section != null; section = section.next)
                    if (section.isObject() && section.has(image.resourceName)) declared = true;
                if (!declared) throw new IllegalStateException("Scene HUD root '" + screenId
                        + "' field '" + fieldPath + "' is missing its source Skin atlas for DRAWABLE '"
                        + image.resourceName + "'.");
    }

    private static void writeArtifact(FileHandle artifacts, TreeSet<String> paths, String relative, byte[] bytes) {
        String canonical = relative.trim().replace('\\', '/');
        FileHandle target = child(artifacts, canonical);
        if (paths.contains(canonical)) {
            if (!Arrays.equals(target.readBytes(), bytes))
                throw new IllegalArgumentException("Conflicting authored HUD export artifact: " + canonical);
            return;
        }
        target.parent().mkdirs();
        target.writeBytes(bytes, false);
        paths.add(canonical);
    }

    private static FileHandle child(FileHandle root, String relative) {
        Path base = root.file().toPath().toAbsolutePath().normalize();
        Path target = base.resolve(relative).normalize();
        if (Path.of(relative).isAbsolute() || !target.startsWith(base) || target.equals(base))
            throw new IllegalArgumentException("HUD export artifact escapes its directory: " + relative);
        AtomicDirectoryPublication.rejectSymlinks(target);
        return new FileHandle(target.toFile());
    }

    /** Owns a private completed candidate; only the explicit Runtime artifact set can be copied. */
    public static final class PreparedExport implements AutoCloseable {
        private final FileHandle workspace;
        private final List<String> artifactPaths;
        private boolean closed;
        private PreparedExport(FileHandle workspace, List<String> artifactPaths) {
            this.workspace = workspace;
            this.artifactPaths = List.copyOf(artifactPaths);
        }
        public List<String> artifactPaths() { return artifactPaths; }
        public void copyTo(FileHandle runtimeDirectory) {
            if (closed) throw new IllegalStateException("HUD export preparation is closed.");
            for (String path : artifactPaths) {
                FileHandle target = child(runtimeDirectory, path);
                target.parent().mkdirs();
                child(workspace.child("artifacts"), path).copyTo(target);
            }
        }
        @Override public void close() {
            if (closed) return;
            if (workspace != null) AtomicDirectoryPublication.discardCandidate(workspace);
            closed = true;
        }
    }
}
