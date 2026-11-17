package support;

import business.domainmodel.Customer;
import business.repository.Repositories;
import business.service.CustomerService;
import business.service.MaterialService;
import business.service.OrderService;
import configuration.StorageType;
import dataaccess.dto.AddressDto;
import dataaccess.gateway.AddressGateway;
import dataaccess.gateway.Gateways;
import dataaccess.h2.H2Database;
import dataaccess.h2.H2Storage;
import dataaccess.identity.IdentityMap;
import dataaccess.mapper.DataMappers;
import dataaccess.mapper.customer.CustomerTables;
import dataaccess.persistence.JsonStorage;
import dataaccess.unitofwork.GatewayEntityPersister;
import dataaccess.unitofwork.StorageTransaction;
import dataaccess.unitofwork.UnitOfWork;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * One application over JSON or H2 in a test directory, wired like {@code ApplicationContext}, but with the
 * customer and address gateways counting their reads, so tests can prove when the storage is accessed.
 */
public final class CountingStorage {
    public final CountingCustomerTables customerGateway;
    public final CountingAddressGateway addressGateway;
    public final UnitOfWork unitOfWork;
    public final Repositories repositories;
    public final CustomerService customers;
    public final MaterialService materials;
    public final OrderService orders;

    private CountingStorage(Gateways plain, StorageTransaction transaction) {
        customerGateway = new CountingCustomerTables(plain.customers());
        addressGateway = new CountingAddressGateway(plain.addresses());
        Gateways gateways = new Gateways(customerGateway, addressGateway, plain.materials(), plain.finishes(),
                plain.materialFinishes(), plain.orders());
        unitOfWork = new UnitOfWork(new IdentityMap(), new GatewayEntityPersister(gateways), transaction);
        repositories = DataMappers.create(gateways, unitOfWork);
        customers = new CustomerService(repositories.customers(), repositories.addresses(), unitOfWork);
        materials = new MaterialService(repositories.materials(), repositories.finishes(), unitOfWork);
        orders = new OrderService(repositories.orders(), customers, materials, unitOfWork);
    }

    /** For JSON the caller must point {@code vis.data.dir} at {@code dir} (see {@link IsolatedJsonStorage}). */
    public static CountingStorage open(StorageType type, Path dir) {
        if (type == StorageType.H2) {
            H2Database database = new H2Database(dir.resolve("vis-h2"));
            return new CountingStorage(H2Storage.gateways(database), H2Storage.transaction(database));
        }
        return new CountingStorage(JsonStorage.gateways(), JsonStorage.transaction());
    }

    /** Counts the reads of customers, whatever tables the inheritance strategy reads them from. */
    public static final class CountingCustomerTables implements CustomerTables {
        private final CustomerTables delegate;
        public int reads;

        CountingCustomerTables(CustomerTables delegate) {
            this.delegate = delegate;
        }

        @Override public void insert(Customer customer) { delegate.insert(customer); }
        @Override public void update(Customer customer) { delegate.update(customer); }
        @Override public void delete(String id) { delegate.delete(id); }
        @Override public Optional<Customer> findById(String id) { reads++; return delegate.findById(id); }
        @Override public List<Customer> findAll() { reads++; return delegate.findAll(); }
        @Override public List<String> tablesOf(String id) { return delegate.tablesOf(id); }
    }

    public static final class CountingAddressGateway implements AddressGateway {
        private final AddressGateway delegate;
        public int reads;

        CountingAddressGateway(AddressGateway delegate) {
            this.delegate = delegate;
        }

        @Override public void insert(AddressDto record) { delegate.insert(record); }
        @Override public void update(AddressDto record) { delegate.update(record); }
        @Override public void delete(String id) { delegate.delete(id); }
        @Override public Optional<AddressDto> findById(String id) { reads++; return delegate.findById(id); }
        @Override public List<AddressDto> findAll() { reads++; return delegate.findAll(); }

        @Override
        public List<AddressDto> findByCustomerId(String customerId) {
            reads++;
            return delegate.findByCustomerId(customerId);
        }
    }
}
