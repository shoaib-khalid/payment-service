package com.kalsym.paymentservice.service;


import com.kalsym.paymentservice.security.model.MySQLUserDetails;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 *
 * @author Sarosh
 */
@Service
public class MySQLUserDetailsService implements UserDetailsService {

    @Autowired
    RestTemplate restTemplate;

    @Value("${services.user-service.session_details:not-known}")
    String userServiceUrl;

    @Override
    public MySQLUserDetails loadUserByUsername(String username) throws UsernameNotFoundException {

        MySQLUserDetails mud = new MySQLUserDetails(username);

        return mud;
    }

}
