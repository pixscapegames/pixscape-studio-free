package games.pixscape.studio.ui.asset;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;
import org.junit.Test;

import static org.junit.Assert.*;

public class ImportDialogApplySupportTest {

    @Test
    public void tryApply_userCorrectableFailure_returnsInlineErrorAndNoSuccess() {
        Array<ImportDialog.ImportItem> items = new Array<>();
        items.add(new ImportDialog.ImportItem(new FileHandle("sheet.png")));

        ImportDialogApplySupport.ApplyResult result = ImportDialogApplySupport.tryApply(
                items,
                ignored -> { throw new IllegalArgumentException("Sheet not divisible by tile size"); }
        );

        assertFalse(result.success);
        assertEquals("Sheet not divisible by tile size", result.errorMessage);
    }

    @Test
    public void applyAndCloseOnSuccess_userCorrectableFailure_setsInlineErrorAndDoesNotClose() {
        Array<ImportDialog.ImportItem> items = new Array<>();
        items.add(new ImportDialog.ImportItem(new FileHandle("sheet.png")));
        final String[] inlineError = {null};
        final int[] closeCalls = {0};

        boolean success = ImportDialogApplySupport.applyAndCloseOnSuccess(
                items,
                ignored -> {
                    throw new IllegalArgumentException("Sheet not divisible by tile size");
                },
                message -> inlineError[0] = message,
                () -> closeCalls[0]++
        );

        assertFalse(success);
        assertEquals("Sheet not divisible by tile size", inlineError[0]);
        assertEquals(0, closeCalls[0]);
    }

    @Test
    public void tryApply_successPath_reportsSuccess() {
        Array<ImportDialog.ImportItem> items = new Array<>();
        items.add(new ImportDialog.ImportItem(new FileHandle("sheet.png")));

        ImportDialogApplySupport.ApplyResult result = ImportDialogApplySupport.tryApply(items, ignored -> {
            // success
        });

        assertTrue(result.success);
        assertNull(result.errorMessage);
    }

    @Test
    public void applyAndCloseOnSuccess_successPath_closesWithoutInlineError() {
        Array<ImportDialog.ImportItem> items = new Array<>();
        items.add(new ImportDialog.ImportItem(new FileHandle("sheet.png")));
        final String[] inlineError = {null};
        final int[] closeCalls = {0};

        boolean success = ImportDialogApplySupport.applyAndCloseOnSuccess(
                items,
                ignored -> {
                    // success
                },
                message -> inlineError[0] = message,
                () -> closeCalls[0]++
        );

        assertTrue(success);
        assertNull(inlineError[0]);
        assertEquals(1, closeCalls[0]);
    }

    @Test(expected = RuntimeException.class)
    public void tryApply_internalRuntimeFailure_isNotSwallowed() {
        Array<ImportDialog.ImportItem> items = new Array<>();
        items.add(new ImportDialog.ImportItem(new FileHandle("sheet.png")));

        ImportDialogApplySupport.tryApply(items, ignored -> {
            throw new RuntimeException("internal bug");
        });
    }
}
