// ServerURLResolver.swift
//
// Resolves which server URL the app should use, per the precedence order:
//   1. Manual override, if the user set one in Settings
//   2. mDNS-discovered LAN server, if discovery found and health-checked one
//   3. Baked-in hosted default
//

import Foundation

enum ServerURLSource: Equatable {
    case manualOverride
    case lanDiscovered
    case hostedDefault
}

struct ResolvedServerURL {
    let url: String
    let source: ServerURLSource
}

enum ServerURLResolver {

    static let hostedDefaultURL = "https://vbccs6bncuo8pdgciav8ra4y.158.158.33.25.sslip.io"

    /// Pure precedence resolution — no I/O, no Foundation networking types
    /// beyond what's already imported for String handling.
    ///
    /// - Parameters:
    ///   - manualOverride: user-entered override from Settings; nil/empty means unset
    ///   - discoveredLANURL: mDNS-discovered + health-checked LAN server; nil
    ///     means discovery hasn't found (or hasn't finished validating) one
    static func resolve(
        manualOverride: String?,
        discoveredLANURL: String?
    ) -> ResolvedServerURL {
        if let override = manualOverride, !override.isEmpty {
            return ResolvedServerURL(url: override, source: .manualOverride)
        }
        if let discovered = discoveredLANURL, !discovered.isEmpty {
            return ResolvedServerURL(url: discovered, source: .lanDiscovered)
        }
        return ResolvedServerURL(url: hostedDefaultURL, source: .hostedDefault)
    }
}
