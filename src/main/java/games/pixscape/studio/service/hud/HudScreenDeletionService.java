package games.pixscape.studio.service.hud;

import com.badlogic.gdx.files.FileHandle;
import games.pixscape.runtime.hud.HudScreenAsset;
import games.pixscape.runtime.hud.HudScreenAssetId;
import games.pixscape.runtime.hud.HudScreenAssetLoader;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.configuration.SceneMeta;
import games.pixscape.studio.io.StudioFs;
import games.pixscape.studio.service.runtimeavailability.SceneHudRoots;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Verifies Scene HUD references and removes one unreferenced HUD descriptor safely.
 *
 * <p>The live configuration covers open Scene edits while a second, freshly loaded
 * configuration covers references which are still persisted on disk.  Both are needed:
 * an in-memory removal must not make a still-persisted reference disappear from this
 * check.</p>
 */
public final class HudScreenDeletionService {
    static final String SCENE_ASSOCIATION = "Scene HUD association";

    @FunctionalInterface
    interface FileMover {
        void move(Path source, Path target) throws IOException;
    }

    @FunctionalInterface
    interface FileDeleter {
        void delete(Path target) throws IOException;
    }

    public record SceneReference(String sceneName, String referenceType) {}

    public record UsageReport(String screenId, List<SceneReference> references) {
        public UsageReport {
            screenId = HudScreenAssetId.normalize(screenId);
            references = List.copyOf(references);
        }
        public boolean used() { return !references.isEmpty(); }
    }

    /** A successful move phase is acquired even when best-effort temporary-file cleanup fails. */
    public record DeletionResult(String screenId, boolean documentDeleted,
                                 boolean deletionAcquired, List<String> temporaryFilesRemaining) {
        public DeletionResult {
            screenId = HudScreenAssetId.normalize(screenId);
            temporaryFilesRemaining = List.copyOf(temporaryFilesRemaining);
        }
        public boolean hasCleanupWarning() { return !temporaryFilesRemaining.isEmpty(); }
    }

    public static final class HudScreenInUseException extends IllegalStateException {
        private final UsageReport usage;

        HudScreenInUseException(UsageReport usage) {
            super("HUD screen '" + usage.screenId() + "' is still used by "
                    + usage.references().size() + " scene(s).");
            this.usage = usage;
        }

        public UsageReport usage() { return usage; }
    }

    private final HudScreenAssetLoader loader;
    private final FileMover mover;
    private final FileDeleter deleter;

    public HudScreenDeletionService() {
        this(new HudScreenAssetLoader(),
                (source, target) -> Files.move(source, target, StandardCopyOption.REPLACE_EXISTING),
                Files::delete);
    }

    HudScreenDeletionService(HudScreenAssetLoader loader, FileMover mover) {
        this(loader, mover, Files::delete);
    }

    HudScreenDeletionService(HudScreenAssetLoader loader, FileMover mover, FileDeleter deleter) {
        this.loader = Objects.requireNonNull(loader, "loader");
        this.mover = Objects.requireNonNull(mover, "mover");
        this.deleter = Objects.requireNonNull(deleter, "deleter");
    }

    /** Reads both live and persisted Scene metadata and returns each using Scene once. */
    public UsageReport inspectUsage(FileHandle projectDir, ProjectConfig liveConfiguration,
                                    String screenId) {
        String canonicalId = HudScreenAssetId.normalize(screenId);
        requireProjectDir(projectDir);
        if (liveConfiguration == null) {
            throw new IllegalStateException("Cannot verify HUD screen references: project configuration is unavailable.");
        }

        ProjectConfig persisted = loadPersistedConfiguration(projectDir, liveConfiguration);
        LinkedHashMap<String, SceneReference> references = new LinkedHashMap<>();
        collectSceneReferences(liveConfiguration, canonicalId, references);
        collectSceneReferences(persisted, canonicalId, references);
        List<SceneReference> sorted = new ArrayList<>(references.values());
        sorted.sort(Comparator.comparing(SceneReference::sceneName)
                .thenComparing(SceneReference::referenceType));
        return new UsageReport(canonicalId, sorted);
    }

    /** Revalidates references immediately before moving the descriptor and its document out of the project. */
    public DeletionResult delete(FileHandle projectDir, ProjectConfig liveConfiguration, String screenId) {
        UsageReport usage = inspectUsage(projectDir, liveConfiguration, screenId);
        if (usage.used()) throw new HudScreenInUseException(usage);

        Targets targets = resolveTargets(projectDir, usage.screenId());
        List<String> remainingTemporaryFiles = moveOutWithRollback(targets);
        return new DeletionResult(usage.screenId(), targets.deleteDocument, true,
                remainingTemporaryFiles);
    }

    private ProjectConfig loadPersistedConfiguration(FileHandle projectDir, ProjectConfig liveConfiguration) {
        if (liveConfiguration.projectFileName == null || liveConfiguration.projectFileName.isBlank()) {
            throw new IllegalStateException("Cannot verify HUD screen references: project file name is unavailable.");
        }
        FileHandle projectFile = projectDir.child(liveConfiguration.projectFileName + StudioFs.EXT_JSON);
        if (!projectFile.exists()) {
            throw new IllegalStateException("Cannot verify HUD screen references: project file is missing: "
                    + projectFile.path());
        }
        try {
            return ProjectConfig.ProjectIO.loadProject(projectFile);
        } catch (RuntimeException failure) {
            throw new IllegalStateException("Cannot verify HUD screen references because project file cannot be read: "
                    + projectFile.path(), failure);
        }
    }

    private static void collectSceneReferences(ProjectConfig configuration, String screenId,
                                               LinkedHashMap<String, SceneReference> output) {
        for (String sceneName : configuration.getSceneNames()) {
            SceneMeta scene = configuration.getSceneMeta(sceneName);
            final List<String> roots;
            try {
                roots = SceneHudRoots.collect(scene);
            } catch (RuntimeException failure) {
                throw new IllegalStateException("Cannot verify HUD screen references in scene '"
                        + sceneName + "': invalid HUD association.", failure);
            }
            if (roots.contains(screenId)) {
                SceneReference reference = new SceneReference(sceneName, SCENE_ASSOCIATION);
                output.putIfAbsent(sceneName + "\u0000" + SCENE_ASSOCIATION, reference);
            }
        }
    }

    private Targets resolveTargets(FileHandle projectDir, String screenId) {
        Path root = requireProjectDir(projectDir);
        String assetName = HudScreenAssetId.assetName(screenId);
        Path descriptor = resolveInside(root, HudScreenAssetId.DIRECTORY + "/"
                + assetName + HudScreenAsset.EXTENSION, "HUD screen descriptor");
        if (!Files.isRegularFile(descriptor)) {
            throw new IllegalStateException("HUD screen descriptor is missing: " + descriptor);
        }
        descriptor = resolveExistingInside(root, descriptor, "HUD screen descriptor");

        final HudScreenAsset asset;
        try {
            asset = loader.load(projectDir, screenId);
        } catch (RuntimeException failure) {
            throw new IllegalStateException("Unable to read HUD screen descriptor '" + screenId + "'.", failure);
        }
        Path document = resolveInside(root, asset.documentId, "HUD authoring document");
        if (!Files.isRegularFile(document)) {
            throw new IllegalStateException("HUD authoring document is missing: " + document);
        }
        document = resolveExistingInside(root, document, "HUD authoring document");

        boolean documentShared = isDocumentReferencedByAnotherHud(projectDir, root, descriptor, document);
        return new Targets(descriptor, document, !documentShared);
    }

    private boolean isDocumentReferencedByAnotherHud(FileHandle projectDir, Path root,
                                                       Path targetDescriptor, Path targetDocument) {
        FileHandle directory = projectDir.child(HudScreenAssetId.DIRECTORY);
        if (!directory.exists()) return false;
        for (FileHandle candidate : directory.list(HudScreenAsset.EXTENSION.substring(1))) {
            if (candidate == null || candidate.isDirectory()) continue;
            Path candidatePath = resolveExistingInside(root,
                    candidate.file().toPath().toAbsolutePath().normalize(),
                    "HUD screen descriptor " + candidate.name());
            if (candidatePath.equals(targetDescriptor)) continue;
            String candidateId;
            try {
                candidateId = HudScreenAssetId.normalize(HudScreenAssetId.DIRECTORY + "/"
                        + candidate.nameWithoutExtension());
                HudScreenAsset other = loader.load(projectDir, candidateId);
                Path otherDocument = resolveInside(root, other.documentId,
                        "HUD authoring document referenced by " + candidate.name());
                if (otherDocument.equals(targetDocument)) return true;
            } catch (RuntimeException failure) {
                throw new IllegalStateException("Cannot verify whether the HUD authoring document is shared: "
                        + candidate.path(), failure);
            }
        }
        return false;
    }

    /**
     * First moves every source to a reversible sibling path.  Only after all moves succeed is
     * deletion acquired; cleanup failures then leave recoverable temporary files, not a partial
     * rollback state.
     */
    private List<String> moveOutWithRollback(Targets targets) {
        List<Move> staged = new ArrayList<>();
        try {
            staged.add(stage(targets.descriptor));
            if (targets.deleteDocument) staged.add(stage(targets.document));
        } catch (IOException failure) {
            List<Path> unrestored = rollback(staged, failure);
            String recovery = unrestored.isEmpty() ? "" : " Temporary files requiring recovery: "
                    + joinPaths(unrestored);
            throw new IllegalStateException("Unable to delete HUD screen files without leaving a broken descriptor."
                    + recovery, failure);
        }
        return cleanupStagedFiles(staged);
    }

    private Move stage(Path source) throws IOException {
        Path staged = source.resolveSibling(source.getFileName() + ".delete-" + UUID.randomUUID());
        mover.move(source, staged);
        return new Move(source, staged);
    }

    private List<String> cleanupStagedFiles(List<Move> staged) {
        List<String> remaining = new ArrayList<>();
        for (Move move : staged) {
            try {
                deleter.delete(move.staged());
            } catch (IOException failure) {
                remaining.add(move.staged().toString());
            }
        }
        return remaining;
    }

    private List<Path> rollback(List<Move> staged, IOException primary) {
        List<Path> unrestored = new ArrayList<>();
        for (int index = staged.size() - 1; index >= 0; index--) {
            Move move = staged.get(index);
            try {
                if (Files.exists(move.staged())) {
                    if (!Files.exists(move.source())) {
                        mover.move(move.staged(), move.source());
                    }
                    if (Files.exists(move.staged())) {
                        unrestored.add(move.staged());
                    }
                }
            } catch (IOException rollbackFailure) {
                primary.addSuppressed(rollbackFailure);
                unrestored.add(move.staged());
            }
        }
        return unrestored;
    }

    private static String joinPaths(List<Path> paths) {
        return paths.stream().map(Path::toString).reduce((left, right) -> left + ", " + right).orElse("");
    }

    private static Path requireProjectDir(FileHandle projectDir) {
        if (projectDir == null) throw new IllegalArgumentException("Project directory is required.");
        Path root = projectDir.file().toPath().toAbsolutePath().normalize();
        try {
            if (!Files.isDirectory(root)) throw new IllegalStateException("Project directory is unavailable: " + root);
            return root.toRealPath();
        } catch (IOException failure) {
            throw new IllegalStateException("Unable to resolve project directory: " + root, failure);
        }
    }

    private static Path resolveInside(Path root, String projectRelativePath, String description) {
        if (projectRelativePath == null || projectRelativePath.isBlank()) {
            throw new IllegalStateException(description + " path is missing.");
        }
        Path resolved = root.resolve(projectRelativePath).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalStateException(description + " must remain inside the project directory.");
        }
        return resolved;
    }

    private static Path resolveExistingInside(Path root, Path candidate, String description) {
        try {
            Path real = candidate.toRealPath();
            if (!real.startsWith(root)) {
                throw new IllegalStateException(description + " must remain inside the project directory.");
            }
            return real;
        } catch (IOException failure) {
            throw new IllegalStateException("Unable to resolve " + description + ": " + candidate, failure);
        }
    }

    private record Targets(Path descriptor, Path document, boolean deleteDocument) {}
    private record Move(Path source, Path staged) {}
}
