package mera.mera_v2.pos.sync.service;

import mera.mera_v2.entity.Order;

public interface OrdersService {
  Order getById(Long id);
}