package com.aicit.repository;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fetches the next value from a Postgres sequence atomically.
 *
 * <p>Using {@code nextval()} guarantees each caller gets a unique, monotonically
 * increasing number even under concurrent requests — eliminating the race in the
 * old "read max, then +1" certificate/batch numbering. Works on PostgreSQL and
 * H2 (PostgreSQL mode) alike.
 */
@Repository
@RequiredArgsConstructor
public class SequenceRepository {

    private final EntityManager em;

    /**
     * REQUIRES_NEW so the sequence advance is independent of the caller's
     * transaction (sequence values are never rolled back anyway).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public long nextVal(String sequenceName) {
        // Sequence name is NOT user input — callers pass compile-time constants.
        Object result = em.createNativeQuery("SELECT nextval('" + sequenceName + "')")
                .getSingleResult();
        return ((Number) result).longValue();
    }
}
