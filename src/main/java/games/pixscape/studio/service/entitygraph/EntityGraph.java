package games.pixscape.studio.service.entitygraph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record EntityGraph(List<EntityGraphEntry> entries) {
    public EntityGraph(List<EntityGraphEntry> entries) {
        this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
    }

    public static EntityGraph empty() {
        return new EntityGraph(Collections.emptyList());
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public int size() {
        return entries.size();
    }
}
