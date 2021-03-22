package com.kalsym.paymentservice.repositories;

import com.kalsym.paymentservice.models.daos.ProviderRatePlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 *
 * @author Sarosh
 */
@Repository
public interface ProviderRatePlanRepository extends JpaRepository<ProviderRatePlan, String> {

    public List<ProviderRatePlan> findByIdProductCode(String productCode);
}
