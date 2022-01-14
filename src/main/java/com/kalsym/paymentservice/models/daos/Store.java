package com.kalsym.paymentservice.models.daos;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;
import java.io.Serializable;

@Entity
@Getter
@Setter
@ToString
@Table(name = "store")
public class Store implements Serializable {

    @Id
    @GeneratedValue(generator = "uuid2")
    @GenericGenerator(name = "uuid2", strategy = "org.hibernate.id.UUIDGenerator")
    private String id;

    private String name;
    private String clientId;
    @JsonIgnore
    private String regionCountryStateId;

    public String getNameAbreviation() {
        String abbreviation = "";

        if (name.length() <= 3) {
            abbreviation = name;
        } else {
            String[] myName = name.split(" ");

            for (int i = 0; i < myName.length; i++) {
                String s = myName[i];
                abbreviation = abbreviation + s.charAt(0);

                if (abbreviation.length() == 3) {
                    break;
                }
            }
        }
        return abbreviation;
    }
}
