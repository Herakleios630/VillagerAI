package de.ajsch.villagerai.util;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

public final class Keys {

    private final NamespacedKey chiefFlagKey;
    private final NamespacedKey chiefIdKey;
    private final NamespacedKey villageIdKey;

    public Keys(JavaPlugin plugin) {
        this.chiefFlagKey = new NamespacedKey(plugin, "chief_flag");
        this.chiefIdKey = new NamespacedKey(plugin, "chief_id");
        this.villageIdKey = new NamespacedKey(plugin, "village_id");
    }

    public NamespacedKey chiefFlagKey() {
        return chiefFlagKey;
    }

    public NamespacedKey chiefIdKey() {
        return chiefIdKey;
    }

    public NamespacedKey villageIdKey() {
        return villageIdKey;
    }
}