package com.tradenova.paper.repository;

import com.tradenova.paper.entity.PaperAccount;
import com.tradenova.paper.entity.PaperPosition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaperAccountRepository extends JpaRepository<PaperAccount, Long> {

    //이 유저가 가진 모든 계좌를 최신 계좌부터 가져오기
    List<PaperAccount> findAllByUserIdOrderByIdDesc(Long userId);
    
    //이 계좌 ID가 정말 이 유저 소유가 맞는지 확인하면서 가져오기
    Optional<PaperAccount> findByIdAndUserId(Long id, Long userId);

    //이 유저의 기본 계좌를 하나 가져오기
    Optional<PaperAccount> findByUserIdAndIsDefaultTrue(Long userId);
    
    //계좌 갯수 세는 리포
    long countByUserId(Long userId);

}
