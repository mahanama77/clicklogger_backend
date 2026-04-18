package com.rgu.clicklogger;

import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.firebase.cloud.FirestoreClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/queries")
@CrossOrigin(origins = "*")
public class QueryController {

    // Query 4a: Android vs PC average tap duration
    @GetMapping("/device-averages")
    public ResponseEntity<Map<String, Object>> getDeviceAverages() {
        try {
            Firestore db = FirestoreClient.getFirestore();
            CollectionReference taps = db.collection("tap_logs");

            Map<String, Object> android = computeAverage(taps, "device_platform", "android");
            Map<String, Object> pc = computeAverage(taps, "device_platform", "pc");

            double androidAvg = (double) android.get("average_duration_ms");
            double pcAvg = (double) pc.get("average_duration_ms");

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("query", "4a - Mean tap duration: Android vs PC");
            response.put("android", android);
            response.put("pc", pc);
            response.put("difference_ms", round(Math.abs(androidAvg - pcAvg)));

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Query failed: " + e.getMessage()));
        }
    }

    // Query 4b: feedbackshown vs nofeedback average tap duration
    @GetMapping("/interface-averages")
    public ResponseEntity<Map<String, Object>> getInterfaceAverages() {
        try {
            Firestore db = FirestoreClient.getFirestore();
            CollectionReference taps = db.collection("tap_logs");

            Map<String, Object> feedback = computeAverage(taps, "interface_type", "feedbackshown");
            Map<String, Object> noFeedback = computeAverage(taps, "interface_type", "nofeedback");

            double feedbackAvg = (double) feedback.get("average_duration_ms");
            double noFeedbackAvg = (double) noFeedback.get("average_duration_ms");

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("query", "4b - Mean tap duration: feedbackshown vs nofeedback");
            response.put("feedbackshown", feedback);
            response.put("nofeedback", noFeedback);
            response.put("difference_ms", round(Math.abs(feedbackAvg - noFeedbackAvg)));

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Query failed: " + e.getMessage()));
        }
    }

    // Query 4c: count completed vs dropped sessions
    @GetMapping("/dropout-analysis")
    public ResponseEntity<Map<String, Object>> getDropoutAnalysis() {
        try {
            Firestore db = FirestoreClient.getFirestore();
            CollectionReference sessions = db.collection("sessions");

            // query sessions collection directly, faster than scanning taps
            QuerySnapshot completedSnap = sessions
                    .whereEqualTo("completed_both", true)
                    .get().get();

            QuerySnapshot droppedSnap = sessions
                    .whereEqualTo("completed_both", false)
                    .get().get();

            long completedCount = completedSnap.size();
            long droppedCount = droppedSnap.size();
            long totalCount = completedCount + droppedCount;

            // avoid divide-by-zero if no sessions yet
            double completionRate = totalCount > 0 ? (completedCount * 100.0 / totalCount) : 0.0;
            double dropoutRate = totalCount > 0 ? (droppedCount * 100.0 / totalCount) : 0.0;

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("query", "4c - Completion vs dropout after first interface");
            response.put("total_sessions", totalCount);
            response.put("completed_both_interfaces", completedCount);
            response.put("dropped_after_first", droppedCount);
            response.put("completion_rate_percent", round(completionRate));
            response.put("dropout_rate_percent", round(dropoutRate));

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Query failed: " + e.getMessage()));
        }
    }

    // helper: run a filter query and compute mean of duration_ms
    private Map<String, Object> computeAverage(CollectionReference collection,
                                                String field, String value) throws Exception {
        QuerySnapshot snap = collection.whereEqualTo(field, value).get().get();
        List<QueryDocumentSnapshot> docs = snap.getDocuments();

        double total = 0.0;
        int count = 0;
        for (QueryDocumentSnapshot doc : docs) {
            Long duration = doc.getLong("duration_ms");
            if (duration != null) {
                total += duration;
                count++;
            }
        }

        double avg = count > 0 ? (total / count) : 0.0;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("average_duration_ms", round(avg));
        result.put("tap_count", count);
        return result;
    }

    // round to 2 decimals
    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}