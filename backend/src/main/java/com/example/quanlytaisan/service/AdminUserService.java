package com.example.quanlytaisan.service;

import com.example.quanlytaisan.dto.AdminUserRequest;
import com.example.quanlytaisan.dto.AdminUserResponse;
import com.example.quanlytaisan.entity.AdminEmployee;
import com.example.quanlytaisan.entity.AppUser;
import com.example.quanlytaisan.repository.AdminEmployeeRepository;
import com.example.quanlytaisan.repository.AppUserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;

@Service
public class AdminUserService {
    private static final Set<String> ROLES = Set.of("ADMIN", "USER");

    private final AppUserRepository userRepository;
    private final AdminEmployeeRepository employeeRepository;
    private final PasswordEncoder passwordEncoder;
    private final ActivityLogService activityLogService;

    public AdminUserService(AppUserRepository userRepository, AdminEmployeeRepository employeeRepository,
            PasswordEncoder passwordEncoder, ActivityLogService activityLogService) {
        this.userRepository = userRepository;
        this.employeeRepository = employeeRepository;
        this.passwordEncoder = passwordEncoder;
        this.activityLogService = activityLogService;
    }

    @Transactional(readOnly = true)
    public List<AdminUserResponse> listUsers() {
        return userRepository.findAll(Sort.by(Sort.Direction.ASC, "userId")).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public AdminUserResponse createUser(AdminUserRequest request, String actor) {
        validate(request, null, false);
        AppUser user = new AppUser();
        user.setUsername(request.username().trim());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(request.role());
        user.setEnabled(request.enabled() == null || request.enabled());
        applyProfile(user, request, null);
        AppUser saved = userRepository.save(user);
        AdminUserResponse response = toResponse(saved);
        activityLogService.record(actor, "CREATE", "AppUser", saved.getUserId(), null, response,
                "Quản trị viên đã tạo tài khoản " + saved.getUsername() + ".");
        return response;
    }

    @Transactional
    public AdminUserResponse updateUser(Integer id, AdminUserRequest request, String actingUsername) {
        AppUser user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy người dùng"));
        validate(request, id, true);

        String username = request.username().trim();
        String role = request.role();
        boolean enabled = request.enabled() == null ? user.isEnabled() : request.enabled();
        boolean isSelf = user.getUsername().equalsIgnoreCase(actingUsername);
        if (isSelf && (!username.equalsIgnoreCase(user.getUsername()) || !enabled || !"ADMIN".equals(role))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Không thể tự đổi tên, vô hiệu hóa hoặc hạ quyền tài khoản đang đăng nhập");
        }
        if ("USER".equals(role) && !"USER".equals(user.getRole()) && request.employeeId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tài khoản nhân viên phải được liên kết với hồ sơ nhân viên");
        }
        ensureAdminRemains(user, role, enabled);

        String oldUsername = user.getUsername();
        AdminUserResponse previous = toResponse(user);
        user.setUsername(username);
        user.setRole(role);
        user.setEnabled(enabled);
        applyProfile(user, request, id);
        if (request.password() != null && !request.password().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(request.password()));
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
        }
        AdminUserResponse response = toResponse(userRepository.save(user));
        activityLogService.record(actingUsername, "UPDATE", "AppUser", id, previous, response,
                "Tài khoản " + username + " đã được cập nhật.");
        if (!oldUsername.equalsIgnoreCase(username)) {
            activityLogService.clearNotificationsForUser(oldUsername);
            activityLogService.clearNotificationsForUser(username);
        }
        return response;
    }

    @Transactional
    public void deleteUser(Integer id, String actingUsername) {
        AppUser user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy người dùng"));
        if (user.getUsername().equalsIgnoreCase(actingUsername)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Không thể xóa tài khoản đang đăng nhập");
        }
        ensureAdminRemains(user, null, false);
        AdminUserResponse previous = toResponse(user);
        userRepository.delete(user);
        activityLogService.record(actingUsername, "DELETE", "AppUser", id, previous, null,
                "Tài khoản " + previous.username() + " đã bị xóa.");
        activityLogService.clearNotificationsForUser(previous.username());
    }

    @Transactional
    public AdminUserResponse unlockUser(Integer id, String actingUsername) {
        AppUser user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy người dùng"));
        AdminUserResponse previous = toResponse(user);
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        AdminUserResponse response = toResponse(userRepository.save(user));
        activityLogService.record(actingUsername, "UNLOCK", "AppUser", id, previous, response,
                "Quản trị viên đã mở khóa tài khoản " + user.getUsername() + ".");
        return response;
    }

    private void applyProfile(AppUser user, AdminUserRequest request, Integer currentId) {
        AdminEmployee employee = null;
        if (request.employeeId() != null) {
            employee = employeeRepository.findById(request.employeeId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Không tìm thấy hồ sơ nhân viên"));
            boolean alreadyLinked = userRepository.findAll().stream()
                    .anyMatch(existing -> !existing.getUserId().equals(currentId)
                            && existing.getEmployee() != null
                            && existing.getEmployee().getId().equals(request.employeeId()));
            if (alreadyLinked) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Hồ sơ nhân viên đã được liên kết với tài khoản khác");
            }
        }
        user.setEmployee(employee);
        String fullName = employee == null ? request.fullName() : employee.getFullName();
        if (fullName == null && currentId != null) {
            fullName = user.getFullName();
        }
        if (fullName != null && fullName.length() > 150) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Họ tên tối đa 150 ký tự");
        }
        user.setFullName(fullName == null || fullName.isBlank() ? null : fullName.trim());
    }

    private void validate(AdminUserRequest request, Integer currentId, boolean update) {
        if (request == null || request.username() == null || request.username().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tên đăng nhập không được để trống");
        }
        String username = request.username().trim();
        if (username.length() > 50 || username.chars().anyMatch(Character::isWhitespace)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tên đăng nhập tối đa 50 ký tự và không chứa khoảng trắng");
        }
        boolean duplicate = currentId == null
                ? userRepository.existsByUsernameIgnoreCase(username)
                : userRepository.existsByUsernameIgnoreCaseAndUserIdNot(username, currentId);
        if (duplicate) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Tên đăng nhập đã tồn tại");
        }
        if (request.role() == null || !ROLES.contains(request.role())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vai trò phải là ADMIN hoặc USER");
        }
        if (!update && "USER".equals(request.role()) && request.employeeId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tài khoản nhân viên phải được liên kết với hồ sơ nhân viên");
        }
        if (request.fullName() != null && request.fullName().length() > 150) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Họ tên tối đa 150 ký tự");
        }
        if (!update && (request.password() == null || request.password().length() < 8)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mật khẩu phải có ít nhất 8 ký tự");
        }
        if (request.password() != null && !request.password().isBlank() && request.password().length() < 8) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mật khẩu phải có ít nhất 8 ký tự");
        }
    }

    private void ensureAdminRemains(AppUser user, String newRole, boolean newEnabled) {
        if ("ADMIN".equals(user.getRole()) && user.isEnabled()
                && (!"ADMIN".equals(newRole) || !newEnabled)
                && userRepository.countByRoleAndEnabledTrue("ADMIN") <= 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Không thể xóa hoặc vô hiệu hóa quản trị viên cuối cùng");
        }
    }

    private AdminUserResponse toResponse(AppUser user) {
        return new AdminUserResponse(
                user.getUserId(),
                user.getUsername(),
                user.getEmployee() == null ? user.getFullName() : user.getEmployee().getFullName(),
                user.getRole(),
                user.isEnabled(),
                user.getFailedLoginAttempts(),
                user.getLockedUntil(),
                user.getEmployee() == null ? null : user.getEmployee().getId(),
                user.getEmployee() == null ? null : user.getEmployee().getFullName());
    }
}
