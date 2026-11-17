package business.domainmodel;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * CV07: abstract base of the customer hierarchy (cv07 p.3) with the common attributes. The concrete type is
 * {@link PersonCustomer} or {@link CompanyCustomer}; how they are stored is decided by the data access layer.
 */
@Getter
public abstract class Customer extends BaseEntity {
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern PHONE = Pattern.compile("^\\+?[0-9 ]+$");

    private final String email;
    private final String phone;
    /** Optional; when present, all four fields are filled (checked by {@link BillingAddress}). */
    private final BillingAddress billingAddress;
    private Supplier<List<Address>> addressesLoader;
    private List<Address> addresses = new ArrayList<>();
    private boolean addressesLoaded = false;

    protected Customer(String customerId, String email, String phone, BillingAddress billingAddress) {
        super(customerId);
        this.email = email;
        this.phone = phone == null || phone.isBlank() ? null : phone.trim();
        this.billingAddress = billingAddress;
    }

    public String getCustomerId() {
        return getId();
    }

    /** The name shown to the user: "first last" for a person, the company name for a company. */
    public abstract String getDisplayName();

    /** Common rules; subclasses add their own and call {@code super.validate()}. */
    public void validate() {
        if (email == null || !EMAIL.matcher(email).matches()) {
            throw new IllegalArgumentException("E-mail zákazníka není platný.");
        }
        if (phone != null) {
            long digits = phone.chars().filter(Character::isDigit).count();
            if (!PHONE.matcher(phone).matches() || digits < 9 || digits > 15) {
                throw new IllegalArgumentException("Telefon musí mít 9–15 číslic (volitelně +).");
            }
        }
    }

    public List<Address> getAddresses() {
        if (addressesLoader != null && !addressesLoaded) {
            List<Address> loaded = addressesLoader.get();
            this.addresses = loaded == null ? new ArrayList<>() : new ArrayList<>(loaded);
            this.addressesLoaded = true;
        }
        return addresses;
    }

    public void setAddresses(List<Address> addresses) {
        this.addresses = addresses == null ? new ArrayList<>() : new ArrayList<>(addresses);
        this.addressesLoaded = true;
    }

    public void addAddress(Address address) {
        if (address == null) {
            return;
        }
        if (addresses == null) {
            addresses = new ArrayList<>();
        }
        if (addressesLoader != null && !addressesLoaded) {
            getAddresses();
        }
        addresses.add(address);
        addressesLoaded = true;
    }

    public void setAddressesLoader(Supplier<List<Address>> addressesLoader) {
        this.addressesLoader = addressesLoader;
    }

    static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
