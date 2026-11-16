package cz.vsb.catering.data.oracle;

import cz.vsb.catering.business.domain.Role;
import cz.vsb.catering.business.domain.User;
import cz.vsb.catering.business.port.UserMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Data Mapper for users with their roles (user_roles). */
public class OracleUserMapper implements UserMapper {
    // role_code is CHAR(7), so the codes come blank-padded and are trimmed
    private static final String SELECT = """
            SELECT u.user_id, u.username, u.full_name, u.email, u.password_hash, u.active,
                   (SELECT LISTAGG(TRIM(r.role_code), ',') WITHIN GROUP (ORDER BY r.role_code)
                    FROM user_roles r WHERE r.user_id = u.user_id) AS role_codes
            FROM users u
            """;

    @Override
    public List<User> findAll() {
        return Jdbc.query(SELECT + "ORDER BY u.username", OracleUserMapper::read);
    }

    @Override
    public Optional<User> findById(long userId) {
        return Jdbc.queryOne(SELECT + "WHERE u.user_id = ?", OracleUserMapper::read, userId);
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return Jdbc.queryOne(SELECT + "WHERE u.username = ?", OracleUserMapper::read, username);
    }

    @Override
    public void insert(User user) {
        user.setId(Jdbc.insert("""
                INSERT INTO users (username, full_name, email, password_hash, active)
                VALUES (?, ?, ?, ?, ?)""", "USER_ID",
                user.getUsername(), user.getFullName(), user.getEmail(), user.getPasswordHash(),
                Jdbc.yesNo(user.isActive())));
        insertRoles(user);
    }

    @Override
    public void update(User user) {
        Jdbc.update("UPDATE users SET full_name = ?, email = ?, password_hash = ?, active = ? WHERE user_id = ?",
                user.getFullName(), user.getEmail(), user.getPasswordHash(), Jdbc.yesNo(user.isActive()), user.getId());
        Jdbc.update("DELETE FROM user_roles WHERE user_id = ?", user.getId());
        insertRoles(user);
    }

    private static void insertRoles(User user) {
        for (Role role : user.getRoles())
            Jdbc.update("INSERT INTO user_roles (user_id, role_code) VALUES (?, ?)", user.getId(), role.name());
    }

    private static User read(ResultSet rs) throws SQLException {
        Set<Role> roles = EnumSet.noneOf(Role.class);
        String codes = rs.getString("role_codes");
        if (codes != null) for (String code : codes.split(",")) roles.add(Role.valueOf(code));
        User user = new User(rs.getString("username"), rs.getString("full_name"), rs.getString("email"),
                rs.getString("password_hash").trim(), "Y".equals(rs.getString("active")), roles);
        user.setId(rs.getLong("user_id"));
        return user;
    }
}
