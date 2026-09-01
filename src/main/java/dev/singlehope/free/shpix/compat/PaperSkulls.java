package dev.singlehope.free.shpix.compat;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import org.bukkit.Bukkit;
import org.bukkit.inventory.meta.SkullMeta;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

final class PaperSkulls {

    private PaperSkulls() {
    }

    static boolean apply(final SkullMeta meta, final String base64) {
        try {
            final UUID id = UUID.nameUUIDFromBytes(base64.getBytes(StandardCharsets.UTF_8));
            final PlayerProfile profile = Bukkit.createProfile(id, null);
            profile.setProperty(new ProfileProperty("textures", base64));
            meta.setPlayerProfile(profile);
            return true;
        } catch (Exception | LinkageError ignored) {
            return false;
        }
    }
}
