package mera.mera_v2.pos.sync.service;

import lombok.Builder;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Builder
public class OrderSyncResult {

  private final int totalOrdersFromApi;
  private final int insertedCustomers;
  private final int updatedCustomers;
  private final int insertedOrders;
  private final int updatedOrders;
  private final int insertedOrderItems;
  private final int updatedOrderItems;
  private final int insertedStatusHistories;
  private final int updatedStatusHistories;
  private final int skippedOrders;

  @Builder.Default
  private final List<String> errorMessages = new ArrayList<>();

  public int getCustomerChanges() {
    return insertedCustomers + updatedCustomers;
  }

  public int getOrderChanges() {
    return insertedOrders + updatedOrders;
  }

  public int getOrderItemChanges() {
    return insertedOrderItems + updatedOrderItems;
  }

  public int getStatusHistoryChanges() {
    return insertedStatusHistories + updatedStatusHistories;
  }
}

