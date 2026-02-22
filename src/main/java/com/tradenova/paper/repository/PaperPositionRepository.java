package com.tradenova.paper.repository;

import com.tradenova.paper.entity.PaperAccount;
import com.tradenova.paper.entity.PaperPosition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaperPositionRepository extends JpaRepository<PaperPosition, Long> {

    //이 계좌가 현재 보유 중인 모든 종목 포지션을 가져오기
    List<PaperPosition> findAllByAccountId(Long accountId);

    //Id 심볼ID 조회 / 이 계좌에 이 종목 포지션이 이미 있는지 확인하기
    Optional<PaperPosition> findByAccountIdAndSymbolId(Long accountId, Long symbolId);

    //이 계좌의 모든 포지션을 전부 삭제해라
    void deleteAllByAccountId(Long accountId);

}
