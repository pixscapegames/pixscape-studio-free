package games.pixscape.studio.ui.main;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.kotcrab.vis.ui.widget.VisImage;
import com.kotcrab.vis.ui.widget.VisTextButton;
import games.pixscape.studio.asset.TileAssetMeta;
import games.pixscape.studio.importer.tmx.TmxSceneImportRequest;
import games.pixscape.studio.ui.asset.AssetNode;
import games.pixscape.studio.ui.property.PropertiesPanel;
import games.pixscape.studio.ui.property.entityproperties.EditPropertiesDialog;
import org.junit.Test;

import static org.junit.Assert.*;

/** Real GL rendering and Scene2D pointer input, without native desktop input. */
public class TilePropertiesGlTest {
    @Test
    public void gridAndCollectionTilesHaveTheirOwnPreviewAndTheEditorResizesByDragging() throws Exception {
        ResourceAssetInspectorGlTest.withStudio(900, (studio, root) -> {
            var project = ResourceAssetInspectorGlTest.project(studio, root.child("project"));
            ResourceAssetInspectorGlTest.open(studio, project);
            Pixmap pixels = new Pixmap(64, 16, Pixmap.Format.RGBA8888);
            pixels.setColor(Color.RED);
            pixels.fillRectangle(0, 0, 32, 16);
            pixels.setColor(Color.GREEN);
            pixels.fillRectangle(32, 0, 32, 16);
            PixmapIO.writePNG(root.child("grid.png"), pixels);
            pixels.dispose();
            pixels = new Pixmap(16, 32, Pixmap.Format.RGBA8888);
            pixels.setColor(Color.BLUE);
            pixels.fill();
            PixmapIO.writePNG(root.child("collection.png"), pixels);
            pixels.dispose();
            for (boolean collection : new boolean[]{false, true}) {
                FileHandle tmx = root.child(collection ? "collection.tmx" : "grid.tmx");
                String properties = "<properties>";
                for (int i = 0; i < 25; i++) {
                    properties += "<property name='value" + i + "' value='Text " + i + "'/>";
                }
                properties += "</properties>";
                tmx.writeString("<map orientation='orthogonal' width='1' height='1' tilewidth='32' tileheight='16'>"
                        + (collection ? "<tileset firstgid='1' name='collection' tilewidth='16' tileheight='32' tilecount='1' columns='0'>"
                        + "<tile id='7'><image source='collection.png' width='16' height='32'/>" + properties + "</tile></tileset>"
                        : "<tileset firstgid='1' name='grid' tilewidth='32' tileheight='16' tilecount='2' columns='2'>"
                        + "<image source='grid.png' width='64' height='16'/><tile id='1'>" + properties + "</tile></tileset>")
                        + "<layer name='Ground' width='1' height='1'><data encoding='csv'>"
                        + (collection ? 8 : 2) + "</data></layer></map>", false);
                var imported = studio.getSceneService().importTmxAsNewScene(new TmxSceneImportRequest(tmx,
                        collection ? "Collection" : "Grid", true));
                assertTrue(String.valueOf(imported.failure()), imported.imported());
                studio.render();
                studio.render();
                var database = studio.getSceneService().getAssetMetaDatabase();
                TileAssetMeta tile = null;
                for (int i = 0; i < database.size(); i++) {
                    if (database.assetAt(i) instanceof TileAssetMeta candidate
                            && candidate.sheetIndex == (collection ? 7 : 1)) {
                        tile = candidate;
                    }
                }
                assertNotNull(tile);
                var panel = (PropertiesPanel) ResourceAssetInspectorGlTest.field(studio, "propertiesPanel");
                panel.inspectAsset(AssetNode.fromAssetMeta(AssetNode.Kind.REGION, AssetNode.Root.TILES,
                        tile.sourceRelPath(), tile));
                var inspector = panel.inspectedTileProperties();
                assertNotNull(inspector);
                var image = (VisImage) inspector.findActor("tileAssetPreview");
                ResourceAssetInspectorGlTest.screenshot(studio, collection ? "tile-collection-preview" : "tile-grid-preview");
                var region = ((TextureRegionDrawable) image.getDrawable()).getRegion();
                assertEquals(collection ? 16 : 32, region.getRegionWidth());
                assertEquals(collection ? 32 : 16, region.getRegionHeight());
                assertEquals(collection ? .5f : 2f, image.getImageWidth() / image.getImageHeight(), .001f);
                assertTrue(image.getImageWidth() <= 160 && image.getImageHeight() <= 160);
                int handle = region.getTexture().getTextureObjectHandle();
                var min = region.getTexture().getMinFilter();
                var mag = region.getTexture().getMagFilter();
                ((VisTextButton) inspector.findActor("editTileCustomProperties")).fire(new ChangeListener.ChangeEvent());
                var stage = studio.getUiStage();
                var dialog = (EditPropertiesDialog) stage.getActors().peek();
                stage.act(0);
                stage.act(1);
                stage.act(0);
                assertEquals(1f, dialog.getColor().a, 0f);
                dialog.setPosition(80, 280);
                ResourceAssetInspectorGlTest.screenshot(studio, "tile-custom-properties-before");
                float oldWidth = dialog.getWidth(), oldHeight = dialog.getHeight();
                Vector2 screen = stage.stageToScreenCoordinates(new Vector2(dialog.getRight() - 2, dialog.getY() + 2));
                stage.touchDown(Math.round(screen.x), Math.round(screen.y), 0, Input.Buttons.LEFT);
                stage.touchDragged(Math.round(screen.x + 180), Math.round(screen.y + 130), 0);
                stage.touchUp(Math.round(screen.x + 180), Math.round(screen.y + 130), 0, Input.Buttons.LEFT);
                ResourceAssetInspectorGlTest.screenshot(studio, "tile-custom-properties-resized");
                assertTrue(dialog.getWidth() > oldWidth + 150);
                assertTrue(dialog.getHeight() > oldHeight + 100);
                dialog.remove();
                panel.inspectAsset(null);
                assertEquals(handle, region.getTexture().getTextureObjectHandle());
                assertEquals(min, region.getTexture().getMinFilter());
                assertEquals(mag, region.getTexture().getMagFilter());
            }
        });
    }
}
