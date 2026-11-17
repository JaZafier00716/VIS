package dataaccess.gateway;

import dataaccess.mapper.customer.CustomerTables;

/**
 * One set of gateways over a single storage (JSON files or H2). {@code customers} is the Customer hierarchy laid
 * out by the active inheritance strategy (CTI or STI) over its own table gateways.
 */
public record Gateways(CustomerTables customers,
                       AddressGateway addresses,
                       MaterialGateway materials,
                       FinishGateway finishes,
                       MaterialFinishGateway materialFinishes,
                       OrderGateway orders) {
}
