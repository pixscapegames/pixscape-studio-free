package games.pixscape.studio.ui.property;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FileTextureData;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Container;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.Scaling;
import com.kotcrab.vis.ui.widget.VisImage;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.VisTextButton;
import games.pixscape.runtime.hud.HudScreenAsset;
import games.pixscape.runtime.hud.HudScreenAssetId;
import games.pixscape.runtime.gameobject.GameObjectAssetId;
import games.pixscape.runtime.gameobject.GameObjectAssetLoader;
import games.pixscape.studio.asset.AssetDisplayInfo;
import games.pixscape.studio.asset.AssetMeta;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.document.EditorDocumentKey;
import games.pixscape.studio.document.EditorDocumentType;
import games.pixscape.studio.document.HudScreenEditorDocument;
import games.pixscape.studio.document.GameObjectEditorDocument;
import games.pixscape.studio.document.SceneEditorDocument;
import games.pixscape.studio.io.StudioFs;
import games.pixscape.studio.service.hud.HudDocumentPersistenceService;
import games.pixscape.studio.service.hud.HudInspectorProjection;
import games.pixscape.studio.service.asset.StudioAssetVisual;
import games.pixscape.studio.service.gameobject.GameObjectPreviewCapture;
import games.pixscape.studio.ui.asset.AssetNode;
import games.pixscape.studio.ui.main.StudioApplicationAdapter;
import games.pixscape.studio.ui.widget.CollapsibleVisTable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Read-only resource details with owned font/skin/particle previews and borrowed image textures. Docking retains the target
 * and previews; PropertiesPanel explicitly clears them when inspection ends.
 */
public final class ResourceAssetProperties extends VisTable {
    private final StudioApplicationAdapter app;
    private final Runnable leaveInspection;
    private final VisTable details;
    private AssetNode.Kind kind;
    private int assetId;
    private String screenId;
    private AssetMeta displayedMeta;
    private HudScreenEditorDocument displayedHud;
    private long displayedRevision;
    private boolean displayedDirty;
    private BitmapFont previewFont;
    private Skin previewSkin;
    private ParticleAssetPreview particlePreview;
    private float previewSpeed = 1f;
    private VisImage imagePreview;
    private StudioAssetVisual imageVisual;
    private String imageAtlasTag;
    private TextureAtlas imageAtlas;
    private int imageAtlasRevision;
    private Texture.TextureFilter imageMin, imageMag;
    private VisLabel imageMinLabel, imageMagLabel;
    private String gameObjectId;
    private EditorDocumentKey gameObjectKey;
    private boolean gameObjectReloadRequested;
    private VisLabel gameObjectDocumentLabel;
    private GameObjectEditorDocument displayedGameObject;
    private boolean gameObjectDirty;
    private GameObjectPreviewCapture gameObjectPreview;
    private VisImage gameObjectImage;

    ResourceAssetProperties(StudioApplicationAdapter app, Runnable leaveInspection) {
        super(true);
        this.app = app;
        this.leaveInspection = leaveInspection;
        top().left();
        CollapsibleVisTable section = new CollapsibleVisTable(true);
        details = section.content();
        details.top().left();
        add(section).growX().row();
    }

    boolean select(AssetNode node) {
        if (node.kind == AssetNode.Kind.HUD_SCREEN) return selectHud(node.path);
        kind = node.kind;
        assetId = node.assetId;
        gameObjectId = kind == AssetNode.Kind.GAME_OBJECT ? GameObjectAssetId.normalize(new FileHandle(node.path).name()) : null;
        gameObjectKey = gameObjectId != null ? new EditorDocumentKey(EditorDocumentType.GAME_OBJECT, gameObjectId) : null;
        screenId = null;
        return reload();
    }

    /** Shared HUD description, also used for the active Scene's association. */
    boolean selectHud(String id) {
        kind = AssetNode.Kind.HUD_SCREEN;
        screenId = id;
        reload();
        // An initially missing reference is descriptive state. Later deletion/reload may leave asset inspection.
        return true;
    }

    /** Called only at selection/reload boundaries, never to poll the filesystem each frame. */
    boolean reload() {
        releasePreview();
        details.clearChildren();
        details.add(new VisLabel(kind == AssetNode.Kind.HUD_SCREEN ? HudInspectorProjection.SCREEN_TITLE
                : kind == AssetNode.Kind.GAME_OBJECT ? "GAME OBJECT"
                : kind == AssetNode.Kind.PARTICLE ? "PARTICLE EFFECT" : kind.name()))
                .colspan(2).center().padBottom(8).row();
        if (kind == AssetNode.Kind.GAME_OBJECT) return showGameObject();
        if (kind == AssetNode.Kind.HUD_SCREEN) {
            displayedHud = null;
            if (screenId == null || screenId.isBlank()) {
                row("Status", "No HUD associated");
                return true;
            }
            FileHandle project = StudioFs.requireStudioProjectDir(ProjectConfig.getInstance());
            try {
                screenId = HudScreenAssetId.normalize(screenId);
                displayedHud = openHud();
                displayedRevision = displayedHud != null ? displayedHud.editSession().currentRevision() : -1;
                displayedDirty = displayedHud != null && displayedHud.isDirty();
                if (displayedHud == null && !project.child(screenId + HudScreenAsset.EXTENSION).exists()) {
                    unavailableHud("HUD asset is missing");
                    return false;
                }
                HudScreenAsset asset = displayedHud != null ? displayedHud.asset()
                        : new HudDocumentPersistenceService().load(project, screenId).asset();
                for (HudInspectorProjection.Field field : HudInspectorProjection.screen(
                        screenId, asset, displayedDirty, displayedHud != null)) row(field.label(), field.value());
            } catch (RuntimeException failure) {
                unavailableHud(failure.getMessage());
                return true;
            }
            // UI navigation only; use the same tab-reusing route as the browser's double-click.
            VisTextButton edit = new VisTextButton("Open HUD editor");
            edit.setName("openHudAssetEditor");
            edit.addListener(new ChangeListener() {
                @Override public void changed(ChangeEvent event, Actor actor) {
                    String target = screenId;
                    leaveInspection.run();
                    app.openHudScreen(target);
                }
            });
            details.add(edit).colspan(2).left().padTop(8).row();
            return true;
        }
        FileHandle project = StudioFs.requireStudioProjectDir(ProjectConfig.getInstance());
        displayedMeta = app.getSceneService().getAssetMetaDatabase().findById(assetId);
        if (displayedMeta == null) return false;
        row("Name", AssetDisplayInfo.from(displayedMeta).displayName());
        if (kind != AssetNode.Kind.PARTICLE && kind != AssetNode.Kind.IMAGE) row("ID", Integer.toString(assetId));
        row("Source", displayedMeta.sourceRelPath());
        if (kind == AssetNode.Kind.IMAGE) {
            imageAtlasTag = activeImageAtlasTag();
            imageAtlas = imageAtlasTag != null ? app.getCanvas().getAtlasService().getAtlas(imageAtlasTag) : null;
            imageAtlasRevision = app.getCanvas().getAtlasService().publicationRevision(imageAtlasTag);
        }
        try {
            if (kind == AssetNode.Kind.IMAGE && (displayedMeta.sourceRelPath() == null || displayedMeta.sourceRelPath().isBlank()))
                throw new IllegalArgumentException("Image source is not configured");
            FileHandle source = project.child(displayedMeta.sourceRelPath());
            if (kind == AssetNode.Kind.IMAGE) showImage(source);
            else if (kind == AssetNode.Kind.PARTICLE) {
                particlePreview = new ParticleAssetPreview(source, project.child(StudioFs.DIR_ORIG_IMAGES),
                        previewSpeed);
                details.add(particlePreview).colspan(2).growX().padTop(8).row();
            } else if (kind == AssetNode.Kind.FONT) showFont(source);
            else showSkin(source);
        } catch (RuntimeException failure) {
            releasePreview();
            row("Preview unavailable", failure.getMessage());
        }
        return true;
    }

    private void unavailableHud(String message) {
        row("Screen ID", screenId);
        row("Status", "Unavailable: " + message);
    }

    /** O(1) identity/revision checks. Open HUD documents are authoritative, including unsaved edits. */
    boolean refreshIfChanged() {
        if (kind == AssetNode.Kind.GAME_OBJECT) {
            if (gameObjectReloadRequested) return reload();
            refreshGameObjectDocument();
            return true;
        }
        if (kind == AssetNode.Kind.HUD_SCREEN) {
            HudScreenEditorDocument hud = openHud();
            if (hud != displayedHud || (hud != null && (hud.editSession().currentRevision() != displayedRevision
                    || hud.isDirty() != displayedDirty)))
                return reload();
        } else if (app.getSceneService().getAssetMetaDatabase().findById(assetId) != displayedMeta) {
            return reload();
        }
        if (kind == AssetNode.Kind.IMAGE) {
            String tag = activeImageAtlasTag();
            if (!Objects.equals(tag, imageAtlasTag)
                    || (tag != null ? app.getCanvas().getAtlasService().getAtlas(tag) : null) != imageAtlas
                    || app.getCanvas().getAtlasService().publicationRevision(tag) != imageAtlasRevision) return reload();
            // Only read the published texture; never load, repack, or change its filtering here.
            if (imageVisual != null) refreshImageFilters();
        }
        return true;
    }

    void gameObjectsChanged() {
        if (kind == AssetNode.Kind.GAME_OBJECT) gameObjectReloadRequested = true;
    }

    private boolean showGameObject() {
        gameObjectReloadRequested = false;
        FileHandle source = StudioFs.requireStudioProjectDir(ProjectConfig.getInstance()).child(gameObjectId);
        if (!source.exists()) return false;
        row("Name", GameObjectAssetId.assetName(gameObjectId));
        row("Source", gameObjectId);
        gameObjectDocumentLabel = row("Document", "Saved version");
        displayedGameObject = null;
        gameObjectDirty = false;
        refreshGameObjectDocument();
        try {
            var asset = new GameObjectAssetLoader().load(source);
            gameObjectPreview = GameObjectPreviewCapture.capture(asset, source.parent().parent(),
                    app.getSceneService().getAssetMetaDatabase(), app.getCanvas().getAtlasService(),
                    app.getCanvas().getAssetVisualResolver());
            if (gameObjectPreview.region() == null) {
                row("Preview", asset.entities.size == 1 ? "Empty composition" : "No visual content");
            } else {
                gameObjectImage = new VisImage(gameObjectPreview.region());
                gameObjectImage.setName("gameObjectAssetPreview");
                gameObjectImage.setScaling(Scaling.fit);
                Container<VisImage> frame = new Container<>(gameObjectImage);
                frame.fill().minWidth(0).minHeight(0).prefWidth(0).prefHeight(0)
                        .maxWidth(gameObjectPreview.width()).maxHeight(gameObjectPreview.height());
                frame.setClip(true);
                details.add(frame).colspan(2).growX().height(Math.min(240, gameObjectPreview.height())).padTop(8).row();
            }
        } catch (RuntimeException failure) {
            row("Preview", "Unavailable: invalid source or missing composition resource");
        }
        VisTextButton open = new VisTextButton("Open GameObject editor");
        open.setName("openGameObjectAssetEditor");
        open.getLabel().setWrap(true);
        open.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) {
                String target = source.path();
                leaveInspection.run();
                app.openGameObject(target);
            }
        });
        details.add(open).colspan(2).growX().minWidth(0).padTop(8).row();
        return true;
    }

    private void refreshGameObjectDocument() {
        var document = app.getEditorDocumentManager().find(gameObjectKey);
        GameObjectEditorDocument opened = document instanceof GameObjectEditorDocument gameObject ? gameObject : null;
        boolean modified = opened != null && opened.isDirty();
        if (opened != displayedGameObject || modified != gameObjectDirty) {
            displayedGameObject = opened;
            gameObjectDirty = modified;
            gameObjectDocumentLabel.setText(modified ? "Saved version - editor has unsaved changes"
                    : opened != null ? "Saved version - editor open" : "Saved version");
        }
    }

    private String activeImageAtlasTag() {
        return app.getEditorDocumentManager().activeDocument() instanceof SceneEditorDocument scene
                ? scene.context().sceneIdentity() : null;
    }

    private void showImage(FileHandle source) {
        if (!source.exists() || source.isDirectory())
            throw new IllegalArgumentException("Image source is missing: " + source.name());
        // Validate even when the renderer still has cached pixels. A broken source must not look healthy.
        Pixmap pixels;
        try { pixels = new Pixmap(source); }
        catch (RuntimeException failure) { throw new IllegalArgumentException("Invalid image source: " + source.name(), failure); }
        pixels.dispose();
        imageVisual = app.getCanvas().getAssetVisualResolver().resolveFirst(assetId, imageAtlasTag);
        if (imageVisual == null || imageVisual.texture().getTextureObjectHandle() == 0)
            throw new IllegalStateException("Image texture is unavailable");

        row("Dimensions", imageVisual.pixelWidth() + " x " + imageVisual.pixelHeight() + " px");
        if (imageVisual.source() == StudioAssetVisual.Source.ATLAS) {
            String page = imageVisual.texture().getTextureData() instanceof FileTextureData data
                    ? data.getFileHandle().name() : "Shared texture";
            row("Atlas page", page + " (" + imageAtlasTag + ")");
            row("Filtering", "Effective - shared scene atlas texture");
        } else {
            row("Filtering", "Effective - Studio standalone texture");
        }
        // IMAGE metadata has no texture profile. Do not infer one from HUD settings or thumbnail filters.
        imageMinLabel = row("Min", imageVisual.texture().getMinFilter().name());
        imageMagLabel = row("Mag", imageVisual.texture().getMagFilter().name());
        imageMin = imageVisual.texture().getMinFilter();
        imageMag = imageVisual.texture().getMagFilter();
        TextureRegion region = new TextureRegion(imageVisual.texture(), imageVisual.u1(), imageVisual.v1(),
                imageVisual.u2(), imageVisual.v2());
        imagePreview = new VisImage(region);
        imagePreview.setName("imageAssetPreview");
        imagePreview.setScaling(Scaling.fit);
        // Scene2D already handles fitting, centering, resize and clipping. Limiting the actor to native
        // dimensions prevents Scaling.fit from ever upscaling, without touching the shared texture.
        Container<VisImage> frame = new Container<>(imagePreview);
        frame.fill().minWidth(0).minHeight(0).prefWidth(0).prefHeight(0)
                .maxWidth(imageVisual.pixelWidth()).maxHeight(imageVisual.pixelHeight());
        frame.setClip(true);
        details.add(frame).colspan(2).growX().height(240).padTop(8).row();
    }

    private void refreshImageFilters() {
        Texture texture = imageVisual.texture();
        if (imageMin != texture.getMinFilter()) {
            imageMin = texture.getMinFilter();
            imageMinLabel.setText(imageMin.name());
        }
        if (imageMag != texture.getMagFilter()) {
            imageMag = texture.getMagFilter();
            imageMagLabel.setText(imageMag.name());
        }
    }

    private HudScreenEditorDocument openHud() {
        if (screenId == null || screenId.isBlank()) return null;
        return app.getEditorDocumentManager().find(new EditorDocumentKey(EditorDocumentType.HUD_SCREEN, screenId))
                instanceof HudScreenEditorDocument hud ? hud : null;
    }

    private void showFont(FileHandle source) {
        previewFont = new BitmapFont(source);
        BitmapFont.BitmapFontData data = previewFont.getData();
        row("Format", "BMFont");
        row("Line height", Float.toString(data.lineHeight));
        row("Cap height", Float.toString(data.capHeight));
        row("Texture pages", Integer.toString(previewFont.getRegions().size));
        Label sample = new Label("The quick brown fox\nAa Bb Cc 0123456789",
                new Label.LabelStyle(previewFont, Color.WHITE));
        sample.setName("fontAssetPreview");
        sample.setWrap(true);
        details.add(sample).colspan(2).growX().padTop(12).row();
    }

    private void showSkin(FileHandle source) {
        JsonValue json = new JsonReader().parse(source);
        List<String> styles = new ArrayList<>();
        List<String> resources = new ArrayList<>();
        for (JsonValue type = json.child; type != null; type = type.next) {
            String typeName = type.name.substring(Math.max(type.name.lastIndexOf('.'), type.name.lastIndexOf('$')) + 1);
            for (JsonValue item = type.child; item != null; item = item.next) {
                (typeName.endsWith("Style") ? styles : resources).add(typeName + " / " + item.name);
            }
        }
        Collections.sort(styles);
        Collections.sort(resources);
        row("Styles", styles.isEmpty() ? "None declared" : String.join("\n", styles));
        row("Resources", resources.isEmpty() ? "None declared" : String.join("\n", resources));
        // Native Scene2D loading/actors, as used by HUD authoring; no separate preview renderer.
        FileHandle atlas = source.sibling(source.nameWithoutExtension() + ".atlas");
        previewSkin = atlas.exists() ? new Skin(new TextureAtlas(atlas)) : new Skin();
        previewSkin.load(source);
        if (previewSkin.getAtlas() != null) {
            List<String> regions = new ArrayList<>();
            for (var region : previewSkin.getAtlas().getRegions()) regions.add(region.name);
            Collections.sort(regions);
            row("Atlas regions", String.join("\n", regions));
        }
        var buttons = previewSkin.getAll(TextButton.TextButtonStyle.class);
        var labels = previewSkin.getAll(Label.LabelStyle.class);
        Actor sample = null;
        if (buttons != null && buttons.size > 0) {
            var names = buttons.keys().toArray();
            names.sort();
            TextButton.TextButtonStyle style = buttons.get(names.first());
            if (style.font != null) sample = new TextButton("Preview", style);
        }
        if (sample == null && labels != null && labels.size > 0) {
            var names = labels.keys().toArray();
            names.sort();
            Label.LabelStyle style = labels.get(names.first());
            if (style.font != null) sample = new Label("Preview Aa 0123", style);
        }
        if (sample != null) {
            sample.setName("skinAssetPreview");
            details.add(sample).colspan(2).left().padTop(12).row();
        } else row("Preview", "No label or text button style available");
    }

    private VisLabel row(String label, String value) {
        VisLabel content = new VisLabel(value != null ? value : "Unavailable");
        content.setName("resourceAsset" + label.replace(" ", ""));
        content.setWrap(true);
        details.add(new VisLabel(label + ":")).left().top();
        details.add(content).growX().left().row();
        return content;
    }

    void clearProject() {
        details.clearChildren();
        releasePreview();
        kind = null;
        assetId = -1;
        screenId = null;
        displayedMeta = null;
        displayedHud = null;
        previewSpeed = 1f;
        imageAtlasTag = null;
        imageAtlas = null;
        imageAtlasRevision = 0;
        gameObjectId = null;
        gameObjectKey = null;
        gameObjectReloadRequested = false;
        gameObjectDocumentLabel = null;
        displayedGameObject = null;
        gameObjectDirty = false;
    }

    private void releasePreview() {
        if (gameObjectImage != null) gameObjectImage.setDrawable((com.badlogic.gdx.scenes.scene2d.utils.Drawable) null);
        gameObjectImage = null;
        if (gameObjectPreview != null) gameObjectPreview.dispose();
        gameObjectPreview = null;
        if (imagePreview != null) imagePreview.setDrawable((com.badlogic.gdx.scenes.scene2d.utils.Drawable) null);
        imagePreview = null;
        imageVisual = null;
        imageMinLabel = imageMagLabel = null;
        imageMin = imageMag = null;
        if (particlePreview != null) {
            previewSpeed = particlePreview.speed();
            particlePreview.dispose();
            particlePreview = null;
        }
        if (previewFont != null) previewFont.dispose();
        if (previewSkin != null) previewSkin.dispose();
        previewFont = null;
        previewSkin = null;
    }

}
