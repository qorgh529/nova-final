package com.nova.approval.demo;

import com.nova.approval.document.DocumentService;
import com.nova.approval.domain.Document;
import com.nova.approval.domain.DocumentType;
import com.nova.approval.domain.Role;
import com.nova.approval.domain.UserAccount;
import com.nova.approval.repo.UserRepository;
import com.nova.approval.web.dto.Dtos;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * 시드와 생성기가 함께 쓰는 결재 동작. 실제 API와 같은 DocumentService를 거쳐서
 * 결재선 규칙과 해시 체인이 운영 경로와 똑같이 기록되게 한다.
 */
@Component
@Profile("demo")
class DemoWorkflow {

    private final UserRepository users;
    private final DocumentService documents;

    DemoWorkflow(UserRepository users, DocumentService documents) {
        this.users = users;
        this.documents = documents;
    }

    /** 기안자 후보: 결재자·관리자가 아닌 일반 직원 */
    List<UserAccount> drafters() {
        return users.findAll().stream()
            .filter(u -> u.getRoles().contains(Role.EMPLOYEE))
            .filter(u -> !u.getRoles().contains(Role.APPROVER) && !u.getRoles().contains(Role.ADMIN))
            .toList();
    }

    /** 결재자 후보: 관리자 권한이 없는 결재자 */
    List<UserAccount> approvers() {
        return users.findAll().stream()
            .filter(u -> u.getRoles().contains(Role.APPROVER) && !u.getRoles().contains(Role.ADMIN))
            .toList();
    }

    /** 결재자 1~2명을 겹치지 않게 고른다 */
    static List<Long> pickApprovers(List<UserAccount> approvers, Random rnd) {
        List<UserAccount> pool = new ArrayList<>(approvers);
        Collections.shuffle(pool, rnd);
        int n = (pool.size() >= 2 && rnd.nextBoolean()) ? 2 : 1;
        return pool.subList(0, n).stream().map(UserAccount::getId).toList();
    }

    Document draft(Long drafterId, DocumentType type, String title, String body,
                   Dtos.LeaveRequest leave, List<Long> approverIds) {
        return documents.create(drafterId, new Dtos.CreateDocumentRequest(type, title, body, leave, approverIds));
    }

    void submit(Document doc) {
        documents.submit(doc.getId(), doc.getDrafterId());
    }

    /** 앞에서부터 steps개 단계를 승인한다 */
    void approve(Document doc, List<Long> approverIds, int steps) {
        for (int i = 0; i < steps; i++) {
            documents.approve(doc.getId(), approverIds.get(i));
        }
    }

    void rejectAtFirstStep(Document doc, List<Long> approverIds) {
        documents.reject(doc.getId(), approverIds.get(0));
    }
}
