package games.pixscape.studio.service.physics;

import games.pixscape.runtime.component.physics.PhysicsJointComponent;

/** Shared Studio display names for authored physics-joint types. */
public final class PhysicsJointUiNames {
    private PhysicsJointUiNames() {
    }

    public static String typeName(int type) {
        return switch (type) {
            case PhysicsJointComponent.TYPE_DISTANCE -> "Distance joint";
            case PhysicsJointComponent.TYPE_REVOLUTE -> "Revolute joint";
            case PhysicsJointComponent.TYPE_PRISMATIC -> "Prismatic joint";
            case PhysicsJointComponent.TYPE_PULLEY -> "Pulley joint";
            case PhysicsJointComponent.TYPE_MOUSE -> "Mouse joint";
            case PhysicsJointComponent.TYPE_GEAR -> "Gear joint";
            case PhysicsJointComponent.TYPE_WHEEL -> "Wheel joint";
            case PhysicsJointComponent.TYPE_WELD -> "Weld joint";
            case PhysicsJointComponent.TYPE_FRICTION -> "Friction joint";
            case PhysicsJointComponent.TYPE_MOTOR -> "Motor joint";
            default -> "Joint";
        };
    }
}
