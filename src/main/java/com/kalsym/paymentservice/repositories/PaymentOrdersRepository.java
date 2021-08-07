package com.kalsym.paymentservice.repositories;

import com.kalsym.paymentservice.models.daos.PaymentOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 *
 * @author Sarosh
 */
@Repository
public interface PaymentOrdersRepository extends JpaRepository<PaymentOrder, String> {
    
    public PaymentOrder findBySpIdAndSpOrderId(Integer spId, String spOrderId );
    
    public PaymentOrder findBySystemTransactionId(String SystemTransactionId );
    public PaymentOrder findByClientTransactionIdAndStatus(String transactionId, String status );
    public PaymentOrder findByClientTransactionId(String transactionId);
}
