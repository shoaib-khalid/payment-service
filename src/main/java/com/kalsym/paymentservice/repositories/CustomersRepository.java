package com.kalsym.paymentservice.repositories;

import com.kalsym.paymentservice.models.daos.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 *
 * @author Sarosh
 */
@Repository
public interface CustomersRepository extends JpaRepository<Customer, String> {

}
