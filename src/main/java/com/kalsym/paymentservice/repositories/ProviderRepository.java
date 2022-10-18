package com.kalsym.paymentservice.repositories;

import com.kalsym.paymentservice.models.daos.Provider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 *
 * @author Sarosh
 */
@Repository
public interface ProviderRepository extends JpaRepository<Provider, Integer> {

    Optional<Provider> findByIdAndRegionCountryId(Integer id , String regionCountryId );
    Optional<Provider> findByRegionCountryIdAndChannel(String regionCountryId , String channel );

}
