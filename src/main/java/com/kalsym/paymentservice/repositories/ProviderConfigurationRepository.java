package com.kalsym.paymentservice.repositories;

import com.kalsym.paymentservice.models.daos.ProviderConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 *
 * @author Sarosh
 */
@Repository
public interface ProviderConfigurationRepository extends JpaRepository<ProviderConfiguration, String> {

    public List<ProviderConfiguration> findByIdSpId(Integer spId);
}
