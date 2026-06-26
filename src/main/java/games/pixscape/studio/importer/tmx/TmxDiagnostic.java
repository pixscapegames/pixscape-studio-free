package games.pixscape.studio.importer.tmx;

public record TmxDiagnostic(TmxDiagnosticSeverity severity,
                            String code,
                            String message,
                            String location) {
}
