package games.pixscape.studio.ui.importer;

import com.badlogic.gdx.files.FileHandle;
import games.pixscape.studio.importer.tmx.TmxDiagnostic;
import games.pixscape.studio.importer.tmx.TmxDiagnosticSeverity;
import games.pixscape.studio.importer.tmx.TmxImportPlan;
import games.pixscape.studio.importer.tmx.TmxImportPlanRequest;
import games.pixscape.studio.importer.tmx.TmxImportPlanResult;
import games.pixscape.studio.importer.tmx.TmxImportPlanner;
import games.pixscape.studio.importer.tmx.TmxLayerPlan;
import games.pixscape.studio.importer.tmx.TmxSceneImportResult;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class TmxImportUiSupport {

    private TmxImportUiSupport() {
    }

    public static TmxImportPreparation prepare(FileHandle tmxFile) {
        TmxImportPlanResult result = new TmxImportPlanner().plan(new TmxImportPlanRequest(tmxFile));
        return new TmxImportPreparation(tmxFile, result);
    }

    public static String resolveSceneName(String proposedSceneName, String rawInput) {
        String candidate = rawInput == null ? proposedSceneName : rawInput;
        candidate = candidate != null ? candidate.trim() : "";
        if (candidate.isBlank()) {
            throw new IllegalArgumentException("Scene name is required.");
        }
        return candidate;
    }

    public static String formatDiagnostics(List<TmxDiagnostic> diagnostics) {
        if (diagnostics == null || diagnostics.isEmpty()) {
            return "No diagnostics.";
        }

        List<TmxDiagnostic> sorted = diagnostics.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator
                        .comparingInt((TmxDiagnostic d) -> severityRank(d.severity()))
                        .thenComparing(d -> d.code() != null ? d.code() : "")
                        .thenComparing(d -> d.message() != null ? d.message() : ""))
                .toList();

        if (sorted.isEmpty()) {
            return "No diagnostics.";
        }

        StringBuilder out = new StringBuilder();
        for (int i = 0; i < sorted.size(); i++) {
            TmxDiagnostic d = sorted.get(i);
            if (i > 0) out.append('\n');
            out.append("- ")
                    .append(d.severity() != null ? d.severity() : TmxDiagnosticSeverity.INFO)
                    .append(" ")
                    .append(d.code() != null && !d.code().isBlank() ? d.code() : "TMX_DIAGNOSTIC")
                    .append(": ")
                    .append(d.message() != null && !d.message().isBlank() ? d.message() : "No message.");
            if (d.location() != null && !d.location().isBlank()) {
                out.append(" (").append(d.location()).append(")");
            }
        }
        return out.toString();
    }

    public static String formatSuccessMessage(TmxSceneImportResult result) {
        String sceneName = result != null && result.sceneName() != null && !result.sceneName().isBlank()
                ? result.sceneName()
                : "Imported TMX";
        int warnings = warningCount(result != null ? result.diagnostics() : List.of());
        if (warnings > 0) {
            return "Tiled map imported as scene '" + sceneName + "'. " + warnings + " warning"
                    + (warnings == 1 ? "" : "s") + " reported.";
        }
        return "Tiled map imported as scene '" + sceneName + "'.";
    }

    public static String formatFailureMessage(TmxSceneImportResult result) {
        if (result == null) {
            return "Tiled map import failed.";
        }

        StringBuilder out = new StringBuilder("Tiled map import failed: ")
                .append(result.status());

        if (result.failure() != null && result.failure().getMessage() != null
                && !result.failure().getMessage().isBlank()) {
            out.append('\n').append(result.failure().getMessage());
        }

        if (result.diagnostics() != null && !result.diagnostics().isEmpty()) {
            out.append('\n').append(formatDiagnostics(result.diagnostics()));
        }
        return out.toString();
    }

    public static int warningCount(List<TmxDiagnostic> diagnostics) {
        if (diagnostics == null) return 0;
        int count = 0;
        for (TmxDiagnostic diagnostic : diagnostics) {
            if (diagnostic != null && diagnostic.severity() == TmxDiagnosticSeverity.WARNING) {
                count++;
            }
        }
        return count;
    }

    static int severityRank(TmxDiagnosticSeverity severity) {
        if (severity == TmxDiagnosticSeverity.BLOCKING) return 0;
        if (severity == TmxDiagnosticSeverity.WARNING) return 1;
        return 2;
    }

    public record TmxImportPreparation(FileHandle tmxFile,
                                       TmxImportPlanResult planResult) {

        public TmxImportPreparation {
            Objects.requireNonNull(planResult, "planResult");
        }

        public boolean hasBlockingDiagnostics() {
            return planResult.preflightReport() != null
                    && planResult.preflightReport().hasBlockingDiagnostics();
        }

        public TmxImportPlan plan() {
            return planResult.plan();
        }

        public String proposedSceneName() {
            return plan() != null && plan().scene() != null ? plan().scene().proposedSceneName() : "Imported TMX";
        }

        public int tilesetCount() {
            return plan() != null ? plan().tilesets().size() : 0;
        }

        public int tileLayerCount() {
            return plan() != null && plan().scene() != null ? plan().scene().tileLayerCount() : 0;
        }

        public int importedLayerCount() {
            return plan() != null ? (int) plan().layers().stream().filter(TmxLayerPlan.class::isInstance).count() : 0;
        }

        public List<TmxDiagnostic> diagnostics() {
            return planResult.preflightReport() != null
                    ? planResult.preflightReport().diagnostics()
                    : List.of();
        }

        public int warningCount() {
            return TmxImportUiSupport.warningCount(diagnostics());
        }
    }
}
