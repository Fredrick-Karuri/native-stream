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
                .frame(width: isDark ? NS.IconButton.sizeSm : NS.IconButton.sizeLg,
                       height: isDark ? NS.IconButton.sizeSm : NS.IconButton.sizeLg)
                .background(isDark ? Color.black.opacity(0.5) : Color.white.opacity(isHovered ? 0.1 : 0.06))
                .clipShape(RoundedRectangle(cornerRadius: isDark ? NS.Radius.sm : NS.Radius.md))
                .overlay(RoundedRectangle(cornerRadius: isDark ? NS.Radius.sm : NS.Radius.md)
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
                .padding(.horizontal, NS.Chip.paddingH)
                .frame(height: NS.Chip.height)
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
                    .frame(width: NS.Badge.dotSize, height: NS.Badge.dotSize)
                    .opacity(pulsing ? 0.3 : 1.0)
                    .animation(.easeInOut(duration: 0.7).repeatForever(autoreverses: true), value: pulsing)
                Text("LIVE")
                    .font(NS.Font.monoSm)
                    .fontWeight(.bold)
                    .kerning(0.6)
                    .foregroundStyle(.white)
            }
            .padding(.horizontal, NS.Spacing.sm)
            .frame(height: NS.Badge.height)
            .background(NS.live)
            .clipShape(RoundedRectangle(cornerRadius: NS.Radius.sm))
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
            .padding(.horizontal, NS.Spacing.sm)
            .frame(height: NS.Badge.height)
            .background(Color.white.opacity(0.08))
            .clipShape(RoundedRectangle(cornerRadius: NS.Radius.sm))
            .overlay(RoundedRectangle(cornerRadius: NS.Radius.sm)
                .stroke(Color.white.opacity(0.1)))
    }
}

// MARK: - NSHealthDot

struct NSHealthDot: View {
    let score: Double

    var body: some View {
        Circle()
            .fill(NS.healthColour(score: score))
            .frame(width: NS.Badge.healthDotSize, height: NS.Badge.healthDotSize)
            .frame(width: NS.Badge.dotSize, height: NS.Badge.dotSize)
            .shadow(color: NS.healthColour(score: score).opacity(0.5), radius: 3)
    }
}

// MARK: - NSPulseDot

struct NSPulseDot: View {
    @State private var pulsing = false

    var body: some View {
        Circle()
            .fill(NS.live)
            .frame(width: NS.Badge.dotSize, height: NS.Badge.dotSize)
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
        HStack(spacing: NS.Spacing.sm) {
            Text(title.uppercased())
                .font(NS.Font.label)
                .kerning(1.0)
                .foregroundStyle(NS.text3)
            if let count {
                Text("\(count)")
                    .font(NS.Font.monoSm)
                    .foregroundStyle(NS.text3)
                    .padding(.horizontal, NS.Spacing.xs)
                    .padding(.vertical, NS.Spacing.xxs)
                    .background(NS.surface2)
                    .clipShape(RoundedRectangle(cornerRadius: NS.Radius.sm))
                    .overlay(RoundedRectangle(cornerRadius: NS.Radius.sm).stroke(NS.border))
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
        HStack(spacing: NS.Spacing.md) {
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
                    .font(.system(size: NS.Help.inlineIconSize))
                    .foregroundStyle(copied ? NS.green : NS.text3)
            }
            .buttonStyle(.plain)
        }
        .padding(NS.Spacing.md)
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
                .frame(width: NS.Toggle.trackW, height: NS.Toggle.trackH)
                .shadow(color: isOn ? NS.accent.opacity(0.4) : .clear, radius: 6)
            Circle()
                .fill(Color.white)
                .frame(width: NS.Toggle.thumbSize, height: NS.Toggle.thumbSize)
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
        HStack(spacing: NS.Spacing.xs) {
            ForEach(options, id: \.value) { option in
                Button(option.label) { selected = option.value }
                    .font(NS.Font.caption)
                    .foregroundStyle(selected == option.value ? NS.accent2 : NS.text3)
                    .padding(.horizontal, NS.Chip.paddingH)
                    .padding(.vertical, NS.Spacing.xs)
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
