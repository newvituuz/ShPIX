package dev.singlehope.free.shpix.user;

import dev.singlehope.free.shpix.storage.Database;
import dev.singlehope.free.shpix.storage.UserRepository;

import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class UserService {

    private final Database database;
    private final UserRepository repository;
    private final Map<UUID, User> cache = new ConcurrentHashMap<>();

    public UserService(final Database database, final UserRepository repository) {
        this.database = database;
        this.repository = repository;
    }

    public void load(final UUID uniqueId, final String name) {
        if (!this.database.isAvailable()) {
            this.cache.put(uniqueId, User.empty(uniqueId, name));
            return;
        }
        try {
            this.cache.put(uniqueId, this.repository.loadOrCreate(uniqueId, name));
        } catch (SQLException exception) {
            this.cache.put(uniqueId, User.empty(uniqueId, name));
        }
    }

    public void refresh(final UUID uniqueId) {
        if (!this.database.isAvailable()) {
            return;
        }
        try {
            this.repository.find(uniqueId).ifPresent(user -> this.cache.put(uniqueId, user));
        } catch (SQLException ignored) {
            // mantém o valor em cache
        }
    }

    public Optional<User> get(final UUID uniqueId) {
        return Optional.ofNullable(this.cache.get(uniqueId));
    }

    public User getOrEmpty(final UUID uniqueId, final String name) {
        return this.cache.computeIfAbsent(uniqueId, id -> User.empty(id, name));
    }

    public void unload(final UUID uniqueId) {
        this.cache.remove(uniqueId);
    }

    public void clear() {
        this.cache.clear();
    }
}
