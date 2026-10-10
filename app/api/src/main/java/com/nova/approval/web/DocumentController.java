package com.nova.approval.web;

import com.nova.approval.auth.CurrentUser;
import com.nova.approval.document.DocumentAssembler;
import com.nova.approval.document.DocumentService;
import com.nova.approval.domain.Document;
import com.nova.approval.web.dto.Dtos;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService service;
    private final DocumentAssembler assembler;

    public DocumentController(DocumentService service, DocumentAssembler assembler) {
        this.service = service;
        this.assembler = assembler;
    }

    @GetMapping
    public List<Dtos.DocumentSummary> myDocuments() {
        return service.myDrafts(CurrentUser.id()).stream().map(assembler::summary).toList();
    }

    @PostMapping
    public ResponseEntity<Dtos.DocumentDetail> create(@Valid @RequestBody Dtos.CreateDocumentRequest req) {
        Document doc = service.create(CurrentUser.id(), req);
        return ResponseEntity.created(URI.create("/api/documents/" + doc.getId()))
            .body(assembler.detail(doc));
    }

    @GetMapping("/{id}")
    public Dtos.DocumentDetail get(@PathVariable Long id) {
        return assembler.detail(service.requireDocument(id));
    }

    @PostMapping("/{id}/submit")
    public Dtos.DocumentDetail submit(@PathVariable Long id) {
        service.submit(id, CurrentUser.id());
        return assembler.detail(service.requireDocument(id));
    }

    @PostMapping("/{id}/approve")
    public Dtos.DocumentDetail approve(@PathVariable Long id) {
        service.approve(id, CurrentUser.id());
        return assembler.detail(service.requireDocument(id));
    }

    @PostMapping("/{id}/reject")
    public Dtos.DocumentDetail reject(@PathVariable Long id) {
        service.reject(id, CurrentUser.id());
        return assembler.detail(service.requireDocument(id));
    }
}
