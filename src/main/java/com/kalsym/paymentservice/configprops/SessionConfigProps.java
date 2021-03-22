package com.kalsym.paymentservice.configprops;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;


/**
 *
 * @author Sarosh
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "session")
public class SessionConfigProps {

    @NotNull
    @Min(600)
    private long expiry;
    
    @NotNull
    private String key;

}
