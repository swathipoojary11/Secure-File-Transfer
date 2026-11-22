package com.dcn.securefiletransfer.controller;

import com.dcn.securefiletransfer.dto.TransferInitiateRequest;
import com.dcn.securefiletransfer.dto.TransferStatusResponse;
import com.dcn.securefiletransfer.model.Transfer;
import com.dcn.securefiletransfer.service.TransferAgentService;
import com.dcn.securefiletransfer.service.TransferService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Transfer REST API Controller.
 * 
 * Networking Viva Explanation:
 * Exposes full management over peer-to-peer file transfers:
 * - Initiates outbound TLS streaming chunk transfers.
 * - Monitors real-time transfer progress, instantaneous speed (KB/s), and duration.
 * - Provides verified file downloads and transfer cancellation.
 */
@RestController
@RequestMapping("/api/transfers")
public class TransferController {

    private final TransferService transferService;
    private final TransferAgentService transferAgentService;

    public TransferController(TransferService transferService, TransferAgentService transferAgentService) {
        this.transferService = transferService;
        this.transferAgentService = transferAgentService;
    }

    /**
     * Initiates a secure file transfer to a target peer device.
     */
    @PostMapping(value = "/initiate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> initiateTransfer(
            @RequestParam("file") MultipartFile file,
            @RequestParam("recipientIp") String recipientIp,
            @RequestParam(value = "recipientPort", defaultValue = "8443") int recipientPort,
            @RequestParam(value = "recipientId", required = false) String recipientId,
            @RequestParam(value = "recipientName", required = false) String recipientName,
            @RequestParam(value = "encryptWithAes", defaultValue = "false") boolean encryptWithAes) throws IOException {

        if (file.isEmpty()) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("error", "Bad Request");
            err.put("message", "Uploaded file is empty");
            return ResponseEntity.badRequest().body(err);
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String userId = (auth != null && auth.getPrincipal() != null) ? auth.getPrincipal().toString() : "anonymous-user";
        String userEmail = (auth != null && auth.getCredentials() != null) ? auth.getCredentials().toString() : "Operator";

        TransferInitiateRequest req = new TransferInitiateRequest();
        req.setRecipientIp(recipientIp);
        req.setRecipientPort(recipientPort);
        req.setRecipientId(recipientId);
        req.setRecipientName(recipientName != null ? recipientName : recipientIp);
        req.setEncryptWithAes(encryptWithAes);

        Transfer transfer = transferAgentService.initiateOutboundTransfer(userId, userEmail, file, req);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", "Secure transfer initiated successfully");
        response.put("transferId", transfer.getId());
        response.put("filename", transfer.getFilename());
        response.put("fileSize", transfer.getFileSize());
        response.put("originalSha256", transfer.getOriginalHash());
        response.put("encryptedWithAes", transfer.isEncryptedWithAes());
        response.put("transferStatus", transfer.getTransferStatus());
        response.put("recipient", transfer.getReceiverName() + " (" + transfer.getReceiverIp() + ":" + transfer.getReceiverPort() + ")");

        return ResponseEntity.ok(response);
    }

    /**
     * Lists transfer history with search, status filtering, and sorting.
     */
    @GetMapping
    public ResponseEntity<List<Transfer>> listTransfers(
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "sort", defaultValue = "DESC") String sort) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String userId = (auth != null && auth.getPrincipal() != null) ? auth.getPrincipal().toString() : null;

        List<Transfer> transfers = transferService.listTransfers(userId, search, status, sort);
        return ResponseEntity.ok(transfers);
    }

    /**
     * Gets a single transfer record by ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Transfer> getTransfer(@PathVariable String id) {
        return transferService.getTransferById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Lightweight status polling endpoint for real-time progress bars.
     */
    @GetMapping("/{id}/status")
    public ResponseEntity<TransferStatusResponse> getTransferStatus(@PathVariable String id) {
        return transferService.getTransferById(id)
                .map(t -> ResponseEntity.ok(new TransferStatusResponse(
                        t.getId(), t.getFilename(), t.getTransferStatus(),
                        t.getProgressPercent(), t.getTransferSpeed(), t.getTransferDurationMs(),
                        t.getIntegrityStatus(), t.getOriginalHash(), t.getReceivedHash(),
                        t.getErrorMessage()
                )))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Cancels an ongoing transfer.
     */
    @PostMapping("/{id}/cancel")
    public ResponseEntity<Map<String, Object>> cancelTransfer(@PathVariable String id) {
        boolean cancelled = transferService.cancelTransfer(id);
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("transferId", id);
        res.put("cancelled", cancelled);
        res.put("message", cancelled ? "Transfer cancelled successfully." : "Transfer cannot be cancelled (already completed or not found).");
        return ResponseEntity.ok(res);
    }

    /**
     * Downloads a verified received file.
     */
    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> downloadReceivedFile(@PathVariable String id) {
        Transfer t = transferService.getTransferById(id).orElse(null);
        if (t == null) {
            return ResponseEntity.notFound().build();
        }

        String targetName = t.getDownloadPath() != null ? t.getDownloadPath() : t.getFilename();
        Resource resource = transferAgentService.getFileAsResource(targetName);

        if (resource == null || !resource.exists()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + t.getFilename() + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }
}
