package com.digitaleye.backend.service;

import com.digitaleye.backend.dto.AuthResponse;
import com.digitaleye.backend.dto.LoginRequest;
import com.digitaleye.backend.dto.RegisterRequest;
import com.digitaleye.backend.security.JwtUtil;
import com.digitaleye.database.dao.UserDao;
import com.digitaleye.database.model.User;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.sql.SQLException;

/**
 * Holds the actual login/registration logic so AuthController stays thin.
 * This is also where a password gets hashed (on register) or where the
 * AuthenticationManager checks a raw password against the stored hash
 * (on login) - the database layer itself never touches raw passwords.
 */
@Service
public class AuthService {

    private final UserDao userDao = new UserDao();
    private final AuthenticationManager authenticationManager;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthService(AuthenticationManager authenticationManager,
                        PasswordEncoder passwordEncoder,
                        JwtUtil jwtUtil) {
        this.authenticationManager = authenticationManager;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );

        try {
            User user = userDao.findByUsername(request.getUsername());
            String token = jwtUtil.generateToken(user.getUsername(), user.getRole());
            return new AuthResponse(token, user.getUsername(), user.getRole());
        } catch (SQLException e) {
            throw new RuntimeException("Database error during login", e);
        }
    }

    public AuthResponse register(RegisterRequest request) {
        try {
            if (userDao.findByUsername(request.getUsername()) != null) {
                throw new IllegalArgumentException("Username already taken: " + request.getUsername());
            }

            String hashedPassword = passwordEncoder.encode(request.getPassword());

            User user = new User(
                    request.getUsername(),
                    hashedPassword,
                    request.getFullName(),
                    request.getEmail(),
                    request.getRole() == null ? "USER" : request.getRole(),
                    request.getJobTitle()
            );

            userDao.insert(user);

            String token = jwtUtil.generateToken(user.getUsername(), user.getRole());
            return new AuthResponse(token, user.getUsername(), user.getRole());
        } catch (SQLException e) {
            throw new RuntimeException("Database error during registration", e);
        }
    }
}
