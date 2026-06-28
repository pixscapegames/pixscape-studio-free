package games.pixscape.studio.service.asset;

import com.badlogic.gdx.files.FileHandle;

public record TsxTilesetDescriptor(String name,
                                   FileHandle tsxFile,
                                   FileHandle imageFile,
                                   String imageSource,
                                   int imageWidth,
                                   int imageHeight,
                                   int tileWidth,
                                   int tileHeight,
                                   int spacing,
                                   int margin,
                                   int tileCount,
                                   int columns) {
}
