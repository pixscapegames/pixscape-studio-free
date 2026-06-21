package games.pixscape.studio.ui.asset;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
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
import games.pixscape.studio.ui.config.CommonLayout;
import games.pixscape.studio.ui.main.StudioApplicationAdapter;

import java.util.Objects;
import java.util.function.Consumer;

public final class ImportDialog extends VisDialog implements OsFilesDropTarget {

    public enum ImportType {
        IMAGE,
        SPRITESHEET,
        TILESET,
        PARTICLE_EFFECT
    }

    private static final float COL_FILE_MIN_W = 220f;
    private static final float COL_TYPE_W = 160f;
    private static final float COL_DETAILS_W = 340f;
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
        public int imageWidth = -1;
        public int imageHeight = -1;

        private boolean dimensionsResolved;
        transient VisValidatableTextField tileWidthField;
        transient VisValidatableTextField tileHeightField;

        public ImportItem(FileHandle file) {
            this.file = file;
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
        root.add(listScroll).grow().minHeight(300).minWidth(700).row();

        inlineErrorLabel.setVisible(false);
        inlineErrorLabel.setColor(1f, 0.4f, 0.4f, 1f);
        root.add(inlineErrorLabel).left().growX().row();

        VisLabel hint = new VisLabel(
                "Tip: PNG is ambiguous. Choose Image or Sprite sheet or Tile set.\n" +
                        "Particle effects are usually .p files."
        );
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
        if (ImportDialogValidation.isSupportedImage(file)) return ImportType.IMAGE;
        return null;
    }

    private void refreshList() {
        listTable.clear();
        listTable.top().left();
        listTable.defaults().pad(4).top();

        if (items.size == 0) {
            listTable.add(new VisLabel("(drop .png or .p files here)"))
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

        if (ImportDialogValidation.isSheetType(item.type)) {
            return buildSheetDetailsActor(item);
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

        String prefix = item.type == ImportType.SPRITESHEET ? "Frame:" : "Tile:";
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

    void applyImportAndCloseIfSuccessful() {
        clearInlineError();

        if (items.size == 0) {
            setInlineError("No compatible files to import.");
            return;
        }

        boolean hasInvalidField = false;
        boolean hasDivisibilityIssue = false;
        boolean hasUnreadableImage = false;
        boolean hasOversizedImage = false;

        for (int i = 0; i < items.size; i++) {
            ImportItem item = items.get(i);

            hasUnreadableImage |= ImportDialogValidation.hasInvalidImageDimensions(item);
            hasOversizedImage |= ImportDialogValidation.exceedsMaxImageSize(item);

            if (!ImportDialogValidation.isSheetType(item.type)) continue;

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

        if (hasDivisibilityIssue) {
            setInlineError("Sheet size must be divisible by tile width and tile height.");
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
}
