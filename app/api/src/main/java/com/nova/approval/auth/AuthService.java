package com.nova.approval.auth;

import com.nova.approval.domain.UserAccount;
import com.nova.approval.repo.UserRepository;
import com.nova.approval.web.ApiException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public UserAccount authenticate(String loginId, String password) {
        UserAccount user = users.findByLoginId(loginId)
            .orElseThrow(() -> new ApiException(org.springframework.http.HttpStatus.UNAUTHORIZED, "로그인 실패"));
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new ApiException(org.springframework.http.HttpStatus.UNAUTHORIZED, "로그인 실패");
        }
        return user;
    }

    public String issueToken(UserAccount user) {
        return jwtService.issue(user);
    }

    public UserAccount requireUser(Long id) {
        return users.findById(id).orElseThrow(() -> ApiException.notFound("사용자 없음: " + id));
    }
}
