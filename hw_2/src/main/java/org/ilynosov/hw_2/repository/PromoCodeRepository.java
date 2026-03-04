package org.ilynosov.hw_2.repository;

import org.ilynosov.hw_2.entity.PromoCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PromoCodeRepository extends JpaRepository<PromoCode, UUID> {

    Optional<PromoCode> findByCode(String code);
}