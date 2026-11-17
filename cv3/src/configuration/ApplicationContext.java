package configuration;

import business.repository.Repositories;
import business.service.CustomerService;
import business.service.MaterialService;
import business.service.OrderDocumentService;
import business.service.OrderPricingService;
import business.service.OrderService;
import business.service.PlaceOrderService;
import dataaccess.SchemaVersion;
import dataaccess.h2.H2Database;
import dataaccess.h2.H2Schema;
import dataaccess.h2.H2Storage;
import dataaccess.identity.IdentityMap;
import dataaccess.mapper.DataMappers;
import dataaccess.persistence.JsonStorage;
import dataaccess.shipping.ShippingGateway;
import dataaccess.shipping.ShippingServiceStub;
import dataaccess.shipping.SimulatedShippingApi;
import dataaccess.gateway.Gateways;
import dataaccess.unitofwork.GatewayEntityPersister;
import dataaccess.unitofwork.StorageTransaction;
import dataaccess.unitofwork.UnitOfWork;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Composition root: the only place that knows which storage is used and the only place where the business
 * layer meets the data access layer. Builds one Unit of Work shared by all services and the Data Mappers that
 * implement the business layer's repository interfaces.
 */
public final class ApplicationContext {
    private final StorageType storageType;
    private final CustomerMapping customerMapping;
    private final Gateways gateways;
    private final UnitOfWork unitOfWork;
    private final CustomerService customerService;
    private final MaterialService materialService;
    private final OrderService orderService;
    private final PlaceOrderService placeOrderService;
    private final OrderDocumentService orderDocumentService;

    private ApplicationContext(StorageType storageType, CustomerMapping customerMapping, Gateways gateways,
                               StorageTransaction transaction) {
        this.storageType = storageType;
        this.customerMapping = customerMapping;
        this.gateways = gateways;
        this.unitOfWork = new UnitOfWork(new IdentityMap(), new GatewayEntityPersister(gateways), transaction);
        Repositories repositories = DataMappers.create(gateways, unitOfWork);
        this.customerService = new CustomerService(repositories.customers(), repositories.addresses(), unitOfWork);
        this.materialService = new MaterialService(repositories.materials(), repositories.finishes(), unitOfWork);
        // CV09 Observer: no observers by default; the presentation registers them via orderService().
        this.orderService = new OrderService(repositories.orders(), customerService, materialService, unitOfWork);
        this.placeOrderService = new PlaceOrderService(repositories, customerService, materialService, orderService,
                unitOfWork);
        this.orderDocumentService = new OrderDocumentService(orderService);
    }

    /** The data directory given by {@code vis.data.dir} (default {@code data}). */
    public static Path defaultDataDirectory() {
        return Path.of(System.getProperty("vis.data.dir", "data"));
    }

    /** Storage in the given directory, mapping from {@code vis.customerMapping} (default CTI). */
    public static ApplicationContext create(StorageType type, Path dataDirectory) {
        return create(type, CustomerMapping.fromSystemProperty(), dataDirectory);
    }

    /**
     * Storage in the given directory with the given inheritance mapping. Each mapping has its own store, because
     * {@code orders} and {@code addresses} can reference only one customer table: the H2 file
     * {@code vis-h2-cti} / {@code vis-h2-sti}, or the JSON folder {@code json-cti/} / {@code json-sti/}.
     */
    public static ApplicationContext create(StorageType type, CustomerMapping mapping, Path dataDirectory) {
        SchemaVersion.refuseOldLayout(dataDirectory);
        if (type == StorageType.H2) {
            Path file = dataDirectory.resolve("vis-h2-" + mapping.suffix());
            if (mapping == CustomerMapping.STI) {
                H2Database database = new H2Database(file, H2Schema.sti());
                return new ApplicationContext(type, mapping, H2Storage.stiGateways(database),
                        H2Storage.transaction(database));
            }
            H2Database database = new H2Database(file, H2Schema.cti());
            return new ApplicationContext(type, mapping, H2Storage.gateways(database), H2Storage.transaction(database));
        }
        System.setProperty("vis.data.dir", jsonDirectory(dataDirectory, mapping).toString());
        Gateways gateways = mapping == CustomerMapping.STI ? JsonStorage.stiGateways() : JsonStorage.gateways();
        return new ApplicationContext(type, mapping, gateways, JsonStorage.transaction());
    }

    /**
     * Like {@link #create(StorageType, CustomerMapping, Path)}, but first empties that store (the JSON folder or
     * the H2 file), so a demonstration starts from nothing. The presentation layer never touches files itself.
     */
    public static ApplicationContext createFresh(StorageType type, CustomerMapping mapping, Path dataDirectory) {
        Path store = type == StorageType.H2 ? dataDirectory : jsonDirectory(dataDirectory, mapping);
        String pattern = type == StorageType.H2 ? "vis-h2-" + mapping.suffix() + ".{mv.db,trace.db}" : "*.json";
        try {
            Files.createDirectories(store);
            try (DirectoryStream<Path> files = Files.newDirectoryStream(store, pattern)) {
                for (Path file : files) {
                    Files.delete(file);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Úložiště " + store + " nelze vyprázdnit.", e);
        }
        return create(type, mapping, dataDirectory);
    }

    public static ApplicationContext createFresh(StorageType type, Path dataDirectory) {
        return createFresh(type, CustomerMapping.fromSystemProperty(), dataDirectory);
    }

    private static Path jsonDirectory(Path dataDirectory, CustomerMapping mapping) {
        return dataDirectory.resolve("json-" + mapping.suffix());
    }

    public StorageType storageType() { return storageType; }
    public CustomerMapping customerMapping() { return customerMapping; }

    /**
     * Diagnostic aid for demonstrations only, not part of the business API: the tables of the active strategy
     * that hold a row of this customer, as plain strings (for JSON the files have the same names with
     * {@code .json}). Used by {@code presentation.Cv07Demonstrations}; nothing in {@code business} may use it.
     */
    public List<String> customerTables(String customerId) {
        return gateways.customers().tablesOf(customerId);
    }
    public UnitOfWork unitOfWork() { return unitOfWork; }
    public CustomerService customerService() { return customerService; }
    public MaterialService materialService() { return materialService; }
    public OrderService orderService() { return orderService; }
    public PlaceOrderService placeOrderService() { return placeOrderService; }
    /** CV09 Factory Method: documents of an order made by the creator the caller chooses. */
    public OrderDocumentService orderDocumentService() { return orderDocumentService; }

    /** CV08: the price calculation with the shipping service of the given mode (stub or gateway). */
    public OrderPricingService orderPricingService(ShippingMode mode) {
        return new OrderPricingService(orderService, mode == ShippingMode.STUB ? new ShippingServiceStub()
                : new ShippingGateway(new SimulatedShippingApi()));
    }
}
