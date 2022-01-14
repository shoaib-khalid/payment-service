package com.kalsym.paymentservice.models.daos;


import com.fasterxml.jackson.annotation.JsonFormat;
import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.*;

import javax.persistence.*;
import javax.persistence.Entity;
import javax.persistence.Table;
import java.util.Date;

@Entity
@Getter
@Setter
@ToString
@Table(name = "order")
public class Order implements Serializable {

    @Id
    @GeneratedValue(generator = "uuid2")
    @GenericGenerator(name = "uuid2", strategy = "org.hibernate.id.UUIDGenerator")
    private String id;


    private Float klCommission;
    private Float deliveryCharges;
    private Float storeServiceCharges;
    private Float appliedDiscount;
    private Float deliveryDiscount;


    private Float subTotal;
    private Float total;
    private Float storeShare;
    private String completionStatus;
    private String paymentStatus;
    private String customerNotes;
    private String privateAdminNotes;
    private String cartId;
    private String deliveryType;
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customerId" )
    @NotFound(action = NotFoundAction.IGNORE)
    private Customer customer;

    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "storeId", insertable = false, updatable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Store store;

    @Temporal(TemporalType.TIMESTAMP)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private Date created;
}