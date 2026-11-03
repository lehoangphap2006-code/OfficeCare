package com.example.quanlytaisan.config;

import com.example.quanlytaisan.entity.AppUser;
import com.example.quanlytaisan.repository.AppUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class SecurityDemoUserInitializer {
    @Bean
    CommandLineRunner initializeDemoUsers(
            AppUserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.security.seed-demo-users}") boolean seedDemoUsers,
            @Value("${app.security.demo-admin-password}") String adminPassword,
            @Value("${app.security.demo-user-password}") String userPassword) {
        return args -> {
            if (!seedDemoUsers) {
                return;
            }
            createUserIfMissing(userRepository, passwordEncoder, "admin", adminPassword, "ADMIN");
            createUserIfMissing(userRepository, passwordEncoder, "user", userPassword, "USER");
        };
    }

    private void createUserIfMissing(
            AppUserRepository repository,
            PasswordEncoder passwordEncoder,
            String username,
            String password,
            String role) {
        if (password.length() < 8) {
            throw new IllegalStateException("Mật khẩu tài khoản kiểm thử phải có ít nhất 8 ký tự");
        }
        if (repository.existsByUsernameIgnoreCase(username)) {
            return;
        }
        AppUser user = new AppUser();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRole(role);
        repository.save(user);
    }
}
