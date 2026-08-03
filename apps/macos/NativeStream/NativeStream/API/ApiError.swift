//
//  ApiError.swift
//

import Foundation


// MARK: - Errors

enum APIError: Error, LocalizedError {
    case serverUnreachable(URL)
    case httpError(Int, String?)
    case decodingFailed(Error)
    case invalidURL(String)
    case noActiveLink

    var errorDescription: String? {
        switch self {
        case .serverUnreachable(let url):
            return "Server unreachable at \(url.host ?? url.absoluteString)"
        case .httpError(let code, let msg):
            return "Server returned \(code)\(msg.map { ": \($0)" } ?? "")"
        case .decodingFailed(let err):
            return "Response decode failed: \(err.localizedDescription)"
        case .invalidURL(let s):
            return "Invalid URL: \(s)"
        case .noActiveLink:
            return "Channel has no active stream link"
        }
    }
}

