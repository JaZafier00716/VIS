package cz.vsb.catering.business.domain;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Account of a worker, manager or administrator (UC01, UC16). */
public class User extends DomainObject {
    public static final int MIN_PASSWORD_LENGTH = 8;
    private static final Pattern EMAIL = Pattern.compile("^[^@ ]+@[^@ ]+\\.[A-Za-z]{2,}$");

    private String username;
    private String fullName;
    private String email;
    private String passwordHash;
    private boolean active = true;
    private final EnumSet<Role> roles = EnumSet.noneOf(Role.class);

    public User(String username, String fullName, String email, String passwordHash, boolean active, Set<Role> roles) {
        this.username = username;
        this.fullName = fullName;
        this.email = email;
        this.passwordHash = passwordHash;
        this.active = active;
        this.roles.addAll(roles);
    }

    /** New account with validated data (UC16 step 4). */
    public static User create(String username, String fullName, String email, String password, Set<Role> roles) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (isBlank(username)) errors.put("username", "Uživatelské jméno je povinné.");
        else if (username.trim().length() > 30) errors.put("username", "Uživatelské jméno má nejvýše 30 znaků.");
        if (isBlank(fullName)) errors.put("fullName", "Celé jméno je povinné.");
        else if (fullName.trim().length() > 100) errors.put("fullName", "Celé jméno má nejvýše 100 znaků.");
        if (isBlank(email) || !EMAIL.matcher(email.trim()).matches()) errors.put("email", "Zadejte platný e-mail.");
        else if (email.trim().length() > 254) errors.put("email", "E-mail má nejvýše 254 znaků.");
        String pwdError = passwordError(password);
        if (pwdError != null) errors.put("password", pwdError);
        if (roles == null || roles.isEmpty()) errors.put("roles", "Vyberte alespoň jednu roli.");
        ValidationException.throwIfAny(errors);
        return new User(username.trim(), fullName.trim(), email.trim(), PasswordHasher.hash(password), true, roles);
    }

    public static String passwordError(String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH)
            return "Heslo musí mít alespoň " + MIN_PASSWORD_LENGTH + " znaků.";
        return null;
    }

    public boolean checkPassword(String password) { return PasswordHasher.matches(password, passwordHash); }

    public void changePassword(String newPassword) {
        String error = passwordError(newPassword);
        if (error != null) throw new ValidationException(error, Map.of("password", error));
        passwordHash = PasswordHasher.hash(newPassword);
    }

    public void changeRoles(Set<Role> newRoles) {
        if (newRoles == null || newRoles.isEmpty())
            throw new ValidationException("Vyberte alespoň jednu roli.", Map.of("roles", "Vyberte alespoň jednu roli."));
        roles.clear();
        roles.addAll(newRoles);
    }

    public void setActive(boolean active) { this.active = active; }

    public boolean isActiveAdmin() { return active && roles.contains(Role.ADMIN); }

    public boolean hasRole(Role role) { return roles.contains(role); }

    public String getUsername() { return username; }
    public String getFullName() { return fullName; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public boolean isActive() { return active; }
    public Set<Role> getRoles() { return EnumSet.copyOf(roles); }

    private static boolean isBlank(String s) { return s == null || s.isBlank(); }
}
