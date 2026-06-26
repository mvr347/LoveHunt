package me.lovelace.loveHunt.service;

import me.lovelace.loveHunt.gui.MenuType;
import me.lovelace.loveHunt.model.SortMode;
import me.lovelace.loveHunt.model.TypeFilter;

public record PlayerInput(InputMode mode, MenuType returnType, int page, SortMode sortMode, TypeFilter typeFilter,
                           boolean onlyMyClan, boolean onlineOnly) {
}
