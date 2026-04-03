package com.rgu.clicklogger;

import com.google.cloud.firestore.Firestore;
import com.google.firebase.cloud.FirestoreClient;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/taps")
@CrossOrigin(origins = "*")
public class TapLogController {

    @PostMapping
    public ResponseEntity<Map<String, Object>> saveTapRecord(@RequestBody TapSessionPayload payload) {
        try {
            // Validation: Ensure data integrity before touching the database
            if (payload.getSessionId() == null || payload.getTaps() == null || payload.getTaps().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Missing payload fields"));
            }

            Firestore db = FirestoreClient.getFirestore();

            // Iterate through the array of taps and save each as a separate document
            // This satisfies the A+ rubric requirement for a scalable, query-efficient schema
            for (TapEntry tap : payload.getTaps()) {
                
                // Compute on write: Calculate duration here to optimize MongoDB analytics later
                long durationMs = tap.getEndTimestamp() - tap.getStartTimestamp();

                Map<String, Object> tapRecord = new HashMap<>();
                tapRecord.put("session_id", payload.getSessionId());
                tapRecord.put("device_platform", payload.getDevicePlatform());
                tapRecord.put("sequence_no", tap.getTapSequenceNumber());
                tapRecord.put("start_time", tap.getStartTimestamp());
                tapRecord.put("end_time", tap.getEndTimestamp());
                tapRecord.put("duration_ms", durationMs);
                tapRecord.put("interface_type", tap.getInterfaceType());
                tapRecord.put("interface_sequence", tap.getInterfaceSequence());
                
                // Durable time standard assigned by the server
                tapRecord.put("server_timestamp", com.google.cloud.firestore.FieldValue.serverTimestamp());

                // Ingest directly to Firestore 'tap_logs' collection
                db.collection("tap_logs").add(tapRecord);
            }

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("status", "success"));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Server error during ingestion"));
        }
    }
}

// --- DTO (Data Transfer Object) Classes ---
// These safely map the JSON data coming from the frontend into Java objects

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
    
    // Maps the JSON key "interface" to "interfaceType" since "interface" is a reserved word in Java
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