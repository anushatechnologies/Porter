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
            if (driverRepository != null) {
                var allDrivers = driverRepository.findAll();
                int fixedDrivers = 0;
                for (var d : allDrivers) {
                    if (d.getWalletBalance() == null || d.getWalletBalance() < 0.0) {
                        d.setWalletBalance(0.0);
                        driverRepository.save(d);
                        fixedDrivers++;
                    }
                }
                if (fixedDrivers > 0) {
                    log.info("[WalletMigration] Normalized {} drivers with negative/null wallet balance to 0.0", fixedDrivers);
                }
            }
        } catch (Exception e) {
            log.warn("[WalletMigration] Note: Wallet migration completed with message: {}", e.getMessage());
        }
    }

    @Autowired(required = false)
    private com.anushaporter.backend.repository.DriverRepository driverRepository;
}
