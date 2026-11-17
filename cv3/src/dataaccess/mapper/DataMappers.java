package dataaccess.mapper;

import business.repository.Repositories;
import dataaccess.gateway.Gateways;
import dataaccess.unitofwork.UnitOfWork;

/** Builds the Data Mappers of one application over one set of gateways and one Unit of Work. */
public final class DataMappers {
    private DataMappers() {}

    public static Repositories create(Gateways gateways, UnitOfWork unitOfWork) {
        AddressDataMapper addresses = new AddressDataMapper(gateways.addresses(), unitOfWork);
        CustomerDataMapper customers = new CustomerDataMapper(gateways.customers(), addresses, unitOfWork);
        FinishDataMapper finishes = new FinishDataMapper(gateways.finishes(), unitOfWork);
        MaterialDataMapper materials = new MaterialDataMapper(gateways.materials(), gateways.materialFinishes(),
                finishes, unitOfWork);
        OrderDataMapper orders = new OrderDataMapper(gateways.orders(), customers, unitOfWork);
        return new Repositories(customers, addresses, materials, finishes, orders);
    }
}
