package games.pixscape.studio.helper;

import com.badlogic.gdx.utils.Array;

import java.util.ArrayList;
import java.util.List;

public final class MetaTagsHelper {
    private MetaTagsHelper() {
    }

    public static List<String> toList(Array<String> tags) {
        ArrayList<String> out = new ArrayList<>();
        if (tags == null) return out;
        for (int i = 0; i < tags.size; i++) {
            String t = tags.get(i);
            if (t == null) continue;
            t = t.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    public static void set(Array<String> dst, List<String> src) {
        if (dst == null) return;
        dst.clear();
        if (src == null) return;
        for (int i = 0; i < src.size(); i++) {
            String t = src.get(i);
            if (t == null) continue;
            t = t.trim();
            if (!t.isEmpty()) dst.add(t);
        }
    }

    public static String toCsv(Array<String> tags) {
        if (tags == null || tags.size == 0) return "No tags";
        StringBuilder sb = new StringBuilder();
        if (tags.size == 1) {
            return tags.get(0);
        } else {
            return tags.get(0) + ", ...";
        }
    }
}
