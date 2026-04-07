package com.crackedgames.craftics.api;

import com.crackedgames.craftics.combat.TrimEffects;

import java.util.HashMap;
import java.util.Map;

public class StatModifiers {
    private final Map<TrimEffects.Bonus, Integer> bonuses = new HashMap<>();
    private TrimEffects.SetBonus setBonus = TrimEffects.SetBonus.NONE;
    private String setBonusName = "";

    public void add(TrimEffects.Bonus bonus, int value) {
        bonuses.merge(bonus, value, Integer::sum);
    }

    public void addSetBonus(TrimEffects.SetBonus bonus, String name) {
        this.setBonus = bonus;
        this.setBonusName = name;
    }

    public int get(TrimEffects.Bonus bonus) {
        return bonuses.getOrDefault(bonus, 0);
    }

    public Map<TrimEffects.Bonus, Integer> getAll() {
        return bonuses;
    }

    public TrimEffects.SetBonus getSetBonus() {
        return setBonus;
    }

    public String getSetBonusName() {
        return setBonusName;
    }
}
