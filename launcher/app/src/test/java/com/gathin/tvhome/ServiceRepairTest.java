package com.gathin.tvhome;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/** How ServiceRepair rewrites enabled_accessibility_services: append missing recorded helpers, touch nothing else. */
public class ServiceRepairTest {
    private static final String PKG = "com.gathin.tvhome";
    private static final String HOME = "com.gathin.tvhome.HomeKeyService";
    private static final String JUOS = "com.gathin.tvhome.JuosHomeRedirectService";
    private static final String INPUT = "com.gathin.tvhome.InputKeyService";

    private static String repair(String list, String... recorded) {
        return ServiceRepair.appendMissing(list, PKG, Arrays.asList(recorded));
    }

    @Test public void nothingRecordedChangesNothing() {
        assertNull(ServiceRepair.appendMissing("a.b/.C", PKG, Collections.emptyList()));
        assertNull(ServiceRepair.appendMissing(null, PKG, Collections.emptyList()));
    }

    @Test public void anEmptyListGetsTheRecordedHelper() {
        assertEquals("com.gathin.tvhome/.JuosHomeRedirectService", repair(null, JUOS));
        assertEquals("com.gathin.tvhome/.JuosHomeRedirectService", repair("", JUOS));
        assertEquals("com.gathin.tvhome/.JuosHomeRedirectService", repair("null", JUOS));
    }

    @Test public void otherServicesKeepTheirPlaceAndTheHelperGoesLast() {
        assertEquals("x.y/.A:p.q/p.q.B:com.gathin.tvhome/.JuosHomeRedirectService",
                repair("x.y/.A:p.q/p.q.B", JUOS));
    }

    @Test public void aListedHelperIsNotAddedTwiceInEitherForm() {
        assertNull(repair("x.y/.A:com.gathin.tvhome/.JuosHomeRedirectService", JUOS));
        assertNull(repair("com.gathin.tvhome/com.gathin.tvhome.JuosHomeRedirectService:x.y/.A", JUOS));
    }

    @Test public void onlyTheMissingOneOfSeveralIsAppended() {
        String out = repair("com.gathin.tvhome/.HomeKeyService:x.y/.A", HOME, JUOS);
        assertEquals("com.gathin.tvhome/.HomeKeyService:x.y/.A:com.gathin.tvhome/.JuosHomeRedirectService", out);
    }

    @Test public void anUnrecordedHelperStaysOff() {
        // InputKeyService is off on purpose (C3S source key) and has no record: it must never come back.
        String out = repair("x.y/.A", JUOS);
        assertEquals("x.y/.A:com.gathin.tvhome/.JuosHomeRedirectService", out);
        assertEquals(-1, out.indexOf("InputKeyService"));
    }

    @Test public void aSameNamedServiceOfAnotherAppDoesNotCount() {
        assertEquals("other.app/com.gathin.tvhome.JuosHomeRedirectService:com.gathin.tvhome/.JuosHomeRedirectService",
                repair("other.app/com.gathin.tvhome.JuosHomeRedirectService", JUOS));
    }

    @Test public void aTrailingSeparatorIsNotDoubled() {
        assertEquals("x.y/.A:com.gathin.tvhome/.JuosHomeRedirectService", repair("x.y/.A:", JUOS));
    }

    @Test public void recordedOrderIsStable() {
        List<String> recorded = Arrays.asList(JUOS, HOME);
        assertEquals("com.gathin.tvhome/.HomeKeyService:com.gathin.tvhome/.JuosHomeRedirectService",
                ServiceRepair.appendMissing(null, PKG, recorded));
    }

    @Test public void ignoresARecordOutsideOurPackage() {
        assertNull(repair("x.y/.A", "evil.pkg.Service"));
        assertNull(repair("x.y/.A", INPUT.substring(0, 0)));
    }
}
