package jpabook.jpashop.repository.order.query;

import jpabook.jpashop.domain.Order;
import jpabook.jpashop.domain.OrderItem;
import jpabook.jpashop.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class OrderQueryService {

    private final OrderRepository orderRepository;

    public List<Order> osivTest2() {
        List<Order> all = orderRepository.osivTest2();

        for (Order order : all) {
            order.getMember().getName();        //  LAZY 강제초기화(Member Proxy 초기화)
            order.getDelivery().getAddress();   //  LAZY 강제초기화(Delivery Proxy 초기화)
            List<OrderItem> orderItems = order.getOrderItems();         //  LAZY 강제초기화(OrderItem Proxy 초기화)
            orderItems.stream().forEach(o -> o.getItem().getName());    //  LAZY 강제초기화(Item Proxy 초기화)
        }

        return all;
    }
}
