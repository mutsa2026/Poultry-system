package com.poultry;

import static org.junit.jupiter.api.Assertions.*;

import com.poultry.domain.AppUser;
import com.poultry.repo.*;
import com.poultry.service.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

/** Covers acceptance criteria 3, 4 and 7 from the SRS (opening record, mortality, concurrency). */
@SpringBootTest
@Testcontainers
class StockIntegrityTest {
    @Container static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", pg::getJdbcUrl);
        r.add("spring.datasource.username", pg::getUsername);
        r.add("spring.datasource.password", pg::getPassword);
    }

    @Autowired BatchService batches;
    @Autowired MortalityService mortality;
    @Autowired UserRepository users;

    @BeforeEach void login() {
        if (users.findByUsername("owner").isEmpty()) {
            AppUser u = new AppUser();
            u.setUsername("owner"); u.setFullName("Owner"); u.setRole(AppUser.Role.OWNER); u.setPasswordHash("x");
            users.save(u);
        }
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            "owner", null, List.of(new SimpleGrantedAuthority("ROLE_OWNER"))));
    }

    private BatchService.BatchView newBatch(String code, int qty) {
        return batches.register(new BatchService.RegisterBatch(
            code, null, LocalDate.now().minusDays(5), "Ross 308", qty, new BigDecimal("500.00")));
    }

    @Test void registeringBatchCreatesOneOpeningMovement() {
        var b = newBatch("B-OPEN", 100);
        assertEquals(100, b.currentStock());
        assertEquals(1, batches.history(b.id()).size());
    }

    @Test void mortalityReducesStockExactlyOnce() {
        var b = newBatch("B-MORT", 100);
        mortality.report(b.id(), new MortalityService.ReportDeaths(LocalDate.now(), 7, "Heat", null));
        assertEquals(93, batches.get(b.id()).currentStock());
    }

    @Test void deathsCannotExceedLiveStock() {
        var b = newBatch("B-OVER", 10);
        assertThrows(BusinessException.class, () ->
            mortality.report(b.id(), new MortalityService.ReportDeaths(LocalDate.now(), 11, "x", null)));
        assertEquals(10, batches.get(b.id()).currentStock());
    }

    @Test void concurrentReportsNeverDriveStockNegative() throws Exception {
        var b = newBatch("B-CONC", 50);
        int threads = 20;                       // 20 x 5 = 100 requested, only 50 available
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger accepted = new AtomicInteger();
        List<Future<?>> fs = new ArrayList<>();
        for (int i = 0; i < threads; i++) fs.add(pool.submit(() -> {
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "owner", null, List.of(new SimpleGrantedAuthority("ROLE_OWNER"))));
            go.await();
            try {
                mortality.report(b.id(), new MortalityService.ReportDeaths(LocalDate.now(), 5, "x", null));
                accepted.incrementAndGet();
            } catch (BusinessException ignored) {}
            return null;
        }));
        go.countDown();
        for (Future<?> f : fs) f.get(30, TimeUnit.SECONDS);
        pool.shutdown();
        assertEquals(10, accepted.get());
        assertEquals(0, batches.get(b.id()).currentStock());
    }
}
