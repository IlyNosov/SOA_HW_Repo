package org.ilynosov.hw_2.repository;

import org.ilynosov.hw_2.entity.OperationType;
import org.ilynosov.hw_2.entity.UserOperation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserOperationRepository extends JpaRepository<UserOperation, UUID> {

    Optional<UserOperation> findFirstByUserIdAndOperationTypeOrderByCreatedAtDesc(
            UUID userId,
            OperationType operationType
    );

    Optional<UserOperation> findTopByUserIdAndOperationTypeOrderByCreatedAtDesc(
            UUID userId,
            OperationType operationType
    );
}