package io.github.cametendo.opencowork.data

import io.github.cametendo.opencowork.data.ServerAddress.Mode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ServerAddressTest {
    @Test fun ipWithPort() = assertEquals("http://192.168.1.20:3001", ServerAddress.build(Mode.IP, "192.168.1.20", "3001", false))
    @Test fun ipDefaultsPort() = assertEquals("http://10.0.0.5:3001", ServerAddress.build(Mode.IP, " 10.0.0.5 ", "", false))
    @Test fun ipHttps() = assertEquals("https://10.0.0.5:8443", ServerAddress.build(Mode.IP, "10.0.0.5", "8443", true))
    @Test fun ipStripsTypedSchemeAndPort() = assertEquals("http://10.0.0.5:9000", ServerAddress.build(Mode.IP, "http://10.0.0.5:3001/", "9000", false))
    @Test fun ipv6() = assertEquals("http://[fe80::1]:3001", ServerAddress.build(Mode.IP, "fe80::1", "3001", false))
    @Test fun ipv6Bracketed() = assertEquals("http://[::1]:3001", ServerAddress.build(Mode.IP, "[::1]", "3001", false))
    @Test fun badPort() { assertThrows(ServerAddress.InvalidAddress::class.java) { ServerAddress.build(Mode.IP, "1.2.3.4", "70000", false) } }
    @Test fun emptyHost() { assertThrows(ServerAddress.InvalidAddress::class.java) { ServerAddress.build(Mode.IP, "  ", "3001", false) } }

    @Test fun domainNoPortNeeded() = assertEquals("https://cowork.example.com", ServerAddress.build(Mode.DOMAIN, "cowork.example.com", "3001", true))
    @Test fun domainKeepsExplicitScheme() = assertEquals("http://cowork.lan", ServerAddress.build(Mode.DOMAIN, "http://cowork.lan/", "", true))
    @Test fun domainWithSubPath() = assertEquals("https://example.com/cowork", ServerAddress.build(Mode.DOMAIN, "example.com/cowork/", "", true))
    @Test fun domainPlainHttp() = assertEquals("http://myserver.tailnet.ts.net", ServerAddress.build(Mode.DOMAIN, "myserver.tailnet.ts.net", "", false))
}
