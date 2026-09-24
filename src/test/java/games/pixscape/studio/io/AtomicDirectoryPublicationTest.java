package games.pixscape.studio.io;

import com.badlogic.gdx.files.FileHandle;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class AtomicDirectoryPublicationTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Test public void retirementMovesAwayOnlyTheRequestedLiveDirectory() throws Exception {
        FileHandle parent = new FileHandle(temporary.newFolder());
        FileHandle live = parent.child("sceneA");
        live.mkdirs(); live.child("hud.atlas").writeString("old", false);
        parent.child("sceneB").mkdirs();
        AtomicDirectoryPublication.retire(live, (source, retired) -> {
            assertEquals(live, source);
            assertTrue(retired.name().startsWith(".retired-sceneA-"));
            AtomicDirectoryPublication.move(source, retired);
            assertFalse(live.exists());
            assertEquals("old", retired.child("hud.atlas").readString());
        });
        assertFalse(live.exists());
        assertTrue(parent.child("sceneB").exists());
        assertEquals(1, parent.list().length);
        AtomicDirectoryPublication.retire(live); // Missing output is already retired.
    }

    @Test public void failedRetirementMovePreservesLiveOutput() throws Exception {
        FileHandle live = new FileHandle(temporary.newFolder()).child("sceneA");
        live.mkdirs(); live.child("hud.atlas").writeString("old", false);
        try {
            AtomicDirectoryPublication.retire(live, (source, retired) -> { throw new IllegalStateException("locked"); });
            fail("Expected required move failure");
        } catch (IllegalStateException expected) { assertEquals("old", live.child("hud.atlas").readString()); }
    }

    @Test public void closeRollsBackAndCommitRetainsCompleteReplacement() throws Exception {
        FileHandle live = new FileHandle(temporary.newFolder()).child("output");
        live.mkdirs();
        live.child("old.txt").writeString("old", false);
        FileHandle candidate = AtomicDirectoryPublication.createCandidate(live);
        candidate.child("new.txt").writeString("new", false);
        try (var published = AtomicDirectoryPublication.publish(candidate, live)) {
            assertEquals("new", live.child("new.txt").readString());
        } finally { AtomicDirectoryPublication.discardCandidate(candidate); }
        assertEquals("old", live.child("old.txt").readString());
        assertFalse(live.child("new.txt").exists());
        candidate = AtomicDirectoryPublication.createCandidate(live);
        candidate.child("new.txt").writeString("new", false);
        try (var published = AtomicDirectoryPublication.publish(candidate, live)) { published.commit(); }
        finally { AtomicDirectoryPublication.discardCandidate(candidate); }
        assertEquals("new", live.child("new.txt").readString());
        assertFalse(live.child("old.txt").exists());
        assertEquals(1, live.parent().list().length);
    }

    @Test public void publisherFailureAfterMovingCandidateRestoresPriorDirectory() throws Exception {
        FileHandle live = new FileHandle(temporary.newFolder()).child("output");
        live.mkdirs(); live.child("old.txt").writeString("old", false);
        FileHandle candidate = AtomicDirectoryPublication.createCandidate(live);
        candidate.child("partial.txt").writeString("partial", false);
        try {
            AtomicDirectoryPublication.publish(candidate, live, (source, target) -> {
                AtomicDirectoryPublication.move(source, target);
                throw new IllegalStateException("Injected post-move failure");
            });
            fail("Expected publication failure");
        } catch (IllegalStateException expected) {
            assertEquals("old", live.child("old.txt").readString());
            assertFalse(live.child("partial.txt").exists());
        } finally { AtomicDirectoryPublication.discardCandidate(candidate); }
        assertEquals(1, live.parent().list().length);
    }

    @Test public void publisherFailureBeforeMoveLeavesNoNewTargetAndCandidateCanBeDiscarded() throws Exception {
        FileHandle live = new FileHandle(temporary.newFolder()).child("output");
        FileHandle candidate = AtomicDirectoryPublication.createCandidate(live);
        candidate.child("input.png").writeString("bytes", false);
        try {
            AtomicDirectoryPublication.publish(candidate, live, (source, target) -> { throw new IllegalStateException("Injected"); });
            fail("Expected publication failure");
        } catch (IllegalStateException expected) { assertFalse(live.exists()); }
        finally { AtomicDirectoryPublication.discardCandidate(candidate); }
        assertEquals(0, live.parent().list().length);
    }

    @Test public void lockedOptionalCandidateParentDoesNotInvalidateCommittedPublication() throws Exception {
        FileHandle live = new FileHandle(temporary.newFolder()).child("output");
        FileHandle candidate = AtomicDirectoryPublication.createCandidate(live);
        candidate.child("new.txt").writeString("new", false);
        try {
            try (var published = AtomicDirectoryPublication.publish(candidate, live)) { published.commit(); }
            assertFalse(candidate.exists());
            AtomicDirectoryPublication.discardCandidate(candidate, parent -> {
                throw new java.nio.file.AccessDeniedException(parent.toString());
            });
            assertEquals("new", live.child("new.txt").readString());
            assertTrue(live.exists());
            for (FileHandle child : live.parent().list()) assertFalse(child.name().startsWith(".backup-"));
        } finally {
            // The injected deletion left only the optional empty container behind.
            AtomicDirectoryPublication.discardCandidate(candidate);
        }
        assertEquals(1, live.parent().list().length);
    }
}
