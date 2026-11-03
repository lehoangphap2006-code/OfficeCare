package com.example.quanlytaisan.service;

import com.example.quanlytaisan.dto.CurrentUserResponse;
import com.example.quanlytaisan.dto.AuthRegisterRequest;
import com.example.quanlytaisan.dto.ChangePasswordRequest;
import com.example.quanlytaisan.dto.LoginRequest;
import com.example.quanlytaisan.dto.LoginResponse;
import com.example.quanlytaisan.dto.UserProfileRequest;
import com.example.quanlytaisan.dto.UserProfileResponse;
import com.example.quanlytaisan.entity.AppUser;
import com.example.quanlytaisan.entity.AdminMasterData;
import com.example.quanlytaisan.entity.RevokedToken;
import com.example.quanlytaisan.repository.AdminMasterDataRepository;
import com.example.quanlytaisan.repository.AppUserRepository;
import com.example.quanlytaisan.repository.RevokedTokenRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class AuthService {
    private static final String INVALID_LOGIN_MESSAGE = "Thông tin đăng nhập không hợp lệ hoặc tài khoản đang tạm khóa";

    private final AppUserRepository userRepository;
    private final AdminMasterDataRepository masterDataRepository;
    private final RevokedTokenRepository revokedTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final ActivityLogService activityLogService;
    private final int maxFailedAttempts;
    private final long lockMinutes;

    public AuthService(
            AppUserRepository userRepository,
            AdminMasterDataRepository masterDataRepository,
            RevokedTokenRepository revokedTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            ActivityLogService activityLogService,
            @Value("${app.security.login.max-failed-attempts}") int maxFailedAttempts,
            @Value("${app.security.login.lock-minutes}") long lockMinutes) {
        this.userRepository = userRepository;
        this.masterDataRepository = masterDataRepository;
        this.revokedTokenRepository = revokedTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.activityLogService = activityLogService;
        this.maxFailedAttempts = maxFailedAttempts;
        this.lockMinutes = lockMinutes;
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public LoginResponse login(LoginRequest request) {
        if (request == null || request.username() == null || request.password() == null
                || request.username().isBlank() || request.password().isBlank()) {
            throw invalidLogin();
        }

        AppUser user = userRepository.findForLogin(request.username().trim())
                .orElseThrow(AuthService::invalidLogin);
        LocalDateTime now = LocalDateTime.now();
        if (!user.isEnabled() || (user.getLockedUntil() != null && user.getLockedUntil().isAfter(now))) {
            throw invalidLogin();
        }
        if (user.getLockedUntil() != null) {
            user.setLockedUntil(null);
            user.setFailedLoginAttempts(0);
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            int attempts = user.getFailedLoginAttempts() + 1;
            user.setFailedLoginAttempts(attempts);
            if (attempts >= maxFailedAttempts) {
                user.setLockedUntil(now.plusMinutes(lockMinutes));
            }
            userRepository.save(user);
            throw invalidLogin();
        }

        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);
        return createLoginResponse(user);
    }

    @Transactional
    public LoginResponse refresh(String token) {
        Claims claims;
        try {
            claims = jwtService.parse(token);
        } catch (JwtException | IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Phiên đăng nhập đã hết hạn hoặc không hợp lệ");
        }
        if (!"refresh".equals(claims.get("token_type", String.class))
                || claims.getId() == null || claims.getSubject() == null
                || revokedTokenRepository.existsById(claims.getId())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Phiên đăng nhập đã hết hạn hoặc không hợp lệ");
        }
        AppUser user = userRepository.findByUsernameIgnoreCase(claims.getSubject())
                .filter(AppUser::isEnabled)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Tài khoản không còn hoạt động"));
        revokeToken(claims);
        return createLoginResponse(user);
    }

    @Transactional
    public LoginResponse register(AuthRegisterRequest request) {
        if (request == null || request.username() == null || request.username().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tên đăng nhập không được để trống");
        }
        String username = request.username().trim();
        if (username.length() > 50 || username.chars().anyMatch(Character::isWhitespace)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Tên đăng nhập tối đa 50 ký tự và không chứa khoảng trắng");
        }
        if (request.password() == null || request.password().length() < 8) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mật khẩu phải có ít nhất 8 ký tự");
        }
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Tên đăng nhập đã tồn tại");
        }

        activityLogService.clearNotificationsForUser(username);
        AppUser user = new AppUser();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole("USER");
        user.setEnabled(true);
        userRepository.save(user);
        activityLogService.record(username, "REGISTER", "AppUser", user.getUserId(), null,
                java.util.Map.of("username", username, "role", "USER", "enabled", true),
                "Tài khoản " + username + " đã được đăng ký.");
        return createLoginResponse(user);
    }

    @Transactional
    public LoginResponse loginWithGoogle(String subject, String email, String fullName) {
        if (subject == null || subject.isBlank() || email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Tài khoản Google không hợp lệ");
        }
        AppUser user = userRepository.findByGoogleSubject(subject).orElseGet(() -> {
            String username = createGoogleUsername(email);
            AppUser created = new AppUser();
            created.setUsername(username);
            created.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
            created.setGoogleSubject(subject);
            created.setEmail(email.trim());
            created.setFullName(fullName == null || fullName.isBlank()
                    ? email.trim()
                    : fullName.trim().substring(0, Math.min(fullName.trim().length(), 150)));
            created.setRole("USER");
            created.setEnabled(true);
            AppUser saved = userRepository.save(created);
            activityLogService.record(username, "GOOGLE_REGISTER", "AppUser", saved.getUserId(),
                    null, java.util.Map.of("username", username, "role", "USER", "provider", "Google"),
                    "Tài khoản nhân viên đã được tạo bằng Google.");
            return saved;
        });
        if (!user.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tài khoản đã bị vô hiệu hóa");
        }
        activityLogService.record(user.getUsername(), "GOOGLE_LOGIN", "AppUser", user.getUserId(),
                null, java.util.Map.of("provider", "Google"), null);
        return createLoginResponse(user);
    }

    @Transactional
    public void changePassword(String username, ChangePasswordRequest request) {
        if (request == null || request.currentPassword() == null || request.newPassword() == null
                || request.newPassword().length() < 8) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mật khẩu mới phải có ít nhất 8 ký tự");
        }
        AppUser user = userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Phiên đăng nhập không hợp lệ"));
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mật khẩu hiện tại không chính xác");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);
        activityLogService.record(username, "CHANGE_PASSWORD", "AppUser", user.getUserId(),
                java.util.Map.of("password", "[đã lưu]"), java.util.Map.of("password", "[đã đổi]"),
                "Mật khẩu tài khoản đã được thay đổi.");
    }


    public CurrentUserResponse currentUser(String username) {
        AppUser user = userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Phiên đăng nhập không hợp lệ"));
        return new CurrentUserResponse(user.getUsername(), user.getRole());
    }

    @Transactional(readOnly = true)
    public UserProfileResponse profile(String username) {
        AppUser user = userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Phiên đăng nhập không hợp lệ"));
        return profileResponse(user);
    }

    @Transactional
    public UserProfileResponse updateProfile(String username, UserProfileRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Thông tin cá nhân không hợp lệ");
        }
        String email = request.email() == null || request.email().isBlank() ? null : request.email().trim();
        String phone = request.phoneNumber() == null || request.phoneNumber().isBlank() ? null : request.phoneNumber().trim();
        if (email != null && (email.length() > 150 || !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email không hợp lệ");
        }
        if (phone != null && (phone.length() > 30 || !phone.matches("^[+0-9() .-]{6,30}$"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Số điện thoại không hợp lệ");
        }
        AppUser user = userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Phiên đăng nhập không hợp lệ"));
        UserProfileResponse previous = profileResponse(user);
        user.setEmail(email);
        user.setPhoneNumber(phone);
        userRepository.save(user);
        UserProfileResponse updated = profileResponse(user);
        activityLogService.record(username, "UPDATE_PROFILE", "AppUser", user.getUserId(), previous, updated, null);
        return updated;
    }

    private UserProfileResponse profileResponse(AppUser user) {
        var employee = user.getEmployee();
        String departmentName = employee == null || employee.getDepartmentId() == null
                ? null
                : masterDataRepository.findById(employee.getDepartmentId())
                        .filter(row -> "DEPARTMENT".equals(row.getKind()))
                        .map(AdminMasterData::getName)
                        .orElse(null);
        return new UserProfileResponse(
                user.getUsername(),
                user.getRole(),
                user.getEmail(),
                user.getPhoneNumber(),
                employee == null ? user.getFullName() : employee.getFullName(),
                departmentName,
                employee == null ? null : employee.getTitle());
    }

    @Transactional
    public void logout(String token, String refreshToken) {
        revokeToken(jwtService.parse(token));
        if (refreshToken != null && !refreshToken.isBlank()) {
            try {
                revokeToken(jwtService.parse(refreshToken));
            } catch (JwtException | IllegalArgumentException exception) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token không hợp lệ");
            }
        }
    }

    private void revokeToken(Claims claims) {
        if (claims.getId() == null || claims.getExpiration() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token không hợp lệ");
        }
        if (!revokedTokenRepository.existsById(claims.getId())) {
            revokedTokenRepository.save(new RevokedToken(
                    claims.getId(), LocalDateTime.ofInstant(claims.getExpiration().toInstant(), java.time.ZoneId.systemDefault())));
        }
    }

    private LoginResponse createLoginResponse(AppUser user) {
        return new LoginResponse(
                jwtService.createToken(user),
                jwtService.createRefreshToken(user),
                "Bearer",
                jwtService.getExpirationSeconds(),
                user.getUsername(),
                user.getRole());
    }

    private String createGoogleUsername(String email) {
        String localPart = email.substring(0, email.indexOf('@'))
                .replaceAll("[^A-Za-z0-9._-]", "_");
        if (localPart.isBlank()) localPart = "google-user";
        localPart = localPart.substring(0, Math.min(localPart.length(), 38));
        String username;
        do {
            username = localPart + "_" + UUID.randomUUID().toString().substring(0, 8);
        } while (userRepository.existsByUsernameIgnoreCase(username));
        return username;
    }

    private static ResponseStatusException invalidLogin() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, INVALID_LOGIN_MESSAGE);
    }
}
