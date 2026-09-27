package com.shiroaki193.mod.registry;

import com.shiroaki193.mod.MobArmsRace;
import com.shiroaki193.mod.item.CreeperShellItem;
import com.shiroaki193.mod.item.HeldCreeperItem;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MobArmsRace.MODID);

    public static final DeferredItem<SpawnEggItem> MORTAR_CREEPER_SPAWN_EGG = ITEMS.registerItem(
            "mortar_creeper_spawn_egg", SpawnEggItem::new,
            () -> new Item.Properties().spawnEgg(ModEntities.MORTAR_CREEPER.get()));

    public static final DeferredItem<CreeperShellItem> CREEPER_SHELL = ITEMS.registerItem(
            "creeper_shell", CreeperShellItem::new, () -> new Item.Properties().stacksTo(16));

    /** A creeper picked up by hand (not in the creative tab: it only makes sense holding a real creeper). */
    public static final DeferredItem<HeldCreeperItem> HELD_CREEPER = ITEMS.registerItem(
            "held_creeper", HeldCreeperItem::new, () -> new Item.Properties().stacksTo(1));

    private ModItems() {
    }
}
