package dataaccess.persistence;

import dataaccess.gateway.Gateways;
import dataaccess.mapper.customer.CtiCustomerTables;
import dataaccess.mapper.customer.StiCustomerTables;
import dataaccess.unitofwork.StorageTransaction;

public final class JsonStorage {
    private JsonStorage() {}

    /** CTI: customers.json + person_customers.json + company_customers.json in the data directory. */
    public static Gateways gateways() {
        return new Gateways(new CtiCustomerTables(new JsonCustomerGateway(), new JsonPersonCustomerGateway(),
                new JsonCompanyCustomerGateway()), new JsonAddressGateway(), new JsonMaterialGateway(),
                new JsonFinishGateway(), new JsonMaterialFinishGateway(), new JsonOrderGateway());
    }

    /** STI: customers_sti.json; addresses and orders reference it instead of customers.json. */
    public static Gateways stiGateways() {
        return new Gateways(new StiCustomerTables(new JsonStiCustomerGateway()),
                new JsonAddressGateway(JsonStiCustomerGateway::exists), new JsonMaterialGateway(),
                new JsonFinishGateway(), new JsonMaterialFinishGateway(),
                new JsonOrderGateway(JsonStiCustomerGateway::exists));
    }

    public static StorageTransaction transaction() {
        return new JsonFileTransaction();
    }
}
