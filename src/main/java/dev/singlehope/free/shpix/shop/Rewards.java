package dev.singlehope.free.shpix.shop;

import java.util.List;

public record Rewards(List<String> commands, List<RewardItem> items) {

    public Rewards {
        commands = List.copyOf(commands);
        items = List.copyOf(items);
    }

    public static Rewards empty() {
        return new Rewards(List.of(), List.of());
    }

    public boolean hasItems() {
        return !this.items.isEmpty();
    }
}
