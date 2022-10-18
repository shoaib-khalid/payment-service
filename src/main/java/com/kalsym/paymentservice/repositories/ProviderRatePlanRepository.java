package com.kalsym.paymentservice.repositories;

import com.kalsym.paymentservice.models.daos.Provider;
import com.kalsym.paymentservice.models.daos.ProviderRatePlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * @author Sarosh
 */
@Repository
public interface ProviderRatePlanRepository extends JpaRepository<ProviderRatePlan, String> {

    public List<ProviderRatePlan> findByIdProductCode(String productCode);

    @Query(value = "Select psrp.* From symplified.payment_sp_rate_plan psrp left join payment_sp ps on ps.id = psrp.spId where ps.regionCountryId = :regionCountryId ", nativeQuery = true)
    public List<ProviderRatePlan> findByIdProductCodeAndProvider(@Param("regionCountryId") String regionCountryId);

    @Query(value = "Select psrp.* From symplified.payment_sp_rate_plan psrp left join payment_sp ps on ps.id = psrp.spId where ps.regionCountryId = :regionCountryId and ps.channel = :channel ", nativeQuery = true)
    public List<ProviderRatePlan> findByIdProductCodeAndProviderAndChannel(@Param("regionCountryId") String regionCountryId, @Param("channel") String channel);
}
