package com.dhruv.incident_copilot.service;

import com.dhruv.incident_copilot.dto.AuthResponse;
import com.dhruv.incident_copilot.dto.LoginRequest;
import com.dhruv.incident_copilot.dto.RegisterRequest;
import com.dhruv.incident_copilot.dto.UserResponse;
import com.dhruv.incident_copilot.entity.Role;
import com.dhruv.incident_copilot.entity.User;
import com.dhruv.incident_copilot.exception.DuplicateResourceException;
import com.dhruv.incident_copilot.repository.UserRepository;
import com.dhruv.incident_copilot.security.JwtService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;
    private final JwtService jwtService;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            UserDetailsService userDetailsService,
            JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.userDetailsService = userDetailsService;
        this.jwtService = jwtService;
    }

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateResourceException("An account with this email already exists");
        }

        User user = new User();
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(Role.USER);
        userRepository.save(user);

        return new AuthResponse(jwtService.generateToken(userDetailsService.loadUserByUsername(user.getEmail())));
    }

    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password()));

        return new AuthResponse(jwtService.generateToken(userDetailsService.loadUserByUsername(request.email())));
    }

    public UserResponse me(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + email));
        return new UserResponse(user.getId(), user.getEmail(), user.getRole());
    }
}
