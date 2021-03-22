package com.kalsym.paymentservice.repositories;

import com.kalsym.paymentservice.models.daos.Provider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 *
 * @author Sarosh
 */
@Repository
public interface ProviderRepository extends JpaRepository<Provider, Integer> {

}
