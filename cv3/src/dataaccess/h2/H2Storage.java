package dataaccess.h2;

import dataaccess.gateway.Gateways;
import dataaccess.mapper.customer.CtiCustomerTables;
import dataaccess.mapper.customer.CustomerTables;
import dataaccess.mapper.customer.StiCustomerTables;
import dataaccess.unitofwork.StorageTransaction;

public final class H2Storage {
    private H2Storage() {}

    /** CTI: a database created with {@link H2Schema#cti()} (the default schema of {@link H2Database}). */
    public static Gateways gateways(H2Database database) {
        return gateways(database, new CtiCustomerTables(new H2CustomerGateway(database),
                new H2PersonCustomerGateway(database), new H2CompanyCustomerGateway(database)));
    }

    /** STI: a database created with {@link H2Schema#sti()}. */
    public static Gateways stiGateways(H2Database database) {
        return gateways(database, new StiCustomerTables(new H2StiCustomerGateway(database)));
    }

    private static Gateways gateways(H2Database database, CustomerTables customers) {
        return new Gateways(customers, new H2AddressGateway(database), new H2MaterialGateway(database),
                new H2FinishGateway(database), new H2MaterialFinishGateway(database), new H2OrderGateway(database));
    }

    public static StorageTransaction transaction(H2Database database) {
        return new H2Transaction(database);
    }
}
