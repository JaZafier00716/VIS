package dataaccess.mapper;

import business.domainmodel.Address;
import business.repository.AddressRepository;
import dataaccess.dto.AddressDto;
import dataaccess.gateway.AddressGateway;
import dataaccess.identity.IdGenerator;
import dataaccess.unitofwork.UnitOfWork;

import java.util.List;

/** Data Mapper for addresses; used by the customer's lazy loader. */
public class AddressDataMapper implements AddressRepository {
    private final AddressGateway gateway;
    private final UnitOfWork unitOfWork;

    public AddressDataMapper(AddressGateway gateway, UnitOfWork unitOfWork) {
        this.gateway = gateway;
        this.unitOfWork = unitOfWork;
    }

    @Override
    public String nextId() {
        return IdGenerator.assign(null, "A");
    }

    @Override
    public List<Address> findByCustomerId(String customerId) {
        return gateway.findByCustomerId(customerId).stream().map(this::identityOrLoad).toList();
    }

    @Override
    public void add(Address address) {
        unitOfWork.registerNew(address, address.getAddressId());
    }

    private Address identityOrLoad(AddressDto record) {
        return unitOfWork.get(Address.class, record.addressId()).orElseGet(() -> {
            Address address = AddressMapper.toDomain(record);
            unitOfWork.remember(address, record.addressId());
            return address;
        });
    }
}
