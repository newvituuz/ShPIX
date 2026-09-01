package dev.singlehope.free.shpix.shop.action;

import org.bukkit.Sound;

public record ProductAction(ActionType type, Sound sound, String message, String actionBar,
                            String title, String subtitle) {
}
