package games.pixscape.studio.service.spatial;

import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.component.SpatialBlockData;
import games.pixscape.runtime.component.SpatialBlocksComponent;
import games.pixscape.runtime.spatial.SpatialWallGeometry;
import games.pixscape.runtime.tiled.TiledMapLayerData;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Deterministic authored-wall connectivity, merge, split, and validation service. */
public final class SpatialStructureTopology {
    private SpatialStructureTopology() {
    }

    public static final class Plan {
        public final boolean valid;
        public final String error;
        public final Array<SpatialBlockData> walls;

        private Plan(boolean valid, String error, Array<SpatialBlockData> walls) {
            this.valid = valid;
            this.error = error;
            this.walls = walls;
        }
    }

    public static Plan add(SpatialBlocksComponent current,
                           SpatialBlockData candidate,
                           TiledMapLayerData map) {
        Array<SpatialBlockData> before = copyWalls(current);
        Array<SpatialBlockData> after = copyWalls(current);
        if (candidate == null) return invalid("Authored wall is missing.", after);
        SpatialBlockData added = candidate.copy();
        if (added.id <= 0) added.id = maxBlockId(after) + 1;
        if (find(after, added.id) != null) return invalid("Authored wall id is not unique.", after);
        after.add(added);
        return normalize(before, after, map);
    }

    public static Plan delete(SpatialBlocksComponent current, int blockId, TiledMapLayerData map) {
        Array<SpatialBlockData> before = copyWalls(current);
        Array<SpatialBlockData> after = copyWalls(current);
        int index = indexOf(after, blockId);
        if (index < 0) return invalid("Authored wall does not exist.", after);
        after.removeIndex(index);
        return normalize(before, after, map);
    }

    public static Plan edit(SpatialBlocksComponent current,
                            int blockId,
                            SpatialBlockData replacement,
                            TiledMapLayerData map) {
        Array<SpatialBlockData> before = copyWalls(current);
        Array<SpatialBlockData> after = copyWalls(current);
        int index = indexOf(after, blockId);
        if (index < 0 || replacement == null) return invalid("Authored wall does not exist.", after);
        SpatialBlockData edited = replacement.copy();
        edited.id = blockId;
        SpatialBlockData old = after.get(index);
        if (Float.compare(old.altitude, edited.altitude) != 0
                || Float.compare(old.height, edited.height) != 0) {
            for (int i = 0; i < after.size; i++) {
                SpatialBlockData wall = after.get(i);
                if (wall.structureId == old.structureId) {
                    wall.altitude = edited.altitude;
                    wall.height = edited.height;
                }
            }
        }
        after.set(index, edited);
        return normalize(before, after, map);
    }

    public static Plan validate(SpatialBlocksComponent component, TiledMapLayerData map) {
        Array<SpatialBlockData> walls = copyWalls(component);
        String wallFailure = validateWalls(walls, map);
        if (wallFailure != null) return invalid(wallFailure, walls);
        List<List<SpatialBlockData>> components = connectedComponents(walls, true);
        if (components == null) return invalid("Duplicate or contained authored wall rectangles are invalid.", walls);
        Map<Integer, Integer> componentByStructure = new HashMap<>();
        for (int c = 0; c < components.size(); c++) {
            List<SpatialBlockData> connected = components.get(c);
            int structureId = connected.get(0).structureId;
            float altitude = connected.get(0).altitude;
            float height = connected.get(0).height;
            for (SpatialBlockData wall : connected) {
                if (wall.structureId != structureId) {
                    return invalid("Overlapping walls must share one structure id.", walls);
                }
                if (Float.compare(wall.altitude, altitude) != 0) {
                    return invalid("All walls in a structure must share altitude.", walls);
                }
                if (Float.compare(wall.height, height) != 0) {
                    return invalid("All walls in a structure must share height.", walls);
                }
            }
            if (componentByStructure.put(structureId, c) != null) {
                return invalid("One structure id may not identify disconnected components.", walls);
            }
        }
        return valid(walls);
    }

    public static Array<SpatialBlockData> copyWalls(SpatialBlocksComponent component) {
        Array<SpatialBlockData> copy = new Array<>(SpatialBlockData[]::new);
        if (component == null || component.blocks == null) return copy;
        for (int i = 0; i < component.blocks.size; i++) {
            SpatialBlockData wall = component.blocks.get(i);
            if (wall != null) copy.add(wall.copy());
        }
        return copy;
    }

    private static Plan normalize(Array<SpatialBlockData> before,
                                  Array<SpatialBlockData> after,
                                  TiledMapLayerData map) {
        String wallFailure = validateWallsIgnoringStructure(after, map);
        if (wallFailure != null) return invalid(wallFailure, after);
        List<List<SpatialBlockData>> components = connectedComponents(after, true);
        if (components == null) return invalid("Duplicate or contained authored wall rectangles are invalid.", after);

        int nextStructureId = maxStructureId(before);
        Map<Integer, List<List<SpatialBlockData>>> claims = new HashMap<>();
        Map<Integer, Integer> proposed = new HashMap<>();
        for (List<SpatialBlockData> component : components) {
            Set<Integer> oldIds = new HashSet<>();
            float altitude = component.get(0).altitude;
            float height = component.get(0).height;
            boolean hasOldProperties = false;
            for (SpatialBlockData wall : component) {
                SpatialBlockData old = find(before, wall.id);
                if (old != null && old.structureId > 0) {
                    oldIds.add(old.structureId);
                    if (!hasOldProperties) {
                        altitude = wall.altitude;
                        height = wall.height;
                        hasOldProperties = true;
                    } else if (Float.compare(wall.altitude, altitude) != 0
                            || Float.compare(wall.height, height) != 0) {
                        return invalid("Connected structures have incompatible altitude or height.", after);
                    }
                }
            }
            for (SpatialBlockData wall : component) {
                if (find(before, wall.id) == null) {
                    wall.altitude = altitude;
                    wall.height = height;
                }
            }
            int chosen = 0;
            for (Integer oldId : oldIds) chosen = chosen == 0 ? oldId : Math.min(chosen, oldId);
            if (chosen > 0) {
                proposed.put(lowestId(component), chosen);
                claims.computeIfAbsent(chosen, ignored -> new ArrayList<>()).add(component);
            }
        }

        Set<List<SpatialBlockData>> needsNewId = new HashSet<>();
        for (Map.Entry<Integer, List<List<SpatialBlockData>>> entry : claims.entrySet()) {
            List<List<SpatialBlockData>> claimed = entry.getValue();
            claimed.sort(Comparator.comparingInt(SpatialStructureTopology::lowestId));
            for (int i = 1; i < claimed.size(); i++) needsNewId.add(claimed.get(i));
        }
        components.sort(Comparator.comparingInt(SpatialStructureTopology::lowestId));
        for (List<SpatialBlockData> component : components) {
            int structureId = proposed.getOrDefault(lowestId(component), 0);
            if (structureId <= 0 || needsNewId.contains(component)) structureId = ++nextStructureId;
            float altitude = component.get(0).altitude;
            float height = component.get(0).height;
            for (SpatialBlockData wall : component) {
                wall.structureId = structureId;
                wall.altitude = altitude;
                wall.height = height;
            }
        }
        after.sort(Comparator.comparingInt(wall -> wall.id));
        return validateArray(after, map);
    }

    private static Plan validateArray(Array<SpatialBlockData> walls, TiledMapLayerData map) {
        SpatialBlocksComponent component = new SpatialBlocksComponent();
        component.blocks = walls;
        return validate(component, map);
    }

    private static String validateWalls(Array<SpatialBlockData> walls, TiledMapLayerData map) {
        Set<Integer> ids = new HashSet<>();
        SpatialWallGeometry.Bounds bounds = new SpatialWallGeometry.Bounds();
        for (int i = 0; i < walls.size; i++) {
            SpatialBlockData wall = walls.get(i);
            if (!ids.add(wall.id)) return "Authored wall ids must be positive and unique.";
            SpatialWallGeometry.CoverageValidation result =
                    SpatialWallGeometry.validateAuthoredWall(wall, map, bounds);
            if (result != SpatialWallGeometry.CoverageValidation.VALID) {
                return "Invalid authored wall " + wall.id + ": " + result + ".";
            }
        }
        return null;
    }

    private static String validateWallsIgnoringStructure(Array<SpatialBlockData> walls, TiledMapLayerData map) {
        for (int i = 0; i < walls.size; i++) {
            if (walls.get(i).structureId <= 0) walls.get(i).structureId = 1;
        }
        return validateWalls(walls, map);
    }

    private static List<List<SpatialBlockData>> connectedComponents(Array<SpatialBlockData> walls,
                                                                     boolean rejectInvalidIntersections) {
        List<SpatialBlockData> ordered = new ArrayList<>();
        for (int i = 0; i < walls.size; i++) ordered.add(walls.get(i));
        ordered.sort(Comparator.comparingInt(wall -> wall.id));
        int count = ordered.size();
        boolean[][] adjacent = new boolean[count][count];
        SpatialWallGeometry.Bounds a = new SpatialWallGeometry.Bounds();
        SpatialWallGeometry.Bounds b = new SpatialWallGeometry.Bounds();
        SpatialWallGeometry.Junction junction = new SpatialWallGeometry.Junction();
        for (int i = 0; i < count; i++) {
            for (int j = i + 1; j < count; j++) {
                SpatialWallGeometry.JunctionClassification classification =
                        SpatialWallGeometry.classifyJunction(ordered.get(i), ordered.get(j), a, b, junction);
                if (classification == SpatialWallGeometry.JunctionClassification.DUPLICATE
                        || classification == SpatialWallGeometry.JunctionClassification.CONTAINMENT
                        || classification == SpatialWallGeometry.JunctionClassification.INVALID) {
                    if (rejectInvalidIntersections) return null;
                } else if (classification == SpatialWallGeometry.JunctionClassification.VALID_RECTANGULAR_JUNCTION) {
                    adjacent[i][j] = true;
                    adjacent[j][i] = true;
                }
            }
        }
        List<List<SpatialBlockData>> components = new ArrayList<>();
        boolean[] visited = new boolean[count];
        for (int start = 0; start < count; start++) {
            if (visited[start]) continue;
            List<SpatialBlockData> component = new ArrayList<>();
            ArrayDeque<Integer> queue = new ArrayDeque<>();
            queue.add(start);
            visited[start] = true;
            while (!queue.isEmpty()) {
                int index = queue.removeFirst();
                component.add(ordered.get(index));
                for (int next = 0; next < count; next++) {
                    if (adjacent[index][next] && !visited[next]) {
                        visited[next] = true;
                        queue.addLast(next);
                    }
                }
            }
            component.sort(Comparator.comparingInt(wall -> wall.id));
            components.add(component);
        }
        components.sort(Comparator.comparingInt(SpatialStructureTopology::lowestId));
        return components;
    }

    private static int lowestId(List<SpatialBlockData> walls) {
        int lowest = Integer.MAX_VALUE;
        for (SpatialBlockData wall : walls) lowest = Math.min(lowest, wall.id);
        return lowest;
    }

    private static int maxBlockId(Array<SpatialBlockData> walls) {
        int max = 0;
        for (int i = 0; i < walls.size; i++) max = Math.max(max, walls.get(i).id);
        return max;
    }

    private static int maxStructureId(Array<SpatialBlockData> walls) {
        int max = 0;
        for (int i = 0; i < walls.size; i++) max = Math.max(max, walls.get(i).structureId);
        return max;
    }

    private static SpatialBlockData find(Array<SpatialBlockData> walls, int blockId) {
        int index = indexOf(walls, blockId);
        return index >= 0 ? walls.get(index) : null;
    }

    private static int indexOf(Array<SpatialBlockData> walls, int blockId) {
        for (int i = 0; i < walls.size; i++) if (walls.get(i).id == blockId) return i;
        return -1;
    }

    private static Plan valid(Array<SpatialBlockData> walls) {
        return new Plan(true, null, walls);
    }

    private static Plan invalid(String error, Array<SpatialBlockData> walls) {
        return new Plan(false, error, walls);
    }
}
