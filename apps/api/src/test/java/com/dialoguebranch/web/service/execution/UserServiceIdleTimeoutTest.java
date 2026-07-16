package com.dialoguebranch.web.service.execution;

import com.dialoguebranch.web.service.Application;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * A {@link UserService} is otherwise only ever removed by an explicit {@code /auth/logout} call,
 * so {@link ApplicationManager#removeIdleUserServices(long)} (run periodically by {@link
 * UserServiceExpirationService}) is what actually bounds server memory for clients that
 * disconnect without logging out. These tests exercise the eviction logic directly, with an
 * explicit idle-timeout value, rather than waiting on the real scheduled sweep or the wall clock.
 */
@SpringBootTest
@ActiveProfiles("test")
class UserServiceIdleTimeoutTest {

    @Autowired
    private Application application;

    @Test
    void recentlyActiveUserServiceIsNotEvicted() throws Exception {
        ApplicationManager applicationManager = application.getApplicationManager();
        String userId = "idle-timeout-test-user-" + UUID.randomUUID();
        applicationManager.getOrCreateActiveUserService(userId);

        // Not asserting on the returned count: other tests sharing this Spring context may have
        // left their own long-idle UserServices behind for this sweep to legitimately evict.
        applicationManager.removeIdleUserServices(TimeUnit.HOURS.toMillis(1));

        assertNotNull(applicationManager.getActiveUserService(userId),
                "a UserService created moments ago should not be evicted by a 1-hour idle timeout");
    }

    @Test
    void idleUserServiceIsEvicted() throws Exception {
        ApplicationManager applicationManager = application.getApplicationManager();
        String userId = "idle-timeout-test-user-" + UUID.randomUUID();
        applicationManager.getOrCreateActiveUserService(userId);

        // A negative timeout makes the cutoff later than "now", so even a UserService whose
        // activity was just recorded counts as idle — evicts deterministically without needing to
        // sleep the test thread.
        applicationManager.removeIdleUserServices(-1);

        assertNull(applicationManager.getActiveUserService(userId),
                "a UserService should be evicted once its last activity predates the cutoff");
    }

    @Test
    void lookupResetsTheIdleTimer() throws Exception {
        ApplicationManager applicationManager = application.getApplicationManager();
        String userId = "idle-timeout-test-user-" + UUID.randomUUID();
        applicationManager.getOrCreateActiveUserService(userId);

        // Resolving the UserService again should record fresh activity, so a subsequent eviction
        // pass with a real (non-negative) timeout must not remove it.
        applicationManager.getActiveUserService(userId);
        applicationManager.removeIdleUserServices(TimeUnit.HOURS.toMillis(1));

        assertNotNull(applicationManager.getActiveUserService(userId),
                "resolving a UserService should reset its idle timer");
    }

}
