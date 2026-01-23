package com.tradenova.training.repository;

import com.tradenova.training.entity.TrainingTrade;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TrainingTradeRepository extends JpaRepository<TrainingTrade, Long> {
    //특정 훈련 세션(sessionId)에 속한 모든 매매 기록을, id 오름차순으로 조회한다.
    List<TrainingTrade> findAllBySessionIdOrderByIdAsc(Long sessionId);
    //특정 세션의 거래를 id 내림차순으로 가져온다.
    List<TrainingTrade> findAllBySessionIdOrderByIdDesc(Long sessionId);
    //특정 세션에 거래가 몇 건 있는지 숫자만 가져온다.
    long countBySessionId(Long sessionId);
}