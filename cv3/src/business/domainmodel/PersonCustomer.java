package business.domainmodel;

import lombok.Getter;

import java.time.LocalDate;

/** CV07: a natural person (fyzická osoba, cv07 p.3). */
@Getter
public class PersonCustomer extends Customer {
    private final String firstName;
    private final String lastName;
    private final LocalDate birthDate;

    public PersonCustomer(String customerId, String email, String phone, BillingAddress billingAddress,
                          String firstName, String lastName, LocalDate birthDate) {
        super(customerId, email, phone, billingAddress);
        this.firstName = firstName;
        this.lastName = lastName;
        this.birthDate = birthDate;
    }

    @Override
    public String getDisplayName() {
        return firstName + " " + lastName;
    }

    @Override
    public void validate() {
        super.validate();
        if (isBlank(firstName) || isBlank(lastName)) {
            throw new IllegalArgumentException("Jméno a příjmení jsou povinné.");
        }
        if (birthDate == null || !birthDate.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Datum narození musí být v minulosti.");
        }
    }
}
