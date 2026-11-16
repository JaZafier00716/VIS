package cz.vsb.catering.business.domain;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/** Customer who orders catering (UC05 Spravovat zákazníky). */
public class Customer extends DomainObject {
    private static final Pattern EMAIL = Pattern.compile("^[^@ ]+@[^@ ]+\\.[A-Za-z]{2,}$");
    private static final Pattern PHONE = Pattern.compile("^\\+?[0-9 ]{9,20}$");   // same as ck_customers_phone

    private String name;
    private String companyName;
    private String email;
    private String phone;
    private String city;

    public Customer(String name, String companyName, String email, String phone, String city) {
        this.name = name;
        this.companyName = companyName;
        this.email = email;
        this.phone = phone;
        this.city = city;
    }

    /** New customer with validated data (UC05). */
    public static Customer create(String name, String companyName, String email, String phone, String city) {
        Customer c = new Customer(null, null, null, null, null);
        c.update(name, companyName, email, phone, city);
        return c;
    }

    /** Validates and changes all contact data; blank optional fields become null. */
    public void update(String name, String companyName, String email, String phone, String city) {
        Map<String, String> errors = new LinkedHashMap<>();
        name = trim(name);
        companyName = trim(companyName);
        email = trim(email);
        phone = trim(phone);
        city = trim(city);
        if (name == null) errors.put("name", "Jméno je povinné.");
        else if (name.length() > 100) errors.put("name", "Jméno má nejvýše 100 znaků.");
        if (companyName != null && companyName.length() > 100) errors.put("companyName", "Název firmy má nejvýše 100 znaků.");
        if (email == null || email.length() > 254 || !EMAIL.matcher(email).matches()) errors.put("email", "Zadejte platný e-mail.");
        if (phone != null && !PHONE.matcher(phone).matches())
            errors.put("phone", "Telefon má 9–20 číslic a mezer, případně + na začátku.");
        if (city != null && city.length() > 60) errors.put("city", "Město má nejvýše 60 znaků.");
        ValidationException.throwIfAny(errors);
        this.name = name;
        this.companyName = companyName;
        this.email = email;
        this.phone = phone;
        this.city = city;
    }

    /** Name shown in lists, e.g. "Jana Nováková (ABC s.r.o.)". */
    public String displayName() {
        return companyName == null || companyName.isBlank() ? name : name + " (" + companyName + ")";
    }

    public String getName() { return name; }
    public String getCompanyName() { return companyName; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public String getCity() { return city; }

    @Override public String toString() { return displayName(); }

    static String trim(String s) { return s == null || s.isBlank() ? null : s.trim(); }
}
