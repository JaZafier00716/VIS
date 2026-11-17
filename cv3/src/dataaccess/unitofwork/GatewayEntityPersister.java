package dataaccess.unitofwork;

import business.domainmodel.Address;
import business.domainmodel.Customer;
import business.domainmodel.Finish;
import business.domainmodel.Material;
import business.domainmodel.Order;
import dataaccess.mapper.AddressMapper;
import dataaccess.mapper.FinishMapper;
import dataaccess.mapper.MaterialMapper;
import dataaccess.mapper.OrderMapper;
import dataaccess.gateway.Gateways;

/**
 * Bridges the Unit of Work to the gateways: domain object -> mapper -> DTO -> table gateway.
 * The same persister works for JSON and H2 because it only sees the gateway interfaces.
 */
public class GatewayEntityPersister implements UnitOfWork.EntityPersister {
    private final Gateways gateways;

    public GatewayEntityPersister(Gateways gateways) {
        this.gateways = gateways;
    }

    @Override
    public void insert(Object entity) {
        if (entity instanceof Customer customer) {
            gateways.customers().insert(customer);
        } else if (entity instanceof Address address) {
            address.validate();
            gateways.addresses().insert(AddressMapper.toDto(address));
        } else if (entity instanceof Finish finish) {
            gateways.finishes().insert(FinishMapper.toDto(finish));
        } else if (entity instanceof Material material) {
            gateways.materials().insert(MaterialMapper.toDto(material));
            gateways.materialFinishes().synchronize(material.getMaterialId(), MaterialMapper.finishIds(material));
        } else if (entity instanceof Order order) {
            order.validate();
            gateways.orders().insert(OrderMapper.toDto(order));
        } else {
            throw unsupported(entity);
        }
    }

    @Override
    public void update(Object entity) {
        if (entity instanceof Customer customer) {
            gateways.customers().update(customer);
        } else if (entity instanceof Address address) {
            address.validate();
            gateways.addresses().update(AddressMapper.toDto(address));
        } else if (entity instanceof Finish finish) {
            gateways.finishes().update(FinishMapper.toDto(finish));
        } else if (entity instanceof Material material) {
            gateways.materials().update(MaterialMapper.toDto(material));
            gateways.materialFinishes().synchronize(material.getMaterialId(), MaterialMapper.finishIds(material));
        } else if (entity instanceof Order order) {
            order.validate();
            gateways.orders().update(OrderMapper.toDto(order));
        } else {
            throw unsupported(entity);
        }
    }

    @Override
    public void delete(Object entity) {
        if (entity instanceof Customer customer) {
            gateways.customers().delete(customer.getCustomerId());
        } else if (entity instanceof Address address) {
            gateways.addresses().delete(address.getAddressId());
        } else if (entity instanceof Finish finish) {
            gateways.finishes().delete(finish.getFinishId());
        } else if (entity instanceof Material material) {
            gateways.materials().delete(material.getMaterialId());
        } else if (entity instanceof Order order) {
            gateways.orders().delete(order.getOrderId());
        } else {
            throw unsupported(entity);
        }
    }

    private static IllegalArgumentException unsupported(Object entity) {
        return new IllegalArgumentException("Nepodporovaný typ entity: " + entity.getClass().getName());
    }
}
