package com.nova.approval.web;

import com.nova.approval.domain.Role;
import com.nova.approval.history.ApprovalHistoryService;
import com.nova.approval.repo.UserRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final UserRepository users;
    private final ApprovalHistoryService history;

    public AdminController(UserRepository users, ApprovalHistoryService history) {
        this.users = users;
        this.history = history;
    }

    @GetMapping("/users")
    public List<Map<String, Object>> users() {
        return users.findAll().stream().map(u -> Map.<String, Object>of(
            "id", u.getId(),
            "loginId", u.getLoginId(),
            "name", u.getName(),
            "department", u.getDepartment() == null ? "" : u.getDepartment(),
            "roles", u.getRoles().stream().map(Role::name).toList(),
            "createdAt", u.getCreatedAt())).toList();
    }

    @GetMapping("/integrity")
    public ApprovalHistoryService.IntegrityResult integrity() {
        return history.verify();
    }
}
