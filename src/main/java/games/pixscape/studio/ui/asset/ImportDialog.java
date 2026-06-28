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
import games.pixscape.studio.OsFilesDropTarget;
import games.pixscape.studio.service.asset.TsxTilesetDescriptor;
import games.pixscape.studio.service.asset.TsxTilesetImportParser;
import games.pixscape.studio.ui.config.CommonLayout;
import games.pixscape.studio.ui.main.StudioApplicationAdapter;

import java.util.Objects;
import java.util.function.Consumer;

public final class ImportDialog extends VisDialog implements OsFilesDropTarget {

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
    private final Consumer<FileHandle> onImportTilesetDirectory;

    private final Array<ImportItem> items = new Array<>();
    private final VisTable listTable = new VisTable(false);
    private final VisScrollPane listScroll = new VisScrollPane(listTable);
    private final VisLabel inlineErrorLabel = new VisLabel("");

    /**
     * Result ready to import (not applied to the project yet).
     */
    public static final class ImportItem {
        public FileHandle file;        // absolu (OS)
        public ImportType type;        // choisi par user
        public int tileWidth = 32;
        public int tileHeight = 32;
        public int tileSpacing = 0;
        public int tileMargin = 0;
        public int imageWidth = -1;
        public int imageHeight = -1;

        private boolean dimensionsResolved;
        transient VisValidatableTextField tileWidthField;
        transient VisValidatableTextField tileHeightField;

        public ImportItem(FileHandle file) {
            this.file = file;
        }

        void applySlicingSettings(int tileWidth, int tileHeight, int tileMargin, int tileSpacing) {
            this.tileWidth = tileWidth;
            this.tileHeight = tileHeight;
            this.tileMargin = tileMargin;
            this.tileSpacing = tileSpacing;
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

    public ImportDialog(StudioApplicationAdapter app,
                        Consumer<Array<ImportItem>> onApply,
                        Consumer<FileHandle> onImportTilesetDirectory) {
        super("Import assets");
        super.getTitleLabel().setAlignment(Align.center);
        this.onApply = onApply;
        this.onImportTilesetDirectory = onImportTilesetDirectory;
        this.app = app;

        setModal(true);
        setResizable(true);

        TableUtils.setSpacingDefaults(this);

        buildUi();

        addCloseButton();
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
        FileChooser chooser = new FileChooser(FileChooser.Mode.OPEN);
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
        FileChooser chooser = new FileChooser(FileChooser.Mode.OPEN);
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
                    onImportTilesetDirectory.accept(directory);
                    fadeOut();
                } catch (IllegalArgumentException ex) {
                    setInlineError(ImportDialogApplySupport.userMessageFor(ex));
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

        VisSelectBox<ImportType> typeBox = new VisSelectBox<>();
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
        VisLabel summary = new VisLabel(formatTilesetSlicingSummary(item));
        summary.setColor(0.78f, 0.78f, 0.78f, 1f);

        VisTextButton settingsButton = new VisTextButton("Slicing settings...");
        settingsButton.setColor(CommonLayout.BUTTON_COLOR);
        settingsButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                openSlicingSettingsDialog(item);
            }
        });

        VisTable details = new VisTable(false);
        details.left();
        details.defaults().left();
        details.add(summary).left().growX();
        details.add(settingsButton).right();

        return details;
    }

    private void openSlicingSettingsDialog(ImportItem item) {
        if (item == null) return;

        VisDialog dialog = new VisDialog("Slicing settings");
        dialog.setModal(true);
        dialog.getTitleLabel().setAlignment(Align.center);
        TableUtils.setSpacingDefaults(dialog);

        int maxTileWidth = item.imageWidth > 0 ? Math.min(item.imageWidth, ImportDialogValidation.MAX_IMAGE_SIZE) : ImportDialogValidation.MAX_IMAGE_SIZE;
        int maxTileHeight = item.imageHeight > 0 ? Math.min(item.imageHeight, ImportDialogValidation.MAX_IMAGE_SIZE) : ImportDialogValidation.MAX_IMAGE_SIZE;

        IntSpinnerModel tileWidthModel = new IntSpinnerModel(Math.max(1, item.tileWidth), 1, maxTileWidth, 1);
        Spinner tileWidthSpinner = new Spinner("", tileWidthModel);

        IntSpinnerModel tileHeightModel = new IntSpinnerModel(Math.max(1, item.tileHeight), 1, maxTileHeight, 1);
        Spinner tileHeightSpinner = new Spinner("", tileHeightModel);

        IntSpinnerModel marginModel = new IntSpinnerModel(Math.max(0, item.tileMargin), 0, ImportDialogValidation.MAX_IMAGE_SIZE, 1);
        Spinner marginSpinner = new Spinner("", marginModel);

        IntSpinnerModel spacingModel = new IntSpinnerModel(Math.max(0, item.tileSpacing), 0, ImportDialogValidation.MAX_IMAGE_SIZE, 1);
        Spinner spacingSpinner = new Spinner("", spacingModel);

        tileWidthSpinner.getTextField().setAlignment(Align.center);
        tileHeightSpinner.getTextField().setAlignment(Align.center);
        marginSpinner.getTextField().setAlignment(Align.center);
        spacingSpinner.getTextField().setAlignment(Align.center);

        tileWidthSpinner.getTextField().addValidator(ImportDialogValidation::isPositiveIntegerWithinMaxSize);
        tileHeightSpinner.getTextField().addValidator(ImportDialogValidation::isPositiveIntegerWithinMaxSize);
        marginSpinner.getTextField().addValidator(ImportDialogValidation::isNonNegativeIntegerWithinMaxSize);
        spacingSpinner.getTextField().addValidator(ImportDialogValidation::isNonNegativeIntegerWithinMaxSize);

        VisLabel previewLabel = new VisLabel("");
        previewLabel.setColor(0.78f, 0.78f, 0.78f, 1f);

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
        form.add(previewLabel).left().colspan(2).padTop(8f).row();
        Cell<VisLabel> errorCell = form.add(errorLabel).left().width(300f).colspan(2);
        errorCell.height(0f).padTop(0f);
        form.row();

        ChangeListener previewListener = new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                refreshSlicingPreview(dialog, previewLabel, errorLabel, errorCell, item, tileWidthSpinner, tileHeightSpinner, marginSpinner, spacingSpinner);
            }
        };

        tileWidthSpinner.addListener(previewListener);
        tileHeightSpinner.addListener(previewListener);
        marginSpinner.addListener(previewListener);
        spacingSpinner.addListener(previewListener);
        tileWidthSpinner.getTextField().addListener(previewListener);
        tileHeightSpinner.getTextField().addListener(previewListener);
        marginSpinner.getTextField().addListener(previewListener);
        spacingSpinner.getTextField().addListener(previewListener);

        VisTextButton applyButton = new VisTextButton("Apply");
        VisTextButton cancelButton = new VisTextButton("Cancel");

        applyButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                SlicingSettings settings = readSlicingSettings(
                        tileWidthSpinner,
                        tileHeightSpinner,
                        marginSpinner,
                        spacingSpinner
                );
                if (settings == null) {
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
                if (!preview.hasTiles()) {
                    setSlicingDialogError(dialog, errorLabel, errorCell, "No tile fits.");
                    return;
                }

                item.applySlicingSettings(
                        settings.tileWidth(),
                        settings.tileHeight(),
                        settings.margin(),
                        settings.spacing()
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

        dialog.getContentTable().add(form).pad(12f);
        dialog.getButtonsTable().defaults().minWidth(96f).height(28f).padTop(4f).padBottom(8f).padRight(8f);
        dialog.getButtonsTable().add(applyButton);
        dialog.getButtonsTable().add(cancelButton).padRight(0f);
        dialog.pack();
        dialog.centerWindow();
        refreshSlicingPreview(dialog, previewLabel, errorLabel, errorCell, item, tileWidthSpinner, tileHeightSpinner, marginSpinner, spacingSpinner);
        getStage().addActor(dialog.fadeIn());
    }

    private void addSlicingFieldRow(VisTable form, String labelText, Spinner spinner) {
        VisLabel label = new VisLabel(labelText);
        label.setColor(0.78f, 0.78f, 0.78f, 1f);
        form.add(label).left().width(92f).padRight(10f);
        form.add(spinner).left().width(96f).row();
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

    private Integer parseSpinnerValue(Spinner spinner, boolean positive) {
        if (spinner == null || spinner.getTextField() == null) return null;
        String text = spinner.getTextField().getText();
        boolean valid = positive
                ? ImportDialogValidation.isPositiveIntegerWithinMaxSize(text)
                : ImportDialogValidation.isNonNegativeIntegerWithinMaxSize(text);
        if (!valid) return null;
        return Integer.parseInt(text.trim());
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

    private String formatTilesetSlicingSummary(ImportItem item) {
        if (item == null) return "";
        return "Tile: " + Math.max(1, item.tileWidth) + "x" + Math.max(1, item.tileHeight)
                + ", margin " + Math.max(0, item.tileMargin)
                + ", spacing " + Math.max(0, item.tileSpacing);
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
        boolean hasTilesetSlicingIssue = false;
        boolean hasUnreadableImage = false;
        boolean hasOversizedImage = false;

        for (int i = 0; i < items.size; i++) {
            ImportItem item = items.get(i);

            hasUnreadableImage |= ImportDialogValidation.hasInvalidImageDimensions(item);
            hasOversizedImage |= ImportDialogValidation.exceedsMaxImageSize(item);

            if (item.type == ImportType.TILESET) {
                hasInvalidTilesetSlicing |= ImportDialogValidation.hasInvalidTilesetSlicingSettings(item);
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
}
