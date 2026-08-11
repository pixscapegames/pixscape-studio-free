package games.pixscape.studio.ui.asset;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Cell;
import com.badlogic.gdx.scenes.scene2d.ui.Tooltip;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.kotcrab.vis.ui.util.TableUtils;
import com.kotcrab.vis.ui.widget.*;
import com.kotcrab.vis.ui.widget.file.FileChooser;
import com.kotcrab.vis.ui.widget.file.FileChooserAdapter;
import com.kotcrab.vis.ui.widget.spinner.IntSpinnerModel;
import com.kotcrab.vis.ui.widget.spinner.Spinner;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.studio.OsFilesDropTarget;
import games.pixscape.studio.asset.TilesetAnchor;
import games.pixscape.studio.asset.TilesetRenderSize;
import games.pixscape.studio.service.asset.TilesetAssetImportService;
import games.pixscape.studio.service.asset.TilesetAssetImportService.DirectoryTilesetAnalysis;
import games.pixscape.studio.service.asset.TilesetAssetImportService.TilesetProfileImportSettings;
import games.pixscape.studio.service.asset.TsxTilesetDescriptor;
import games.pixscape.studio.service.asset.TsxTilesetImportParser;
import games.pixscape.studio.ui.config.CommonLayout;
import games.pixscape.studio.ui.main.StudioApplicationAdapter;
import games.pixscape.studio.ui.modal.StudioDialog;
import games.pixscape.studio.ui.modal.StudioFileChooser;
import games.pixscape.studio.ui.widget.SimpleSelectBox;

import java.util.Objects;
import java.util.function.Consumer;

public final class ImportDialog extends StudioDialog implements OsFilesDropTarget {

    public enum ImportType {
        IMAGE,
        SPRITESHEET,
        TILESET,
        TILESET_TSX,
        PARTICLE_EFFECT
    }

    private static final float DIALOG_MIN_W = 1000f;
    private static final float DIALOG_MIN_H = 460f;
    private static final float LIST_MIN_W = 940f;
    private static final float LIST_MIN_H = 320f;
    private static final float COL_FILE_MIN_W = 260f;
    private static final float COL_TYPE_W = 160f;
    private static final float COL_DETAILS_W = 500f;
    private static final float COL_SPIN_W = 90f;
    private static final float COL_X_W = 34f;
    private static final float DETAILS_GAP = 6f;

    private final StudioApplicationAdapter app;
    private final Consumer<Array<ImportItem>> onApply;
    private final TilesetDirectoryImportHandler onImportTilesetDirectory;

    private final Array<ImportItem> items = new Array<>();
    private final VisTable listTable = new VisTable(false);
    private final VisScrollPane listScroll = new VisScrollPane(listTable);
    private final VisLabel inlineErrorLabel = new VisLabel("");

    /**
     * Result ready to import (not applied to the project yet).
     */
    public static final class ImportItem {
        public FileHandle file;        // absolute OS path
        public ImportType type;        // selected by the user
        public int tileWidth = 32;
        public int tileHeight = 32;
        public int tileSpacing = 0;
        public int tileMargin = 0;
        public int imageWidth = -1;
        public int imageHeight = -1;
        public int referenceCellWidth = 32;
        public int referenceCellHeight = 32;
        public SceneMetaRuntime.TiledProjection projection = SceneMetaRuntime.TiledProjection.ORTHO;
        public TilesetAnchor anchor = TilesetAnchor.TOP_CENTER;
        public int offsetX = 0;
        public int offsetY = 0;
        public TilesetRenderSize renderSize = TilesetRenderSize.NATIVE;

        private boolean dimensionsResolved;
        transient VisValidatableTextField tileWidthField;
        transient VisValidatableTextField tileHeightField;

        public ImportItem(FileHandle file) {
            this.file = file;
        }

        void applySlicingSettings(int tileWidth, int tileHeight, int tileMargin, int tileSpacing) {
            int oldTileWidth = this.tileWidth;
            int oldTileHeight = this.tileHeight;
            this.tileWidth = tileWidth;
            this.tileHeight = tileHeight;
            this.tileMargin = tileMargin;
            this.tileSpacing = tileSpacing;
            updateProfileDefaultsAfterTileSizeChange(oldTileWidth, oldTileHeight, tileWidth, tileHeight);
        }

        void applyTilesetProfileSettings(int tileWidth,
                                         int tileHeight,
                                         int tileMargin,
                                         int tileSpacing,
                                         int referenceCellWidth,
                                         int referenceCellHeight,
                                         SceneMetaRuntime.TiledProjection projection,
                                         TilesetAnchor anchor,
                                         int offsetX,
                                         int offsetY,
                                         TilesetRenderSize renderSize) {
            applySlicingSettings(tileWidth, tileHeight, tileMargin, tileSpacing);
            this.referenceCellWidth = referenceCellWidth;
            this.referenceCellHeight = referenceCellHeight;
            this.projection = projection;
            this.anchor = anchor;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.renderSize = renderSize;
        }

        public TilesetProfileImportSettings tilesetProfileSettings() {
            return new TilesetProfileImportSettings(
                    referenceCellWidth,
                    referenceCellHeight,
                    projection,
                    anchor,
                    offsetX,
                    offsetY,
                    renderSize
            );
        }

        private void updateProfileDefaultsAfterTileSizeChange(int oldTileWidth,
                                                              int oldTileHeight,
                                                              int newTileWidth,
                                                              int newTileHeight) {
            if (referenceCellWidth == oldTileWidth) {
                referenceCellWidth = newTileWidth;
            }
            if (referenceCellHeight == oldTileHeight) {
                referenceCellHeight = newTileHeight;
            }
        }

        void resolveDimensionsIfNeeded(ImportDialogValidation.DimensionReader reader) {
            if (dimensionsResolved) return;
            dimensionsResolved = true;
            if (reader == null) return;

            int[] size;
            try {
                size = reader.read(file);
            } catch (RuntimeException ignored) {
                return;
            }
            if (size == null || size.length < 2) return;

            imageWidth = size[0];
            imageHeight = size[1];
        }
    }

    @FunctionalInterface
    public interface TilesetDirectoryImportHandler {
        void importDirectory(FileHandle directory, TilesetProfileImportSettings profileSettings);
    }

    public ImportDialog(StudioApplicationAdapter app,
                        Consumer<Array<ImportItem>> onApply,
                        TilesetDirectoryImportHandler onImportTilesetDirectory) {
        super("Import assets");
        this.onApply = onApply;
        this.onImportTilesetDirectory = onImportTilesetDirectory;
        this.app = app;

        setModal(true);
        setResizable(true);

        TableUtils.setSpacingDefaults(this);

        buildUi();

        buildButtons();

        pack();
        setSize(Math.max(getWidth(), DIALOG_MIN_W), Math.max(getHeight(), DIALOG_MIN_H));
        centerWindow();
    }

    private void buildButtons() {
        VisTextButton importButton = new VisTextButton("Import");
        VisTextButton cancelButton = new VisTextButton("Cancel");

        importButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                applyImportAndCloseIfSuccessful();
            }
        });

        cancelButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                fadeOut();
            }
        });

        getButtonsTable().add(importButton);
        getButtonsTable().add(cancelButton);
    }

    private void buildUi() {
        VisTable root = new VisTable(true);

        // --- Top bar (Add files / Add tileset directory / Clear) ---
        VisTextButton addFilesBtn = new VisTextButton("Add files...");
        addFilesBtn.setColor(CommonLayout.BUTTON_COLOR);

        VisTextButton addTilesetDirBtn = new VisTextButton("Add tileset directory...");
        addTilesetDirBtn.setColor(CommonLayout.BUTTON_COLOR);

        VisTextButton clearBtn = new VisTextButton("Clear");
        clearBtn.setColor(CommonLayout.BUTTON_COLOR);

        addFilesBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                openFileChooser();
            }
        });

        addTilesetDirBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                openTilesetDirectoryChooser();
            }
        });

        clearBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                items.clear();
                clearInlineError();
                refreshList();
            }
        });

        VisTable top = new VisTable(true);
        top.center();
        top.add(addFilesBtn).padTop(20);
        top.add(addTilesetDirBtn).padTop(20).padLeft(6);
        top.add(clearBtn).padTop(20).padLeft(6);

        root.add(top).growX().row();

        // --- List ---
        listScroll.setFadeScrollBars(false);
        listScroll.setScrollingDisabled(true, false);
        root.add(listScroll).grow().minHeight(LIST_MIN_H).minWidth(LIST_MIN_W).row();

        inlineErrorLabel.setVisible(false);
        inlineErrorLabel.setColor(1f, 0.4f, 0.4f, 1f);
        root.add(inlineErrorLabel).left().growX().row();

        VisLabel hint = new VisLabel("Tip: PNG is ambiguous. Choose Image, Sprite sheet or Tile set.");
        hint.setColor(0.8f, 0.8f, 0.8f, 1f);
        root.add(hint).left().padTop(6).row();

        getContentTable().add(root).grow();
        refreshList();
    }

    private void openFileChooser() {
        FileChooser chooser = new StudioFileChooser(FileChooser.Mode.OPEN);
        chooser.setMultiSelectionEnabled(true);
        chooser.setSize(900, 650);

        chooser.setListener(new FileChooserAdapter() {
            @Override
            public void selected(Array<FileHandle> files) {
                if (files == null || files.size == 0) return;

                for (FileHandle file : files) {
                    addDroppedEntry(file);
                }

                clearInlineError();
                refreshList();
            }
        });

        getStage().addActor(chooser.fadeIn());
    }

    private void openTilesetDirectoryChooser() {
        FileChooser chooser = new StudioFileChooser(FileChooser.Mode.OPEN);
        chooser.setMultiSelectionEnabled(false);
        chooser.setSelectionMode(FileChooser.SelectionMode.DIRECTORIES);
        chooser.setSize(900, 650);

        chooser.setListener(new FileChooserAdapter() {
            @Override
            public void selected(Array<FileHandle> files) {
                if (files == null || files.size == 0) return;

                FileHandle directory = files.first();
                if (directory == null || !directory.exists() || !directory.isDirectory()) {
                    setInlineError("Please select a valid directory.");
                    return;
                }

                if (onImportTilesetDirectory == null) {
                    setInlineError("Tileset directory import is not available.");
                    return;
                }

                try {
                    DirectoryTilesetAnalysis analysis = TilesetAssetImportService.analyzeDirectory(directory);
                    if (analysis.sourceTiles().length == 0) {
                        setInlineError("Tileset directory contains no PNG files.");
                        return;
                    }
                    openTilesetDirectoryProfileDialog(directory, analysis);
                } catch (IllegalArgumentException ex) {
                    setInlineError(ImportDialogApplySupport.userMessageFor(ex));
                } catch (RuntimeException ex) {
                    setInlineError("Tileset directory could not be analyzed.");
                }
            }
        });

        getStage().addActor(chooser.fadeIn());
    }

    private void addDroppedEntry(FileHandle entry) {
        if (entry == null || !entry.exists()) return;

        Array<FileHandle> supported = new Array<>();
        collectSupportedFiles(entry, supported);

        for (FileHandle file : supported) {
            addOrSkip(file);
        }
    }

    private void collectSupportedFiles(FileHandle entry, Array<FileHandle> out) {
        if (entry == null || !entry.exists() || out == null) return;

        if (entry.isDirectory()) {
            FileHandle[] children = entry.list();
            if (children == null) return;

            for (FileHandle child : children) {
                collectSupportedFiles(child, out);
            }
            return;
        }

        if (ImportDialogValidation.isSupportedImportFile(entry)) {
            out.add(entry);
        }
    }

    private void addOrSkip(FileHandle file) {
        if (file == null || !file.exists() || file.isDirectory()) return;
        if (!ImportDialogValidation.isSupportedImportFile(file)) return;

        for (int i = 0; i < items.size; i++) {
            ImportItem existing = items.get(i);
            if (existing.file != null && Objects.equals(existing.file.path(), file.path())) {
                return;
            }
        }

        ImportItem item = new ImportItem(file);
        item.type = guessType(file);
        item.resolveDimensionsIfNeeded(ImportDialogValidation::readImageDimensions);
        items.add(item);
    }

    private static ImportType guessType(FileHandle file) {
        if (ImportDialogValidation.isParticleFile(file)) return ImportType.PARTICLE_EFFECT;
        if (ImportDialogValidation.isTsxFile(file)) return ImportType.TILESET_TSX;
        if (ImportDialogValidation.isSupportedImage(file)) return ImportType.IMAGE;
        return null;
    }

    private void refreshList() {
        listTable.clear();
        listTable.top().left();
        listTable.defaults().pad(4).top();

        if (items.size == 0) {
            listTable.add(new VisLabel("drop files here  (.png, .p, .tsx)"))
                    .left()
                    .pad(6)
                    .colspan(4)
                    .row();
            return;
        }

        // Header
        listTable.add(new VisLabel("File"))
                .left()
                .minWidth(COL_FILE_MIN_W)
                .growX();

        listTable.add(new VisLabel("Type"))
                .left()
                .width(COL_TYPE_W);

        listTable.add(new VisLabel("Details"))
                .left()
                .width(COL_DETAILS_W);

        listTable.add()
                .width(COL_X_W);

        listTable.row();

        listTable.add(new Separator())
                .growX()
                .colspan(4)
                .padBottom(4);

        listTable.row();

        for (int i = 0; i < items.size; i++) {
            ImportItem item = items.get(i);
            item.tileWidthField = null;
            item.tileHeightField = null;

            String fileName = item.file != null ? item.file.name() : "?";
            String filePath = item.file != null ? item.file.path() : "?";

            VisLabel fileLabel = new VisLabel(fileName);
            fileLabel.setEllipsis(true);
            fileLabel.setTouchable(Touchable.enabled);
            fileLabel.addListener(new Tooltip<>(new VisLabel(filePath)));

            Actor typeActor = buildTypeActor(item);
            Actor detailsActor = buildDetailsActor(item);

            VisTextButton removeBtn = new VisTextButton("X");
            removeBtn.setColor(CommonLayout.BUTTON_COLOR);
            removeBtn.getLabel().setFontScale(0.9f);
            removeBtn.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    items.removeValue(item, true);
                    clearInlineError();
                    refreshList();
                }
            });

            listTable.add(fileLabel)
                    .left()
                    .minWidth(COL_FILE_MIN_W)
                    .growX();

            listTable.add(typeActor)
                    .left()
                    .width(COL_TYPE_W);

            listTable.add(detailsActor)
                    .left()
                    .width(COL_DETAILS_W)
                    .fillX();

            listTable.add(removeBtn)
                    .right()
                    .width(COL_X_W);

            listTable.row();
        }
    }

    private Actor buildTypeActor(ImportItem item) {
        if (ImportDialogValidation.isParticleFile(item.file)) {
            VisLabel label = new VisLabel("Particle effect");
            label.setColor(0.85f, 0.85f, 0.85f, 1f);
            return label;
        }

        if (ImportDialogValidation.isTsxFile(item.file)) {
            VisLabel label = new VisLabel("TSX tile set");
            label.setColor(0.85f, 0.85f, 0.85f, 1f);
            return label;
        }

        SimpleSelectBox<ImportType> typeBox = new SimpleSelectBox<>();
        typeBox.setItems(ImportType.IMAGE, ImportType.SPRITESHEET, ImportType.TILESET);
        typeBox.setSelected(item.type != null ? item.type : ImportType.IMAGE);

        typeBox.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                item.type = typeBox.getSelected();
                clearInlineError();
                refreshList();
            }
        });

        return typeBox;
    }

    private Actor buildDetailsActor(ImportItem item) {
        String rowError = getRowErrorMessage(item);
        if (rowError != null) {
            VisLabel errorLabel = new VisLabel(rowError);
            errorLabel.setWrap(true);
            errorLabel.setAlignment(Align.left);
            errorLabel.setColor(1f, 0.35f, 0.35f, 1f);

            VisTable wrap = new VisTable(false);
            wrap.left();
            wrap.add(errorLabel).left().width(COL_DETAILS_W - 8f).fillX();
            return wrap;
        }

        if (ImportDialogValidation.isParticleFile(item.file)) {
            VisLabel label = new VisLabel("Particle effect file");
            label.setColor(0.78f, 0.78f, 0.78f, 1f);

            VisTable wrap = new VisTable(false);
            wrap.left();
            wrap.add(label).left();
            return wrap;
        }

        if (item.type == ImportType.TILESET_TSX || ImportDialogValidation.isTsxFile(item.file)) {
            return buildTsxTilesetDetailsActor(item);
        }

        if (item.type == ImportType.SPRITESHEET) {
            return buildSheetDetailsActor(item);
        }

        if (item.type == ImportType.TILESET) {
            return buildTilesetDetailsActor(item);
        }

        String sizeText = formatImageSize(item);
        VisLabel label = new VisLabel(sizeText.isBlank() ? "" : sizeText);
        label.setColor(0.78f, 0.78f, 0.78f, 1f);

        VisTable wrap = new VisTable(false);
        wrap.left();
        wrap.add(label).left();
        return wrap;
    }

    private Actor buildSheetDetailsActor(ImportItem item) {
        int maxTileWidth = item.imageWidth > 0 ? item.imageWidth : ImportDialogValidation.MAX_IMAGE_SIZE;
        int maxTileHeight = item.imageHeight > 0 ? item.imageHeight : ImportDialogValidation.MAX_IMAGE_SIZE;

        // No reusable non-ECS studio wrapper exists for these modal-local VisUI spinners yet.
        IntSpinnerModel tileWidthModel = new IntSpinnerModel(Math.max(1, item.tileWidth), 1, maxTileWidth, 1);
        Spinner tileWidthSpinner = new Spinner("W", tileWidthModel);

        IntSpinnerModel tileHeightModel = new IntSpinnerModel(Math.max(1, item.tileHeight), 1, maxTileHeight, 1);
        Spinner tileHeightSpinner = new Spinner("H", tileHeightModel);

        tileWidthSpinner.getTextField().setAlignment(Align.center);
        tileHeightSpinner.getTextField().setAlignment(Align.center);

        tileWidthSpinner.getTextField().addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                String text = tileWidthSpinner.getTextField().getText();
                if (ImportDialogValidation.isPositiveInteger(text)) {
                    int value = Integer.parseInt(text.trim());
                    tileWidthModel.setValue(value);
                    item.tileWidth = value;
                }
                clearInlineError();
            }
        });

        tileHeightSpinner.getTextField().addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                String text = tileHeightSpinner.getTextField().getText();
                if (ImportDialogValidation.isPositiveInteger(text)) {
                    int value = Integer.parseInt(text.trim());
                    tileHeightModel.setValue(value);
                    item.tileHeight = value;
                }
                clearInlineError();
            }
        });

        tileWidthSpinner.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                item.tileWidth = Math.max(1, tileWidthModel.getValue());
            }
        });

        tileHeightSpinner.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                item.tileHeight = Math.max(1, tileHeightModel.getValue());
            }
        });

        item.tileWidthField = tileWidthSpinner.getTextField();
        item.tileHeightField = tileHeightSpinner.getTextField();

        item.tileWidthField.addValidator(input ->
                ImportDialogValidation.isDivisibleForType(item.type, item.imageWidth, input)
        );
        item.tileWidthField.addValidator(ImportDialogValidation::isPositiveIntegerWithinMaxSize);
        item.tileHeightField.addValidator(input ->
                ImportDialogValidation.isDivisibleForType(item.type, item.imageHeight, input)
        );
        item.tileHeightField.addValidator(ImportDialogValidation::isPositiveIntegerWithinMaxSize);

        String prefix = "Frame:";
        String imageSize = formatImageSize(item);

        VisLabel prefixLabel = new VisLabel(prefix);
        prefixLabel.setColor(0.78f, 0.78f, 0.78f, 1f);

        VisTable details = new VisTable(false);
        details.left();
        details.defaults().padRight(DETAILS_GAP).center();

        details.add(prefixLabel).left();
        details.add(tileWidthSpinner).width(COL_SPIN_W).center();
        details.add(tileHeightSpinner).width(COL_SPIN_W).center();

        if (!imageSize.isBlank()) {
            VisLabel sizeLabel = new VisLabel(imageSize);
            sizeLabel.setColor(0.70f, 0.70f, 0.70f, 1f);
            details.add(sizeLabel).left().expandX().fillX().padRight(0f);
        } else {
            details.add().expandX().fillX().padRight(0f);
        }

        return details;
    }

    private Actor buildTsxTilesetDetailsActor(ImportItem item) {
        VisLabel label = new VisLabel(formatTsxTilesetSummary(item));
        label.setWrap(true);
        label.setColor(0.78f, 0.78f, 0.78f, 1f);

        VisTable wrap = new VisTable(false);
        wrap.left();
        wrap.add(label).left().width(COL_DETAILS_W - 8f).fillX();
        return wrap;
    }

    private Actor buildTilesetDetailsActor(ImportItem item) {
        VisLabel summary = new VisLabel(formatTilesetCompactProfileSummary(item));
        summary.setColor(0.78f, 0.78f, 0.78f, 1f);
        summary.setEllipsis(true);
        summary.setTouchable(Touchable.enabled);
        summary.addListener(new Tooltip<>(new VisLabel(formatTilesetFullProfileSummary(item))));

        VisTextButton settingsButton = new VisTextButton("Profile...");
        settingsButton.setColor(CommonLayout.BUTTON_COLOR);
        settingsButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                openTilesetProfileDialog(item);
            }
        });

        VisTable details = new VisTable(false);
        details.left();
        details.defaults().left();
        details.add(summary).left().width(260f).fillX();
        details.add().growX();
        details.add(settingsButton).right().width(96f);

        return details;
    }

    private void openTilesetDirectoryProfileDialog(FileHandle directory, DirectoryTilesetAnalysis analysis) {
        if (directory == null || analysis == null || analysis.sourceTiles().length == 0) return;

        FileHandle[] sourceTiles = analysis.sourceTiles();
        int defaultCellWidth = Math.max(1, analysis.referenceTileWidth());
        int defaultCellHeight = Math.max(1, analysis.referenceTileHeight());

        VisDialog dialog = new StudioDialog("Tileset profile");
        dialog.setModal(true);
        TableUtils.setSpacingDefaults(dialog);

        IntSpinnerModel referenceCellWidthModel = new IntSpinnerModel(defaultCellWidth, 1, ImportDialogValidation.MAX_IMAGE_SIZE, 1);
        Spinner referenceCellWidthSpinner = new Spinner("", referenceCellWidthModel);
        IntSpinnerModel referenceCellHeightModel = new IntSpinnerModel(defaultCellHeight, 1, ImportDialogValidation.MAX_IMAGE_SIZE, 1);
        Spinner referenceCellHeightSpinner = new Spinner("", referenceCellHeightModel);
        IntSpinnerModel offsetXModel = new IntSpinnerModel(0, -ImportDialogValidation.MAX_TILESET_OFFSET, ImportDialogValidation.MAX_TILESET_OFFSET, 1);
        Spinner offsetXSpinner = new Spinner("", offsetXModel);
        IntSpinnerModel offsetYModel = new IntSpinnerModel(0, -ImportDialogValidation.MAX_TILESET_OFFSET, ImportDialogValidation.MAX_TILESET_OFFSET, 1);
        Spinner offsetYSpinner = new Spinner("", offsetYModel);

        SimpleSelectBox<String> projectionBox = new SimpleSelectBox<>();
        projectionBox.setItems("Orthogonal", "Isometric");
        projectionBox.setSelected("Orthogonal");

        SimpleSelectBox<String> anchorBox = new SimpleSelectBox<>();
        anchorBox.setItems("Top center", "Bottom center", "Bottom left", "Center", "Top left");
        anchorBox.setSelected("Top center");

        referenceCellWidthSpinner.getTextField().setAlignment(Align.center);
        referenceCellHeightSpinner.getTextField().setAlignment(Align.center);
        offsetXSpinner.getTextField().setAlignment(Align.center);
        offsetYSpinner.getTextField().setAlignment(Align.center);
        referenceCellWidthSpinner.getTextField().addValidator(ImportDialogValidation::isPositiveIntegerWithinMaxSize);
        referenceCellHeightSpinner.getTextField().addValidator(ImportDialogValidation::isPositiveIntegerWithinMaxSize);
        offsetXSpinner.getTextField().addValidator(ImportDialogValidation::isIntegerWithinTilesetOffsetRange);
        offsetYSpinner.getTextField().addValidator(ImportDialogValidation::isIntegerWithinTilesetOffsetRange);

        int[] previewImageIndex = {0};
        VisTextButton previousImageButton = new VisTextButton("Previous");
        VisTextButton nextImageButton = new VisTextButton("Next");
        VisLabel previewImageLabel = new VisLabel("");
        VisLabel previewLabel = new VisLabel("");
        previewLabel.setColor(0.78f, 0.78f, 0.78f, 1f);
        previewLabel.setWrap(true);

        TilesetTilePreviewActor tilePreviewActor = new TilesetTilePreviewActor(sourceTiles[0]);
        VisLabel tilePreviewStatusLabel = new VisLabel(tilePreviewActor.statusText());
        tilePreviewStatusLabel.setColor(0.70f, 0.70f, 0.70f, 1f);
        previousImageButton.setColor(CommonLayout.BUTTON_COLOR);
        nextImageButton.setColor(CommonLayout.BUTTON_COLOR);

        VisLabel errorLabel = new VisLabel("");
        errorLabel.setVisible(false);
        errorLabel.setColor(1f, 0.4f, 0.4f, 1f);

        VisTable form = new VisTable(false);
        form.left();
        form.defaults().left().padBottom(8f);
        form.padTop(20f);
        addSlicingFieldRow(form, "Cell width", referenceCellWidthSpinner);
        addSlicingFieldRow(form, "Cell height", referenceCellHeightSpinner);
        addSelectRow(form, "Projection", projectionBox);
        addSelectRow(form, "Anchor", anchorBox);
        addSlicingFieldRow(form, "Offset X", offsetXSpinner);
        addSlicingFieldRow(form, "Offset Y", offsetYSpinner);
        addReadOnlyRow(form, "Render size", "Native");
        Cell<VisLabel> errorCell = form.add(errorLabel).left().width(300f).colspan(2);
        errorCell.height(0f).padTop(0f);
        form.row();

        VisTable nav = new VisTable(false);
        nav.center();
        nav.defaults().padRight(6f);
        nav.add(previousImageButton).left().width(96f);
        nav.add(previewImageLabel).left().width(96f);
        nav.add(nextImageButton).left().width(96f).padRight(0f);

        VisTable previewColumn = new VisTable(false);
        previewColumn.top().center();
        previewColumn.defaults().center().padBottom(8f);
        previewColumn.add(tilePreviewActor).width(160f).height(160f).row();
        previewColumn.add(tilePreviewStatusLabel).width(220f).row();
        previewColumn.add(nav).row();
        previewColumn.add(previewLabel).left().width(240f).row();

        boolean[] updatingControls = {false};
        TilesetProfileReferenceDefaults referenceDefaults = new TilesetProfileReferenceDefaults(
                defaultCellWidth,
                defaultCellHeight,
                defaultCellWidth,
                defaultCellHeight,
                SceneMetaRuntime.TiledProjection.ORTHO
        );
        SceneMetaRuntime.TiledProjection[] previousProjection = {SceneMetaRuntime.TiledProjection.ORTHO};

        ChangeListener previewListener = new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (updatingControls[0]) return;

                SceneMetaRuntime.TiledProjection projection = projectionFromDisplayName(projectionBox.getSelected());
                if (projection != null && projection != previousProjection[0]) {
                    TilesetProfileReferenceDefaults.ReferenceSize referenceSize =
                            referenceDefaults.referenceSizeAfterProjectionChange(
                                    projection,
                                    defaultCellWidth,
                                    defaultCellHeight,
                                    referenceCellWidthModel.getValue(),
                                    referenceCellHeightModel.getValue()
                            );
                    previousProjection[0] = projection;
                    setSpinnerModelValueGuarded(referenceCellWidthModel, referenceSize.width(), updatingControls);
                    setSpinnerModelValueGuarded(referenceCellHeightModel, referenceSize.height(), updatingControls);
                }

                refreshTilesetDirectoryProfilePreview(
                        dialog,
                        previewLabel,
                        errorLabel,
                        errorCell,
                        previewImageIndex,
                        previewImageLabel,
                        previousImageButton,
                        nextImageButton,
                        tilePreviewActor,
                        tilePreviewStatusLabel,
                        sourceTiles,
                        referenceCellWidthSpinner,
                        referenceCellHeightSpinner,
                        projectionBox,
                        anchorBox,
                        offsetXSpinner,
                        offsetYSpinner
                );
            }
        };

        referenceCellWidthSpinner.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (!updatingControls[0]) {
                    referenceDefaults.markReferenceWidthEdited();
                    previewListener.changed(event, actor);
                }
            }
        });
        referenceCellWidthSpinner.getTextField().addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (!updatingControls[0]) {
                    referenceDefaults.markReferenceWidthEdited();
                    previewListener.changed(event, actor);
                }
            }
        });
        referenceCellHeightSpinner.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (!updatingControls[0]) {
                    referenceDefaults.markReferenceHeightEdited();
                    previewListener.changed(event, actor);
                }
            }
        });
        referenceCellHeightSpinner.getTextField().addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (!updatingControls[0]) {
                    referenceDefaults.markReferenceHeightEdited();
                    previewListener.changed(event, actor);
                }
            }
        });
        offsetXSpinner.addListener(previewListener);
        offsetYSpinner.addListener(previewListener);
        offsetXSpinner.getTextField().addListener(previewListener);
        offsetYSpinner.getTextField().addListener(previewListener);
        projectionBox.addListener(previewListener);
        anchorBox.addListener(previewListener);

        previousImageButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (previewImageIndex[0] > 0) {
                    previewImageIndex[0]--;
                }
                refreshTilesetDirectoryProfilePreview(
                        dialog, previewLabel, errorLabel, errorCell, previewImageIndex,
                        previewImageLabel, previousImageButton, nextImageButton,
                        tilePreviewActor, tilePreviewStatusLabel, sourceTiles,
                        referenceCellWidthSpinner, referenceCellHeightSpinner, projectionBox,
                        anchorBox, offsetXSpinner, offsetYSpinner
                );
            }
        });

        nextImageButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                previewImageIndex[0] = Math.min(previewImageIndex[0] + 1, sourceTiles.length - 1);
                refreshTilesetDirectoryProfilePreview(
                        dialog, previewLabel, errorLabel, errorCell, previewImageIndex,
                        previewImageLabel, previousImageButton, nextImageButton,
                        tilePreviewActor, tilePreviewStatusLabel, sourceTiles,
                        referenceCellWidthSpinner, referenceCellHeightSpinner, projectionBox,
                        anchorBox, offsetXSpinner, offsetYSpinner
                );
            }
        });

        VisTextButton applyButton = new VisTextButton("Apply");
        VisTextButton cancelButton = new VisTextButton("Cancel");
        applyButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                TilesetProfileImportSettings settings = readDirectoryProfileDialogSettings(
                        referenceCellWidthSpinner,
                        referenceCellHeightSpinner,
                        projectionBox,
                        anchorBox,
                        offsetXSpinner,
                        offsetYSpinner
                );
                if (settings == null) {
                    setSlicingDialogError(dialog, errorLabel, errorCell, "Use valid tileset profile values.");
                    return;
                }

                try {
                    onImportTilesetDirectory.importDirectory(directory, settings);
                    dialog.fadeOut();
                    fadeOut();
                } catch (IllegalArgumentException ex) {
                    setSlicingDialogError(dialog, errorLabel, errorCell, ImportDialogApplySupport.userMessageFor(ex));
                } catch (RuntimeException ex) {
                    setSlicingDialogError(dialog, errorLabel, errorCell, "Tileset directory import failed.");
                }
            }
        });
        cancelButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                dialog.fadeOut();
            }
        });

        VisTable dialogBody = new VisTable(false);
        dialogBody.top().left();
        dialogBody.defaults().top().pad(12f);
        dialogBody.padLeft(12f);
        dialogBody.add(previewColumn).width(260f).padLeft(8f);
        dialogBody.add(form).width(320f);

        dialog.getContentTable().add(dialogBody).pad(8f);
        dialog.getButtonsTable().defaults().minWidth(96f).height(28f).padTop(4f).padBottom(8f).padRight(8f);
        dialog.getButtonsTable().add(applyButton);
        dialog.getButtonsTable().add(cancelButton).padRight(0f);
        dialog.pack();
        dialog.centerWindow();
        refreshTilesetDirectoryProfilePreview(
                dialog,
                previewLabel,
                errorLabel,
                errorCell,
                previewImageIndex,
                previewImageLabel,
                previousImageButton,
                nextImageButton,
                tilePreviewActor,
                tilePreviewStatusLabel,
                sourceTiles,
                referenceCellWidthSpinner,
                referenceCellHeightSpinner,
                projectionBox,
                anchorBox,
                offsetXSpinner,
                offsetYSpinner
        );
        getStage().addActor(dialog.fadeIn());
    }

    private void openTilesetProfileDialog(ImportItem item) {
        if (item == null) return;

        VisDialog dialog = new StudioDialog("Tileset profile");
        dialog.setModal(true);
        TableUtils.setSpacingDefaults(dialog);

        int maxTileWidth = item.imageWidth > 0 ? Math.min(item.imageWidth, ImportDialogValidation.MAX_IMAGE_SIZE) : ImportDialogValidation.MAX_IMAGE_SIZE;
        int maxTileHeight = item.imageHeight > 0 ? Math.min(item.imageHeight, ImportDialogValidation.MAX_IMAGE_SIZE) : ImportDialogValidation.MAX_IMAGE_SIZE;

        // No reusable non-ECS studio wrapper exists for these modal-local VisUI spinners yet.
        IntSpinnerModel tileWidthModel = new IntSpinnerModel(Math.max(1, item.tileWidth), 1, maxTileWidth, 1);
        Spinner tileWidthSpinner = new Spinner("", tileWidthModel);

        IntSpinnerModel tileHeightModel = new IntSpinnerModel(Math.max(1, item.tileHeight), 1, maxTileHeight, 1);
        Spinner tileHeightSpinner = new Spinner("", tileHeightModel);

        IntSpinnerModel marginModel = new IntSpinnerModel(Math.max(0, item.tileMargin), 0, ImportDialogValidation.MAX_IMAGE_SIZE, 1);
        Spinner marginSpinner = new Spinner("", marginModel);

        IntSpinnerModel spacingModel = new IntSpinnerModel(Math.max(0, item.tileSpacing), 0, ImportDialogValidation.MAX_IMAGE_SIZE, 1);
        Spinner spacingSpinner = new Spinner("", spacingModel);

        IntSpinnerModel referenceCellWidthModel = new IntSpinnerModel(Math.max(1, item.referenceCellWidth), 1, Integer.MAX_VALUE, 1);
        Spinner referenceCellWidthSpinner = new Spinner("", referenceCellWidthModel);

        IntSpinnerModel referenceCellHeightModel = new IntSpinnerModel(Math.max(1, item.referenceCellHeight), 1, Integer.MAX_VALUE, 1);
        Spinner referenceCellHeightSpinner = new Spinner("", referenceCellHeightModel);

        IntSpinnerModel offsetXModel = new IntSpinnerModel(
                item.offsetX,
                -ImportDialogValidation.MAX_TILESET_OFFSET,
                ImportDialogValidation.MAX_TILESET_OFFSET,
                1
        );
        Spinner offsetXSpinner = new Spinner("", offsetXModel);

        IntSpinnerModel offsetYModel = new IntSpinnerModel(
                item.offsetY,
                -ImportDialogValidation.MAX_TILESET_OFFSET,
                ImportDialogValidation.MAX_TILESET_OFFSET,
                1
        );
        Spinner offsetYSpinner = new Spinner("", offsetYModel);

        SimpleSelectBox<String> projectionBox = new SimpleSelectBox<>();
        projectionBox.setItems("Orthogonal", "Isometric");
        projectionBox.setSelected(displayName(item.projection));

        SimpleSelectBox<String> anchorBox = new SimpleSelectBox<>();
        anchorBox.setItems("Top center", "Bottom center", "Bottom left", "Center", "Top left");
        anchorBox.setSelected(displayName(item.anchor));

        tileWidthSpinner.getTextField().setAlignment(Align.center);
        tileHeightSpinner.getTextField().setAlignment(Align.center);
        marginSpinner.getTextField().setAlignment(Align.center);
        spacingSpinner.getTextField().setAlignment(Align.center);
        referenceCellWidthSpinner.getTextField().setAlignment(Align.center);
        referenceCellHeightSpinner.getTextField().setAlignment(Align.center);
        offsetXSpinner.getTextField().setAlignment(Align.center);
        offsetYSpinner.getTextField().setAlignment(Align.center);

        tileWidthSpinner.getTextField().addValidator(ImportDialogValidation::isPositiveIntegerWithinMaxSize);
        tileHeightSpinner.getTextField().addValidator(ImportDialogValidation::isPositiveIntegerWithinMaxSize);
        marginSpinner.getTextField().addValidator(ImportDialogValidation::isNonNegativeIntegerWithinMaxSize);
        spacingSpinner.getTextField().addValidator(ImportDialogValidation::isNonNegativeIntegerWithinMaxSize);
        referenceCellWidthSpinner.getTextField().addValidator(ImportDialogValidation::isPositiveInteger);
        referenceCellHeightSpinner.getTextField().addValidator(ImportDialogValidation::isPositiveInteger);
        offsetXSpinner.getTextField().addValidator(ImportDialogValidation::isIntegerWithinTilesetOffsetRange);
        offsetYSpinner.getTextField().addValidator(ImportDialogValidation::isIntegerWithinTilesetOffsetRange);

        VisLabel previewLabel = new VisLabel("");
        previewLabel.setColor(0.78f, 0.78f, 0.78f, 1f);
        previewLabel.setWrap(true);

        int[] previewTileIndex = {0};
        VisTextButton previousTileButton = new VisTextButton("Previous");
        VisTextButton nextTileButton = new VisTextButton("Next");
        VisLabel previewTileLabel = new VisLabel("");
        TilesetTilePreviewActor tilePreviewActor = new TilesetTilePreviewActor(item.file);
        VisLabel tilePreviewStatusLabel = new VisLabel(tilePreviewActor.statusText());
        tilePreviewStatusLabel.setColor(0.70f, 0.70f, 0.70f, 1f);
        previousTileButton.setColor(CommonLayout.BUTTON_COLOR);
        nextTileButton.setColor(CommonLayout.BUTTON_COLOR);

        VisLabel errorLabel = new VisLabel("");
        errorLabel.setVisible(false);
        errorLabel.setColor(1f, 0.4f, 0.4f, 1f);

        VisTable form = new VisTable(false);
        form.left();
        form.defaults().left().padBottom(8f);
        form.padTop(20f);
        addSlicingFieldRow(form, "Tile width", tileWidthSpinner);
        addSlicingFieldRow(form, "Tile height", tileHeightSpinner);
        addSlicingFieldRow(form, "Margin", marginSpinner);
        addSlicingFieldRow(form, "Spacing", spacingSpinner);
        addSlicingFieldRow(form, "Cell width", referenceCellWidthSpinner);
        addSlicingFieldRow(form, "Cell height", referenceCellHeightSpinner);
        addSelectRow(form, "Projection", projectionBox);
        addSelectRow(form, "Anchor", anchorBox);
        addSlicingFieldRow(form, "Offset X", offsetXSpinner);
        addSlicingFieldRow(form, "Offset Y", offsetYSpinner);
        addReadOnlyRow(form, "Render size", "Native");

        VisTable nav = new VisTable(false);
        nav.center();
        nav.defaults().padRight(6f);
        nav.add(previousTileButton).left().width(96f);
        nav.add(previewTileLabel).left().width(96f);
        nav.add(nextTileButton).left().width(96f).padRight(0f);

        VisTable previewColumn = new VisTable(false);
        previewColumn.top().center();
        previewColumn.defaults().center().padBottom(8f);
        previewColumn.add(tilePreviewActor).width(160f).height(160f).row();
        previewColumn.add(tilePreviewStatusLabel).width(220f).row();
        previewColumn.add(nav).row();
        previewColumn.add(previewLabel).left().width(240f).row();

        Cell<VisLabel> errorCell = form.add(errorLabel).left().width(300f).colspan(2);
        errorCell.height(0f).padTop(0f);
        form.row();

        boolean[] updatingControls = {false};
        TilesetProfileReferenceDefaults referenceDefaults = new TilesetProfileReferenceDefaults(
                item.tileWidth,
                item.tileHeight,
                item.referenceCellWidth,
                item.referenceCellHeight,
                item.projection
        );
        SceneMetaRuntime.TiledProjection[] previousProjection = {item.projection};

        ChangeListener previewListener = new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (updatingControls[0]) return;

                Integer tileWidth = parseSpinnerValue(tileWidthSpinner, true);
                Integer tileHeight = parseSpinnerValue(tileHeightSpinner, true);
                SceneMetaRuntime.TiledProjection projection = projectionFromDisplayName(projectionBox.getSelected());

                if (tileWidth != null && tileHeight != null && projection != null) {
                    TilesetProfileReferenceDefaults.ReferenceSize referenceSize;
                    if (projection != previousProjection[0]) {
                        referenceSize = referenceDefaults.referenceSizeAfterProjectionChange(
                                projection,
                                tileWidth,
                                tileHeight,
                                referenceCellWidthModel.getValue(),
                                referenceCellHeightModel.getValue()
                        );
                        previousProjection[0] = projection;
                    } else {
                        referenceSize = referenceDefaults.referenceSizeAfterTileSizeChange(
                                tileWidth,
                                tileHeight,
                                referenceCellWidthModel.getValue(),
                                referenceCellHeightModel.getValue()
                        );
                    }
                    setSpinnerModelValueGuarded(referenceCellWidthModel, referenceSize.width(), updatingControls);
                    setSpinnerModelValueGuarded(referenceCellHeightModel, referenceSize.height(), updatingControls);
                }

                refreshTilesetProfilePreview(
                        dialog,
                        previewLabel,
                        errorLabel,
                        errorCell,
                        previewTileIndex,
                        previewTileLabel,
                        previousTileButton,
                        nextTileButton,
                        tilePreviewActor,
                        tilePreviewStatusLabel,
                        item,
                        tileWidthSpinner,
                        tileHeightSpinner,
                        marginSpinner,
                        spacingSpinner,
                        referenceCellWidthSpinner,
                        referenceCellHeightSpinner,
                        projectionBox,
                        anchorBox,
                        offsetXSpinner,
                        offsetYSpinner
                );
            }
        };

        tileWidthSpinner.addListener(previewListener);
        tileHeightSpinner.addListener(previewListener);
        marginSpinner.addListener(previewListener);
        spacingSpinner.addListener(previewListener);
        offsetXSpinner.addListener(previewListener);
        offsetYSpinner.addListener(previewListener);
        projectionBox.addListener(previewListener);
        anchorBox.addListener(previewListener);
        tileWidthSpinner.getTextField().addListener(previewListener);
        tileHeightSpinner.getTextField().addListener(previewListener);
        marginSpinner.getTextField().addListener(previewListener);
        spacingSpinner.getTextField().addListener(previewListener);
        offsetXSpinner.getTextField().addListener(previewListener);
        offsetYSpinner.getTextField().addListener(previewListener);

        ChangeListener referenceWidthListener = new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (!updatingControls[0]) {
                    referenceDefaults.markReferenceWidthEdited();
                    previewListener.changed(event, actor);
                }
            }
        };
        ChangeListener referenceHeightListener = new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (!updatingControls[0]) {
                    referenceDefaults.markReferenceHeightEdited();
                    previewListener.changed(event, actor);
                }
            }
        };
        referenceCellWidthSpinner.addListener(referenceWidthListener);
        referenceCellWidthSpinner.getTextField().addListener(referenceWidthListener);
        referenceCellHeightSpinner.addListener(referenceHeightListener);
        referenceCellHeightSpinner.getTextField().addListener(referenceHeightListener);

        previousTileButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (previewTileIndex[0] > 0) {
                    previewTileIndex[0]--;
                }
                refreshTilesetProfilePreview(
                        dialog, previewLabel, errorLabel, errorCell, previewTileIndex,
                        previewTileLabel, previousTileButton, nextTileButton,
                        tilePreviewActor, tilePreviewStatusLabel, item,
                        tileWidthSpinner, tileHeightSpinner, marginSpinner, spacingSpinner,
                        referenceCellWidthSpinner, referenceCellHeightSpinner, projectionBox,
                        anchorBox, offsetXSpinner, offsetYSpinner
                );
            }
        });

        nextTileButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                TilesetProfileDialogSettings settings = readTilesetProfileDialogSettings(
                        tileWidthSpinner,
                        tileHeightSpinner,
                        marginSpinner,
                        spacingSpinner,
                        referenceCellWidthSpinner,
                        referenceCellHeightSpinner,
                        projectionBox,
                        anchorBox,
                        offsetXSpinner,
                        offsetYSpinner
                );
                if (settings != null) {
                    ImportDialogValidation.TilesetSlicingPreview preview = ImportDialogValidation.calculateTilesetSlicing(
                            item.imageWidth,
                            item.imageHeight,
                            settings.tileWidth(),
                            settings.tileHeight(),
                            settings.spacing(),
                            settings.margin()
                    );
                    previewTileIndex[0] = Math.min(previewTileIndex[0] + 1, Math.max(0, preview.tileCount() - 1));
                }
                refreshTilesetProfilePreview(
                        dialog, previewLabel, errorLabel, errorCell, previewTileIndex,
                        previewTileLabel, previousTileButton, nextTileButton,
                        tilePreviewActor, tilePreviewStatusLabel, item,
                        tileWidthSpinner, tileHeightSpinner, marginSpinner, spacingSpinner,
                        referenceCellWidthSpinner, referenceCellHeightSpinner, projectionBox,
                        anchorBox, offsetXSpinner, offsetYSpinner
                );
            }
        });

        VisTextButton applyButton = new VisTextButton("Apply");
        VisTextButton cancelButton = new VisTextButton("Cancel");

        applyButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                TilesetProfileDialogSettings settings = readTilesetProfileDialogSettings(
                        tileWidthSpinner,
                        tileHeightSpinner,
                        marginSpinner,
                        spacingSpinner,
                        referenceCellWidthSpinner,
                        referenceCellHeightSpinner,
                        projectionBox,
                        anchorBox,
                        offsetXSpinner,
                        offsetYSpinner
                );
                if (settings == null) {
                    setSlicingDialogError(dialog, errorLabel, errorCell, "Use valid tileset profile values.");
                    return;
                }

                ImportDialogValidation.TilesetSlicingPreview preview = ImportDialogValidation.calculateTilesetSlicing(
                        item.imageWidth,
                        item.imageHeight,
                        settings.tileWidth(),
                        settings.tileHeight(),
                        settings.spacing(),
                        settings.margin()
                );
                if (!preview.hasTiles()) {
                    setSlicingDialogError(dialog, errorLabel, errorCell, "No tile fits.");
                    return;
                }

                item.applyTilesetProfileSettings(
                        settings.tileWidth(),
                        settings.tileHeight(),
                        settings.margin(),
                        settings.spacing(),
                        settings.referenceCellWidth(),
                        settings.referenceCellHeight(),
                        settings.projection(),
                        settings.anchor(),
                        settings.offsetX(),
                        settings.offsetY(),
                        settings.renderSize()
                );
                clearInlineError();
                dialog.fadeOut();
                refreshList();
            }
        });

        cancelButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                dialog.fadeOut();
            }
        });

        VisTable dialogBody = new VisTable(false);
        dialogBody.top().left();
        dialogBody.defaults().top().pad(12f);
        dialogBody.padLeft(12f);
        dialogBody.add(previewColumn).width(260f).padLeft(8f);
        dialogBody.add(form).width(320f);

        dialog.getContentTable().add(dialogBody).pad(8f);
        dialog.getButtonsTable().defaults().minWidth(96f).height(28f).padTop(4f).padBottom(8f).padRight(8f);
        dialog.getButtonsTable().add(applyButton);
        dialog.getButtonsTable().add(cancelButton).padRight(0f);
        dialog.pack();
        dialog.centerWindow();
        refreshTilesetProfilePreview(
                dialog,
                previewLabel,
                errorLabel,
                errorCell,
                previewTileIndex,
                previewTileLabel,
                previousTileButton,
                nextTileButton,
                tilePreviewActor,
                tilePreviewStatusLabel,
                item,
                tileWidthSpinner,
                tileHeightSpinner,
                marginSpinner,
                spacingSpinner,
                referenceCellWidthSpinner,
                referenceCellHeightSpinner,
                projectionBox,
                anchorBox,
                offsetXSpinner,
                offsetYSpinner
        );
        getStage().addActor(dialog.fadeIn());
    }

    private void addSlicingFieldRow(VisTable form, String labelText, Spinner spinner) {
        VisLabel label = new VisLabel(labelText);
        label.setColor(0.78f, 0.78f, 0.78f, 1f);
        form.add(label).left().width(92f).padRight(10f);
        form.add(spinner).left().width(96f).row();
    }

    private void addSelectRow(VisTable form, String labelText, VisSelectBox<String> selectBox) {
        VisLabel label = new VisLabel(labelText);
        label.setColor(0.78f, 0.78f, 0.78f, 1f);
        form.add(label).left().width(92f).padRight(10f);
        form.add(selectBox).left().width(160f).row();
    }

    private void addReadOnlyRow(VisTable form, String labelText, String valueText) {
        VisLabel label = new VisLabel(labelText);
        label.setColor(0.78f, 0.78f, 0.78f, 1f);
        VisLabel value = new VisLabel(valueText);
        value.setColor(0.78f, 0.78f, 0.78f, 1f);
        form.add(label).left().width(92f).padRight(10f);
        form.add(value).left().width(160f).row();
    }

    private void refreshTilesetProfilePreview(VisDialog dialog,
                                              VisLabel previewLabel,
                                              VisLabel errorLabel,
                                              Cell<VisLabel> errorCell,
                                              int[] previewTileIndex,
                                              VisLabel previewTileLabel,
                                              VisTextButton previousTileButton,
                                              VisTextButton nextTileButton,
                                              TilesetTilePreviewActor tilePreviewActor,
                                              VisLabel tilePreviewStatusLabel,
                                              ImportItem item,
                                              Spinner tileWidthSpinner,
                                              Spinner tileHeightSpinner,
                                              Spinner marginSpinner,
                                              Spinner spacingSpinner,
                                              Spinner referenceCellWidthSpinner,
                                              Spinner referenceCellHeightSpinner,
                                              VisSelectBox<String> projectionBox,
                                              VisSelectBox<String> anchorBox,
                                              Spinner offsetXSpinner,
                                              Spinner offsetYSpinner) {
        if (previewLabel == null || item == null) return;

        TilesetProfileDialogSettings settings = readTilesetProfileDialogSettings(
                tileWidthSpinner,
                tileHeightSpinner,
                marginSpinner,
                spacingSpinner,
                referenceCellWidthSpinner,
                referenceCellHeightSpinner,
                projectionBox,
                anchorBox,
                offsetXSpinner,
                offsetYSpinner
        );
        if (settings == null) {
            previewLabel.setText("Tiles: -");
            updatePreviewTileNavigation(previewTileIndex, previewTileLabel, previousTileButton, nextTileButton, 0);
            clearTilePreview(tilePreviewActor, tilePreviewStatusLabel, "Preview unavailable: invalid slice");
            setSlicingDialogError(dialog, errorLabel, errorCell, "Use valid tileset profile values.");
            return;
        }

        ImportDialogValidation.TilesetSlicingPreview preview = ImportDialogValidation.calculateTilesetSlicing(
                item.imageWidth,
                item.imageHeight,
                settings.tileWidth(),
                settings.tileHeight(),
                settings.spacing(),
                settings.margin()
        );

        if (!preview.validSettings()) {
            previewLabel.setText("Tiles: -");
            updatePreviewTileNavigation(previewTileIndex, previewTileLabel, previousTileButton, nextTileButton, 0);
            clearTilePreview(tilePreviewActor, tilePreviewStatusLabel, "Preview unavailable: invalid image size");
            setSlicingDialogError(dialog, errorLabel, errorCell, "Image size is unavailable.");
            return;
        }

        updatePreviewTileNavigation(
                previewTileIndex,
                previewTileLabel,
                previousTileButton,
                nextTileButton,
                preview.tileCount()
        );
        updateTilePreview(
                tilePreviewActor,
                tilePreviewStatusLabel,
                settings.tileWidth(),
                settings.tileHeight(),
                settings.spacing(),
                settings.margin(),
                previewTileIndex != null ? previewTileIndex[0] : 0,
                settings.referenceCellWidth(),
                settings.referenceCellHeight(),
                settings.projection(),
                settings.anchor(),
                settings.offsetX(),
                settings.offsetY()
        );

        previewLabel.setText(
                "Tileset size: " + preview.imageWidth() + "x" + preview.imageHeight()
                        + "\nTiles: " + preview.tileCount()
                        + " (" + preview.columns() + "x" + preview.rows() + ")"
                        + "\nUnused: right " + preview.unusedRightPixels()
                        + ", bottom " + preview.unusedBottomPixels()
        );

        if (!preview.hasTiles()) {
            setSlicingDialogError(dialog, errorLabel, errorCell, "No tile fits.");
        } else {
            setSlicingDialogError(dialog, errorLabel, errorCell, null);
        }
    }

    private void refreshTilesetDirectoryProfilePreview(VisDialog dialog,
                                                       VisLabel previewLabel,
                                                       VisLabel errorLabel,
                                                       Cell<VisLabel> errorCell,
                                                       int[] previewImageIndex,
                                                       VisLabel previewImageLabel,
                                                       VisTextButton previousImageButton,
                                                       VisTextButton nextImageButton,
                                                       TilesetTilePreviewActor tilePreviewActor,
                                                       VisLabel tilePreviewStatusLabel,
                                                       FileHandle[] sourceTiles,
                                                       Spinner referenceCellWidthSpinner,
                                                       Spinner referenceCellHeightSpinner,
                                                       VisSelectBox<String> projectionBox,
                                                       VisSelectBox<String> anchorBox,
                                                       Spinner offsetXSpinner,
                                                       Spinner offsetYSpinner) {
        if (previewLabel == null || sourceTiles == null || sourceTiles.length == 0) return;

        TilesetProfileImportSettings settings = readDirectoryProfileDialogSettings(
                referenceCellWidthSpinner,
                referenceCellHeightSpinner,
                projectionBox,
                anchorBox,
                offsetXSpinner,
                offsetYSpinner
        );
        if (settings == null) {
            previewLabel.setText("Images: " + sourceTiles.length);
            updateDirectoryPreviewNavigation(previewImageIndex, previewImageLabel, previousImageButton, nextImageButton, sourceTiles.length);
            clearTilePreview(tilePreviewActor, tilePreviewStatusLabel, "Preview unavailable: invalid profile");
            setSlicingDialogError(dialog, errorLabel, errorCell, "Use valid tileset profile values.");
            return;
        }

        updateDirectoryPreviewNavigation(
                previewImageIndex,
                previewImageLabel,
                previousImageButton,
                nextImageButton,
                sourceTiles.length
        );

        int index = previewImageIndex != null ? previewImageIndex[0] : 0;
        FileHandle image = sourceTiles[Math.max(0, Math.min(index, sourceTiles.length - 1))];
        int[] size = ImportDialogValidation.readImageDimensions(image);
        if (size == null || size.length < 2 || size[0] <= 0 || size[1] <= 0) {
            clearTilePreview(tilePreviewActor, tilePreviewStatusLabel, "Preview unavailable: image size");
            previewLabel.setText("Images: " + sourceTiles.length + "\nImage size: -");
            setSlicingDialogError(dialog, errorLabel, errorCell, "Image size is unavailable.");
            return;
        }

        tilePreviewActor.setSourceFile(image);
        tilePreviewActor.updatePreview(
                size[0],
                size[1],
                0,
                0,
                0,
                settings.referenceCellWidth(),
                settings.referenceCellHeight(),
                settings.projection(),
                settings.anchor(),
                settings.offsetX(),
                settings.offsetY()
        );
        syncTilePreviewStatus(tilePreviewActor, tilePreviewStatusLabel);

        previewLabel.setText(
                "Images: " + sourceTiles.length
                        + "\nImage size: " + size[0] + "x" + size[1]
                        + "\nCell: " + settings.referenceCellWidth() + "x" + settings.referenceCellHeight()
        );
        setSlicingDialogError(dialog, errorLabel, errorCell, null);
    }

    private void updateDirectoryPreviewNavigation(int[] previewImageIndex,
                                                  VisLabel previewImageLabel,
                                                  VisTextButton previousImageButton,
                                                  VisTextButton nextImageButton,
                                                  int imageCount) {
        int count = Math.max(0, imageCount);
        if (previewImageIndex != null) {
            previewImageIndex[0] = count > 0 ? Math.min(Math.max(0, previewImageIndex[0]), count - 1) : 0;
        }
        int index = previewImageIndex != null ? previewImageIndex[0] : 0;

        if (previewImageLabel != null) {
            previewImageLabel.setText(count > 0 ? "Image " + (index + 1) + " / " + count : "Image 0 / 0");
        }
        if (previousImageButton != null) {
            previousImageButton.setDisabled(index <= 0);
        }
        if (nextImageButton != null) {
            nextImageButton.setDisabled(count <= 0 || index >= count - 1);
        }
    }

    private void updatePreviewTileNavigation(int[] previewTileIndex,
                                             VisLabel previewTileLabel,
                                             VisTextButton previousTileButton,
                                             VisTextButton nextTileButton,
                                             int tileCount) {
        int count = Math.max(0, tileCount);
        if (previewTileIndex != null) {
            previewTileIndex[0] = count > 0 ? Math.min(Math.max(0, previewTileIndex[0]), count - 1) : 0;
        }
        int index = previewTileIndex != null ? previewTileIndex[0] : 0;

        if (previewTileLabel != null) {
            previewTileLabel.setText(count > 0 ? "Tile " + (index + 1) + " / " + count : "Tile 0 / 0");
        }
        if (previousTileButton != null) {
            previousTileButton.setDisabled(index <= 0);
        }
        if (nextTileButton != null) {
            nextTileButton.setDisabled(count <= 0 || index >= count - 1);
        }
    }

    private void updateTilePreview(TilesetTilePreviewActor tilePreviewActor,
                                   VisLabel tilePreviewStatusLabel,
                                   int tileWidth,
                                   int tileHeight,
                                   int spacing,
                                   int margin,
                                   int tileIndex,
                                   int referenceCellWidth,
                                   int referenceCellHeight,
                                   SceneMetaRuntime.TiledProjection projection,
                                   TilesetAnchor anchor,
                                   int offsetX,
                                   int offsetY) {
        if (tilePreviewActor == null) return;
        tilePreviewActor.updatePreview(
                tileWidth,
                tileHeight,
                spacing,
                margin,
                tileIndex,
                referenceCellWidth,
                referenceCellHeight,
                projection,
                anchor,
                offsetX,
                offsetY
        );
        syncTilePreviewStatus(tilePreviewActor, tilePreviewStatusLabel);
    }

    private void clearTilePreview(TilesetTilePreviewActor tilePreviewActor,
                                  VisLabel tilePreviewStatusLabel,
                                  String statusText) {
        if (tilePreviewActor == null) return;
        tilePreviewActor.clearPreview(statusText);
        syncTilePreviewStatus(tilePreviewActor, tilePreviewStatusLabel);
    }

    private void syncTilePreviewStatus(TilesetTilePreviewActor tilePreviewActor,
                                       VisLabel tilePreviewStatusLabel) {
        if (tilePreviewActor == null || tilePreviewStatusLabel == null) return;
        String status = tilePreviewActor.statusText();
        tilePreviewStatusLabel.setText(status != null ? status : "");
        tilePreviewStatusLabel.setVisible(status != null && !status.isBlank());
    }

    private void setSpinnerModelValueGuarded(IntSpinnerModel model,
                                             int value,
                                             boolean[] updatingControls) {
        if (model == null || updatingControls == null || updatingControls.length == 0) return;
        if (model.getValue() == value) return;

        updatingControls[0] = true;
        try {
            model.setValue(value);
        } finally {
            updatingControls[0] = false;
        }
    }

    private void refreshSlicingPreview(VisDialog dialog,
                                       VisLabel previewLabel,
                                       VisLabel errorLabel,
                                       Cell<VisLabel> errorCell,
                                       ImportItem item,
                                       Spinner tileWidthSpinner,
                                       Spinner tileHeightSpinner,
                                       Spinner marginSpinner,
                                       Spinner spacingSpinner) {
        if (previewLabel == null || item == null) return;

        SlicingSettings settings = readSlicingSettings(
                tileWidthSpinner,
                tileHeightSpinner,
                marginSpinner,
                spacingSpinner
        );
        if (settings == null) {
            previewLabel.setText("Detected tiles: -");
            setSlicingDialogError(dialog, errorLabel, errorCell, "Use valid slicing values.");
            return;
        }

        ImportDialogValidation.TilesetSlicingPreview preview = ImportDialogValidation.calculateTilesetSlicing(
                item.imageWidth,
                item.imageHeight,
                settings.tileWidth(),
                settings.tileHeight(),
                settings.spacing(),
                settings.margin()
        );

        if (!preview.validSettings()) {
            previewLabel.setText("Detected tiles: -");
            setSlicingDialogError(dialog, errorLabel, errorCell, "Image size is unavailable.");
            return;
        }

        previewLabel.setText(
                "Image size: " + preview.imageWidth() + "x" + preview.imageHeight()
                        + "\nDetected tiles: " + preview.tileCount()
                        + " (" + preview.columns() + "x" + preview.rows() + ")"
                        + "\nUnused: right " + preview.unusedRightPixels()
                        + ", bottom " + preview.unusedBottomPixels()
        );

        if (!preview.hasTiles()) {
            setSlicingDialogError(dialog, errorLabel, errorCell, "No tile fits.");
        } else {
            setSlicingDialogError(dialog, errorLabel, errorCell, null);
        }
    }

    private SlicingSettings readSlicingSettings(Spinner tileWidthSpinner,
                                                Spinner tileHeightSpinner,
                                                Spinner marginSpinner,
                                                Spinner spacingSpinner) {
        Integer tileWidth = parseSpinnerValue(tileWidthSpinner, true);
        Integer tileHeight = parseSpinnerValue(tileHeightSpinner, true);
        Integer margin = parseSpinnerValue(marginSpinner, false);
        Integer spacing = parseSpinnerValue(spacingSpinner, false);
        if (tileWidth == null || tileHeight == null || margin == null || spacing == null) {
            return null;
        }
        return new SlicingSettings(tileWidth, tileHeight, margin, spacing);
    }

    private TilesetProfileDialogSettings readTilesetProfileDialogSettings(Spinner tileWidthSpinner,
                                                                          Spinner tileHeightSpinner,
                                                                          Spinner marginSpinner,
                                                                          Spinner spacingSpinner,
                                                                          Spinner referenceCellWidthSpinner,
                                                                          Spinner referenceCellHeightSpinner,
                                                                          VisSelectBox<String> projectionBox,
                                                                          VisSelectBox<String> anchorBox,
                                                                          Spinner offsetXSpinner,
                                                                          Spinner offsetYSpinner) {
        SlicingSettings slicing = readSlicingSettings(
                tileWidthSpinner,
                tileHeightSpinner,
                marginSpinner,
                spacingSpinner
        );
        Integer referenceCellWidth = parsePositiveSpinnerValue(referenceCellWidthSpinner);
        Integer referenceCellHeight = parsePositiveSpinnerValue(referenceCellHeightSpinner);
        Integer offsetX = parseOffsetSpinnerValue(offsetXSpinner);
        Integer offsetY = parseOffsetSpinnerValue(offsetYSpinner);
        SceneMetaRuntime.TiledProjection projection = projectionFromDisplayName(projectionBox != null ? projectionBox.getSelected() : null);
        TilesetAnchor anchor = anchorFromDisplayName(anchorBox != null ? anchorBox.getSelected() : null);

        if (slicing == null
                || referenceCellWidth == null
                || referenceCellHeight == null
                || offsetX == null
                || offsetY == null
                || projection == null
                || anchor == null) {
            return null;
        }

        return new TilesetProfileDialogSettings(
                slicing.tileWidth(),
                slicing.tileHeight(),
                slicing.margin(),
                slicing.spacing(),
                referenceCellWidth,
                referenceCellHeight,
                projection,
                anchor,
                offsetX,
                offsetY,
                TilesetRenderSize.NATIVE
        );
    }

    private TilesetProfileImportSettings readDirectoryProfileDialogSettings(Spinner referenceCellWidthSpinner,
                                                                            Spinner referenceCellHeightSpinner,
                                                                            VisSelectBox<String> projectionBox,
                                                                            VisSelectBox<String> anchorBox,
                                                                            Spinner offsetXSpinner,
                                                                            Spinner offsetYSpinner) {
        Integer referenceCellWidth = parsePositiveSpinnerValue(referenceCellWidthSpinner);
        Integer referenceCellHeight = parsePositiveSpinnerValue(referenceCellHeightSpinner);
        Integer offsetX = parseOffsetSpinnerValue(offsetXSpinner);
        Integer offsetY = parseOffsetSpinnerValue(offsetYSpinner);
        SceneMetaRuntime.TiledProjection projection = projectionFromDisplayName(projectionBox != null ? projectionBox.getSelected() : null);
        TilesetAnchor anchor = anchorFromDisplayName(anchorBox != null ? anchorBox.getSelected() : null);

        if (referenceCellWidth == null
                || referenceCellHeight == null
                || offsetX == null
                || offsetY == null
                || projection == null
                || anchor == null) {
            return null;
        }

        return new TilesetProfileImportSettings(
                referenceCellWidth,
                referenceCellHeight,
                projection,
                anchor,
                offsetX,
                offsetY,
                TilesetRenderSize.NATIVE
        );
    }

    private Integer parseSpinnerValue(Spinner spinner, boolean positive) {
        if (spinner == null || spinner.getTextField() == null) return null;
        String text = spinner.getTextField().getText();
        boolean valid = positive
                ? ImportDialogValidation.isPositiveIntegerWithinMaxSize(text)
                : ImportDialogValidation.isNonNegativeIntegerWithinMaxSize(text);
        if (!valid) return null;
        return Integer.parseInt(text.trim());
    }

    private Integer parsePositiveSpinnerValue(Spinner spinner) {
        if (spinner == null || spinner.getTextField() == null) return null;
        String text = spinner.getTextField().getText();
        if (!ImportDialogValidation.isPositiveInteger(text)) return null;
        return Integer.parseInt(text.trim());
    }

    private Integer parseOffsetSpinnerValue(Spinner spinner) {
        if (spinner == null || spinner.getTextField() == null) return null;
        String text = spinner.getTextField().getText();
        if (!ImportDialogValidation.isIntegerWithinTilesetOffsetRange(text)) return null;
        return Integer.parseInt(text.trim());
    }

    private static String displayName(SceneMetaRuntime.TiledProjection projection) {
        if (projection == SceneMetaRuntime.TiledProjection.ISO) return "Isometric";
        return "Orthogonal";
    }

    private static String displayName(TilesetAnchor anchor) {
        if (anchor == TilesetAnchor.TOP_CENTER) return "Top center";
        if (anchor == TilesetAnchor.BOTTOM_LEFT) return "Bottom left";
        if (anchor == TilesetAnchor.CENTER) return "Center";
        if (anchor == TilesetAnchor.TOP_LEFT) return "Top left";
        if (anchor == TilesetAnchor.BOTTOM_CENTER) return "Bottom center";
        return "Top center";
    }

    private static SceneMetaRuntime.TiledProjection projectionFromDisplayName(String raw) {
        if ("Isometric".equals(raw)) return SceneMetaRuntime.TiledProjection.ISO;
        if ("Orthogonal".equals(raw)) return SceneMetaRuntime.TiledProjection.ORTHO;
        return null;
    }

    private static TilesetAnchor anchorFromDisplayName(String raw) {
        if ("Top center".equals(raw)) return TilesetAnchor.TOP_CENTER;
        if ("Bottom center".equals(raw)) return TilesetAnchor.BOTTOM_CENTER;
        if ("Bottom left".equals(raw)) return TilesetAnchor.BOTTOM_LEFT;
        if ("Center".equals(raw)) return TilesetAnchor.CENTER;
        if ("Top left".equals(raw)) return TilesetAnchor.TOP_LEFT;
        return null;
    }

    private void setSlicingDialogError(VisDialog dialog,
                                       VisLabel errorLabel,
                                       Cell<VisLabel> errorCell,
                                       String message) {
        if (dialog == null || errorLabel == null || errorCell == null) return;

        boolean visible = message != null && !message.isBlank();
        errorLabel.setText(visible ? message : "");
        errorLabel.setVisible(visible);

        errorCell.height(visible ? errorLabel.getPrefHeight() : 0f);
        errorCell.padTop(visible ? 4f : 0f);

        dialog.invalidateHierarchy();
        dialog.pack();
        dialog.centerWindow();
    }

    private String getRowErrorMessage(ImportItem item) {
        if (ImportDialogValidation.hasInvalidImageDimensions(item)) {
            return "Unreadable PNG file.";
        }
        if (ImportDialogValidation.exceedsMaxImageSize(item)) {
            if (ImportDialogValidation.isSheetType(item.type)) return null;

            String size = formatImageSize(item);
            if (!size.isBlank()) {
                return size + " exceeds maximum allowed size ("
                        + ImportDialogValidation.MAX_IMAGE_SIZE + "x"
                        + ImportDialogValidation.MAX_IMAGE_SIZE + ")";
            }
            return "Image exceeds maximum allowed size ("
                    + ImportDialogValidation.MAX_IMAGE_SIZE + "x"
                    + ImportDialogValidation.MAX_IMAGE_SIZE + ")";
        }
        return null;
    }

    private String formatImageSize(ImportItem item) {
        if (item == null) return "";
        if (item.imageWidth <= 0 || item.imageHeight <= 0) return "";
        return item.imageWidth + "x" + item.imageHeight;
    }

    static String formatTilesetCompactProfileSummary(ImportItem item) {
        if (ImportDialogValidation.hasInvalidTilesetProfileSettings(item)) {
            return "Invalid profile";
        }
        if (item == null) return "";
        return "Tile " + Math.max(1, item.tileWidth) + "×" + Math.max(1, item.tileHeight)
                + " · Ref " + Math.max(1, item.referenceCellWidth) + "×" + Math.max(1, item.referenceCellHeight);
    }

    private static String formatTilesetFullProfileSummary(ImportItem item) {
        if (item == null) return "";
        return "Tile: " + Math.max(1, item.tileWidth) + "x" + Math.max(1, item.tileHeight)
                + ", margin " + Math.max(0, item.tileMargin)
                + ", spacing " + Math.max(0, item.tileSpacing)
                + ", ref " + Math.max(1, item.referenceCellWidth) + "x" + Math.max(1, item.referenceCellHeight)
                + ", " + displayName(item.projection)
                + ", " + displayName(item.anchor)
                + ", offset " + item.offsetX + "," + item.offsetY
                + ", native";
    }

    private String formatTsxTilesetSummary(ImportItem item) {
        if (item == null || item.file == null) return "TSX tile set";
        try {
            TsxTilesetDescriptor descriptor = new TsxTilesetImportParser().parse(item.file);
            String imageName = descriptor.imageFile() != null ? descriptor.imageFile().name() : descriptor.imageSource();
            return "TSX tile set: "
                    + descriptor.tileWidth() + "x" + descriptor.tileHeight()
                    + ", margin " + descriptor.margin()
                    + ", spacing " + descriptor.spacing()
                    + ", image: " + imageName;
        } catch (IllegalArgumentException ex) {
            String message = ex.getMessage();
            return message == null || message.isBlank() ? "TSX file is invalid." : message;
        }
    }

    void applyImportAndCloseIfSuccessful() {
        clearInlineError();

        if (items.size == 0) {
            setInlineError("No compatible files to import.");
            return;
        }

        boolean hasInvalidField = false;
        boolean hasDivisibilityIssue = false;
        boolean hasInvalidTilesetSlicing = false;
        boolean hasInvalidTilesetProfile = false;
        boolean hasTilesetSlicingIssue = false;
        boolean hasUnreadableImage = false;
        boolean hasOversizedImage = false;

        for (int i = 0; i < items.size; i++) {
            ImportItem item = items.get(i);

            hasUnreadableImage |= ImportDialogValidation.hasInvalidImageDimensions(item);
            hasOversizedImage |= ImportDialogValidation.exceedsMaxImageSize(item);

            if (item.type == ImportType.TILESET) {
                hasInvalidTilesetSlicing |= ImportDialogValidation.hasInvalidTilesetSlicingSettings(item);
                hasInvalidTilesetProfile |= ImportDialogValidation.hasInvalidTilesetProfileSettings(item);
                hasTilesetSlicingIssue |= ImportDialogValidation.hasTilesetSlicingIssue(item);
            } else if (item.type == ImportType.SPRITESHEET) {
                if (item.tileWidthField != null) {
                    item.tileWidthField.validateInput();
                    hasInvalidField |= !item.tileWidthField.isInputValid();
                }
                if (item.tileHeightField != null) {
                    item.tileHeightField.validateInput();
                    hasInvalidField |= !item.tileHeightField.isInputValid();
                }

                hasDivisibilityIssue |= ImportDialogValidation.hasDivisibilityIssue(item);
            }
        }

        if (hasUnreadableImage) {
            setInlineError("Some PNG images could not be read.");
            return;
        }

        if (hasOversizedImage) {
            setInlineError("Images, tiles, and frames cannot exceed 2048x2048.");
            return;
        }

        if (hasInvalidField) {
            setInlineError("Tile width and tile height must be positive integers.");
            return;
        }

        if (hasInvalidTilesetSlicing) {
            setInlineError("Tile width, tile height, margin, and spacing must be valid.");
            return;
        }

        if (hasInvalidTilesetProfile) {
            setInlineError("Tileset reference cell, projection, anchor, offset, and render size must be valid.");
            return;
        }

        if (hasDivisibilityIssue) {
            setInlineError("Sheet size must be divisible by tile width and tile height.");
            return;
        }

        if (hasTilesetSlicingIssue) {
            setInlineError("Tile set slicing must detect at least one tile.");
            return;
        }

        for (int i = 0; i < items.size; i++) {
            ImportItem item = items.get(i);

            if (ImportDialogValidation.isSheetType(item.type)) {
                if (item.tileWidthField != null) {
                    item.tileWidth = Integer.parseInt(item.tileWidthField.getText().trim());
                }
                if (item.tileHeightField != null) {
                    item.tileHeight = Integer.parseInt(item.tileHeightField.getText().trim());
                }
            }

            item.tileWidth = Math.max(1, item.tileWidth);
            item.tileHeight = Math.max(1, item.tileHeight);
            item.tileMargin = Math.max(0, item.tileMargin);
            item.tileSpacing = Math.max(0, item.tileSpacing);
            if (item.type == ImportType.TILESET) {
                item.referenceCellWidth = Math.max(1, item.referenceCellWidth);
                item.referenceCellHeight = Math.max(1, item.referenceCellHeight);
                if (item.projection == null) {
                    item.projection = SceneMetaRuntime.TiledProjection.ORTHO;
                }
                if (item.anchor == null) {
                    item.anchor = TilesetAnchor.TOP_CENTER;
                }
                item.offsetX = Math.max(
                        -ImportDialogValidation.MAX_TILESET_OFFSET,
                        Math.min(ImportDialogValidation.MAX_TILESET_OFFSET, item.offsetX)
                );
                item.offsetY = Math.max(
                        -ImportDialogValidation.MAX_TILESET_OFFSET,
                        Math.min(ImportDialogValidation.MAX_TILESET_OFFSET, item.offsetY)
                );
                item.renderSize = TilesetRenderSize.NATIVE;
            }
        }

        ImportDialogApplySupport.applyAndCloseOnSuccess(
                items,
                onApply,
                this::setInlineError,
                this::fadeOut
        );
    }

    private void setInlineError(String message) {
        inlineErrorLabel.setText(message == null ? "Import failed." : message);
        inlineErrorLabel.setVisible(true);
    }

    private void clearInlineError() {
        inlineErrorLabel.setText("");
        inlineErrorLabel.setVisible(false);
    }

    @Override
    public VisDialog show(Stage stage) {
        VisDialog dialog = super.show(stage);
        if (app != null) app.pushOsDropTarget(this);
        return dialog;
    }

    @Override
    public void hide() {
        if (app != null) app.popOsDropTarget(this);
        super.hide();
    }

    @Override
    public boolean onOsFilesDropped(String[] files) {
        if (files == null || files.length == 0) return true;

        for (String path : files) {
            if (path == null || path.isBlank()) continue;
            FileHandle file = Gdx.files.absolute(path);
            if (!file.exists()) continue;
            addDroppedEntry(file);
        }

        clearInlineError();
        refreshList();
        return true;
    }

    private record SlicingSettings(int tileWidth, int tileHeight, int margin, int spacing) {
    }

    private record TilesetProfileDialogSettings(int tileWidth,
                                                int tileHeight,
                                                int margin,
                                                int spacing,
                                                int referenceCellWidth,
                                                int referenceCellHeight,
                                                SceneMetaRuntime.TiledProjection projection,
                                                TilesetAnchor anchor,
                                                int offsetX,
                                                int offsetY,
                                                TilesetRenderSize renderSize) {
    }
}
