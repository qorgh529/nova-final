package com.nova.approval.web;

import com.nova.approval.auth.AuthService;
import com.nova.approval.auth.CurrentUser;
import com.nova.approval.domain.Role;
import com.nova.approval.domain.UserAccount;
import com.nova.approval.web.dto.Dtos;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/auth/login")
    public Dtos.LoginResponse login(@Valid @RequestBody Dtos.LoginRequest req) {
        UserAccount user = auth.authenticate(req.loginId(), req.password());
        String token = auth.issueToken(user);
        return new Dtos.LoginResponse(token, user.getId(), user.getName(), roleNames(user));
    }

    @GetMapping("/me")
    public Dtos.MeResponse me() {
        UserAccount user = auth.requireUser(CurrentUser.id());
        return new Dtos.MeResponse(user.getId(), user.getLoginId(), user.getName(),
            user.getDepartment(), roleNames(user));
    }

    private List<String> roleNames(UserAccount user) {
        return user.getRoles().stream().map(Role::name).toList();
    }
}
