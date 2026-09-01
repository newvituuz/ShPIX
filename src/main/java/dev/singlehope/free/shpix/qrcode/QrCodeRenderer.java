package dev.singlehope.free.shpix.qrcode;

import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.jetbrains.annotations.NotNull;

import java.awt.image.BufferedImage;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class QrCodeRenderer extends MapRenderer {

    private final BufferedImage image;
    private final Set<UUID> rendered = ConcurrentHashMap.newKeySet();

    public QrCodeRenderer(final BufferedImage image) {
        super(false);
        this.image = image;
    }

    @Override
    public void render(final @NotNull MapView view, final @NotNull MapCanvas canvas, final @NotNull Player player) {
        if (this.image == null || !this.rendered.add(player.getUniqueId())) {
            return;
        }
        canvas.drawImage(0, 0, this.image);
    }
}
