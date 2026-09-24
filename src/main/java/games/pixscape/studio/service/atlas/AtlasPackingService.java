package games.pixscape.studio.service.atlas;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.tools.texturepacker.TexturePacker;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData;
import games.pixscape.runtime.hud.HudTextureProfile;
import java.io.IOException;

/** One TexturePacker configuration authority for generated Studio atlases. */
public final class AtlasPackingService {
    private AtlasPackingService() {
    }

    static void packScene(FileHandle inputDir, FileHandle outputDir, String atlasName) {
        TexturePacker.process(fixedPageSettings(), inputDir.path(), outputDir.path(), atlasName);
    }

    public static void packHud(FileHandle inputDir, FileHandle outputDir, String atlasName,
                        HudTextureProfile profile) {
        TexturePacker.process(hudSettings(profile), inputDir.path(), outputDir.path(), atlasName);
    }

    static TexturePacker.Settings hudSettings(HudTextureProfile profile) {
        TexturePacker.Settings settings = fixedPageSettings();
        settings.maxWidth = profile.pageWidth();
        settings.maxHeight = profile.pageHeight();
        settings.minWidth = profile.pageWidth();
        settings.minHeight = profile.pageHeight();
        settings.filterMin = profile.minFilter();
        settings.filterMag = profile.magFilter();
        settings.wrapX = profile.uWrap();
        settings.wrapY = profile.vWrap();
        settings.format = profile.outputFormat();
        return settings;
    }

    /** Shared GL-free validation, independent of membership or logical region identity. */
    public static void validateHudPages(TextureAtlasData data, HudTextureProfile profile) {
        if (data.getPages().size == 0) throw new IllegalStateException("HUD atlas has no pages.");
        for (TextureAtlasData.Page page : data.getPages()) {
            if (page.textureFile == null || !page.textureFile.exists() || page.textureFile.length() <= 0)
                throw new IllegalStateException("HUD atlas page file is missing or empty.");
            if ((int) page.width != profile.pageWidth() || (int) page.height != profile.pageHeight())
                throw new IllegalStateException("HUD atlas page does not match profile dimensions.");
            if (page.minFilter != profile.minFilter() || page.magFilter != profile.magFilter()
                    || page.uWrap != profile.uWrap() || page.vWrap != profile.vWrap()
                    || page.useMipMaps != profile.useMipMaps())
                throw new IllegalStateException("HUD atlas page does not match profile sampler policy.");
            try {
                var image = javax.imageio.ImageIO.read(page.textureFile.file());
                if (image == null || image.getWidth() != profile.pageWidth() || image.getHeight() != profile.pageHeight())
                    throw new IllegalStateException("HUD atlas page pixels do not match profile dimensions.");
            } catch (IOException failure) { throw new IllegalStateException("Invalid HUD atlas page pixels", failure); }
        }
    }

    private static TexturePacker.Settings fixedPageSettings() {
        TexturePacker.Settings settings = new TexturePacker.Settings();
        settings.maxWidth = 2048;
        settings.maxHeight = 2048;
        settings.minWidth = 2048;
        settings.minHeight = 2048;
        settings.duplicatePadding = true;
        settings.edgePadding = true;
        settings.combineSubdirectories = true;
        settings.silent = true;
        return settings;
    }
}
