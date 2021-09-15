package jpabook.jpashop.repository.order.simplequery;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import java.util.List;

@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderSimpleQueryRepository {

    private final EntityManager em;
    /**
     * 1. 반환형이 DTO기 때문에 데이터를 변경해도 실제 DB에 반영되지 않는다.
     * 2. 재사용성이 떨어진다.
     * 3. 원하는 데이터가 가져오므로 DB, 어플리케이션 네트워크 용량 최적화
     *
     */
    public List<SimpleOrderQueryDto> findOrderDtos() {
        return em.createQuery("select new jpabook.jpashop.repository.order.simplequery.SimpleOrderQueryDto(o.id, m.name, o.orderDate, o.status, d.address)" +
                " from Order o join o.member m join o.delivery d", SimpleOrderQueryDto.class).getResultList();
    }
}
