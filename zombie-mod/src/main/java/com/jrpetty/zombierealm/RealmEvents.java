package com.jrpetty.zombierealm;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

/**
 * The Rotten Realm's reward loop: any monster a player slays inside the realm
 * has a chance to drop Soul Shards — the currency you craft back into light.
 */
public final class RealmEvents {

    private RealmEvents() {
    }

    public static void onLivingDrops(LivingDropsEvent event) {
        LivingEntity dead = event.getEntity();
        if (!(dead instanceof Monster)) {
            return;
        }
        if (!dead.level().dimension().equals(ZombieRealm.ROTTEN_REALM)) {
            return;
        }
        if (!(event.getSource().getEntity() instanceof ServerPlayer)) {
            return; // must be a player kill, not fall damage / mob-on-mob
        }
        RandomSource rng = dead.getRandom();
        int count = 0;
        if (rng.nextFloat() < 0.55F) count++;
        if (rng.nextFloat() < 0.25F) count++;
        if (count > 0) {
            ItemStack shards = new ItemStack(ModItems.SOUL_SHARD.get(), count);
            event.getDrops().add(new ItemEntity(dead.level(),
                    dead.getX(), dead.getY() + 0.3, dead.getZ(), shards));
        }
    }
}
