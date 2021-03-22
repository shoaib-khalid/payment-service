package com.kalsym.paymentservice.security.model;

import com.kalsym.paymentservice.VersionHolder;
import com.kalsym.paymentservice.utils.LogUtil;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 *
 * @author Sarosh
 */
public class MySQLUserDetails implements UserDetails {

    private String userName;
    private String password;
    private boolean locked;
    private boolean expired;
    private String role;

    private List<GrantedAuthority> grantedAuthorities;

    public MySQLUserDetails() {
    }

    public MySQLUserDetails(String username) {
        this.userName = username;
    }

    public MySQLUserDetails(Auth auth, List<String> auths) {
        this.userName = auth.getSession().getUsername();
        this.password = auth.getRole();
        this.locked = false;
        LogUtil.info(VersionHolder.VERSION, "MySQLUserDetails", "user:"+auth, "");
        this.expired = false;
        this.role = auth.getRole();
        grantedAuthorities = new ArrayList<>();

        auths.stream().forEach((userAuth) -> {
            grantedAuthorities.add(new SimpleGrantedAuthority(userAuth));
        });
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return this.grantedAuthorities;
    }

    @Override
    public String getPassword() {
        return this.password;
    }

    @Override
    public String getUsername() {
        return this.userName;
    }

    @Override
    public boolean isAccountNonExpired() {
        return !this.expired;
    }

    @Override
    public boolean isAccountNonLocked() {
        return !this.locked;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return !this.locked;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

}
