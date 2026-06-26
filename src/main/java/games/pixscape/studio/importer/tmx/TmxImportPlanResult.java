package games.pixscape.studio.importer.tmx;

import java.util.List;

public record TmxImportPlanResult(TmxImportPlanStatus status,
                                  TmxPreflightReport preflightReport,
                                  TmxImportPlan plan) {

    public boolean hasPlan() {
        return plan != null;
    }

    public List<TmxDiagnostic> blockingDiagnostics() {
        return preflightReport.diagnostics().stream()
                .filter(d -> d.severity() == TmxDiagnosticSeverity.BLOCKING)
                .toList();
    }
}
