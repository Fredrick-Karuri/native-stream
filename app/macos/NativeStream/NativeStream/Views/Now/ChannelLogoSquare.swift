// ChannelLogoSquare.swift
// Square channel logo for use in rows and lists.
// ChannelLogoView owns 16:9 card sizing — this is the separate row/list variant.

import SwiftUI

struct ChannelLogoSquare: View {
    let channel: Channel
    var size: CGFloat = 36
    var cornerRadius: CGFloat = NS.Radius.md

    var body: some View {
        Group {
            if let url = channel.logoURL {
                AsyncImage(url: url) { phase in
                    switch phase {
                    case .success(let img):
                        img.resizable().scaledToFill()
                    default:
                        placeholder
                    }
                }
            } else {
                placeholder
            }
        }
        .frame(width: size, height: size)
        .background(NS.surface2)
        .clipShape(RoundedRectangle(cornerRadius: cornerRadius))
        .overlay(
            RoundedRectangle(cornerRadius: cornerRadius)
                .stroke(NS.border, lineWidth: 0.5)
        )
    }

    private var placeholder: some View {
        ZStack {
            NS.surface2
            Text(channel.name.prefix(3).uppercased())
                .font(NS.Font.monoSm)
                .foregroundStyle(NS.text3)
        }
    }
}
