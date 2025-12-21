package com.tradenova.user.repository;

import com.tradenova.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * User 엔티티용 JPA Repository.
 * 기본 CRUD + 이메일로 조회 메서드 제공.
 */
public interface UserRepository extends JpaRepository<User, Long> {
    /**
     * 이메일로 유저를 찾는다.
     * - 회원가입 시 중복 체크
     * - 로그인 시 유저 조회
     */
    Optional<User> findByEmail(String email);

    /**
     * 이메일 중복 여부만 빠르게 확인하고 싶을 때 사용.
     */
    boolean existsByEmail(String email);

    //닉네임 중복 체크
    boolean existsByNickname(String nickname);
}
