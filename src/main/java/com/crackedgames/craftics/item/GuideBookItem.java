package com.crackedgames.craftics.item;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
//? if <=1.21.1 {
/*import net.minecraft.util.TypedActionResult;
*///?}
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
    //? if <=1.21.1 {
    /*public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        if (world.isClient() && openScreenAction != null) {
            openScreenAction.run();
        }
        return TypedActionResult.success(user.getStackInHand(hand));
    }
    *///?} else {
    public ActionResult use(World world, PlayerEntity user, Hand hand) {
        if (world.isClient() && openScreenAction != null) {
            openScreenAction.run();
        }
        return ActionResult.SUCCESS;
    }
    //?}
}
