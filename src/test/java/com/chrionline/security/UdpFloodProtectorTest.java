package com.chrionline.security;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UdpFloodProtectorTest {

    @Test
    void shouldAccept_allowsPacketsUntilLimitIsReached() throws Exception {
        UdpFloodProtector protector = new UdpFloodProtector(3, 1_000L);
        InetAddress ip = InetAddress.getByName("127.0.0.1");

        assertTrue(protector.shouldAccept(ip));
        assertTrue(protector.shouldAccept(ip));
        assertTrue(protector.shouldAccept(ip));
        assertFalse(protector.shouldAccept(ip));
    }

    @Test
    void shouldAccept_resetsCounterAfterWindowExpires() throws Exception {
        UdpFloodProtector protector = new UdpFloodProtector(2, 50L);
        InetAddress ip = InetAddress.getByName("127.0.0.1");

        assertTrue(protector.shouldAccept(ip));
        assertTrue(protector.shouldAccept(ip));
        assertFalse(protector.shouldAccept(ip));

        Thread.sleep(80L);

        assertTrue(protector.shouldAccept(ip));
    }

    @Test
    void shouldAccept_rejectsBlockedIp() throws Exception {
        UdpFloodProtector protector = new UdpFloodProtector();
        InetAddress ip = InetAddress.getByName("127.0.0.1");

        protector.blockIp(ip.getHostAddress());

        assertTrue(protector.isBlocked(ip.getHostAddress()));
        assertFalse(protector.shouldAccept(ip));
    }
}
