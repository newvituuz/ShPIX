package dev.singlehope.free.shpix.menu;

import dev.singlehope.free.shpix.scheduler.Schedulers;
import dev.singlehope.free.shpix.util.ItemBuilder;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public final class LoadingMenu extends Menu {

    private static final int[] RING = {3, 4, 5, 14, 23, 22, 21, 12};

    private final Plugin plugin;
    private final Player player;

    private Schedulers.Task animation;
    private int frame;
    private volatile boolean closing;

    public LoadingMenu(final Plugin plugin, final Player player, final String title) {
        super(title, 3);
        this.plugin = plugin;
        this.player = player;
    }

    @Override
    protected void build(final Player viewer) {
        drawRing(-1, -1);
    }

    private void drawRing(final int headIndex, final int tailIndex) {
        final ItemStack idle = new ItemBuilder("GRAY_STAINED_GLASS_PANE").name("&7 ").build();
        final ItemStack head = new ItemBuilder("LIME_STAINED_GLASS_PANE").name("&a&lGERANDO QR CODE...").build();
        final ItemStack tail = new ItemBuilder("GREEN_STAINED_GLASS_PANE").name("&a&lGERANDO QR CODE...").build();
        for (int index = 0; index < RING.length; index++) {
            final ItemStack stack = index == headIndex ? head : index == tailIndex ? tail : idle;
            set(RING[index], stack);
        }
    }

    public void start() {
        open(this.plugin, this.player);
        this.animation = Schedulers.entityTimer(this.plugin, this.player, () -> {
            if (this.closing || !this.player.isOnline()) {
                return;
            }
            final int headIndex = this.frame % RING.length;
            final int tailIndex = (this.frame + RING.length - 1) % RING.length;
            drawRing(headIndex, tailIndex);
            this.frame++;
        }, 3L, 3L);
    }

    public void stop() {
        this.closing = true;
        if (this.animation != null) {
            this.animation.cancel();
            this.animation = null;
        }
        Schedulers.entity(this.plugin, this.player, () -> {
            if (this.player.isOnline() && this.player.getOpenInventory().getTopInventory().getHolder() == this) {
                this.player.closeInventory();
            }
        });
    }

    @Override
    public void handleClose(final Player closer) {
        this.closing = true;
        if (this.animation != null) {
            this.animation.cancel();
            this.animation = null;
        }
    }
}
