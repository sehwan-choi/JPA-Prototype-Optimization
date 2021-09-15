package jpabook.jpashop.api;

import jpabook.jpashop.domain.Address;
import jpabook.jpashop.domain.Order;
import jpabook.jpashop.domain.OrderItem;
import jpabook.jpashop.domain.OrderStatus;
import jpabook.jpashop.repository.OrderRepository;
import jpabook.jpashop.repository.OrderSearch;
import jpabook.jpashop.repository.order.query.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.hibernate.cache.cfg.internal.AbstractDomainDataCachingConfig;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.*;

/**
 * X to Many( One to Many, Many to Many) 성능 최적화
 *
 */
@RestController
@RequiredArgsConstructor
public class OrderApiController {

    private final OrderRepository orderRepository;
    private final OrderQueryRepository orderQueryRepository;

    /**
     * ordersV1
     * 문제점
     * 1. orderRepository.findAllByString 하게 되면 Member, orderItem, delivery를 가져오게 되는데 모두 LAZY LOADING으로 설정 되어 있기 때문에
     *    위 함수 호출 시점에는 실제 데이터가 아닌 Proxy 객체가 들어가 있게 되므로 에러가 발생한다.
     *   error : [com.fasterxml.jackson.databind.exc.InvalidDefinitionException: No serializer found for class org.hibernate.proxy.pojo.bytebuddy.ByteBuddyInterceptor and no properties discovered to create BeanSerializer (to avoid exception, disable SerializationFeature.FAIL_ON_EMPTY_BEANS) (through reference chain: java.util.ArrayList[0]->jpabook.jpashop.domain.Order["member"]->jpabook.jpashop.domain.Member$HibernateProxy$Y7sAZ8N9["hibernateLazyInitializer"])]
     *
     * 2. 엔티티를 return 하고 있음. 이렇게 되면 엔티티의 값이 변경 되는 경우 API 스펙이 변경되는 치명적인 단점이 발생생
     *
     * 3. 1번에 이유로 Proxy 객체를 강제 초기화 해야하는 문제가 발생
     *
     * 4. 엔티티를 직접 노출 하는 경우네는 양방향 연관관계가 걸린 곳은 반드시 한곳을 @JsonIgnore 처리 해야한다. 안그러면 양쪽을 서로 호출하면서 무한 루프에 빠지게 된다.
     *
     * 5. LAZY 로딩으로 인한 데이터베이스 쿼리가 너무 많이 호출 되는 문제가 있다.(성능상 이슈가 발생)
     *
     * 참고: 앞에서 계속 강조했듯이 정말 간단한 애플리케이션이 아니면 엔티티를 API 응답으로 외부로 노출하는 것은 좋지 않다.
     * 따라서 Hibernate5Module 를 사용하기 보다는 DTO로 변환해서 반환하는 것이 더 좋은 방법이다.
     *
     * 주의: 지연 로딩(LAZY)을 피하기 위해 즉시 로딩(EARGR)으로 설정하면 안된다! 즉시 로딩 때문에 연관관계가 필요 없는 경우에도 데이터를 항상 조회해서 성능 문제가 발생할 수 있다.
     * 즉시 로딩으로 설정하면 성능 튜닝이 매우 어려워 진다. 항상 지연 로딩을 기본으로 하고, 성능 최적화가 필요한 경우에는 페치 조인(fetch join)을 사용해야한다(V3 에서 설명)
     */
    @GetMapping("/api/v1/orders")
    public List<Order> ordersV1() {
        List<Order> all = orderRepository.findAllByString(new OrderSearch());
        for (Order order : all) {
            order.getMember().getName();        //  LAZY 강제초기화(Member Proxy 초기화)
            order.getDelivery().getAddress();   //  LAZY 강제초기화(Delivery Proxy 초기화)
            List<OrderItem> orderItems = order.getOrderItems();         //  LAZY 강제초기화(OrderItem Proxy 초기화)
            orderItems.stream().forEach(o -> o.getItem().getName());    //  LAZY 강제초기화(Item Proxy 초기화)
        }
        return all;
    }

    /**
     * ordersV2
     * 문제점
     * 1. LAZY 로딩으로 인한 데이터베이스 쿼리가 너무 많이 호출 되는 문제가 있다.(성능상 이슈가 발생) -> ordersV1의 5번 문제와 같음
     * Order - Member ( N : 1 )
     * Order - Delivery ( 1 : 1 )
     * Order - OrderItem ( 1 : N )
     * 의 관계 이므로 Order를 가져올때 Member와 Delivery, OrderItem를 가져와야 한다.
     * 결국 1개의 Order 쿼리 후 Member, Delivery, OrderItem을 가져오는 쿼리가 추가 실행되어야 하므로 1 + N + N + N번의 쿼리가 나가게 된다.
     *
     */

    @GetMapping("/api/v2/orders")
    public Result ordersV2() {
        List<Order> all = orderRepository.findAllByString(new OrderSearch());
        List<OrderDto> collect = all.stream().map(o -> new OrderDto(o)).collect(toList());

        return new Result(collect);
    }

    @Getter
    static class OrderDto {

        private Long orderId;
        private String name;
        private LocalDateTime orderDate;
        private OrderStatus orderStatus;
        private Address address;
        private List<OrderItemDto> orderItems;

        public OrderDto(Order o) {
            orderId = o.getId();
            name = o.getMember().getName();
            orderDate = o.getOrderDate();
            orderStatus = o.getStatus();
            address = o.getDelivery().getAddress();;
            orderItems = o.getOrderItems().stream().map(m -> new OrderItemDto(m)).collect(toList());
        }
    }

    @Getter
    static class OrderItemDto {

        private String itemName;
        private int orderPrice;
        private int count;

        public OrderItemDto(OrderItem m) {
            itemName = m.getItem().getName();
            orderPrice = m.getOrderPrice();
            count = m.getCount();
        }
    }

    @Data
    @AllArgsConstructor
    static class Result<T> {

        private T data;
    }


    /**
     * ordersV2
     * 문제점
     * 1.  컬렉션 페치 조인을 사용하면 페이징이 불가능하다. 하이버네이트는 경고 로그를 남기면서 모든 데이터를 DB에서 읽어오고,
     *     메모리에서 페이징 해버린다(매우 위험하다).
     *
     * 참고: 컬렉션 페치 조인은 1개만 사용할 수 있다. 컬렉션 둘 이상에 페치 조인을 사용하면 안된다. 데이터가
     * 부정합하게 조회될 수 있다.
     *
     */
    @GetMapping("/api/v3/orders")
    public Result ordersV3() {
        List<Order> all = orderRepository.findAllWithItem();
        List<OrderDto> collect = all.stream().map(o -> new OrderDto(o)).collect(toList());

        return new Result(collect);
    }

    /**
     * V3.1 엔티티를 조회해서 DTO로 변환 페이징 고려
     * - ToOne 관계만 우선 모두 페치 조인으로 최적화
     * - 컬렉션 관계는 hibernate.default_batch_fetch_size, @BatchSize로 최적화
     */
    @GetMapping("/api/v3.1/orders")
    public Result ordersV3_1(
            @RequestParam(value = "offset", defaultValue = "0") int offset,
            @RequestParam(value = "limit", defaultValue = "100") int limit) {
        List<Order> all = orderRepository.findAllWithMemberDelivery(offset,limit);
        List<OrderDto> collect = all.stream().map(o -> new OrderDto(o)).collect(toList());

        return new Result(collect);
    }

    /**
     * 컬렉션은 별도로 조회
     * Query: 최초초 1번 컬렉션 N 번
     * 단건 조회에서 많이 사용하는 방식
     */
    @GetMapping("/api/v4/orders")
    public Result ordersV4() {
        List<OrderQueryDto> orderQueryDtos = orderQueryRepository.findOrderQueryDtos();
        return new Result(orderQueryDtos);
    }

    @GetMapping("/api/v5/orders")
    public Result ordersV5() {
        List<OrderQueryDto> orderQueryDtos = orderQueryRepository.findAllByDto_opmization();
        return new Result(orderQueryDtos);
    }

    @GetMapping("/api/v6/orders")
    public Result ordersV6() {
        List<OrderFlatDto> flatDtos = orderQueryRepository.findAllByDto_flat();
        List<OrderQueryDto> collect = flatDtos.stream()
                .collect(groupingBy(o -> new OrderQueryDto(o.getOrderId(),
                                o.getName(), o.getOrderDate(), o.getOrderStatus(), o.getAddress()),
                        mapping(o -> new OrderItemQueryDto(o.getOrderId(),
                                o.getItemName(), o.getOrderPrice(), o.getCount()), toList())
                )).entrySet().stream()
                .map(e -> new OrderQueryDto(e.getKey().getOrderId(),
                        e.getKey().getName(), e.getKey().getOrderDate(), e.getKey().getOrderStatus(),
                        e.getKey().getAddress(), e.getValue()))
                .collect(toList());
        return new Result(collect);
    }

    /**
     * OSIV 테스트(OSIV = false 일때, LAZY 로딩으로 인한 [could not initialize proxy [jpabook.jpashop.domain.Member#1] - no Session] 에러 발생)
     */
    @GetMapping("/api/osiv1")
    public List<Order> ordersOsiv() {
        List<Order> all = orderRepository.osivTest();
        for (Order order : all) {
            order.getMember().getName();        //  LAZY 강제초기화(Member Proxy 초기화)
            order.getDelivery().getAddress();   //  LAZY 강제초기화(Delivery Proxy 초기화)
            List<OrderItem> orderItems = order.getOrderItems();         //  LAZY 강제초기화(OrderItem Proxy 초기화)
            orderItems.stream().forEach(o -> o.getItem().getName());    //  LAZY 강제초기화(Item Proxy 초기화)
        }
        return all;
    }

    private final OrderQueryService orderQueryService;

    /**
     * OSIV 테스트(OSIV = false 일때, Service에서 데이터를 모두 초기화)
     */
    @GetMapping("/api/osiv2")
    public List<Order> ordersOsiv2() {
        return orderQueryService.osivTest2();
    }
}
