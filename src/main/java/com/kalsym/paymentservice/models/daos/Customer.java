package com.kalsym.paymentservice.models.daos;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.io.Serializable;
import java.util.Objects;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.GenericGenerator;

/**
 *
 * @author Sarosh
 */
@Entity
@Table(name = "customers")
@Getter
@Setter
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Customer implements Serializable {
    
    @NotBlank(message = "name is required")
    private String name;
    
    @NotBlank(message = "city is required")
    private String city;

    @NotBlank(message = "mobileNumber is required")
    private String mobileNumber;

    @NotBlank(message = "address is required")
    private String address;

    @NotBlank(message = "zipcode is required")
    private String zipcode;

    @Id
    @GeneratedValue(generator = "uuid2")
    @GenericGenerator(name = "uuid2", strategy = "org.hibernate.id.UUIDGenerator")
    private String userId;

    public void updateCustomer(Customer seller) {
        
        if (null != seller.getName()) {
            name = seller.getName();
        }
        
        if (null != seller.getCity()) {
            city = seller.getCity();
        }

        if (null != seller.getMobileNumber()) {
            mobileNumber = seller.getMobileNumber();
        }

        if (null != seller.getZipcode()) {
            zipcode = seller.getZipcode();
        }

        if (null != seller.getUserId()) {
            userId = seller.getUserId();
        }

    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final Customer other = (Customer) obj;
        if (!Objects.equals(this.userId, other.userId)) {
            return false;
        }
        return true;
    }

}
