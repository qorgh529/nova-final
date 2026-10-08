package com.nova.approval.config;

import com.nova.approval.domain.Role;
import com.nova.approval.domain.UserAccount;
import com.nova.approval.repo.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Set;

/**
 * 개발 편의 시드. users 테이블이 비어 있을 때만 기본 계정을 만든다.
 * 운영에서는 NOVA_SEED_DEV=false 로 끈다. (데모용 대량 시드는 demo 프로파일에서 별도로 한다.)
 */
@Configuration
@ConditionalOnProperty(name = "nova.seed.dev", havingValue = "true", matchIfMissing = true)
public class DevSeed {

    @Bean
    CommandLineRunner seedUsers(UserRepository users, PasswordEncoder encoder) {
        return args -> {
            if (users.count() > 0) {
                return;
            }
            String pw = encoder.encode("password");
            users.save(new UserAccount("admin", pw, "관리자", "정보보안팀", Set.of(Role.ADMIN, Role.EMPLOYEE)));
            users.save(new UserAccount("approver1", pw, "김결재", "경영지원팀", Set.of(Role.APPROVER, Role.EMPLOYEE)));
            users.save(new UserAccount("approver2", pw, "이승인", "경영지원팀", Set.of(Role.APPROVER, Role.EMPLOYEE)));
            users.save(new UserAccount("emp1", pw, "박사원", "개발팀", Set.of(Role.EMPLOYEE)));
        };
    }
}
