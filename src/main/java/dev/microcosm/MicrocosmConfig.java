package dev.microcosm;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server config ({@code serverconfig/microcosm-server.toml}) for balancing the room access devices. */
public final class MicrocosmConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue ALLOW_PLAYER_PLACEMENT_IN_ROOMS;
    public static final ModConfigSpec.IntValue MAX_QUEUED_ACTIONS;

    public static final Tier TIER_1;
    public static final Tier TIER_2;
    public static final Tier TIER_3;

    /** Settings of one device tier. Tier N reaches rooms down to scale level N. */
    public static final class Tier {
        /** Ticks between view updates; for tier 3 the duration of one scan. */
        public final ModConfigSpec.IntValue viewTicks;
        public final ModConfigSpec.IntValue viewEnergy;
        public final ModConfigSpec.IntValue actionTicks;
        public final ModConfigSpec.IntValue actionEnergy;
        public final ModConfigSpec.IntValue energyCapacity;

        Tier(ModConfigSpec.Builder b, String name, String comment, int viewTicks, int viewEnergy,
             int actionTicks, int actionEnergy, int capacity) {
            b.comment(comment).push(name);
            this.viewTicks = b.comment("Ticks between view refreshes (tier 3: duration of one scan)")
                    .defineInRange("view_ticks", viewTicks, 1, 72000);
            this.viewEnergy = b.comment("Energy (FE) per view refresh or scan")
                    .defineInRange("view_energy", viewEnergy, 0, Integer.MAX_VALUE);
            this.actionTicks = b.comment("Ticks one place/break/use action takes")
                    .defineInRange("action_ticks", actionTicks, 1, 72000);
            this.actionEnergy = b.comment("Energy (FE) per action")
                    .defineInRange("action_energy", actionEnergy, 0, Integer.MAX_VALUE);
            this.energyCapacity = b.comment("Energy buffer of devices of this tier (0 = no energy needed)")
                    .defineInRange("energy_capacity", capacity, 0, Integer.MAX_VALUE);
            b.pop();
        }
    }

    public static Tier tier(int tier) {
        return switch (tier) {
            case 1 -> TIER_1;
            case 2 -> TIER_2;
            default -> TIER_3;
        };
    }

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("general");
        ALLOW_PLAYER_PLACEMENT_IN_ROOMS = b
                .comment("Whether players standing inside a room may place blocks by hand. Fake players (machines) always can.")
                .define("allow_player_placement_in_rooms", true);
        MAX_QUEUED_ACTIONS = b.comment("Maximum number of queued actions per manipulator")
                .defineInRange("max_queued_actions", 64, 1, 4096);
        b.pop();
        TIER_1 = new Tier(b, "tier_1", "Optical microscope and manipulator arm (scale level 1)",
                10, 0, 10, 0, 0);
        TIER_2 = new Tier(b, "tier_2", "Electron microscope and nano probe (scale level 2)",
                60, 200, 40, 500, 200_000);
        TIER_3 = new Tier(b, "tier_3", "Field manipulator (scale level 3, no live view)",
                100, 5_000, 60, 2_000, 1_000_000);
        SPEC = b.build();
    }

    private MicrocosmConfig() {}
}
