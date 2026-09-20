package com.tradenova.training.repository;

import com.tradenova.symbol.entity.Symbol;
import com.tradenova.training.entity.TrainingSession;
import com.tradenova.training.entity.TrainingStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Collection;
import java.util.Optional;

/**
 * TrainingSession 엔티티 전용 Repository
 * - 훈련 세션 조회 / 소유권 검증용
 */
public interface TrainingSessionRepository extends JpaRepository<TrainingSession, Long> {

    //특정 유저가 가진 모든 TrainingSession 조회 최신 세션이 먼저 오도록 id DESC
    List<TrainingSession> findAllByUserIdOrderByIdDesc(Long userId);

    /** Finished sessions only: unfinished blind charts remain in the training workspace. */
    List<TrainingSession> findAllByUserIdAndStatusOrderByIdDesc(Long userId, TrainingStatus status);

    @Query("select s from TrainingSession s join fetch s.user where s.user.id in :userIds and s.status = :status")
    List<TrainingSession> findAllForCommunityProfiles(@Param("userIds") Collection<Long> userIds,
                                                       @Param("status") TrainingStatus status);

    //특정 세션 ID가 해당 유저의 세션이 맞는지 확인하면서 조회
    Optional<TrainingSession> findByIdAndUserId(Long id, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from TrainingSession s where s.id = :sessionId and s.user.id = :userId")
    Optional<TrainingSession> findForUpdateByIdAndUserId(
            @Param("sessionId") Long sessionId,
            @Param("userId") Long userId
    );

    // 특정 유저의 "가장 최근 진행 중 세션 1개" 조회, 새로고침 후 이어하기 용도
    Optional<TrainingSession> findTopByUserIdAndStatusOrderByIdDesc(Long userId, TrainingStatus status);

}
