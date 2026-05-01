package com.crackedgames.craftics.client.hints;

import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

public sealed interface HintPresenter
        permits HintPresenter.HudPopup, HintPresenter.WorldArrow {

    /** A short text bubble shown on the HUD; queued by priority. */
    record HudPopup(Text text, int durationTicks) implements HintPresenter {}

    /** A bobbing particle column over a world block. Multiple may be active. */
    record WorldArrow(BlockPos target) implements HintPresenter {}
}
