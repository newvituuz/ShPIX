package dev.singlehope.free.shpix.shop;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class ShopCatalog {

    private volatile Map<String, Category> categories = Map.of();
    private volatile Map<String, Product> products = Map.of();

    public void replace(final List<Category> newCategories, final List<Product> newProducts) {
        final Map<String, Category> categoryMap = new LinkedHashMap<>();
        for (final Category category : newCategories) {
            categoryMap.put(category.id().toLowerCase(Locale.ROOT), category);
        }
        final Map<String, Product> productMap = new LinkedHashMap<>();
        for (final Product product : newProducts) {
            productMap.put(product.id().toLowerCase(Locale.ROOT), product);
        }
        this.categories = Map.copyOf(categoryMap);
        this.products = Map.copyOf(productMap);
    }

    public Optional<Category> category(final String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(this.categories.get(id.toLowerCase(Locale.ROOT)));
    }

    public Optional<Product> product(final String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(this.products.get(id.toLowerCase(Locale.ROOT)));
    }

    public List<Category> categories() {
        return List.copyOf(this.categories.values());
    }

    public List<Product> productsOf(final Category category) {
        final List<Product> result = new ArrayList<>();
        for (final Product product : this.products.values()) {
            if (product.categoryId().equalsIgnoreCase(category.id())) {
                result.add(product);
            }
        }
        return result;
    }

    public int productCount() {
        return this.products.size();
    }

    public int categoryCount() {
        return this.categories.size();
    }
}
