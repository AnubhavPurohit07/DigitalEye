package com.digitaleye.backend.security;

import com.digitaleye.database.dao.UserDao;
import com.digitaleye.database.model.User;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.util.List;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserDao userDao = new UserDao();

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        try {
            User user = userDao.findByUsername(username);
            if (user == null) {
                throw new UsernameNotFoundException("No user found with username: " + username);
            }

            return org.springframework.security.core.userdetails.User
                    .withUsername(user.getUsername())
                    .password(user.getPasswordHash())
                    .authorities(List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole())))
                    .build();
        } catch (SQLException e) {
            throw new RuntimeException("Database error while loading user", e);
        }
    }
}
