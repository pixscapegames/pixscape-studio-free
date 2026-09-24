package games.pixscape.studio.service.hud;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.ui.Container;
import com.badlogic.gdx.scenes.scene2d.ui.Stack;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import games.pixscape.runtime.hud.HudMaterializer;
import games.pixscape.runtime.hud.HudResourceRequirements;
import games.pixscape.runtime.hud.HudResources;
import games.pixscape.runtime.hud.HudScreenAsset;
import games.pixscape.runtime.hud.MaterializedHud;
import games.pixscape.runtime.hud.document.HudDocumentV1;
import games.pixscape.runtime.hud.document.HudNode;
import games.pixscape.runtime.hud.document.HudNodeKind;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class HudRootGroupAuthoringPipelineTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @BeforeClass public static void bootGdx() {
        if (Gdx.files == null) new HeadlessApplication(new ApplicationAdapter() {},
                new HeadlessApplicationConfiguration());
    }

    @Test
    public void transactionValidatesAndMaterializesExistingRootGroupWithoutContentResources()
            throws Exception {
        HudScreenAsset asset = new HudScreenAsset();
        asset.documentId = "hud/root.json";
        HudDocumentEditSession editSession = new HudDocumentEditSession(asset,
                new HudDocumentV1(new HudNode("root", HudNodeKind.GROUP)));
        AtomicReference<MaterializedHud> installed = new AtomicReference<>();
        AtomicReference<HudResources> prepared = new AtomicReference<>();
        var projectDir = Gdx.files.absolute(temporary.newFolder().getAbsolutePath());

        editSession.bindActivePreview((candidateAsset, candidate, validation) -> {
            HudResourceRequirements requirements = HudResourceRequirements.from(
                    validation.validatedDocument());
            assertFalse(requirements.requiresSkin());
            assertFalse(requirements.requiresAtlas());
            HudResources resources = HudResources.prepareStandalone(asset, projectDir, requirements);
            prepared.set(resources);
            installed.set(new HudMaterializer().materialize(
                    validation.validatedDocument(), resources.select(null)));
        });

        try {
            editSession.edit("Resize root Group", candidate -> {
                candidate.root.actor.width = 64f;
                return candidate;
            });

            MaterializedHud materialized = installed.get();
            assertNotNull(materialized);
            assertSame(materialized.root(), materialized.actor("root"));
            assertTrue(materialized.actor("root") instanceof Group);
            assertEquals("root", materialized.actor("root").getName());
            assertEquals(1, materialized.actorById().size());
        } finally {
            if (prepared.get() != null) prepared.get().dispose();
            editSession.close();
        }
    }

    @Test
    public void nestedLayoutTransactionMaterializesNativeParentRelationshipsWithoutResources()
            throws Exception {
        HudScreenAsset asset = new HudScreenAsset();
        asset.documentId = "hud/nested.json";
        HudDocumentEditSession editSession = new HudDocumentEditSession(asset,
                new HudDocumentV1(new HudNode("root", HudNodeKind.GROUP)));
        AtomicReference<MaterializedHud> installed = new AtomicReference<>();
        AtomicReference<HudResources> prepared = new AtomicReference<>();
        var projectDir = Gdx.files.absolute(temporary.newFolder().getAbsolutePath());
        editSession.bindActivePreview((candidateAsset, candidate, validation) -> {
            HudResourceRequirements requirements = HudResourceRequirements.from(
                    validation.validatedDocument());
            assertFalse(requirements.requiresSkin());
            assertFalse(requirements.requiresAtlas());
            HudResources resources = HudResources.prepareStandalone(asset, projectDir, requirements);
            prepared.set(resources);
            installed.set(new HudMaterializer().materialize(
                    validation.validatedDocument(), resources.select(null)));
        });

        try {
            editSession.edit("Build nested layout", candidate -> {
                String container = HudLayoutAuthoring.addChild(
                        candidate, "root", HudNodeKind.CONTAINER);
                String table = HudLayoutAuthoring.addChild(
                        candidate, container, HudNodeKind.TABLE);
                String stack = HudLayoutAuthoring.addChild(
                        candidate, table, HudNodeKind.STACK);
                HudLayoutAuthoring.addChild(candidate, stack, HudNodeKind.GROUP);
                return candidate;
            });

            MaterializedHud hud = installed.get();
            assertNotNull(hud);
            assertTrue(hud.actor("root") instanceof Group);
            assertTrue(hud.actor("container-1") instanceof Container);
            assertTrue(hud.actor("table-1") instanceof Table);
            assertTrue(hud.actor("stack-1") instanceof Stack);
            assertTrue(hud.actor("group-1") instanceof Group);
            assertSame(hud.actor("container-1").getParent(), hud.actor("root"));
            assertSame(hud.actor("table-1"), ((Container<?>) hud.actor("container-1")).getActor());
            assertSame(hud.actor("stack-1").getParent(), hud.actor("table-1"));
            assertSame(hud.actor("group-1").getParent(), hud.actor("stack-1"));
            assertEquals(5, hud.actorById().size());
        } finally {
            if (prepared.get() != null) prepared.get().dispose();
            editSession.close();
        }
    }
}
