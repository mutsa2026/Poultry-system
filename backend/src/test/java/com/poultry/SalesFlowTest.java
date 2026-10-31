package com.poultry;

import static org.junit.jupiter.api.Assertions.*;

import com.poultry.auth.AuthController;
import com.poultry.customer.CustomerService;
import com.poultry.domain.AppUser;
import com.poultry.processing.DressedProcessingService;
import com.poultry.repo.UserRepository;
import com.poultry.sale.*;
import com.poultry.service.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

/** SRS acceptance criteria 5, 9–11: live sale reduces stock once, partial payments, credit limits, allocation. */
@SpringBootTest
@Testcontainers
class SalesFlowTest {
    @Container static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16");
    @DynamicPropertySource static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", pg::getJdbcUrl); r.add("spring.datasource.username", pg::getUsername); r.add("spring.datasource.password", pg::getPassword);
    }
    @Autowired BatchService batches; @Autowired SaleService sales; @Autowired PaymentService payments;
    @Autowired CustomerService customers; @Autowired UserRepository users; @Autowired DressedProcessingService processing;
    @Autowired AuthController auth; @Autowired PasswordEncoder passwordEncoder; @Autowired SessionRegistry sessions;

    @BeforeEach void login() {
        if (users.findByUsername("owner").isEmpty()) {
            AppUser u = new AppUser(); u.setUsername("owner"); u.setFullName("Owner"); u.setRole(AppUser.Role.OWNER); u.setPasswordHash("x"); users.save(u);
        }
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("owner", null, List.of(new SimpleGrantedAuthority("ROLE_OWNER"))));
    }

    private Long customer(String limit) {
        return customers.create(new CustomerService.CreateCustomer("Test " + System.nanoTime(), null, null, null, null, new BigDecimal(limit), 14)).id();
    }
    private SaleService.CreateSale live(Long cust, Long batch, int qty, String paid) {
        return new SaleService.CreateSale(cust, "LIVE_BROILER", batch, null, new BigDecimal(qty), new BigDecimal("5.00"), BigDecimal.ZERO, new BigDecimal(paid), "CASH");
    }
    private Long batch(String code, int qty) {
        return batches.register(new BatchService.RegisterBatch(code, null, LocalDate.now().minusDays(10), "Ross", qty, new BigDecimal("100"))).id();
    }

    @Test void liveSaleReducesStockOnceAndPartialPaymentLeavesBalance() {
        Long b = batch("S-1", 100), c = customer("500");
        var s = sales.create(live(c, b, 20, "40"));        // total 100, paid 40
        assertEquals(80, batches.get(b).currentStock());
        assertEquals(0, new BigDecimal("60.00").compareTo(s.balance()));
        assertEquals(0, new BigDecimal("60.00").compareTo(customers.get(c).balance()));
    }

    @Test void creditLimitIsEnforced() {
        Long b = batch("S-2", 100), c = customer("50");
        assertThrows(BusinessException.class, () -> sales.create(live(c, b, 20, "0")));   // would owe 100 > 50
        assertEquals(100, batches.get(b).currentStock());                                  // nothing was deducted
    }

    @Test void cannotSellMoreBirdsThanAlive() {
        Long b = batch("S-3", 10), c = customer("1000");
        assertThrows(BusinessException.class, () -> sales.create(live(c, b, 11, "55")));
    }

    @Test void paymentAllocatesAcrossInvoicesAndRejectsOverpayment() {
        Long b = batch("S-4", 100), c = customer("1000");
        sales.create(live(c, b, 10, "0"));   // 50
        sales.create(live(c, b, 10, "0"));   // 50
        var r = payments.record(c, null, new BigDecimal("70"), "CASH", null);
        assertEquals(2, r.allocations().size());
        assertEquals(0, new BigDecimal("30.00").compareTo(r.newBalance()));
        assertThrows(BusinessException.class, () -> payments.record(c, null, new BigDecimal("31"), "CASH", null));
    }

    @Test void cancelRestoresStock() {
        Long b = batch("S-5", 50), c = customer("1000");
        var s = sales.create(live(c, b, 10, "0"));
        assertEquals(40, batches.get(b).currentStock());
        sales.cancel(s.id());
        assertEquals(50, batches.get(b).currentStock());
    }

    @Test void processingConsumesLiveBirdsAndDressedSaleDeductsAndRestoresItsLot() {
        Long source = batch("S-6", 50), cust = customer("1000");
        var lot = processing.process(new DressedProcessingService.ProcessBatch(
            source, LocalDate.now(), 10, new BigDecimal("18.500"), "Morning processing"));

        assertEquals(40, batches.get(source).currentStock());
        assertEquals(0, new BigDecimal("18.500").compareTo(lot.stockKg()));

        var sale = sales.create(new SaleService.CreateSale(cust, "DRESSED_CHICKEN", null, lot.id(),
            new BigDecimal("4.250"), new BigDecimal("8.00"), BigDecimal.ZERO, BigDecimal.ZERO, null));
        assertEquals(lot.batchNo(), sale.batchCode());
        assertEquals(0, new BigDecimal("14.250").compareTo(processing.list().get(0).stockKg()));

        sales.cancel(sale.id());
        assertEquals(0, new BigDecimal("18.500").compareTo(processing.list().get(0).stockKg()));
    }

    @Test void processingAndDressedSalesCannotOverdrawTheirSourceStock() {
        Long source = batch("S-7", 5), cust = customer("1000");
        var lot = processing.process(new DressedProcessingService.ProcessBatch(
            source, LocalDate.now(), 5, new BigDecimal("7.250"), null));
        assertThrows(BusinessException.class, () -> processing.process(new DressedProcessingService.ProcessBatch(
            source, LocalDate.now(), 1, new BigDecimal("1.000"), null)));
        assertThrows(BusinessException.class, () -> sales.create(new SaleService.CreateSale(cust, "DRESSED_CHICKEN",
            null, lot.id(), new BigDecimal("7.251"), new BigDecimal("8.00"), BigDecimal.ZERO, BigDecimal.ZERO, null)));
        assertEquals(0, new BigDecimal("7.250").compareTo(processing.list().get(0).stockKg()));
    }

    @Test void passwordChangeRequiresCurrentPasswordAndExpiresEverySession() {
        AppUser owner = users.findByUsername("owner").orElseThrow();
        owner.setPasswordHash(passwordEncoder.encode("old-password"));
        users.saveAndFlush(owner);
        String firstSession = UUID.randomUUID().toString();
        String secondSession = UUID.randomUUID().toString();
        sessions.registerNewSession(firstSession, "owner");
        sessions.registerNewSession(secondSession, "owner");

        try {
            auth.changePassword(new AuthController.ChangePasswordRequest("old-password", "new-password"),
                SecurityContextHolder.getContext().getAuthentication());

            AppUser changed = users.findByUsername("owner").orElseThrow();
            assertTrue(passwordEncoder.matches("new-password", changed.getPasswordHash()));
            assertFalse(passwordEncoder.matches("old-password", changed.getPasswordHash()));
            assertTrue(sessions.getAllSessions("owner", true).stream().allMatch(s -> s.isExpired()));
        } finally {
            sessions.removeSessionInformation(firstSession);
            sessions.removeSessionInformation(secondSession);
        }
    }

    @Test void incorrectCurrentPasswordDoesNotChangePasswordOrExpireSessions() {
        AppUser owner = users.findByUsername("owner").orElseThrow();
        owner.setPasswordHash(passwordEncoder.encode("old-password"));
        users.saveAndFlush(owner);
        String sessionId = UUID.randomUUID().toString();
        sessions.registerNewSession(sessionId, "owner");

        try {
            assertThrows(BusinessException.class, () -> auth.changePassword(
                new AuthController.ChangePasswordRequest("incorrect-password", "new-password"),
                SecurityContextHolder.getContext().getAuthentication()));
            assertTrue(passwordEncoder.matches("old-password", users.findByUsername("owner").orElseThrow().getPasswordHash()));
            assertFalse(sessions.getSessionInformation(sessionId).isExpired());
        } finally {
            sessions.removeSessionInformation(sessionId);
        }
    }
}
