package com.dhruv.incident_copilot.config;

import com.dhruv.incident_copilot.entity.Role;
import com.dhruv.incident_copilot.entity.User;
import com.dhruv.incident_copilot.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AdminSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminEmail;
    private final String adminPassword;

    public AdminSeeder(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.admin.email}") String adminEmail,
            @Value("${app.admin.password}") String adminPassword) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(String... args) {
        if (userRepository.findByEmail(adminEmail).isPresent()) {
            return;
        }

        User admin = new User();
        admin.setEmail(adminEmail);
        admin.setPasswordHash(passwordEncoder.encode(adminPassword));
        admin.setRole(Role.ADMIN);
        userRepository.save(admin);
    }
}
