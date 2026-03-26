package com.crackedgames.craftics.item;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * Physical guide book item. Right-click opens the GuideBookScreen on client.
 */
public class GuideBookItem extends Item {

    /** Set by CrafticsClient to provide screen-opening logic without cross-source-set references. */
    public static Runnable openScreenAction = null;

    public GuideBookItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        if (world.isClient() && openScreenAction != null) {
            openScreenAction.run();
        }
        return TypedActionResult.success(user.getStackInHand(hand));
    }
}
