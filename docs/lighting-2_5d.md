# Lighting 2.5D authoring

Point and cone inspectors expose source height, shadow quality (None, Hard,
PCF) and resolution (256, 512, 1024). Height and radius use the Runtime common
XYZ pixel metric. Existing position, color, intensity, direction and opening
remain authored through their existing controls.

The Lighting section for a sprite/animation exposes receiving light, casting a
shadow, altitude, normalized foot U/V, horizontal sheet direction X/Y and alpha
threshold. Defaults receive light but do not cast. Use the same foot in every
untrimmed animation frame; transparent margins remain part of the surface.
Do not turn the sheet to follow the source: lateral lights reveal its lack of
thickness. These controls use stable history identity, dirty notifications and
undo/redo, including removal of a newly created optional component on undo.

Tiled map `Altitude:` is the absolute altitude of its drawn reference plane.
Lighting uses the same convention: physical Z equals authored absolute altitude.
`Default height:` initializes new volumes and is updated atomically when an
ensemble's height changes; unrelated ensembles are never resized. There is no
separate Lighting plane Z control. Feature-era files with the removed
`lightingPlaneAltitude` field require explicit conversion before load/export.
See Runtime `docs/lighting-2_5d.md` for the full XYZ and local-description contract.

Spatial block Shadow caster or Light occluder enables its complete box as a
shadow obstacle. Actor occluder still controls image ordering separately.
Existing false flags remain false; lighting does not force all walls to cast.

F8 in the world canvas cycles positions, normals, approximated/missing coverage
and normal rendering. It is transient, starts disabled, and is not saved or
exported. Coverage shows amber approximations, green local descriptions and
magenta unknown surfaces. Black original channels limit diagnostic colors.

Surface placements and optional local XYZ/UV/triangle/normal descriptions are
included in scene and Game Object data, copies and snapshots. Map-local
descriptions replace automatic geometry per asset; this stage has no mesh
editor. Free and Pro share the same public Runtime consumer and history behavior.

Standard ALPHA/PREMULT_ALPHA contours receive light. Additive effects retain
their original/ambient behavior. Standard particles with no declared receiver
have ambient-only coverage. Active-light rendering rejects custom/repeated
coverage and custom source shaders until explicit adapters exist; user GPU
parameter rows are preserved. Transformed volume tiles and non-ISO volume
reconstruction also fail explicitly. See the Runtime contract for details.

GPU resources are prepared at the actual canvas frame boundary. Loading,
scene/tab activation and ordinary World processing do not authorize submission.
The world pass restores the caller target/viewport/scissor and aborts pending
batches on error; editor overlays follow the lit world.

For local validation without publishing Runtime, use Gradle composite builds:

```powershell
.\gradlew.bat --include-build ..\pixscape-runtime test studioLightCompositionGlSmoke
.\gradlew.bat --include-build ..\pixscape-runtime :html-player:compileGwt
```

The desktop GL regression covers fractional coverage, multiple contributions,
standard materials, scoped authorization, interruption, resize, nested targets,
material failure and recovery. GWT compilation is distinct from a real WebGL
test; Android/WebGL device sessions and manual EDIT/TEST/Game Object UI sessions
must be reported separately from automated tests.

## Vertical Raccorder

Select one volume and choose **Raccorder**. The native dialog searches above
and below, also across maps, and shows all compatible references for choice.
The selected connected ensemble adapts; the reference ensemble stays fixed.
Below keeps its base and reaches the reference base. Above keeps its top and
moves its base to the reference top. All resulting heights must be positive.
Parallel connected bands align to the reference band. Perpendicular bands
retain thickness and transverse position; endpoints adjust only where required
to retain corners. The complete detached preview shows every before/after
footprint, altitude, top, height and Default height, or a blocking conflict.
Cancel discards it. Apply is one undo/redo operation, with stale map, reference,
geometry and physics context refusal; redo restores the recorded result.
No vertical link, map fusion, tile reassignment or image movement is created.

Only compatible ISO axes/aspect ratios and layer parallax are searched. Cell
sizes may differ and layers/render indices do not define floors. Search uses
positive longitudinal overlap and a transverse centre distance of at most
the larger of one selected-map cell and either band thickness. Nearby choices
remain explicit; sorting by vertical/transverse gap does not select a floor.
ORTHO volumes, mixed metrics/parallax, ambiguous square orientations, invalid
linked regions, contradictory corner constraints and new disjoint connections
are refused. The command does not fix a map's drawn-plane convention or rebase
its data. Perform that reviewed preparation separately when necessary.
