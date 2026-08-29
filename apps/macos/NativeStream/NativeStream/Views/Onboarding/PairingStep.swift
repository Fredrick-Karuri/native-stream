// PairingStep.swift
//

import SwiftUI
import AppKit

struct PairingStep: View {
    let viewModel: PairingViewModel?
    let serverURLString: String
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
                adminURLHint
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

    private var adminURLHint: some View {
        let adminURL = serverURLString.trimmingCharacters(in: CharacterSet(charactersIn: "/")) + "/admin"
 
        return VStack(spacing: NS.Spacing.xs) {
            Text("Approve at:")
                .font(NS.Font.caption)
                .foregroundStyle(NS.text3)
            HStack(spacing: NS.Spacing.sm) {
                Text(adminURL)
                    .font(NS.Font.monoSm)
                    .foregroundStyle(NS.accent)
                    .lineLimit(1)
                    .truncationMode(.middle)
                Button {
                    let pasteboard = NSPasteboard.general
                    pasteboard.clearContents()
                    pasteboard.setString(adminURL, forType: .string)
                } label: {
                    Image(systemName: "doc.on.doc")
                        .font(.system(size: 11))
                        .foregroundStyle(NS.text3)
                }
                .buttonStyle(.plain)
                .help("Copy admin URL")
 
                if let url = URL(string: adminURL) {
                    Button {
                        NSWorkspace.shared.open(url)
                    } label: {
                        Image(systemName: "arrow.up.right.square")
                            .font(.system(size: 11))
                            .foregroundStyle(NS.text3)
                    }
                    .buttonStyle(.plain)
                    .help("Open in browser")
                }
            }
        }
        .padding(.top, NS.Spacing.xs)
    }
}
