package com.crackedgames.craftics.mixin;

import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Reaches TextDisplayEntity's private text setter for the infinite-score hologram. */
@Mixin(DisplayEntity.TextDisplayEntity.class)
public interface TextDisplayInvoker {
    @Invoker("setText")
    void craftics$setText(Text text);

    @Invoker("setLineWidth")
    void craftics$setLineWidth(int width);
}
