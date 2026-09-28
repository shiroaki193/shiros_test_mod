package com.shiroaki193.mod.entity;

import com.shiroaki193.mod.Config;
import com.shiroaki193.mod.entity.ai.MortarAttackGoal;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.SwellGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.animal.feline.Ocelot;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.animal.golem.SnowGolem;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * A creeper carrying firework-and-elytra creepers as mortar rounds. It shells villages from up to
 * 120 blocks away, reloads a full salvo after each one, and keeps firing while it has a target in
 * range. With reloading disabled it falls back to ordinary creeper behaviour once out of ammo.
 */
public class MortarCreeper extends Creeper {
    /** Covers the full firing range; target lookups use this attribute. */
    public static final double FOLLOW_RANGE = 128.0;

    private int ammo = Config.MORTAR_AMMO.get();
    private int reloadTicksLeft;
    private boolean naturalSpawn;

    public MortarCreeper(EntityType<? extends MortarCreeper> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Creeper.createAttributes().add(Attributes.FOLLOW_RANGE, FOLLOW_RANGE);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(2, new MortarAttackGoal(this));
        this.goalSelector.addGoal(3, new SwellGoal(this));
        this.goalSelector.addGoal(4, new AvoidEntityGoal<>(this, Ocelot.class, 6.0F, 1.0, 1.2));
        this.goalSelector.addGoal(4, new AvoidEntityGoal<>(this, Cat.class, 6.0F, 1.0, 1.2));
        this.goalSelector.addGoal(5, new MeleeAttackGoal(this, 1.0, false));
        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        // Indirect fire: targets do not need to be in line of sight.
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        // The village first: villagers and both golem kinds (knocking out snow golems opens the air defence).
        this.targetSelector.addGoal(2, new IndirectFireTargetGoal<>(this, LivingEntity.class, (target, level) -> isVillageTarget(target)));
        this.targetSelector.addGoal(3, new IndirectFireTargetGoal<>(this, Player.class, null));
    }

    /**
     * Vanilla target goals only pick targets in line of sight (even with mustSee off, which only
     * governs keeping a target), so a mortar behind a ridge or a parapet never acquired one.
     */
    private static final class IndirectFireTargetGoal<T extends LivingEntity> extends NearestAttackableTargetGoal<T> {
        IndirectFireTargetGoal(MortarCreeper mob, Class<T> type, net.minecraft.world.entity.ai.targeting.TargetingConditions.@org.jspecify.annotations.Nullable Selector selector) {
            super(mob, type, 10, false, false, selector);
            this.targetConditions.ignoreLineOfSight();
        }
    }

    public static boolean isVillageTarget(LivingEntity target) {
        return target instanceof AbstractVillager || target instanceof IronGolem || target instanceof SnowGolem;
    }

    /**
     * Artillery sits far from players by design (up to 120 blocks). Vanilla despawns hostile mobs
     * beyond 128 blocks at once, and beyond 32 after 30 s idle, which is exactly a reloading mortar.
     * So placed mortars (spawn egg, commands) stay. Natural spawns still despawn like any monster,
     * or the world would fill up with them.
     */
    @Override
    public boolean removeWhenFarAway(double distSqr) {
        return this.naturalSpawn && super.removeWhenFarAway(distSqr);
    }

    public void markNaturalSpawn() {
        this.naturalSpawn = true;
    }

    public boolean isNaturalSpawn() {
        return this.naturalSpawn;
    }

    public int getAmmo() {
        return this.ammo;
    }

    public void setAmmo(int ammo) {
        this.ammo = Math.max(0, ammo);
    }

    public void consumeAmmo() {
        this.setAmmo(this.ammo - 1);
        if (this.ammo == 0) {
            this.reloadTicksLeft = Config.MORTAR_RELOAD_TICKS.get();
        }
    }

    /** Out of shells but refilling; the mortar holds its firing position meanwhile. */
    public boolean isReloading() {
        return this.ammo == 0 && Config.MORTAR_RELOAD_TICKS.get() > 0;
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (this.isReloading() && --this.reloadTicksLeft <= 0) {
            this.ammo = Config.MORTAR_AMMO.get();
        }
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("MortarAmmo", this.ammo);
        output.putInt("MortarReload", this.reloadTicksLeft);
        output.putBoolean("MortarNaturalSpawn", this.naturalSpawn);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.ammo = input.getIntOr("MortarAmmo", Config.MORTAR_AMMO.get());
        this.reloadTicksLeft = input.getIntOr("MortarReload", Config.MORTAR_RELOAD_TICKS.get());
        this.naturalSpawn = input.getBooleanOr("MortarNaturalSpawn", false);
    }
}
