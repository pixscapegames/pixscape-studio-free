package games.pixscape.studio.service.asset;

import com.badlogic.gdx.files.FileHandle;

import java.util.Collections;
import java.util.List;

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
                                   int columns,
                                   List<ImageCollectionTile> imageCollectionTiles,
                                   List<TileAnimation> tileAnimations) {

    public TsxTilesetDescriptor {
        imageCollectionTiles = imageCollectionTiles == null
                ? List.of()
                : Collections.unmodifiableList(List.copyOf(imageCollectionTiles));
        tileAnimations = tileAnimations == null
                ? List.of()
                : Collections.unmodifiableList(List.copyOf(tileAnimations));
    }

    public boolean imageCollection() {
        return !imageCollectionTiles.isEmpty();
    }

    public record ImageCollectionTile(int localTileId,
                                      FileHandle imageFile,
                                      String imageSource,
                                      int imageWidth,
                                      int imageHeight) {
    }

    public record TileAnimation(int baseLocalTileId, List<Frame> frames) {
        public TileAnimation {
            frames = frames == null ? List.of() : Collections.unmodifiableList(List.copyOf(frames));
        }
    }

    public record Frame(int localTileId, int durationMs) {
    }
}
