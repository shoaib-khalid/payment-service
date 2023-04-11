package com.kalsym.paymentservice.repositories;

import com.kalsym.paymentservice.models.daos.Store;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StoreRepository extends JpaRepository<Store, String> {
}
