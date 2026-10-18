# Actor shadow authoring

Select one sprite or animation and enable Lighting > Cast shadow. Its foot
directrix and two handles appear on that selected actor in the canvas. Drag
either handle freely over the displayed frame; camera, zoom, image transforms,
offsets and flips use the existing canvas conversions. The gesture preserves
the actor position and selection. Escape or touch-focus cancellation restores
the candidate; completion creates one Undo/Redo action.

The segment describes a foot line, not a silhouette crop or a width reference.
Coincident endpoints are rejected. Threshold controls caster alpha rejection;
Thickness ranges from 0 to 0.5. Zero uses the exact calibrated sheet. Increasing
it folds that single sheet while retaining its foot contacts and source alpha;
maximum thickness approximates stable breadth around the actor. Actual breadth
depends on alpha coverage, light altitude and receiving surface.

Static images store their profile on the actor. Animations store profiles by
animation asset and clip, independently of the current frame and atlas packing.
Copies, Game Objects, scene save/reload and Undo/Redo preserve those settings.
A clip without a profile retains its fallback support, without borrowing a
different clip's profile.

Actors cast onto decor and receive occlusion only from decor. Actor-to-actor
shadows and actor self-shadowing are excluded. Ambient and normal mapping remain
available. Vertical structure joins align height and vertical faces; linked
image-anchor cells do not require upper walls to become thinner.

## Rendering limits and validation

Shared-corner reception is selected geometrically, independently of volume
insertion order. Curved/nonrectilinear tiles still use approximate supports;
source alpha does not reconstruct missing physical depth. The 3–5 degree
grazing ramp preserves ambient, but existing PCF 256 visibility quantization
can still create a residual step inside that angular range.

Native desktop Studio and Preview were executed with the local Runtime.
Runtime Java 8 and both HTML players' GWT compilation were checked. Desktop
ES300 shader compilation is not GLES execution; no real WebGL or Android GLES
validation is claimed. These changes do not establish a general FPS gain.
