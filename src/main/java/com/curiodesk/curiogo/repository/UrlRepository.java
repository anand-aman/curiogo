package com.curiodesk.curiogo.repository;

import com.curiodesk.curiogo.domain.Url;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface UrlRepository extends JpaRepository<Url, Long> {
    Optional<Url> findByShortCode(String shortCode);

    boolean existsByShortCode(String shortCode);

    @Modifying
    @Query("update Url u set u.clickCount = u.clickCount + :delta where u.shortCode = :code")
    int addClicks(@Param("code") String code, @Param("delta") long delta);

    int deleteByExpiresAtBefore(Instant expiresAt);
}
