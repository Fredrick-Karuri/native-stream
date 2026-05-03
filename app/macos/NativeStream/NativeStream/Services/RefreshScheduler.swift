// RefreshScheduler
// Background task manager for periodic playlist and EPG refreshes.

import Foundation

actor RefreshScheduler {

    private var tasks: [String: Task<Void, Never>] = [:]

    /// Schedule a repeating async job under a named key.
    /// Cancels any existing job with the same key before starting.
    func schedule(key: String, interval: TimeInterval, job: @escaping @Sendable () async -> Void) {
        tasks[key]?.cancel()
        guard interval > 0 else { return }

        tasks[key] = Task {
            while !Task.isCancelled {
                try? await Task.sleep(for: .seconds(interval))
                guard !Task.isCancelled else { return }
                await job()
            }
        }
    }

    func cancel(key: String) {
        tasks[key]?.cancel()
        tasks.removeValue(forKey: key)
    }

    func cancelAll() {
        tasks.values.forEach { $0.cancel() }
        tasks.removeAll()
    }
}