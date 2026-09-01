package dev.singlehope.free.shpix.shop;

import dev.singlehope.free.shpix.config.PluginConfig;
import dev.singlehope.free.shpix.payment.gateway.GatewayType;
import dev.singlehope.free.shpix.shop.action.ActionType;
import dev.singlehope.free.shpix.shop.action.ProductAction;
import dev.singlehope.free.shpix.util.Money;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

public final class ShopLoader {

    private final Plugin plugin;
    private final Logger logger;

    public ShopLoader(final Plugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    public void load(final ShopCatalog catalog, final PluginConfig config) {
        final List<Category> categories = loadCategories();
        final List<Product> products = loadProducts(categories, config);
        catalog.replace(categories, products);
        this.logger.info("Carregadas " + categories.size() + " categorias e " + products.size() + " produtos.");
    }

    private List<Category> loadCategories() {
        final List<Category> categories = new ArrayList<>();
        final FileConfiguration config = this.plugin.getConfig();
        final ConfigurationSection section = config.getConfigurationSection("categories");
        if (section == null) {
            this.logger.warning("Nenhuma categoria definida em config.yml (seção 'categories').");
            return categories;
        }
        final Set<String> seen = new HashSet<>();
        for (final String key : section.getKeys(false)) {
            final ConfigurationSection entry = section.getConfigurationSection(key);
            if (entry == null) {
                continue;
            }
            if (!seen.add(key.toLowerCase(Locale.ROOT))) {
                this.logger.warning("Categoria duplicada ignorada: " + key);
                continue;
            }
            final Material material = material(entry.getString("icon.material"), "categoria " + key);
            categories.add(new Category(
                    key,
                    entry.getString("name", key),
                    entry.getStringList("description"),
                    material,
                    Math.max(0, entry.getInt("icon.id", 0))));
        }
        return categories;
    }

    private List<Product> loadProducts(final List<Category> categories, final PluginConfig config) {
        final List<Product> products = new ArrayList<>();
        final File directory = new File(this.plugin.getDataFolder(), "products");
        if (!directory.exists() && !directory.mkdirs()) {
            this.logger.warning("Não foi possível criar a pasta de produtos.");
            return products;
        }

        final File[] files = directory.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null || files.length == 0) {
            this.plugin.saveResource("products/exemplo.yml", false);
            return loadFrom(directory, categories, config);
        }
        return loadFrom(directory, categories, config);
    }

    private List<Product> loadFrom(final File directory, final List<Category> categories, final PluginConfig config) {
        final List<Product> products = new ArrayList<>();
        final File[] files = directory.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null) {
            return products;
        }
        final Set<String> seen = new HashSet<>();
        for (final File file : files) {
            final Product product = readProduct(file, categories, config);
            if (product == null) {
                continue;
            }
            if (!seen.add(product.id().toLowerCase(Locale.ROOT))) {
                this.logger.warning("Produto com id duplicado ignorado: " + product.id() + " (" + file.getName() + ")");
                continue;
            }
            products.add(product);
        }
        return products;
    }

    private Product readProduct(final File file, final List<Category> categories, final PluginConfig config) {
        final YamlConfiguration yaml;
        try {
            yaml = YamlConfiguration.loadConfiguration(file);
        } catch (Exception exception) {
            this.logger.warning("Arquivo de produto inválido: " + file.getName());
            return null;
        }

        final String id = yaml.getString("id");
        if (id == null || id.isBlank()) {
            this.logger.warning("Produto sem 'id' ignorado: " + file.getName());
            return null;
        }

        final String categoryId = yaml.getString("category");
        final boolean categoryExists = categories.stream().anyMatch(c -> c.id().equalsIgnoreCase(categoryId));
        if (!categoryExists) {
            this.logger.warning("Categoria '" + categoryId + "' não existe; produto " + id + " ignorado.");
            return null;
        }

        final BigDecimal price = Money.fromConfig(yaml.get("price"), null);
        if (price == null || !Money.isPositive(price)) {
            this.logger.warning("Preço inválido no produto " + id + "; produto ignorado.");
            return null;
        }
        if (!Money.isWithin(price, config.minAmount(), config.maxAmount())) {
            this.logger.warning("Preço do produto " + id + " fora dos limites configurados; produto ignorado.");
            return null;
        }

        final String name = yaml.getString("name", id);
        final ProductIcon icon = new ProductIcon(
                yaml.getString("icon.name", name),
                yaml.getStringList("icon.description"),
                material(yaml.getString("icon.material"), "produto " + id),
                Math.max(0, yaml.getInt("icon.id", 0)));

        return new Product(id, name, categoryId, icon, readRewards(yaml, id),
                readActions(yaml, id), readGateways(yaml, id), price);
    }

    private Rewards readRewards(final YamlConfiguration yaml, final String productId) {
        final List<String> commands = new ArrayList<>();
        for (final String command : yaml.getStringList("rewards.commands")) {
            if (command == null || command.isBlank()) {
                continue;
            }
            commands.add(command);
        }

        final List<RewardItem> items = new ArrayList<>();
        final ConfigurationSection section = yaml.getConfigurationSection("rewards.items");
        if (section != null) {
            for (final String key : section.getKeys(false)) {
                final Material material = Material.matchMaterial(key);
                if (material == null || !material.isItem()) {
                    this.logger.warning("Item de recompensa inválido '" + key + "' no produto " + productId + ".");
                    continue;
                }
                final int amount = Math.max(1, Math.min(64, section.getInt(key + ".amount", 1)));
                final int data = Math.max(0, section.getInt(key + ".id", 0));
                items.add(new RewardItem(material, data, amount));
            }
        }
        return new Rewards(commands, items);
    }

    private Map<ActionType, ProductAction> readActions(final YamlConfiguration yaml, final String productId) {
        final Map<ActionType, ProductAction> actions = new EnumMap<>(ActionType.class);
        final ConfigurationSection section = yaml.getConfigurationSection("actions");
        if (section == null) {
            return actions;
        }
        for (final String key : section.getKeys(false)) {
            final ActionType type = ActionType.parse(key);
            if (type == null) {
                this.logger.warning("Ação desconhecida '" + key + "' no produto " + productId + ".");
                continue;
            }
            actions.put(type, new ProductAction(
                    type,
                    sound(section.getString(key + ".sound"), productId),
                    section.getString(key + ".message", ""),
                    section.getString(key + ".action-bar", ""),
                    section.getString(key + ".screen.title", ""),
                    section.getString(key + ".screen.subtitle", "")));
        }
        return actions;
    }

    private Set<GatewayType> readGateways(final YamlConfiguration yaml, final String productId) {
        final Set<GatewayType> gateways = EnumSet.noneOf(GatewayType.class);
        for (final String raw : yaml.getStringList("gateways")) {
            final GatewayType type = GatewayType.parse(raw);
            if (type == null) {
                this.logger.warning("Gateway desconhecida '" + raw + "' no produto " + productId + ".");
                continue;
            }
            gateways.add(type);
        }
        return gateways;
    }

    private Material material(final String raw, final String context) {
        final Material material = raw == null ? null : Material.matchMaterial(raw);
        if (material == null || !material.isItem()) {
            this.logger.warning("Material inválido '" + raw + "' na " + context + "; usando BARRIER.");
            return Material.BARRIER;
        }
        return material;
    }

    private Sound sound(final String raw, final String productId) {
        if (raw == null || raw.isBlank()) {
            return Sound.ENTITY_EXPERIENCE_ORB_PICKUP;
        }
        try {
            return Sound.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            this.logger.warning("Som inválido '" + raw + "' no produto " + productId + "; usando o som padrão.");
            return Sound.ENTITY_EXPERIENCE_ORB_PICKUP;
        }
    }
}
