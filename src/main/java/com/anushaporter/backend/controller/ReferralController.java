package com.anushaporter.backend.controller;

import com.anushaporter.backend.model.Referral;
import com.anushaporter.backend.repository.ReferralRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/user/referral")
public class ReferralController {

    @Autowired
    private ReferralRepository repository;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getReferral() {
        List<Referral> all = repository.findAll();
        if (all.isEmpty()) {
            Referral ref = new Referral();
            ref.setId("ref_user");
            ref.setReferralCode("PORTER50");
            ref.setTotalInvites(0);
            ref.setTotalRewards(0.0);
            repository.save(ref);
        }
        Referral ref = repository.findAll().get(0);
        return ResponseEntity.ok(Map.of(
                "referralCode", ref.getReferralCode(),
                "totalEarned", ref.getTotalRewards(),
                "successfulReferrals", ref.getTotalInvites(),
                "totalRewards", ref.getTotalRewards(),
                "totalInvites", ref.getTotalInvites()));
    }

    @PostMapping("/invite")
    public ResponseEntity<Map<String, Boolean>> inviteReferral() {
        List<Referral> all = repository.findAll();
        if (!all.isEmpty()) {
            Referral ref = all.get(0);
            ref.setTotalInvites(ref.getTotalInvites() + 1);
            repository.save(ref);
        }
        return ResponseEntity.ok(Map.of("success", true));
    }
}
