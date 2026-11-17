package business.domainmodel;

import lombok.Getter;

import java.util.regex.Pattern;

/** CV07: a company (firma, cv07 p.3). IČO is required and checked; DIČ and contact person are optional. */
@Getter
public class CompanyCustomer extends Customer {
    private static final Pattern ICO = Pattern.compile("^[0-9]{8}$");
    private static final Pattern DIC = Pattern.compile("^CZ[0-9]{8,10}$");

    private final String companyName;
    private final String ico;
    private final String dic;
    private final String contactPerson;

    public CompanyCustomer(String customerId, String email, String phone, BillingAddress billingAddress,
                           String companyName, String ico, String dic, String contactPerson) {
        super(customerId, email, phone, billingAddress);
        this.companyName = companyName;
        this.ico = ico;
        this.dic = isBlank(dic) ? null : dic.trim();
        this.contactPerson = isBlank(contactPerson) ? null : contactPerson.trim();
    }

    @Override
    public String getDisplayName() {
        return companyName;
    }

    @Override
    public void validate() {
        super.validate();
        if (isBlank(companyName)) {
            throw new IllegalArgumentException("Název firmy je povinný.");
        }
        if (!isValidIco(ico)) {
            throw new IllegalArgumentException("IČO musí mít 8 číslic se správnou kontrolní číslicí.");
        }
        if (dic != null && !DIC.matcher(dic).matches()) {
            throw new IllegalArgumentException("DIČ musí mít tvar CZ + 8 až 10 číslic.");
        }
    }

    /** IČO checksum: weights 8,7,6,5,4,3,2 on the first 7 digits, r = sum % 11, check digit = (11 − r) % 10. */
    public static boolean isValidIco(String ico) {
        if (ico == null || !ICO.matcher(ico).matches()) {
            return false;
        }
        int sum = 0;
        for (int i = 0; i < 7; i++) {
            sum += (ico.charAt(i) - '0') * (8 - i);
        }
        int r = sum % 11;
        return ico.charAt(7) - '0' == (11 - r) % 10;
    }
}
