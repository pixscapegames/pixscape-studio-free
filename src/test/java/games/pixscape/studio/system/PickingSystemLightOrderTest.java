package games.pixscape.studio.system;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PickingSystemLightOrderTest {

    @Test
    public void lightPickingPrefersHigherLayerThenZThenEntityId() {
        int bestEntity = -1;
        int bestLayer = Integer.MIN_VALUE;
        int bestZ = Integer.MIN_VALUE;

        int lightA = 10;
        int layerA = 0;
        int zA = 5;

        int lightB = 11;
        int layerB = 1;
        int zB = -3;

        if (PickingSystem.isBetterHit(bestEntity, bestLayer, bestZ, lightA, layerA, zA)) {
            bestEntity = lightA;
            bestLayer = layerA;
            bestZ = zA;
        }

        if (PickingSystem.isBetterHit(bestEntity, bestLayer, bestZ, lightB, layerB, zB)) {
            bestEntity = lightB;
            bestLayer = layerB;
            bestZ = zB;
        }

        assertEquals(lightB, bestEntity);
    }

    @Test
    public void lightPickingPrefersHigherZWithinSameLayer() {
        int bestEntity = -1;
        int bestLayer = Integer.MIN_VALUE;
        int bestZ = Integer.MIN_VALUE;

        int lightA = 12;
        int layer = 2;
        int zA = 1;

        int lightB = 13;
        int zB = 7;

        if (PickingSystem.isBetterHit(bestEntity, bestLayer, bestZ, lightA, layer, zA)) {
            bestEntity = lightA;
            bestLayer = layer;
            bestZ = zA;
        }

        if (PickingSystem.isBetterHit(bestEntity, bestLayer, bestZ, lightB, layer, zB)) {
            bestEntity = lightB;
            bestLayer = layer;
            bestZ = zB;
        }

        assertEquals(lightB, bestEntity);
    }

    @Test
    public void lightPickingPrefersHigherEntityIdWhenLayerAndZMatch() {
        int bestEntity = -1;
        int bestLayer = Integer.MIN_VALUE;
        int bestZ = Integer.MIN_VALUE;

        int lightA = 20;
        int lightB = 21;
        int layer = 1;
        int z = 4;

        if (PickingSystem.isBetterHit(bestEntity, bestLayer, bestZ, lightA, layer, z)) {
            bestEntity = lightA;
            bestLayer = layer;
            bestZ = z;
        }

        if (PickingSystem.isBetterHit(bestEntity, bestLayer, bestZ, lightB, layer, z)) {
            bestEntity = lightB;
            bestLayer = layer;
            bestZ = z;
        }

        assertEquals(lightB, bestEntity);
    }
}
