package games.pixscape.studio.ui.shaders;

import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.tabbedpane.Tab;
import games.pixscape.runtime.render.ShaderVariant;

public class VariantTab extends Tab {

    private final ShaderVariant variant;
    private final String title;
    private final VisTable content;

    public VariantTab(ShaderVariant variant, String title, VisTable content) {
        super(false, false);
        this.variant = variant;
        this.title = title;
        this.content = content;
    }

    public ShaderVariant getVariant() {
        return variant;
    }

    @Override
    public String getTabTitle() {
        return title;
    }

    @Override
    public VisTable getContentTable() {
        return content;
    }
}
