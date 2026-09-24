package games.pixscape.studio.service.hud;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton;
import com.badlogic.gdx.scenes.scene2d.ui.ImageTextButton;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.ProgressBar;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.TextTooltip;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.ObjectMap;
import com.github.tommyettinger.textra.Font;
import games.pixscape.runtime.hud.HudBuiltInTextButtonStyle;
import games.pixscape.runtime.hud.HudBuiltInImageButtonStyle;
import games.pixscape.runtime.hud.HudBuiltInImageTextButtonStyle;
import games.pixscape.runtime.hud.HudBuiltInTextFieldStyle;
import games.pixscape.runtime.hud.HudBuiltInSelectBoxStyle;
import games.pixscape.runtime.hud.HudBuiltInCheckBoxStyle;
import games.pixscape.runtime.hud.HudBuiltInSliderStyle;
import games.pixscape.runtime.hud.HudBuiltInProgressBarStyle;
import games.pixscape.runtime.hud.HudBuiltInScrollPaneStyle;
import games.pixscape.runtime.hud.HudBuiltInTextTooltipStyle;
import games.pixscape.runtime.hud.HudBuiltInWindowStyle;
import games.pixscape.runtime.hud.HudStyleUsability;
import games.pixscape.runtime.hud.HudTextraFontFactory;
import games.pixscape.runtime.hud.HudVisualResources;
import games.pixscape.runtime.hud.document.HudImageReferences;
import games.pixscape.runtime.hud.document.HudFontReferences;
import games.pixscape.runtime.hud.document.HudImageSource;
import games.pixscape.runtime.hud.document.HudNode;
import games.pixscape.runtime.hud.document.ValidatedHudDocument;
import games.pixscape.runtime.hud.document.HudResourceCatalog;
import games.pixscape.studio.asset.AssetMetaDatabase;
import games.pixscape.studio.asset.AssetMeta;
import games.pixscape.studio.asset.AssetType;
import games.pixscape.studio.service.atlas.HudImageAssetRef;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/** Studio-owned native resources for one detached HUD authoring preview. */
public final class HudAuthoringResources implements HudVisualResources, HudResourceCatalog, Disposable {
    private final Map<String, Texture> texturesBySource = new HashMap<>();
    private final Map<String, TextureRegion> regionsByName = new HashMap<>();
    private final Map<Integer, BitmapFont> bitmapFonts = new HashMap<>();
    private final Map<BitmapFont, Font> textraFonts = new IdentityHashMap<>();
    private final Skin skin;
    private final BitmapFont builtInLabelFont;
    private final Label.LabelStyle builtInLabelStyle;
    private final Texture builtInTextButtonTexture;
    private final TextButton.TextButtonStyle builtInTextButtonStyle;
    private final ImageButton.ImageButtonStyle builtInImageButtonStyle;
    private final ImageTextButton.ImageTextButtonStyle builtInImageTextButtonStyle;
    private final TextField.TextFieldStyle builtInTextFieldStyle;
    private final SelectBox.SelectBoxStyle builtInSelectBoxStyle;
    private final CheckBox.CheckBoxStyle builtInCheckBoxStyle;
    private final Slider.SliderStyle builtInSliderStyle;
    private final ProgressBar.ProgressBarStyle builtInProgressBarStyle;
    private final ScrollPane.ScrollPaneStyle builtInScrollPaneStyle;
    private final TextTooltip.TextTooltipStyle builtInTextTooltipStyle;
    private final Window.WindowStyle builtInWindowStyle;
    private boolean disposed;

    private HudAuthoringResources(Skin skin) {
        this.skin = skin;
        builtInLabelFont = new BitmapFont();
        builtInLabelStyle = new Label.LabelStyle(builtInLabelFont, new Color(Color.WHITE));
        Pixmap white = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        try {
            white.setColor(Color.WHITE);
            white.fill();
            builtInTextButtonTexture = new Texture(white);
        } finally {
            white.dispose();
        }
        builtInTextButtonStyle = HudBuiltInTextButtonStyle.create(
                new TextureRegion(builtInTextButtonTexture), builtInLabelFont);
        builtInImageButtonStyle = HudBuiltInImageButtonStyle.create(
                new TextureRegion(builtInTextButtonTexture));
        builtInImageTextButtonStyle = HudBuiltInImageTextButtonStyle.create(builtInTextButtonStyle);
        builtInTextFieldStyle = HudBuiltInTextFieldStyle.create(
                new TextureRegion(builtInTextButtonTexture), builtInLabelFont);
        builtInSelectBoxStyle = HudBuiltInSelectBoxStyle.create(
                new TextureRegion(builtInTextButtonTexture), builtInLabelFont);
        builtInCheckBoxStyle = HudBuiltInCheckBoxStyle.create(
                new TextureRegion(builtInTextButtonTexture), builtInLabelFont);
        builtInSliderStyle = HudBuiltInSliderStyle.create(
                new TextureRegion(builtInTextButtonTexture));
        builtInProgressBarStyle = HudBuiltInProgressBarStyle.create(builtInSliderStyle);
        builtInScrollPaneStyle = HudBuiltInScrollPaneStyle.create(new TextureRegion(builtInTextButtonTexture));
        builtInTextTooltipStyle = HudBuiltInTextTooltipStyle.create(
                new TextureRegion(builtInTextButtonTexture), builtInLabelStyle);
        builtInWindowStyle = HudBuiltInWindowStyle.create(
                new TextureRegion(builtInTextButtonTexture), builtInLabelStyle);
    }

    public static HudAuthoringResources prepare(ValidatedHudDocument document, FileHandle projectDir,
                                                AssetMetaDatabase assetDatabase,
                                                String skinId) {
        if (document == null) throw new IllegalArgumentException("Validated HUD document is required.");
        if (projectDir == null) throw new IllegalArgumentException("Studio project directory is required.");
        Skin skin = loadSkin(projectDir, skinId);
        HudAuthoringResources resources = new HudAuthoringResources(skin);
        try {
            for (HudNode node : document.nodeIndex().values()) {
                Integer fontAssetId = HudFontReferences.assetId(node);
                if (fontAssetId != null) resources.loadBitmapFont(
                        fontAssetId, projectDir, assetDatabase);
                Integer tooltipFontAssetId = HudFontReferences.tooltipAssetId(node);
                if (tooltipFontAssetId != null) resources.loadBitmapFont(
                        tooltipFontAssetId, projectDir, assetDatabase);
                HudImageReferences.visit(node, (ignored, fieldPath, image) -> {
                    if (image.source == HudImageSource.REGION) {
                        resources.loadRegion(image.resourceName, projectDir, assetDatabase);
                    }
                });
            }
            return resources;
        } catch (RuntimeException failure) {
            resources.dispose();
            throw failure;
        }
    }

    private void loadBitmapFont(int assetId, FileHandle projectDir,
                                AssetMetaDatabase database) {
        if (bitmapFonts.containsKey(assetId)) return;
        AssetMeta meta = database != null ? database.findById(assetId) : null;
        if (meta == null || meta.type() != AssetType.FONT) {
            throw new IllegalStateException("HUD widget references unknown bitmap font Asset "
                    + assetId + ".");
        }
        FileHandle descriptor = projectDir.child(meta.sourceRelPath());
        if (!descriptor.exists() || descriptor.isDirectory()) {
            throw new IllegalStateException("Bitmap font Asset " + assetId
                    + " descriptor does not exist: " + meta.sourceRelPath() + ".");
        }
        BitmapFont.BitmapFontData data = new BitmapFont.BitmapFontData(descriptor, false);
        String[] imagePaths = data.getImagePaths();
        if (imagePaths == null || imagePaths.length == 0) {
            throw new IllegalStateException("Bitmap font Asset " + assetId + " has no pages.");
        }
        com.badlogic.gdx.utils.Array<TextureRegion> pageRegions =
                new com.badlogic.gdx.utils.Array<>(imagePaths.length);
        for (String imagePath : imagePaths) {
            FileHandle page = new FileHandle(imagePath);
            String sourceKey = page.file().getAbsolutePath();
            Texture texture = texturesBySource.get(sourceKey);
            if (texture == null) {
                texture = new Texture(page);
                texturesBySource.put(sourceKey, texture);
            }
            pageRegions.add(new TextureRegion(texture));
        }
        BitmapFont font = new BitmapFont(data, pageRegions, true);
        font.setOwnsTexture(false);
        bitmapFonts.put(assetId, font);
    }

    private void loadRegion(String resourceName, FileHandle projectDir, AssetMetaDatabase database) {
        HudImageAssetRef ref = HudImageAssetRef.resolve(resourceName, database);
        FileHandle source = projectDir.child(ref.sourceRelPath());
        if (!source.exists() || source.isDirectory()) {
            throw new IllegalStateException("HUD Image asset '" + resourceName
                    + "' source does not exist: " + ref.sourceRelPath() + ".");
        }
        String sourceKey = source.file().getAbsolutePath();
        Texture texture = texturesBySource.get(sourceKey);
        if (texture == null) {
            texture = new Texture(source);
            texturesBySource.put(sourceKey, texture);
        }
        regionsByName.put(resourceName, new TextureRegion(texture));
    }

    private static Skin loadSkin(FileHandle projectDir, String skinId) {
        if (skinId == null || skinId.trim().isEmpty()) return null;
        FileHandle file = projectDir.child(skinId);
        if (!file.exists() || file.isDirectory()) {
            throw new IllegalStateException("HUD Skin resource does not exist: " + skinId + ".");
        }
        return new Skin(file);
    }

    @Override public TextureRegion region(String name) { requireOpen(); return regionsByName.get(name); }
    @Override public Drawable drawable(String name) {
        requireOpen(); return skin == null ? null : skin.optional(name, Drawable.class);
    }
    @Override public Label.LabelStyle labelStyle(String name) {
        requireOpen(); return skin == null ? null : skin.optional(name, Label.LabelStyle.class);
    }
    @Override public BitmapFont bitmapFont(int assetId) {
        requireOpen(); return bitmapFonts.get(assetId);
    }
    @Override public Font textraFont(BitmapFont bitmapFont) {
        requireOpen();
        if (bitmapFont == null) return null;
        return textraFonts.computeIfAbsent(bitmapFont, font -> HudTextraFontFactory.prepare(
                font, new TextureRegion(builtInTextButtonTexture)));
    }
    @Override public boolean hasRegion(String name) { return region(name) != null; }
    @Override public boolean hasDrawable(String name) { return drawable(name) != null; }
    @Override public boolean hasLabelStyle(String name) { return labelStyle(name) != null; }
    @Override public boolean hasLabelStyleFont(String name) {
        return HudStyleUsability.isUsableLabelStyle(labelStyle(name), false);
    }
    @Override public boolean hasBitmapFont(int assetId) { return bitmapFont(assetId) != null; }
    @Override public boolean hasBuiltInLabelStyle() { return builtInLabelStyle() != null; }
    @Override public Label.LabelStyle builtInLabelStyle() {
        requireOpen(); return builtInLabelStyle;
    }
    public List<String> labelStyleNames() {
        return labelStyleNames(false);
    }
    public List<String> labelStyleNames(boolean hasFontOverride) {
        requireOpen();
        if (skin == null) return List.of();
        ObjectMap<String, Label.LabelStyle> styles = skin.getAll(Label.LabelStyle.class);
        return styleNames(styles, style -> HudStyleUsability.isUsableLabelStyle(
                style, hasFontOverride));
    }
    public LabelDefaults defaultLabel() {
        requireOpen();
        return new LabelDefaults(null);
    }
    @Override public TextButton.TextButtonStyle textButtonStyle(String name) {
        requireOpen(); return skin == null ? null : skin.optional(name, TextButton.TextButtonStyle.class);
    }
    @Override public TextButton.TextButtonStyle builtInTextButtonStyle() {
        requireOpen(); return builtInTextButtonStyle;
    }
    @Override public boolean hasTextButtonStyle(String name) {
        return hasTextButtonStyle(name, false);
    }
    @Override public boolean hasTextButtonStyle(String name, boolean hasFontOverride) {
        return HudStyleUsability.isUsableTextButtonStyle(textButtonStyle(name), hasFontOverride);
    }
    @Override public boolean hasBuiltInTextButtonStyle() {
        return HudStyleUsability.isUsableTextButtonStyle(builtInTextButtonStyle(), false);
    }
    @Override public ImageButton.ImageButtonStyle imageButtonStyle(String name) {
        requireOpen(); return skin == null ? null : skin.optional(name, ImageButton.ImageButtonStyle.class);
    }
    @Override public ImageButton.ImageButtonStyle builtInImageButtonStyle() {
        requireOpen(); return builtInImageButtonStyle;
    }
    @Override public ImageTextButton.ImageTextButtonStyle imageTextButtonStyle(String name) {
        requireOpen(); return skin == null ? null : skin.optional(name, ImageTextButton.ImageTextButtonStyle.class);
    }
    @Override public ImageTextButton.ImageTextButtonStyle builtInImageTextButtonStyle() {
        requireOpen(); return builtInImageTextButtonStyle;
    }
    @Override public boolean hasImageTextButtonStyle(String name, boolean hasFontOverride) {
        return HudStyleUsability.isUsableImageTextButtonStyle(imageTextButtonStyle(name), hasFontOverride);
    }
    @Override public boolean hasBuiltInImageTextButtonStyle() {
        return HudStyleUsability.isUsableImageTextButtonStyle(builtInImageTextButtonStyle, false);
    }
    @Override public boolean hasImageButtonStyle(String name) { return usable(imageButtonStyle(name)); }
    @Override public boolean hasBuiltInImageButtonStyle() { return usable(builtInImageButtonStyle()); }
    @Override public TextField.TextFieldStyle textFieldStyle(String name) {
        requireOpen(); return skin == null ? null : skin.optional(name, TextField.TextFieldStyle.class);
    }
    @Override public TextField.TextFieldStyle builtInTextFieldStyle() {
        requireOpen(); return builtInTextFieldStyle;
    }
    @Override public boolean hasTextFieldStyle(String name) {
        return hasTextFieldStyle(name, false);
    }
    @Override public boolean hasTextFieldStyle(String name, boolean hasFontOverride) {
        return HudStyleUsability.isUsableTextFieldStyle(textFieldStyle(name), hasFontOverride);
    }
    @Override public boolean hasBuiltInTextFieldStyle() {
        return HudStyleUsability.isUsableTextFieldStyle(builtInTextFieldStyle(), false);
    }
    @Override public SelectBox.SelectBoxStyle selectBoxStyle(String name) {
        requireOpen(); return skin == null ? null : skin.optional(name, SelectBox.SelectBoxStyle.class);
    }
    @Override public SelectBox.SelectBoxStyle builtInSelectBoxStyle() {
        requireOpen(); return builtInSelectBoxStyle;
    }
    @Override public boolean hasSelectBoxStyle(String name) {
        return hasSelectBoxStyle(name, false);
    }
    @Override public boolean hasSelectBoxStyle(String name, boolean hasFontOverride) {
        return HudStyleUsability.isUsableSelectBoxStyle(selectBoxStyle(name), hasFontOverride);
    }
    @Override public boolean hasBuiltInSelectBoxStyle() {
        return HudStyleUsability.isUsableSelectBoxStyle(builtInSelectBoxStyle(), false);
    }
    @Override public com.badlogic.gdx.scenes.scene2d.ui.List.ListStyle listStyle(String name) {
        requireOpen();
        return skin == null ? null : skin.optional(name, com.badlogic.gdx.scenes.scene2d.ui.List.ListStyle.class);
    }
    @Override public com.badlogic.gdx.scenes.scene2d.ui.List.ListStyle builtInListStyle() {
        requireOpen(); return builtInSelectBoxStyle.listStyle;
    }
    @Override public boolean hasListStyle(String name, boolean hasFontOverride) {
        return HudStyleUsability.isUsableListStyle(listStyle(name), hasFontOverride);
    }
    @Override public boolean hasBuiltInListStyle() {
        return HudStyleUsability.isUsableListStyle(builtInListStyle(), false);
    }
    @Override public CheckBox.CheckBoxStyle checkBoxStyle(String name) {
        requireOpen(); return skin == null ? null : skin.optional(name, CheckBox.CheckBoxStyle.class);
    }
    @Override public CheckBox.CheckBoxStyle builtInCheckBoxStyle() {
        requireOpen(); return builtInCheckBoxStyle;
    }
    @Override public boolean hasCheckBoxStyle(String name) {
        return hasCheckBoxStyle(name, false);
    }
    @Override public boolean hasCheckBoxStyle(String name, boolean hasFontOverride) {
        return HudStyleUsability.isUsableCheckBoxStyle(checkBoxStyle(name), hasFontOverride);
    }
    @Override public boolean hasBuiltInCheckBoxStyle() {
        return HudStyleUsability.isUsableCheckBoxStyle(builtInCheckBoxStyle(), false);
    }
    @Override public Slider.SliderStyle sliderStyle(String name) {
        requireOpen(); return skin == null ? null : skin.optional(name, Slider.SliderStyle.class);
    }
    @Override public Slider.SliderStyle builtInSliderStyle() {
        requireOpen(); return builtInSliderStyle;
    }
    @Override public boolean hasSliderStyle(String name) {
        return HudStyleUsability.isUsableSliderStyle(sliderStyle(name));
    }
    @Override public boolean hasBuiltInSliderStyle() {
        return HudStyleUsability.isUsableSliderStyle(builtInSliderStyle());
    }
    @Override public ProgressBar.ProgressBarStyle progressBarStyle(String name) {
        requireOpen(); return skin == null ? null : skin.optional(name, ProgressBar.ProgressBarStyle.class);
    }
    @Override public ProgressBar.ProgressBarStyle builtInProgressBarStyle() {
        requireOpen(); return builtInProgressBarStyle;
    }
    @Override public boolean hasProgressBarStyle(String name) {
        return HudStyleUsability.isUsableProgressBarStyle(progressBarStyle(name));
    }
    @Override public boolean hasBuiltInProgressBarStyle() {
        return HudStyleUsability.isUsableProgressBarStyle(builtInProgressBarStyle());
    }
    @Override public ScrollPane.ScrollPaneStyle scrollPaneStyle(String name) {
        requireOpen(); return skin == null ? null : skin.optional(name, ScrollPane.ScrollPaneStyle.class);
    }
    @Override public ScrollPane.ScrollPaneStyle builtInScrollPaneStyle() {
        requireOpen(); return builtInScrollPaneStyle;
    }
    @Override public boolean hasScrollPaneStyle(String name) {
        return HudStyleUsability.isUsableScrollPaneStyle(scrollPaneStyle(name));
    }
    @Override public boolean hasBuiltInScrollPaneStyle() {
        return HudStyleUsability.isUsableScrollPaneStyle(builtInScrollPaneStyle());
    }
    @Override public TextTooltip.TextTooltipStyle textTooltipStyle(String name) {
        requireOpen(); return skin == null ? null : skin.optional(name, TextTooltip.TextTooltipStyle.class);
    }
    @Override public TextTooltip.TextTooltipStyle builtInTextTooltipStyle() {
        requireOpen(); return builtInTextTooltipStyle;
    }
    @Override public boolean hasTextTooltipStyle(String name, boolean hasFontOverride) {
        return HudStyleUsability.isUsableTextTooltipStyle(textTooltipStyle(name), hasFontOverride);
    }
    @Override public boolean hasBuiltInTextTooltipStyle() {
        return HudStyleUsability.isUsableTextTooltipStyle(builtInTextTooltipStyle(), false);
    }
    @Override public Window.WindowStyle windowStyle(String name) {
        requireOpen(); return skin == null ? null : skin.optional(name, Window.WindowStyle.class);
    }
    @Override public Window.WindowStyle builtInWindowStyle() {
        requireOpen(); return builtInWindowStyle;
    }
    @Override public boolean hasWindowStyle(String name, boolean hasFontOverride) {
        return HudStyleUsability.isUsableWindowStyle(windowStyle(name), hasFontOverride);
    }
    @Override public boolean hasBuiltInWindowStyle() {
        return HudStyleUsability.isUsableWindowStyle(builtInWindowStyle(), false);
    }
    public List<String> textButtonStyleNames() {
        return textButtonStyleNames(false);
    }
    public List<String> textButtonStyleNames(boolean hasFontOverride) {
        requireOpen();
        if (skin == null) return List.of();
        ObjectMap<String, TextButton.TextButtonStyle> styles =
                skin.getAll(TextButton.TextButtonStyle.class);
        return styleNames(styles, style -> HudStyleUsability.isUsableTextButtonStyle(
                style, hasFontOverride));
    }
    public TextButtonDefaults defaultTextButton() {
        requireOpen();
        List<String> customStyles = textButtonStyleNames();
        String styleName = customStyles.isEmpty() ? null : customStyles.get(0);
        TextButton.TextButtonStyle style = HudBuiltInTextButtonStyle.isSelected(styleName)
                ? builtInTextButtonStyle : textButtonStyle(styleName);
        if (!HudStyleUsability.isUsableTextButtonStyle(style, false)) return null;
        return new TextButtonDefaults(styleName);
    }

    public List<String> imageButtonStyleNames() {
        requireOpen();
        if (skin == null) return List.of();
        ObjectMap<String, ImageButton.ImageButtonStyle> styles =
                skin.getAll(ImageButton.ImageButtonStyle.class);
        return styleNames(styles, HudAuthoringResources::usable);
    }

    public ImageButtonDefaults defaultImageButton() {
        requireOpen();
        return usable(builtInImageButtonStyle) ? new ImageButtonDefaults(null) : null;
    }

    public ImageTextButtonDefaults defaultImageTextButton() {
        requireOpen();
        return HudStyleUsability.isUsableImageTextButtonStyle(builtInImageTextButtonStyle, false)
                ? new ImageTextButtonDefaults(null) : null;
    }

    public List<String> imageTextButtonStyleNames(boolean hasFontOverride) {
        requireOpen();
        if (skin == null) return List.of();
        ObjectMap<String, ImageTextButton.ImageTextButtonStyle> styles =
                skin.getAll(ImageTextButton.ImageTextButtonStyle.class);
        return styleNames(styles, style -> HudStyleUsability.isUsableImageTextButtonStyle(
                style, hasFontOverride));
    }

    public List<String> textFieldStyleNames() {
        return textFieldStyleNames(false);
    }
    public List<String> textFieldStyleNames(boolean hasFontOverride) {
        requireOpen();
        if (skin == null) return List.of();
        ObjectMap<String, TextField.TextFieldStyle> styles =
                skin.getAll(TextField.TextFieldStyle.class);
        return styleNames(styles, style -> HudStyleUsability.isUsableTextFieldStyle(
                style, hasFontOverride));
    }

    public TextFieldDefaults defaultTextField() {
        requireOpen();
        return HudStyleUsability.isUsableTextFieldStyle(builtInTextFieldStyle, false)
                ? new TextFieldDefaults(null) : null;
    }

    public List<String> selectBoxStyleNames() {
        return selectBoxStyleNames(false);
    }
    public List<String> selectBoxStyleNames(boolean hasFontOverride) {
        requireOpen();
        if (skin == null) return List.of();
        ObjectMap<String, SelectBox.SelectBoxStyle> styles = skin.getAll(SelectBox.SelectBoxStyle.class);
        return styleNames(styles, style -> HudStyleUsability.isUsableSelectBoxStyle(
                style, hasFontOverride));
    }

    public SelectBoxDefaults defaultSelectBox() {
        requireOpen();
        return HudStyleUsability.isUsableSelectBoxStyle(builtInSelectBoxStyle, false)
                ? new SelectBoxDefaults(null) : null;
    }

    public List<String> listStyleNames(boolean hasFontOverride) {
        requireOpen();
        if (skin == null) return List.of();
        ObjectMap<String, com.badlogic.gdx.scenes.scene2d.ui.List.ListStyle> styles =
                skin.getAll(com.badlogic.gdx.scenes.scene2d.ui.List.ListStyle.class);
        return styleNames(styles, style -> HudStyleUsability.isUsableListStyle(style, hasFontOverride));
    }

    public boolean hasDefaultList() {
        requireOpen();
        return HudStyleUsability.isUsableListStyle(builtInSelectBoxStyle.listStyle, false);
    }

    boolean hasUsableListStyle(String styleName, boolean hasFontOverride) {
        requireOpen();
        return HudStyleUsability.isUsableListStyle(HudBuiltInSelectBoxStyle.isSelected(styleName)
                ? builtInSelectBoxStyle.listStyle : listStyle(styleName), hasFontOverride);
    }

    public List<String> checkBoxStyleNames() {
        return checkBoxStyleNames(false);
    }
    public List<String> checkBoxStyleNames(boolean hasFontOverride) {
        requireOpen();
        if (skin == null) return List.of();
        ObjectMap<String, CheckBox.CheckBoxStyle> styles = skin.getAll(CheckBox.CheckBoxStyle.class);
        return styleNames(styles, style -> HudStyleUsability.isUsableCheckBoxStyle(
                style, hasFontOverride));
    }

    public CheckBoxDefaults defaultCheckBox() {
        requireOpen();
        return HudStyleUsability.isUsableCheckBoxStyle(builtInCheckBoxStyle, false)
                ? new CheckBoxDefaults(null) : null;
    }

    public List<String> sliderStyleNames() {
        requireOpen();
        if (skin == null) return List.of();
        ObjectMap<String, Slider.SliderStyle> styles = skin.getAll(Slider.SliderStyle.class);
        return styleNames(styles, HudStyleUsability::isUsableSliderStyle);
    }

    public SliderDefaults defaultSlider() {
        requireOpen();
        return HudStyleUsability.isUsableSliderStyle(builtInSliderStyle)
                ? new SliderDefaults(null) : null;
    }

    public List<String> progressBarStyleNames() {
        requireOpen();
        if (skin == null) return List.of();
        ObjectMap<String, ProgressBar.ProgressBarStyle> styles = skin.getAll(ProgressBar.ProgressBarStyle.class);
        return styleNames(styles, HudStyleUsability::isUsableProgressBarStyle);
    }

    public List<String> scrollPaneStyleNames() {
        requireOpen();
        if (skin == null) return List.of();
        ObjectMap<String, ScrollPane.ScrollPaneStyle> styles = skin.getAll(ScrollPane.ScrollPaneStyle.class);
        return styleNames(styles, HudStyleUsability::isUsableScrollPaneStyle);
    }

    public List<String> textTooltipStyleNames(boolean hasFontOverride) {
        requireOpen();
        if (skin == null) return List.of();
        ObjectMap<String, TextTooltip.TextTooltipStyle> styles =
                skin.getAll(TextTooltip.TextTooltipStyle.class);
        return styleNames(styles, style -> HudStyleUsability.isUsableTextTooltipStyle(
                style, hasFontOverride));
    }

    public List<String> windowStyleNames(boolean hasFontOverride) {
        requireOpen();
        if (skin == null) return List.of();
        ObjectMap<String, Window.WindowStyle> styles = skin.getAll(Window.WindowStyle.class);
        return styleNames(styles, style -> HudStyleUsability.isUsableWindowStyle(
                style, hasFontOverride));
    }

    public ProgressBarDefaults defaultProgressBar() {
        requireOpen();
        return HudStyleUsability.isUsableProgressBarStyle(builtInProgressBarStyle)
                ? new ProgressBarDefaults(null) : null;
    }

    public ScrollPaneDefaults defaultScrollPane() {
        requireOpen();
        return HudStyleUsability.isUsableScrollPaneStyle(builtInScrollPaneStyle)
                ? new ScrollPaneDefaults(null) : null;
    }

    public boolean hasDefaultWindow() {
        requireOpen();
        return HudStyleUsability.isUsableWindowStyle(builtInWindowStyle, false);
    }

    boolean hasUsableTextButtonStyle(String styleName, boolean hasFontOverride) {
        requireOpen();
        return HudStyleUsability.isUsableTextButtonStyle(
                HudBuiltInTextButtonStyle.isSelected(styleName)
                        ? builtInTextButtonStyle : textButtonStyle(styleName), hasFontOverride);
    }
    boolean hasUsableImageButtonStyle(String styleName) {
        requireOpen();
        return usable(HudBuiltInImageButtonStyle.isSelected(styleName)
                ? builtInImageButtonStyle : imageButtonStyle(styleName));
    }
    boolean hasUsableImageTextButtonStyle(String styleName, boolean hasFontOverride) {
        requireOpen();
        return HudStyleUsability.isUsableImageTextButtonStyle(
                HudBuiltInImageTextButtonStyle.isSelected(styleName)
                        ? builtInImageTextButtonStyle : imageTextButtonStyle(styleName), hasFontOverride);
    }
    boolean hasUsableTextFieldStyle(String styleName, boolean hasFontOverride) {
        requireOpen();
        return HudStyleUsability.isUsableTextFieldStyle(HudBuiltInTextFieldStyle.isSelected(styleName)
                ? builtInTextFieldStyle : textFieldStyle(styleName), hasFontOverride);
    }
    boolean hasUsableSelectBoxStyle(String styleName, boolean hasFontOverride) {
        requireOpen();
        return HudStyleUsability.isUsableSelectBoxStyle(HudBuiltInSelectBoxStyle.isSelected(styleName)
                ? builtInSelectBoxStyle : selectBoxStyle(styleName), hasFontOverride);
    }
    boolean hasUsableCheckBoxStyle(String styleName, boolean hasFontOverride) {
        requireOpen();
        return HudStyleUsability.isUsableCheckBoxStyle(HudBuiltInCheckBoxStyle.isSelected(styleName)
                ? builtInCheckBoxStyle : checkBoxStyle(styleName), hasFontOverride);
    }
    boolean hasUsableSliderStyle(String styleName) {
        requireOpen();
        return HudStyleUsability.isUsableSliderStyle(HudBuiltInSliderStyle.isSelected(styleName)
                ? builtInSliderStyle : sliderStyle(styleName));
    }
    boolean hasUsableProgressBarStyle(String styleName) {
        requireOpen();
        return HudStyleUsability.isUsableProgressBarStyle(HudBuiltInProgressBarStyle.isSelected(styleName)
                ? builtInProgressBarStyle : progressBarStyle(styleName));
    }
    boolean hasUsableScrollPaneStyle(String styleName) {
        requireOpen();
        return HudStyleUsability.isUsableScrollPaneStyle(HudBuiltInScrollPaneStyle.isSelected(styleName)
                ? builtInScrollPaneStyle : scrollPaneStyle(styleName));
    }
    boolean hasUsableTextTooltipStyle(String styleName, boolean hasFontOverride) {
        requireOpen();
        return HudStyleUsability.isUsableTextTooltipStyle(
                HudBuiltInTextTooltipStyle.isSelected(styleName)
                        ? builtInTextTooltipStyle : textTooltipStyle(styleName), hasFontOverride);
    }
    boolean hasUsableWindowStyle(String styleName, boolean hasFontOverride) {
        requireOpen();
        return HudStyleUsability.isUsableWindowStyle(
                HudBuiltInWindowStyle.isSelected(styleName)
                        ? builtInWindowStyle : windowStyle(styleName), hasFontOverride);
    }
    private static boolean usable(ImageButton.ImageButtonStyle style) {
        return style != null;
    }
    private static <T> List<String> styleNames(ObjectMap<String, T> styles,
                                                Predicate<T> isUsable) {
        if (styles == null || styles.isEmpty()) return List.of();
        List<String> names = new ArrayList<>();
        for (ObjectMap.Entry<String, T> entry : styles) {
            if (entry.key != null && !entry.key.isBlank() && isUsable.test(entry.value)) {
                names.add(entry.key);
            }
        }
        names.sort(String::compareTo);
        return List.copyOf(names);
    }
    private void requireOpen() { if (disposed) throw new IllegalStateException("HUD authoring resources are disposed."); }
    @Override public void dispose() {
        if (disposed) return;
        disposed = true;
        for (Font font : textraFonts.values()) font.dispose();
        textraFonts.clear();
        if (skin != null) skin.dispose();
        for (BitmapFont font : bitmapFonts.values()) font.dispose();
        bitmapFonts.clear();
        builtInTextButtonTexture.dispose();
        builtInLabelFont.dispose();
        for (Texture texture : texturesBySource.values()) texture.dispose();
        texturesBySource.clear();
        regionsByName.clear();
    }

    public record LabelDefaults(String styleName) { }
    public record TextButtonDefaults(String styleName) { }
    public record ImageButtonDefaults(String styleName) { }
    public record ImageTextButtonDefaults(String styleName) { }
    public record TextFieldDefaults(String styleName) { }
    public record SelectBoxDefaults(String styleName) { }
    public record CheckBoxDefaults(String styleName) { }
    public record SliderDefaults(String styleName) { }
    public record ProgressBarDefaults(String styleName) { }
    public record ScrollPaneDefaults(String styleName) { }
}
