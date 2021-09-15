package jpabook.jpashop.api;

import jpabook.jpashop.domain.Address;
import jpabook.jpashop.domain.Order;
import jpabook.jpashop.domain.OrderStatus;
import jpabook.jpashop.repository.OrderRepository;
import jpabook.jpashop.repository.OrderSearch;
import jpabook.jpashop.repository.order.simplequery.OrderSimpleQueryRepository;
import jpabook.jpashop.repository.order.simplequery.SimpleOrderQueryDto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * X to One(Many to One, One to One) 성능 최적화
 */
@RestController
@RequiredArgsConstructor
public class OrderSImpleApiController {

    private final OrderRepository orderRepository;
    private final OrderSimpleQueryRepository orderSimpleQueryRepository;

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
    @GetMapping("/api/v1/simple-orders")
    public List<Order> ordersV1() {
        List<Order> all = orderRepository.findAllByString(new OrderSearch());

        for (Order order : all) {
            order.getMember().getName();        //  LAZY 초기화(Member Proxy 초기화)
            order.getDelivery().getAddress();   //  LAZY 초기화(Delivery Proxy 초기화)
        }
        return all;
    }

    /**
     * ordersV2
     * 문제점
     * 1. LAZY 로딩으로 인한 데이터베이스 쿼리가 너무 많이 호출 되는 문제가 있다.(성능상 이슈가 발생) -> ordersV1의 5번 문제와 같음
     * Order - Member ( N : 1 )
     * Order - Delivery ( 1 : 1 )
     * 의 관계 이므로 Order를 가져올때 Member와 Delivery를 가져와야 한다.
     * 결국 1개의 Order 쿼리 후 Member, Delivery를 가져오는 쿼리가 추가 실행되어야 하므로 총 3번의 쿼리가 나가게 된다.
     * 만약 100개의 Order를 가져온다면? 1 + 100 + 100 = 201번의 쿼리가 실행 될것이다.
     * 쿼리가 1 + N + N 번이 실행된 셈이다.
     *
     */

    @GetMapping("/api/v2/simple-orders")
    public Result ordersV2() {
        List<Order> orders = orderRepository.findAllByString(new OrderSearch());
        List<SimpleOrderDto> collect = orders.stream().map(m -> new SimpleOrderDto(m)).collect(Collectors.toList());

        return new Result(collect);
    }

    @Data
    static class SimpleOrderDto {
        private Long orderId;
        private String name;
        private LocalDateTime orderDate;
        private OrderStatus orderStatus;
        private Address address;

        public SimpleOrderDto(Order order) {
            this.orderId = order.getId();
            this.name = order.getMember().getName();    //  LAZY 초기화
            this.orderDate = order.getOrderDate();
            this.orderStatus = order.getStatus();
            this.address = order.getDelivery().getAddress();    //  LAZY 초기화
        }
    }

    @Data
    @AllArgsConstructor
    static class Result<T> {
        private T data;
    }

    @GetMapping("/api/v3/simple-orders")
    public Result ordersV3() {
        List<Order> orders = orderRepository.findAllWithMemberDelivery();
        List<SimpleOrderDto> collect = orders.stream().map(m -> new SimpleOrderDto(m)).collect(Collectors.toList());

        return new Result(collect);
    }

    @GetMapping("/api/v4/simple-orders")
    public Result ordersV4() {
        List<SimpleOrderQueryDto> collect = orderSimpleQueryRepository.findOrderDtos();
        return new Result(collect);
    }

}
