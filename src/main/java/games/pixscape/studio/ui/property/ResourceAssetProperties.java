package games.pixscape.studio.ui.property;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.VisTextButton;
import games.pixscape.runtime.hud.HudScreenAsset;
import games.pixscape.runtime.hud.HudScreenAssetId;
import games.pixscape.studio.asset.AssetDisplayInfo;
import games.pixscape.studio.asset.AssetMeta;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.document.EditorDocumentKey;
import games.pixscape.studio.document.EditorDocumentType;
import games.pixscape.studio.document.HudScreenEditorDocument;
import games.pixscape.studio.io.StudioFs;
import games.pixscape.studio.service.hud.HudDocumentPersistenceService;
import games.pixscape.studio.service.hud.HudInspectorProjection;
import games.pixscape.studio.ui.asset.AssetNode;
import games.pixscape.studio.ui.main.StudioApplicationAdapter;
import games.pixscape.studio.ui.widget.CollapsibleVisTable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Read-only HUD/Font/Skin details. Native preview resources are owned by this view only. */
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
        details.add(new VisLabel(kind == AssetNode.Kind.HUD_SCREEN ? HudInspectorProjection.SCREEN_TITLE : kind.name()))
                .colspan(2).center().padBottom(8).row();
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
        row("ID", Integer.toString(assetId));
        row("Source", displayedMeta.sourceRelPath());
        try {
            FileHandle source = project.child(displayedMeta.sourceRelPath());
            if (kind == AssetNode.Kind.FONT) showFont(source);
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
        if (kind == AssetNode.Kind.HUD_SCREEN) {
            HudScreenEditorDocument hud = openHud();
            if (hud != displayedHud || (hud != null && (hud.editSession().currentRevision() != displayedRevision
                    || hud.isDirty() != displayedDirty)))
                return reload();
        } else if (app.getSceneService().getAssetMetaDatabase().findById(assetId) != displayedMeta) {
            return reload();
        }
        return true;
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

    private void row(String label, String value) {
        VisLabel content = new VisLabel(value != null ? value : "Unavailable");
        content.setName("resourceAsset" + label.replace(" ", ""));
        content.setWrap(true);
        details.add(new VisLabel(label + ":")).left().top();
        details.add(content).growX().left().row();
    }

    void clearProject() {
        details.clearChildren();
        releasePreview();
        kind = null;
        assetId = -1;
        screenId = null;
        displayedMeta = null;
        displayedHud = null;
    }

    private void releasePreview() {
        if (previewFont != null) previewFont.dispose();
        if (previewSkin != null) previewSkin.dispose();
        previewFont = null;
        previewSkin = null;
    }

    @Override protected void setStage(Stage stage) {
        if (stage == null) clearProject();
        super.setStage(stage);
    }
}
