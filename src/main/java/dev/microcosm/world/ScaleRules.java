package dev.microcosm.world;

import dev.microcosm.Microcosm;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Keeps material from changing its worth when it moves between scales, so nothing can be duplicated:
 * <ul>
 *   <li>rooms are only entered and left through the size shifter (no portals or teleporters);</li>
 *   <li>the ender chest stores items with their absolute scale;</li>
 *   <li>blocks that move items between dimensions can be banned from rooms ({@link #BLOCKED_IN_ROOMS});</li>
 *   <li>items of a foreign scale cannot be used, eaten, worn or swung, only stored, moved and converted.</li>
 * </ul>
 * Crafting with foreign items is prevented in {@code IngredientMixin}; ports convert items, fluids and energy.
 */
public final class ScaleRules {
    /** Blocks that cannot be placed inside rooms, typically storage or transport reaching other dimensions. */
    public static final TagKey<Block> BLOCKED_IN_ROOMS = TagKey.create(Registries.BLOCK, Microcosm.id("blocked_in_rooms"));

    private static final ThreadLocal<Boolean> ALLOWED_TRAVEL = ThreadLocal.withInitial(() -> false);

    private ScaleRules() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(EntityTravelToDimensionEvent.class, ScaleRules::onTravel);
        NeoForge.EVENT_BUS.addListener(PlayerEvent.Clone.class, ScaleRules::onClone);
        NeoForge.EVENT_BUS.addListener(PlayerContainerEvent.Open.class, ScaleRules::onOpen);
        NeoForge.EVENT_BUS.addListener(PlayerContainerEvent.Close.class, ScaleRules::onClose);
        NeoForge.EVENT_BUS.addListener(BlockEvent.EntityPlaceEvent.class, ScaleRules::onPlace);
        NeoForge.EVENT_BUS.addListener(PlayerInteractEvent.RightClickItem.class, ScaleRules::onUseItem);
        NeoForge.EVENT_BUS.addListener(LivingEntityUseItemEvent.Start.class, ScaleRules::onStartUsing);
        NeoForge.EVENT_BUS.addListener(AttackEntityEvent.class, ScaleRules::onAttack);
        NeoForge.EVENT_BUS.addListener(PlayerEvent.BreakSpeed.class, ScaleRules::onBreakSpeed);
        NeoForge.EVENT_BUS.addListener(PlayerTickEvent.Post.class, ScaleRules::onPlayerTick);
    }

    /** Runs a teleport of our own (size shifter, debug commands), which may cross into or out of rooms. */
    public static void allowTravel(Runnable teleport) {
        ALLOWED_TRAVEL.set(true);
        try {
            teleport.run();
        } finally {
            ALLOWED_TRAVEL.set(false);
        }
    }

    private static int depth(Level level) {
        return MicrocosmLevels.depth(level);
    }

    // ---------------------------------------------------------------- crossing between dimensions

    /** Portals, teleporters and thrown items must not carry anything out of (or into) a room. */
    private static void onTravel(EntityTravelToDimensionEvent event) {
        if (ALLOWED_TRAVEL.get()) {
            return;
        }
        if (depth(event.getEntity().level()) > 0 || MicrocosmLevels.depth(event.getDimension()) > 0) {
            event.setCanceled(true);
        }
    }

    /** With keepInventory, a player who dies in a room respawns with the room's material: it keeps its scale. */
    private static void onClone(PlayerEvent.Clone event) {
        int from = depth(event.getOriginal().level());
        int to = depth(event.getEntity().level());
        if (from != to) {
            for (int i = 0; i < event.getEntity().getInventory().getContainerSize(); i++) {
                ItemScales.move(event.getEntity().getInventory().getItem(i), from, to);
            }
        }
    }

    private static boolean isEnderChest(Player player, PlayerContainerEvent event) {
        return event.getContainer() instanceof ChestMenu chest && chest.getContainer() == player.getEnderChestInventory();
    }

    /** The ender chest is reachable from every scale, so it stores absolute scales and shows them for the place. */
    private static void onOpen(PlayerContainerEvent.Open event) {
        if (event.getEntity() instanceof ServerPlayer player && isEnderChest(player, event)) {
            Container chest = player.getEnderChestInventory();
            for (int i = 0; i < chest.getContainerSize(); i++) {
                ItemScales.fromAbsolute(chest.getItem(i), depth(player.level()));
            }
        }
    }

    private static void onClose(PlayerContainerEvent.Close event) {
        if (event.getEntity() instanceof ServerPlayer player && isEnderChest(player, event)) {
            Container chest = player.getEnderChestInventory();
            for (int i = 0; i < chest.getContainerSize(); i++) {
                ItemScales.toAbsolute(chest.getItem(i), depth(player.level()));
            }
        }
    }

    private static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel() instanceof Level level && depth(level) > 0 && event.getPlacedBlock().is(BLOCKED_IN_ROOMS)) {
            event.setCanceled(true);
            if (event.getEntity() instanceof Player player) {
                player.sendOverlayMessage(Component.translatable("message.microcosm.blocked_in_rooms"));
            }
        }
    }

    // ---------------------------------------------------------------- foreign items are inert

    private static boolean foreign(ItemStack stack) {
        return ItemScales.isForeign(stack);
    }

    private static void onUseItem(PlayerInteractEvent.RightClickItem event) {
        if (foreign(event.getItemStack())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    private static void onStartUsing(LivingEntityUseItemEvent.Start event) {
        if (foreign(event.getItem())) {
            event.setCanceled(true);
        }
    }

    private static void onAttack(AttackEntityEvent event) {
        if (foreign(event.getEntity().getMainHandItem())) {
            event.setCanceled(true);
        }
    }

    private static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        if (foreign(event.getEntity().getMainHandItem())) {
            event.setCanceled(true);
        }
    }

    /** Armour of a foreign scale does not fit: it goes back into the inventory (or drops if that is full). */
    private static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 20 != 0) {
            return;
        }
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR || slot == EquipmentSlot.OFFHAND) {
                ItemStack worn = player.getItemBySlot(slot);
                if (slot != EquipmentSlot.OFFHAND ? foreign(worn) : false) {
                    player.setItemSlot(slot, ItemStack.EMPTY);
                    if (!player.getInventory().add(worn)) {
                        player.drop(worn, false);
                    }
                    player.sendOverlayMessage(Component.translatable("message.microcosm.wrong_scale_armor"));
                }
            }
        }
    }
}
