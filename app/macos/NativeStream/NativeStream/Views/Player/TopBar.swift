//
//  TopBar.swift
//

import SwiftUI


struct PlayerTopBar: View {
    let channel: Channel?
    let programme: Programme?
    let onBack: () -> Void
    let onStop: () -> Void

    var body: some View {
        HStack(alignment: .top, spacing: NS.Spacing.md) {
            Button(action: onBack) {
                Image(systemName: "chevron.left")
                    .font(.system(size: NS.Help.inlineIconSize, weight: .medium))
                    .foregroundStyle(.white)
                    .frame(width: NS.IconButton.sizeLg, height: NS.IconButton.sizeLg)
                    .background(Color.white.opacity(0.06))
                    .clipShape(RoundedRectangle(cornerRadius: NS.Radius.md))
                    .overlay(RoundedRectangle(cornerRadius: NS.Radius.md).stroke(Color.white.opacity(0.1)))
            }
            .buttonStyle(.plain)
            .padding(.top, NS.Spacing.xxs)

            VStack(alignment: .leading, spacing: 2) {
                Text(channel?.name ?? "")
                    .font(NS.Font.heading).foregroundStyle(.white)
                if let prog = programme {
                    Text(prog.title)
                        .font(NS.Font.caption)
                        .foregroundStyle(Color.white.opacity(0.5))
                }
            }

            Spacer()
            
            NSIconButton(icon: "xmark") { onStop() }
        }
        .padding(.horizontal, NS.Spacing.xl)
        .padding(.top, NS.Spacing.lg)
        .padding(.bottom, NS.Spacing.xl)
        .background(NS.playerTopGradient)
    }
}

