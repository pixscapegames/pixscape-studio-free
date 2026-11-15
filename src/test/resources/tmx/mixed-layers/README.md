# Mixed-layer overlap witness

Import `mixed-layers.tmx` through Studio's normal File > Import > Tiled map menu.
The 2x2 orthogonal map has 16x16 cells. Bottom red and Top blue share cell (0,0)
in TMX coordinates. An image layer (yellow inset) and object layer (green diamond)
are interleaved. Blue must draw last. Middle green has the local `purpose` property;
Green diamond has integer `health=7`. The small PNGs are self-contained fixtures.

The integration test also adds an object-valued reference to the map before
copying, then verifies its remapping, history restoration and save/reload.
