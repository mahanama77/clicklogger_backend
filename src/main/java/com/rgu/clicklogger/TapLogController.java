package com.rgu.clicklogger;

import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.SetOptions;
import com.google.cloud.firestore.WriteBatch;
import com.google.firebase.cloud.FirestoreClient;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/taps")
@CrossOrigin(origins = "*")
public class TapLogController {

    @PostMapping
    public ResponseEntity<Map<String, Object>> saveTapRecord(@RequestBody TapSessionPayload payload) {
        try {
            // ============================================================
            // 1. VALIDATION — fail fast before touching the database
            // ============================================================
            if (payload.getSessionId() == null || payload.getSessionId().isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Missing sessionId"));
            }
            if (payload.getTaps() == null || payload.getTaps().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Empty taps array"));
            }
            if (payload.getDevicePlatform() == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Missing devicePlatform"));
            }

            Firestore db = FirestoreClient.getFirestore();

            String sessionId = payload.getSessionId();
            String devicePlatform = payload.getDevicePlatform();
            int tapCount = payload.getTaps().size();

            // ============================================================
            // 2. AGGREGATE METADATA ACROSS ALL TAPS IN THE BATCH
            //    A single POST may contain taps from multiple interface
            //    variations (e.g., 50 feedbackshown + 50 nofeedback).
            //    Collect the full set of interfaces seen and the max
            //    sequence number so session metadata reflects reality.
            // ============================================================
            Set<String> interfaceTypesInBatch = new HashSet<>();
            int maxInterfaceSeq = 0;
            for (TapEntry tap : payload.getTaps()) {
                interfaceTypesInBatch.add(tap.getInterfaceType());
                if (tap.getInterfaceSequence() > maxInterfaceSeq) {
                    maxInterfaceSeq = tap.getInterfaceSequence();
                }
            }

            // ============================================================
            // 3. CHECK IF SESSION IS NEW
            //    Required so created_at is set only once — SetOptions.merge()
            //    would otherwise overwrite it on later interface submits.
            // ============================================================
            DocumentReference sessionRef = db.collection("sessions").document(sessionId);
            DocumentSnapshot existingSession = sessionRef.get().get();
            boolean isNewSession = !existingSession.exists();

            // ============================================================
            // 4. BUILD WRITE BATCH — atomic, single network round-trip
            //    Rationale: 100 separate .add() calls = 100 network calls
            //    with no atomicity guarantee. WriteBatch = 1 call with
            //    all-or-nothing semantics.
            // ============================================================
            WriteBatch batch = db.batch();

            // --- 4a. Write all taps to tap_logs collection ---
            for (TapEntry tap : payload.getTaps()) {
                long durationMs = tap.getEndTimestamp() - tap.getStartTimestamp();

                Map<String, Object> tapRecord = new HashMap<>();
                tapRecord.put("session_id", sessionId);
                tapRecord.put("device_platform", devicePlatform);
                tapRecord.put("sequence_no", tap.getTapSequenceNumber());
                tapRecord.put("start_time", tap.getStartTimestamp());
                tapRecord.put("end_time", tap.getEndTimestamp());
                tapRecord.put("duration_ms", durationMs);
                tapRecord.put("interface_type", tap.getInterfaceType());
                tapRecord.put("interface_sequence", tap.getInterfaceSequence());
                tapRecord.put("server_timestamp", FieldValue.serverTimestamp());

                DocumentReference tapRef = db.collection("tap_logs").document();
                batch.set(tapRef, tapRecord);
            }

            // --- 4b. Create/update session document ---
            // SetOptions.merge() semantics:
            //   - New session  → creates the document
            //   - Existing doc → merges fields (arrayUnion appends,
            //     increment adds atomically, last_activity_at overwrites)
            Map<String, Object> sessionUpdate = new HashMap<>();
            sessionUpdate.put("session_id", sessionId);
            sessionUpdate.put("device_platform", devicePlatform);
            sessionUpdate.put("last_activity_at", FieldValue.serverTimestamp());
            sessionUpdate.put("interfaces_completed",
                    FieldValue.arrayUnion(interfaceTypesInBatch.toArray()));
            sessionUpdate.put("max_interface_sequence", maxInterfaceSeq);
            sessionUpdate.put("total_taps", FieldValue.increment(tapCount));
            sessionUpdate.put("completed_both", maxInterfaceSeq >= 2);

            // created_at: set ONLY on first write — preserves original creation time
            if (isNewSession) {
                sessionUpdate.put("created_at", FieldValue.serverTimestamp());
            }

            batch.set(sessionRef, sessionUpdate, SetOptions.merge());

            // ============================================================
            // 5. COMMIT ATOMICALLY — awaited via .get()
            //    On failure, no documents are written (no partial state).
            // ============================================================
            batch.commit().get();

            // ============================================================
            // 6. RESPONSE
            // ============================================================
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("taps_saved", tapCount);
            response.put("session_id", sessionId);
            response.put("max_interface_sequence", maxInterfaceSeq);
            response.put("interfaces_in_batch", interfaceTypesInBatch);
            response.put("is_new_session", isNewSession);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Server error: " + e.getMessage()));
        }
    }
}

// ============================================================
// DTO Classes — map incoming JSON to Java objects
// ============================================================

class TapSessionPayload {
    private String sessionId;
    private String devicePlatform;
    private List<TapEntry> taps;

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getDevicePlatform() { return devicePlatform; }
    public void setDevicePlatform(String devicePlatform) { this.devicePlatform = devicePlatform; }
    public List<TapEntry> getTaps() { return taps; }
    public void setTaps(List<TapEntry> taps) { this.taps = taps; }
}

class TapEntry {
    private int tapSequenceNumber;
    private long startTimestamp;
    private long endTimestamp;
    private int interfaceSequence;

    // Maps JSON key "interface" → Java field "interfaceType"
    // (because "interface" is a reserved word in Java)
    @JsonProperty("interface")
    private String interfaceType;

    public int getTapSequenceNumber() { return tapSequenceNumber; }
    public void setTapSequenceNumber(int tapSequenceNumber) { this.tapSequenceNumber = tapSequenceNumber; }
    public long getStartTimestamp() { return startTimestamp; }
    public void setStartTimestamp(long startTimestamp) { this.startTimestamp = startTimestamp; }
    public long getEndTimestamp() { return endTimestamp; }
    public void setEndTimestamp(long endTimestamp) { this.endTimestamp = endTimestamp; }
    public int getInterfaceSequence() { return interfaceSequence; }
    public void setInterfaceSequence(int interfaceSequence) { this.interfaceSequence = interfaceSequence; }
    public String getInterfaceType() { return interfaceType; }
    public void setInterfaceType(String interfaceType) { this.interfaceType = interfaceType; }
}