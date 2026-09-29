package com.tradenova.waitlist.repository;

import com.tradenova.waitlist.entity.BetaWaitlist;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BetaWaitlistRepository extends JpaRepository<BetaWaitlist, Long> {
    boolean existsByEmailIgnoreCase(String email);
}
