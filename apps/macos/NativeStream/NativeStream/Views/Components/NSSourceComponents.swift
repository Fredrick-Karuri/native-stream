// NSSourceComponents.swift
// MAC-BROWSE-003: NSSourcePill
// MAC-BROWSE-004: NSSourcePickerView

import SwiftUI

// MARK: - NSSourcePill

struct NSSourcePill: View {

    let source:  PlaylistSource?
    let action:  () -> Void

    @State private var isHovered = false

    private var isAll: Bool { source == nil || source!.isAll }

    private var label: String {
        isAll ? "All Sources" : source!.label
    }

    private var dotColor: Color {
        guard !isAll, let source else { return NS.text3 }
        return Color(hex: source.colorHex)
    }

    private var bgColor: Color {
        guard !isAll, let source else { return .clear }
        return Color(hex: source.colorHex).opacity(0.08)
    }

    private var borderColor: Color {
        guard !isAll, let source else { return NS.border2 }
        return Color(hex: source.colorHex).opacity(0.25)
    }

    var body: some View {
        Button(action: action) {
            HStack(spacing: NS.Spacing.xs) {
                Circle()
                    .fill(dotColor)
                    .frame(width: 6, height: 6)

                Text(label)
                    .font(NS.Font.caption)
                    .foregroundStyle(isAll ? NS.text3 : NS.text)
                    .lineLimit(1)

                Image(systemName: "chevron.down")
                    .font(.system(size: 10, weight: .medium))
                    .foregroundStyle(NS.text3)
            }
            .padding(.horizontal, NS.Spacing.md)
            .padding(.vertical, NS.Spacing.xs)
            .background(
                RoundedRectangle(cornerRadius: NS.Radius.pill)
                    .fill(isHovered ? bgColor.opacity(1.5) : bgColor)
            )
            .overlay(
                RoundedRectangle(cornerRadius: NS.Radius.pill)
                    .strokeBorder(borderColor, lineWidth: 0.5)
            )
        }
        .buttonStyle(.plain)
        .onHover { isHovered = $0 }
    }
}

// MARK: - NSSourcePickerView

struct NSSourcePickerView: View {

    let sources:        [PlaylistSource]
    let selectedSource: PlaylistSource?
    let onSelect:       (PlaylistSource?) -> Void
    let onAddPlaylist:  () -> Void
    let onDismiss:      () -> Void

    var body: some View {
        VStack(spacing: 0) {
            // Header
            HStack {
                Text("Playlist")
                    .font(NS.Font.heading)
                    .foregroundStyle(NS.text)
                Spacer()
            }
            .padding(.horizontal, NS.Spacing.lg)
            .padding(.vertical, NS.Spacing.md)

            Divider().overlay(NS.border)

            // All Sources row
            SourceRow(
                label:     "All Sources",
                meta:      "\(sources.reduce(0) { $0 + $1.channelCount }) channels",
                dotColor:  NS.text3,
                isActive:  selectedSource == nil || selectedSource!.isAll,
                onClick:   { onSelect(nil); onDismiss() }
            )

            // Per-source rows
            ForEach(sources) { source in
                Divider().overlay(NS.border).padding(.leading, NS.Spacing.lg)
                SourceRow(
                    label:    source.label,
                    meta:     "\(source.url.host ?? source.url.absoluteString) · \(source.channelCount) ch",
                    dotColor: Color(hex: source.colorHex),
                    isActive: selectedSource?.id == source.id,
                    onClick:  { onSelect(source); onDismiss() }
                )
            }

            Divider().overlay(NS.border)

            // Add playlist footer
            Button(action: { onDismiss(); onAddPlaylist() }) {
                HStack(spacing: NS.Spacing.sm) {
                    ZStack {
                        Circle()
                            .strokeBorder(NS.border2, lineWidth: 1)
                            .frame(width: 20, height: 20)
                        Image(systemName: "plus")
                            .font(.system(size: 10, weight: .medium))
                            .foregroundStyle(NS.text3)
                    }
                    Text("Add playlist")
                        .font(NS.Font.caption)
                        .foregroundStyle(NS.text3)
                    Spacer()
                }
                .padding(.horizontal, NS.Spacing.lg)
                .padding(.vertical, NS.Spacing.md)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
        }
        .frame(width: 280)
        .background(NS.surface)
        .clipShape(RoundedRectangle(cornerRadius: NS.Radius.lg))
        .overlay(
            RoundedRectangle(cornerRadius: NS.Radius.lg)
                .strokeBorder(NS.border2, lineWidth: 0.5)
        )
    }
}

// MARK: - SourceRow
extension NSSourcePickerView{
    private struct SourceRow: View {
        
        let label:    String
        let meta:     String
        let dotColor: Color
        let isActive: Bool
        let onClick:  () -> Void
        
        @State private var isHovered = false
        
        var body: some View {
            Button(action: onClick) {
                HStack(spacing: NS.Spacing.sm) {
                    Circle()
                        .fill(dotColor)
                        .frame(width: 8, height: 8)
                    
                    VStack(alignment: .leading, spacing: 2) {
                        Text(label)
                            .font(NS.Font.captionMed)
                            .foregroundStyle(isActive ? NS.text : NS.text2)
                        Text(meta)
                            .font(NS.Font.mono)
                            .foregroundStyle(NS.text3)
                            .lineLimit(1)
                    }
                    
                    Spacer()
                    
                    if isActive {
                        Image(systemName: "checkmark")
                            .font(.system(size: 11, weight: .semibold))
                            .foregroundStyle(NS.accent2)
                    }
                }
                .padding(.horizontal, NS.Spacing.lg)
                .padding(.vertical, NS.Spacing.md)
                .background(isHovered ? NS.surface2 : Color.clear)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .onHover { isHovered = $0 }
        }
    }
}
