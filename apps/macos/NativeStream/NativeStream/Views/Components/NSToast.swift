/// Components/NSToast.swift
///
/// Reusable toast notification system. ToastCenter is injected once at the
/// app root; any view calls toastCenter.show(...) via @Environment to surface
/// a transient, self-dismissing, floating notification. ToastOverlay renders
/// the active queue and is mounted once at the AppShell root.

import SwiftUI

private let maxVisibleToasts = 3
private let defaultDuration: TimeInterval = 4
private let toastSpacing: CGFloat = 8
private let toastMaxWidth: CGFloat = 340

enum ToastStyle {
    case info
    case success

    var icon: String {
        switch self {
        case .info:    return "circle.fill"
        case .success: return "checkmark.circle.fill"
        }
    }

    var tint: Color {
        switch self {
        case .info:    return NS.text2
        case .success: return NS.accent
        }
    }

    var background: Color {
        switch self {
        case .info:    return NS.surface2
        case .success: return NS.accentGlow
        }
    }
}

struct Toast: Identifiable, Equatable {
    let id = UUID()
    let message: String
    let style: ToastStyle
    let duration: TimeInterval
}

@Observable
@MainActor
final class ToastCenter {

    private(set) var toasts: [Toast] = []
    private var dismissTasks: [Toast.ID: Task<Void, Never>] = [:]

    func show(_ message: String, style: ToastStyle = .info, duration: TimeInterval = defaultDuration) {
        let toast = Toast(message: message, style: style, duration: duration)

        withAnimation(.easeOut(duration: 0.2)) {
            toasts.append(toast)
            if toasts.count > maxVisibleToasts {
                let evicted = toasts.removeFirst()
                dismissTasks[evicted.id]?.cancel()
                dismissTasks[evicted.id] = nil
            }
        }

        dismissTasks[toast.id] = Task {
            try? await Task.sleep(for: .seconds(toast.duration))
            guard !Task.isCancelled else { return }
            dismiss(toast.id)
        }
    }

    func dismiss(_ id: Toast.ID) {
        dismissTasks[id]?.cancel()
        dismissTasks[id] = nil
        withAnimation(.easeIn(duration: 0.15)) {
            toasts.removeAll { $0.id == id }
        }
    }
}

struct ToastOverlay: View {
    let toasts: [Toast]
    let onDismiss: (Toast.ID) -> Void

    var body: some View {
        VStack(alignment: .trailing, spacing: toastSpacing) {
            ForEach(toasts) { toast in
                ToastRow(toast: toast, onDismiss: { onDismiss(toast.id) })
                    .transition(.asymmetric(
                        insertion: .move(edge: .top).combined(with: .opacity),
                        removal:   .opacity
                    ))
            }
        }
        .padding(NS.Spacing.md)
        .frame(maxWidth: .infinity, alignment: .trailing)
        .allowsHitTesting(!toasts.isEmpty)
    }
}

private struct ToastRow: View {
    let toast: Toast
    let onDismiss: () -> Void

    var body: some View {
        HStack(spacing: NS.Spacing.sm) {
            Image(systemName: toast.style.icon)
                .font(.system(size: 8))
                .foregroundStyle(toast.style.tint)
            Text(toast.message)
                .font(NS.Font.captionMed)
                .foregroundStyle(NS.text)
                .lineLimit(2)
        }
        .padding(.horizontal, NS.Spacing.md)
        .padding(.vertical, NS.Spacing.sm)
        .frame(maxWidth: toastMaxWidth, alignment: .leading)
        .background(toast.style.background)
        .clipShape(RoundedRectangle(cornerRadius: NS.Radius.md))
        .overlay(
            RoundedRectangle(cornerRadius: NS.Radius.md)
                .stroke(NS.border2, lineWidth: 0.5)
        )
        .shadow(color: .black.opacity(0.2), radius: 8, y: 4)
        .onTapGesture { onDismiss() }
    }
}
