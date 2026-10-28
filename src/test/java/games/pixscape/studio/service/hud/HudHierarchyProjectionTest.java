package games.pixscape.studio.service.hud;

import games.pixscape.runtime.hud.document.HudChild;
import games.pixscape.runtime.hud.document.HudDocumentV1;
import games.pixscape.runtime.hud.document.HudNode;
import games.pixscape.runtime.hud.document.HudNodeKind;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

public class HudHierarchyProjectionTest {
    @Test
    public void preservesPreorderChildOrderAndStableIds() {
        HudNode root = new HudNode("root", HudNodeKind.GROUP);
        HudNode first = new HudNode("first", HudNodeKind.LABEL);
        HudNode nested = new HudNode("nested", HudNodeKind.IMAGE);
        HudNode second = new HudNode("second", HudNodeKind.STACK);
        second.children.add(HudChild.direct(nested));
        root.children.add(HudChild.direct(first));
        root.children.add(HudChild.direct(second));

        List<HudHierarchyEntry> entries = HudHierarchyProjection.from(new HudDocumentV1(root));
        Assert.assertEquals(List.of("root", "first", "second", "nested"),
                entries.stream().map(HudHierarchyEntry::nodeId).toList());
        Assert.assertEquals(List.of(0, 1, 1, 2),
                entries.stream().map(HudHierarchyEntry::depth).toList());
    }
}
