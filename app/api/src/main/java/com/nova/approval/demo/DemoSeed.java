package com.nova.approval.demo;

import com.nova.approval.attachment.AttachmentService;
import com.nova.approval.domain.Document;
import com.nova.approval.domain.DocumentType;
import com.nova.approval.domain.LeaveType;
import com.nova.approval.domain.Role;
import com.nova.approval.domain.UserAccount;
import com.nova.approval.repo.AttachmentRepository;
import com.nova.approval.repo.DocumentRepository;
import com.nova.approval.repo.UserRepository;
import com.nova.approval.web.dto.Dtos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * demo 프로파일 대량 시드: 사용자, 문서(상태 골고루), EICAR 테스트 파일 첨부 1건.
 * 여러 번 기동해도 모자란 만큼만 채운다. 기본 계정(admin 등)은 DevSeed가 먼저 만든다
 * (CommandLineRunner가 ApplicationReadyEvent보다 먼저 실행된다).
 */
@Component
@Profile("demo")
public class DemoSeed {

    private static final Logger log = LoggerFactory.getLogger(DemoSeed.class);

    static final String EICAR_FILE_NAME = "eicar-test.txt";
    static final String EICAR_TITLE = "[DEMO] 보안 점검용 첨부 (EICAR 테스트 파일)";

    private static final String[] DEPARTMENTS = {"개발팀", "영업팀", "인사팀", "재무팀", "경영지원팀"};
    private static final String[] NAMES = {
        "강민준", "김서연", "박지호", "이하은", "정도윤", "최지우", "조시우", "윤서윤", "장예준", "임수아",
        "한주원", "오지민", "서하준", "신채원", "권은우", "황유나", "안건우", "송다인", "류현우", "홍소율"};
    private static final String[] SUBJECTS = {
        "비품 구매 품의", "출장비 정산", "교육 참가 신청", "외주 계약 검토", "법인카드 사용 보고", "회의실 장비 교체"};

    private final DemoProperties props;
    private final DemoWorkflow workflow;
    private final UserRepository users;
    private final DocumentRepository documents;
    private final AttachmentRepository attachments;
    private final AttachmentService attachmentService;
    private final PasswordEncoder encoder;

    public DemoSeed(DemoProperties props, DemoWorkflow workflow, UserRepository users,
                    DocumentRepository documents, AttachmentRepository attachments,
                    AttachmentService attachmentService, PasswordEncoder encoder) {
        this.props = props;
        this.workflow = workflow;
        this.users = users;
        this.documents = documents;
        this.attachments = attachments;
        this.attachmentService = attachmentService;
        this.encoder = encoder;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seed() {
        seedUsers();
        seedDocuments();
        if (props.getSeed().isEicar()) {
            seedEicarAttachment();
        }
    }

    void seedUsers() {
        String pw = null;
        int created = 0;
        for (int i = 1; i <= props.getSeed().getUsers(); i++) {
            String loginId = String.format("demo%02d", i);
            if (users.findByLoginId(loginId).isPresent()) {
                continue;
            }
            if (pw == null) {
                pw = encoder.encode("password");
            }
            // 5명마다 1명은 결재자. 관리자(ADMIN)는 만들지 않는다: 권한 점검의 기준선은 기본 계정 admin 하나다
            Set<Role> roles = (i % 5 == 0) ? Set.of(Role.APPROVER, Role.EMPLOYEE) : Set.of(Role.EMPLOYEE);
            users.save(new UserAccount(loginId, pw, NAMES[(i - 1) % NAMES.length],
                DEPARTMENTS[(i - 1) % DEPARTMENTS.length], roles));
            created++;
        }
        log.info("[demo] 사용자 시드: {}명 생성", created);
    }

    void seedDocuments() {
        long missing = props.getSeed().getDocuments() - documents.count();
        if (missing <= 0) {
            return;
        }
        List<UserAccount> drafters = workflow.drafters();
        List<UserAccount> approvers = workflow.approvers();
        if (drafters.isEmpty() || approvers.isEmpty()) {
            log.warn("[demo] 기안자 또는 결재자가 없어 문서 시드를 건너뜀");
            return;
        }
        // 고정 시드: 몇 번을 다시 만들어도 같은 분포가 나온다
        Random rnd = new Random(42);
        for (int i = 0; i < missing; i++) {
            Long drafterId = drafters.get(rnd.nextInt(drafters.size())).getId();
            List<Long> approverIds = DemoWorkflow.pickApprovers(approvers, rnd);
            Document doc = (i % 3 == 0)
                ? workflow.draft(drafterId, DocumentType.LEAVE, "[DEMO] 휴가 신청 #" + (i + 1), null,
                    leave(i, rnd), approverIds)
                : workflow.draft(drafterId, DocumentType.APPROVAL,
                    "[DEMO] " + SUBJECTS[rnd.nextInt(SUBJECTS.length)] + " #" + (i + 1),
                    "데모 시드 문서", null, approverIds);
            finish(doc, approverIds, i % 10);
        }
        log.info("[demo] 문서 시드: {}건 생성", missing);
    }

    /** 상태 분포: 기안 10%, 상신 대기 10%, 반려 10%, 일부 승인 10%, 최종 승인 60% */
    private void finish(Document doc, List<Long> approverIds, int bucket) {
        switch (bucket) {
            case 0 -> {
            }
            case 1 -> workflow.submit(doc);
            case 2 -> {
                workflow.submit(doc);
                workflow.rejectAtFirstStep(doc, approverIds);
            }
            case 3 -> {
                workflow.submit(doc);
                workflow.approve(doc, approverIds, approverIds.size() - 1);
            }
            default -> {
                workflow.submit(doc);
                workflow.approve(doc, approverIds, approverIds.size());
            }
        }
    }

    private static Dtos.LeaveRequest leave(int i, Random rnd) {
        LocalDate start = LocalDate.of(2026, 11, 2).plusDays(i % 60);
        LeaveType type = LeaveType.values()[rnd.nextInt(LeaveType.values().length)];
        return new Dtos.LeaveRequest(type, start, start.plusDays(rnd.nextInt(3)));
    }

    void seedEicarAttachment() {
        if (attachments.existsByFileName(EICAR_FILE_NAME)) {
            return;
        }
        // 이전 기동에서 업로드만 실패했다면 그 문서를 다시 쓴다 (재시작마다 빈 문서가 쌓이지 않게)
        Document doc = documents.findFirstByTitleOrderByIdAsc(EICAR_TITLE).orElse(null);
        if (doc == null) {
            List<UserAccount> drafters = workflow.drafters();
            List<UserAccount> approvers = workflow.approvers();
            if (drafters.isEmpty() || approvers.isEmpty()) {
                return;
            }
            doc = workflow.draft(drafters.get(0).getId(), DocumentType.APPROVAL, EICAR_TITLE,
                "복원 전 첨부파일 스캔(ClamAV)이 걸러내야 하는 무해한 표준 테스트 파일이 첨부되어 있다.",
                null, List.of(approvers.get(0).getId()));
        }
        try {
            attachmentService.upload(doc.getId(), doc.getDrafterId(), EICAR_FILE_NAME, "text/plain", eicarBytes());
            log.info("[demo] EICAR 테스트 첨부 생성: document={}", doc.getId());
        } catch (RuntimeException e) {
            log.warn("[demo] EICAR 테스트 첨부 업로드 실패 (스토리지 설정 확인): {}", e.toString());
        }
    }

    /**
     * EICAR 표준 안티바이러스 테스트 문자열 (무해함).
     * 소스·클래스 파일·jar에 원문이 그대로 들어가면 개발자 PC의 백신과 클린 파이프라인의 이미지 스캔이
     * 앱 자체를 악성으로 잡으므로, 뒤집어 저장했다가 실행 시점에만 복원한다.
     */
    static byte[] eicarBytes() {
        String reversed = "*H+H$!ELIF-TSET-SURIVITNA-DRADNATS-RACIE$}7)CC7)^P(45XZP\\4[PA@%P!O5X";
        return new StringBuilder(reversed).reverse().toString().getBytes(StandardCharsets.US_ASCII);
    }
}
