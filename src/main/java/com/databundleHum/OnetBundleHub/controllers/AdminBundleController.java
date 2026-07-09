package com.databundleHum.OnetBundleHub.controllers;

import com.databundleHum.OnetBundleHub.dtos.response.BigDreamsBundleResponse;
import com.databundleHum.OnetBundleHub.services.BigDreamsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/bundles")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")  // protect it — admin only
public class AdminBundleController {

    private final BigDreamsService bigDreamsService;

    /**
     * GET /api/admin/bundles
     * Fetch all available bundles across all networks (with our buying price).
     */
    @GetMapping
    public ResponseEntity<List<BigDreamsBundleResponse>> getAllBundles() {
        return ResponseEntity.ok(bigDreamsService.fetchAvailableBundles());
    }

    /**
     * GET /api/admin/bundles?network=mtn
     * Fetch bundles filtered by network: mtn | telecel | ishare
     */
    @GetMapping(params = "network")
    public ResponseEntity<List<BigDreamsBundleResponse>> getBundlesByNetwork(
            @RequestParam String network) {
        return ResponseEntity.ok(bigDreamsService.fetchAvailableBundles(network));
    }
}