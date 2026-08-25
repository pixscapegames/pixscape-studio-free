package games.pixscape.studio.ui.property.entityproperties;

import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class EntityPropertiesCustomPropertiesUiContractTest {

    @Test
    public void commonHeaderPlacesPropertiesBetweenTagsAndLayer() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/games/pixscape/studio/ui/property/entityproperties/EntityProperties.java"),
                StandardCharsets.UTF_8);

        int name = source.indexOf("new VisLabel(\"Name:\")");
        int tags = source.indexOf("new VisLabel(\"Tags:\")");
        int properties = source.indexOf("new VisLabel(\"Properties:\")");
        int layer = source.indexOf("new VisLabel(\"Layer:\")");
        int zindex = source.indexOf("new VisLabel(\"Zindex:\")");

        Assert.assertTrue(name >= 0 && name < tags);
        Assert.assertTrue(tags < properties);
        Assert.assertTrue(properties < layer);
        Assert.assertTrue(layer < zindex);
        Assert.assertTrue(source.contains("new CustomPropertiesEditorRow(ctx)"));
    }
}
