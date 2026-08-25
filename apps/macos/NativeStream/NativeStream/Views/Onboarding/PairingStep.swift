// PairingStep.swift
//

import SwiftUI

struct PairingStep: View {
    let viewModel: PairingViewModel?
    var onApproved: () -> Void

    var body: some View {
        VStack(spacing: NS.Spacing.xl) {
            Image(systemName: "link.circle")
                .font(.system(size: 48))
                .foregroundStyle(NS.accent)

            Text("Pair this device")
                .font(NS.Font.display)
                .foregroundStyle(NS.text)

            content
        }
        .padding(NS.Spacing.xxl)
        .frame(maxWidth: 560)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(NS.bg)
        .onChange(of: viewModel?.state) { _, newState in
            if case .approved = newState {
                onApproved()
            }
        }
        .onDisappear {
            viewModel?.stop()
        }
    }

    @ViewBuilder
    private var content: some View {
        switch viewModel?.state {
        case .none, .idle, .starting:
            VStack(spacing: NS.Spacing.md) {
                ProgressView().controlSize(.small)
                Text("Preparing device pairing…")
                    .font(NS.Font.body)
                    .foregroundStyle(NS.text3)
            }

        case .waitingForApproval(let code):
            VStack(spacing: NS.Spacing.md) {
                Text("Approve this device from your server's admin page")
                    .font(NS.Font.body)
                    .foregroundStyle(NS.text3)
                    .multilineTextAlignment(.center)
                Text(code)
                    .font(.system(.largeTitle, design: .monospaced, weight: .bold))
                    .foregroundStyle(NS.accent)
                    .tracking(6)
            }

        case .approved:
            Text("✓ Device paired")
                .font(NS.Font.captionMed)
                .foregroundStyle(NS.accent)

        case .expired:
            retryBlock(message: "Pairing code expired")

        case .denied:
            retryBlock(message: "Pairing was denied")

        case .failed(let message):
            retryBlock(message: message)
        }
    }

    private func retryBlock(message: String) -> some View {
        VStack(spacing: NS.Spacing.md) {
            Text("✗ \(message)")
                .font(NS.Font.captionMed)
                .foregroundStyle(NS.red)
            Button("Retry") {
                viewModel?.start()
            }
            .buttonStyle(.borderedProminent)
        }
    }
}
