package com.kalsym.paymentservice.filters;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kalsym.paymentservice.utils.DateTimeUtil;
import com.kalsym.paymentservice.utils.LogUtil;
import com.kalsym.paymentservice.VersionHolder;
import com.kalsym.paymentservice.security.model.Auth;
import com.kalsym.paymentservice.security.model.MySQLUserDetails;
import com.kalsym.paymentservice.models.HttpReponse;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Optional;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 *
 * @author Sarosh
 */
@Component
public class SessionRequestFilter extends OncePerRequestFilter {
    
    @Autowired
    RestTemplate restTemplate;

    @Value("${services.user-service.session_details:not-known}")
    String userServiceSessionDetailsUrl;
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String logprefix = request.getRequestURI() + " ";
        String location = Thread.currentThread().getStackTrace()[1].getMethodName();
        LogUtil.info("", "", "----------" + logprefix + "----------", "");

        final String authHeader = request.getHeader("Authorization");

        String accessToken = null;

        // Token is in the form "Bearer token". Remove Bearer word and get only the Token
        if (null != authHeader && authHeader.startsWith("Bearer ")) {
            accessToken = authHeader.replace("Bearer ", "");
            LogUtil.info(VersionHolder.VERSION, location, "token: " + accessToken, "");
            LogUtil.info(VersionHolder.VERSION, location, "token length: " + accessToken.length(), "");            
        } else {
            LogUtil.warn(VersionHolder.VERSION, location, "token does not begin with Bearer String", "");
        }

        if (accessToken != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            //Logger.application.info(Logger.pattern, VersionHolder.VERSION, logprefix, "sessionId: " + sessionId, "");
            ResponseEntity<HttpReponse> authResponse = null;
            try {
                authResponse = restTemplate.postForEntity(userServiceSessionDetailsUrl, accessToken, HttpReponse.class);
            
                Date expiryTime = null;

                Auth auth = null;
                String username = null;

                if (authResponse.getStatusCode() == HttpStatus.ACCEPTED) {
                    ObjectMapper mapper = new ObjectMapper();
                    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);//                    LogUtil.info(VersionHolder.VERSION, location, "data: " + authResponse.getBody().getData(), "");

                    auth = mapper.convertValue(authResponse.getBody().getData(), Auth.class);
                    username = auth.getSession().getUsername();
                    expiryTime = auth.getSession().getExpiry();
                }

                if (null != expiryTime && null != username) {
                    long diff = 0;
                    try {
                        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                        Date currentTime = sdf.parse(DateTimeUtil.currentTimestamp());
                        diff = expiryTime.getTime() - currentTime.getTime();
                    } catch (ParseException e) {
                        LogUtil.warn(VersionHolder.VERSION, location, "error calculating time to session expiry", "");
                    }
                    LogUtil.info(VersionHolder.VERSION, location, "time to session expiry: " + diff + "ms", "");
                    if (0 < diff) {
                        MySQLUserDetails userDetails = new MySQLUserDetails(auth, auth.getAuthorities());

                        UsernamePasswordAuthenticationToken usernamePasswordAuthenticationToken = new UsernamePasswordAuthenticationToken(
                                userDetails, null, userDetails.getAuthorities());
                        usernamePasswordAuthenticationToken
                                .setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        LogUtil.info(VersionHolder.VERSION, location, "isAuthentcated:"+usernamePasswordAuthenticationToken.isAuthenticated(),"");
                        SecurityContextHolder.getContext().setAuthentication(usernamePasswordAuthenticationToken);
                    } else {
                        LogUtil.warn(VersionHolder.VERSION, location, "session expired", "");
                        //response.setStatus(HttpStatus.UNAUTHORIZED);
                        response.getWriter().append("Session expired");
                    }
                } else {
                    LogUtil.error(VersionHolder.VERSION, location, "Fail to validate token", "", null);
                }
            } catch (Exception ex) {
                LogUtil.warn(VersionHolder.VERSION, location, " Fail to validate token error : "+ex.getMessage(), "");
            }
        }
        chain.doFilter(request, response);
    }
}
