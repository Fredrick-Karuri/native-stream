// NSComponents.swift — UX-003
// Atomic component library. All screens build from these.

import SwiftUI

// MARK: - NSIconButton

struct NSIconButton: View {
    let icon: String
    var size: CGFloat = 14
    var isDark: Bool = false
    var action: () -> Void
    @State private var isHovered = false

    var body: some View {
        Button(action: action) {
            Image(systemName: icon)
                .font(.system(size: size, weight: .medium))
                .foregroundStyle(Color.white.opacity(isDark ? 0.6 : 1))
                .frame(width: isDark ? 22 : 32, height: isDark ? 22 : 32)
                .background(isDark ? Color.black.opacity(0.5) : Color.white.opacity(isHovered ? 0.1 : 0.06))
                .clipShape(RoundedRectangle(cornerRadius: isDark ? 5 : 8))
                .overlay(RoundedRectangle(cornerRadius: isDark ? 5 : 8)
                    .stroke(Color.white.opacity(0.1)))
        }
        .buttonStyle(.plain)
        .onHover { isHovered = $0 }
    }
}

// MARK: - NSChip

struct NSChip: View {
    let label: String
    let isActive: Bool
    var action: (() -> Void)? = nil

    var body: some View {
        Button(action: { action?() }) {
            Text(label)
                .font(NS.Font.captionMed)
                .foregroundStyle(isActive ? NS.accent2 : NS.text3)
                .padding(.horizontal, 10)
                .frame(height: 28)
                .background(isActive ? NS.accentGlow : Color.clear)
                .clipShape(Capsule())
                .overlay(Capsule().stroke(isActive ? NS.accentBorder : NS.border2))
        }
        .buttonStyle(.plain)
    }
}

// MARK: - NSCard

struct NSCard<Content: View>: View {
    @ViewBuilder var content: Content
    @State private var isHovered = false

    var body: some View {
        content
            .background(isHovered ? NS.surface3 : NS.surface2)
            .clipShape(RoundedRectangle(cornerRadius: NS.Radius.lg))
            .overlay(RoundedRectangle(cornerRadius: NS.Radius.lg)
                .stroke(isHovered ? NS.border3 : NS.border))
            .offset(y: isHovered ? -1 : 0)
            .shadow(color: .black.opacity(isHovered ? 0.31 : 0),
                    radius: isHovered ? 12 : 0, y: isHovered ? 8 : 0)
            .animation(.easeOut(duration: 0.12), value: isHovered)
            .onHover { isHovered = $0 }
    }
}

// MARK: - NSProgressBar

struct NSProgressBar: View {
    let value: Double   // 0–1
    var height: CGFloat = 2
    var glow: Bool = false

    var body: some View {
        GeometryReader { geo in
            ZStack(alignment: .leading) {
                RoundedRectangle(cornerRadius: height / 2)
                    .fill(NS.border)
                RoundedRectangle(cornerRadius: height / 2)
                    .fill(NS.accent)
                    .frame(width: geo.size.width * max(0, min(1, value)))
                    .shadow(color: glow ? NS.accent.opacity(0.6) : .clear, radius: 4)
            }
        }
        .frame(height: height)
    }
}

// MARK: - NSLiveBadge

struct NSLiveBadge: View {
    var isLive: Bool = false
    @State private var pulsing = false

    var body: some View {
        if isLive {
            HStack(spacing: 4) {
                Circle()
                    .fill(Color.white)
                    .frame(width: 5, height: 5)
                    .opacity(pulsing ? 0.3 : 1.0)
                    .animation(.easeInOut(duration: 0.7).repeatForever(autoreverses: true), value: pulsing)
                Text("LIVE")
                    .font(NS.Font.monoSm)
                    .fontWeight(.bold)
                    .kerning(0.6)
                    .foregroundStyle(.white)
            }
            .padding(.horizontal, 8)
            .frame(height: 24)
            .background(NS.live)
            .clipShape(RoundedRectangle(cornerRadius: 5))
            .onAppear { pulsing = true }
        }
    }
}

// MARK: - NSQualityBadge

struct NSQualityBadge: View {
    let quality: String // "720p", "1080p", "480p"

    var body: some View {
        Text(quality)
            .font(NS.Font.monoSm)
            .foregroundStyle(Color.white.opacity(0.6))
            .padding(.horizontal, 8)
            .frame(height: 24)
            .background(Color.white.opacity(0.08))
            .clipShape(RoundedRectangle(cornerRadius: 5))
            .overlay(RoundedRectangle(cornerRadius: 5)
                .stroke(Color.white.opacity(0.1)))
    }
}

// MARK: - NSHealthDot

struct NSHealthDot: View {
    let score: Double

    var body: some View {
        Circle()
            .fill(NS.healthColour(score: score))
            .frame(width: 6, height: 6)
            .shadow(color: NS.healthColour(score: score).opacity(0.5), radius: 3)
    }
}

// MARK: - NSPulseDot

struct NSPulseDot: View {
    @State private var pulsing = false

    var body: some View {
        Circle()
            .fill(NS.live)
            .frame(width: 5, height: 5)
            .opacity(pulsing ? 0.2 : 1.0)
            .animation(.easeInOut(duration: 0.7).repeatForever(autoreverses: true), value: pulsing)
            .onAppear { pulsing = true }
    }
}

// MARK: - NSGroupHeader

struct NSGroupHeader: View {
    let title: String
    var count: Int? = nil

    var body: some View {
        HStack(spacing: 8) {
            Text(title.uppercased())
                .font(NS.Font.label)
                .kerning(1.0)
                .foregroundStyle(NS.text3)
            if let count {
                Text("\(count)")
                    .font(NS.Font.monoSm)
                    .foregroundStyle(NS.text3)
                    .padding(.horizontal, 5)
                    .padding(.vertical, 1)
                    .background(NS.surface2)
                    .clipShape(RoundedRectangle(cornerRadius: 4))
                    .overlay(RoundedRectangle(cornerRadius: 4).stroke(NS.border))
            }
            Rectangle()
                .fill(NS.border)
                .frame(height: 1)
        }
    }
}

// MARK: - NSCodeBlock

struct NSCodeBlock: View {
    let code: String
    @State private var copied = false

    var body: some View {
        HStack(spacing: 10) {
            Text(code)
                .font(NS.Font.mono)
                .foregroundStyle(NS.text2)
                .textSelection(.enabled)
            Spacer()
            Button {
                NSPasteboard.general.clearContents()
                NSPasteboard.general.setString(code, forType: .string)
                copied = true
                DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) { copied = false }
            } label: {
                Image(systemName: copied ? "checkmark" : "doc.on.doc")
                    .font(.system(size: 11))
                    .foregroundStyle(copied ? NS.green : NS.text3)
            }
            .buttonStyle(.plain)
        }
        .padding(10)
        .background(NS.surface2)
        .clipShape(RoundedRectangle(cornerRadius: NS.Radius.md))
        .overlay(RoundedRectangle(cornerRadius: NS.Radius.md).stroke(NS.border2))
    }
}

// MARK: - NSToggle

struct NSToggle: View {
    @Binding var isOn: Bool

    var body: some View {
        ZStack(alignment: isOn ? .trailing : .leading) {
            Capsule()
                .fill(isOn ? NS.accent : NS.surface3)
                .frame(width: 36, height: 20)
                .shadow(color: isOn ? NS.accent.opacity(0.4) : .clear, radius: 6)
            Circle()
                .fill(Color.white)
                .frame(width: 16, height: 16)
                .shadow(color: .black.opacity(0.25), radius: 2, y: 1)
                .padding(2)
        }
        .animation(.spring(response: 0.25, dampingFraction: 0.7), value: isOn)
        .onTapGesture { isOn.toggle() }
    }
}

// MARK: - NSSegmentedPicker

struct NSSegmentedPicker<T: Hashable>: View {
    let options: [(label: String, value: T)]
    @Binding var selected: T

    var body: some View {
        HStack(spacing: 4) {
            ForEach(options, id: \.value) { option in
                Button(option.label) { selected = option.value }
                    .font(NS.Font.caption)
                    .foregroundStyle(selected == option.value ? NS.accent2 : NS.text3)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 4)
                    .background(selected == option.value ? NS.accentGlow : NS.surface3)
                    .clipShape(RoundedRectangle(cornerRadius: NS.Radius.sm))
                    .overlay(RoundedRectangle(cornerRadius: NS.Radius.sm)
                        .stroke(selected == option.value ? NS.accentBorder : NS.border2))
                    .buttonStyle(.plain)
            }
        }
    }
}

// MARK: - NSSectionDivider

struct NSSectionDivider: View {
    var body: some View {
        Rectangle().fill(NS.border).frame(height: 1)
            .padding(.vertical, NS.Spacing.xl)
    }
}
