// File: DiscoveryCandidateURL.swift
//
// Pure host/port → candidate URL construction

import Foundation

enum DiscoveryCandidateURL {

    /// Builds the candidate server URL from a resolved host string and port,
    /// stripping the macOS interface zone suffix NWEndpoint.Host sometimes
    /// appends to link-local addresses (e.g. "fe80::1%en0"), and bracketing
    /// IPv6 literals per RFC 3986 §3.2.2 so the host/port boundary is
    /// unambiguous (e.g. "[fe80::1]:8888" instead of "fe80::1:8888").
    static func build(hostString: String, port: Int) -> URL? {
        let cleaned = stripZoneSuffix(hostString)
        let formattedHost = isIPv6Literal(cleaned) ? "[\(cleaned)]" : cleaned
        return URL(string: "http://\(formattedHost):\(port)")
    }

    /// Removes a trailing "%<zone>" interface suffix, if present, regardless
    /// of which interface it names (en0, en1, lo0, awdl0, etc.).
    private static func stripZoneSuffix(_ host: String) -> String {
        guard let percentIndex = host.firstIndex(of: "%") else { return host }
        return String(host[host.startIndex..<percentIndex])
    }

    /// True if `host` parses as a valid IPv6 address (e.g. "fe80::1", "::1").
    private static func isIPv6Literal(_ host: String) -> Bool {
        var addr = in6_addr()
        return host.withCString { cString in
            inet_pton(AF_INET6, cString, &addr) == 1
        }
    }
}
