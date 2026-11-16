package cz.vsb.catering.business.fake;

import cz.vsb.catering.business.domain.User;
import cz.vsb.catering.business.port.UserMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Stores copies and hands out copies, like a real database: changes count only after insert/update. */
public class FakeUserMapper implements UserMapper {
    public final Map<Long, User> rows = new LinkedHashMap<>();
    public final List<User> inserted = new ArrayList<>();
    public final List<User> updated = new ArrayList<>();
    private long nextId = 1;

    /** Existing user in the "database"; returns the stored copy's id. */
    public long add(User user) {
        user.setId(nextId++);
        rows.put(user.getId(), copy(user));
        return user.getId();
    }

    @Override public List<User> findAll() { return rows.values().stream().map(FakeUserMapper::copy).toList(); }

    @Override public Optional<User> findById(long userId) { return Optional.ofNullable(rows.get(userId)).map(FakeUserMapper::copy); }

    @Override public Optional<User> findByUsername(String username) {
        return rows.values().stream().filter(u -> u.getUsername().equals(username)).findFirst().map(FakeUserMapper::copy);
    }

    @Override public void insert(User user) {
        add(user);
        inserted.add(user);
    }

    @Override public void update(User user) {
        rows.put(user.getId(), copy(user));
        updated.add(user);
    }

    public User stored(long id) { return rows.get(id); }

    private static User copy(User u) {
        User c = new User(u.getUsername(), u.getFullName(), u.getEmail(), u.getPasswordHash(), u.isActive(), u.getRoles());
        c.setId(u.getId());
        return c;
    }
}
