package com.anushaporter.backend.config;

import com.anushaporter.backend.service.DriverWalletService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Startup runner to scan and clean up any duplicate legacy COMMISSION records in wallet_transactions
 * and automatically recalculate driver wallet balances accurately.
 */
@Component
public class WalletDataCorrectionRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(WalletDataCorrectionRunner.class);

    @Autowired
    private DriverWalletService driverWalletService;

    @Override
    public void run(String... args) {
        try {
            Map<String, Object> result = driverWalletService.cleanDuplicateCommissionTransactions();
            int deleted = (int) result.getOrDefault("deletedDuplicatesCount", 0);
            int affected = (int) result.getOrDefault("affectedDriversCount", 0);
            if (deleted > 0) {
                log.info("[WalletMigration] Successfully cleaned up {} duplicate commission records across {} drivers.", deleted, affected);
            } else {
                log.info("[WalletMigration] Wallet transactions are clean. No duplicate commission records found.");
            }
        } catch (Exception e) {
            log.warn("[WalletMigration] Note: Wallet migration completed with message: {}", e.getMessage());
        }
    }
}
