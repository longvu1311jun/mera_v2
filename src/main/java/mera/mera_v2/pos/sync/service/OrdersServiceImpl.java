package mera.mera_v2.pos.sync.service;

import lombok.RequiredArgsConstructor;
import mera.mera_v2.entity.Order;
import mera.mera_v2.repository.OrderRepository;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrdersServiceImpl implements OrdersService {
  private final OrderRepository orderRepository;

  @Override
  public Order getById(Long id) {
    return orderRepository.findById(id).orElse(null);
  }
}
