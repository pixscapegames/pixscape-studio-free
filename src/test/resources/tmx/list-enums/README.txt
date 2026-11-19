Hand-authored TMX/TSX and project fixtures, not saved by the Tiled application.
Syntax follows Tiled v1.12.2 readPropertyValue/writePropertyValue and EnumPropertyType:
https://github.com/mapeditor/tiled/blob/v1.12.2/src/libtiled/mapreader.cpp
https://github.com/mapeditor/tiled/blob/v1.12.2/src/libtiled/mapwriter.cpp
https://github.com/mapeditor/tiled/blob/v1.12.2/src/libtiled/propertytype.cpp
Images reuse the existing mixed-layers witnesses. OBJECT IDs are map-local; tile
properties use only None because tile-collision object references are unsupported.
