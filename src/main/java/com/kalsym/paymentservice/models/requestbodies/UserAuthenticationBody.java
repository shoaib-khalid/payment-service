package com.kalsym.paymentservice.models.requestbodies;

import javax.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 *
 * @author Sarosh
 */
@ToString
@Getter
@Setter
public class UserAuthenticationBody {

    @NotBlank(message = "username is required")
    private String username;

    @NotBlank(message = "password is required")
    private String password;
}
