package dev.microcosm.device;

/**
 * The room access devices. Each is placed next to a cell. Viewers show the cell's room (and rooms of cells nested
 * inside it, down to their tier); actuators place, break and use items in those rooms.
 */
public enum DeviceKind {
    /** Tier 1 viewer: live colour view of rooms on scale level 1. */
    OPTICAL_MICROSCOPE("optical_microscope", 1, 0, false),
    /** Tier 1 actuator with a 9-slot inventory. */
    MANIPULATOR_ARM("manipulator_arm", 0, 1, true),
    /** Tier 2 viewer: grayscale view refreshed every few seconds, needs energy, reaches scale level 2. */
    ELECTRON_MICROSCOPE("electron_microscope", 2, 0, false),
    /** Tier 2 actuator: slower, needs energy, reaches scale level 2. */
    NANO_PROBE("nano_probe", 0, 2, true),
    /** Tier 3 viewer and actuator in one, without a live view: it works from scans. */
    FIELD_MANIPULATOR("field_manipulator", 3, 3, true);

    public final String id;
    public final int viewerTier;
    public final int actuatorTier;
    public final boolean hasInventory;

    DeviceKind(String id, int viewerTier, int actuatorTier, boolean hasInventory) {
        this.id = id;
        this.viewerTier = viewerTier;
        this.actuatorTier = actuatorTier;
        this.hasInventory = hasInventory;
    }

    public int tier() {
        return Math.max(viewerTier, actuatorTier);
    }

    public boolean isViewer() {
        return viewerTier > 0;
    }

    public boolean isActuator() {
        return actuatorTier > 0;
    }

    /** Tier 3 has no live view; the room is only known from scans. */
    public boolean isBlind() {
        return viewerTier >= 3;
    }
}
