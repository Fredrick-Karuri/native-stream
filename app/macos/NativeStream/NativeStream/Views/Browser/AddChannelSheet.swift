import SwiftUI

struct AddChannelSheet: View {
    let onDone: () -> Void

    @Environment(ChannelManagerViewModel.self) private var channelManager

    @State private var name       = ""
    @State private var streamURL  = ""
    @State private var groupTitle = "General"
    @State private var tvgID      = ""
    @State private var isLoading  = false
    @State private var error: String? = nil

    var body: some View {
        VStack(alignment: .leading, spacing: NS.Spacing.xl) {
            Text("Add Channel")
                .font(NS.Font.heading)
                .foregroundStyle(NS.text)

            VStack(alignment: .leading, spacing: NS.Spacing.md) {
                field("Stream URL *", placeholder: "https://...", text: $streamURL)
                field("Name *", placeholder: "e.g. NBC", text: $name)
                field("Group", placeholder: "e.g. General, Sports", text: $groupTitle)
                field("TVG ID", placeholder: "optional", text: $tvgID)
            }

            if let error {
                Text(error)
                    .font(NS.Font.monoSm)
                    .foregroundStyle(NS.live)
                    .padding(NS.Spacing.sm)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(NS.live.opacity(0.08))
                    .clipShape(RoundedRectangle(cornerRadius: NS.Radius.md))
                    .overlay(RoundedRectangle(cornerRadius: NS.Radius.md).stroke(NS.live.opacity(0.2), lineWidth: 0.5))
            }

            HStack {
                Spacer()
                Button("Cancel") { onDone() }
                    .font(NS.Font.captionMed)
                    .foregroundStyle(NS.text2)
                    .buttonStyle(.plain)
                    .padding(.horizontal, NS.Spacing.md)
                    .frame(height: NS.Help.searchHeight)
                    .background(NS.surface3)
                    .clipShape(RoundedRectangle(cornerRadius: NS.Radius.md))
                    .overlay(RoundedRectangle(cornerRadius: NS.Radius.md).stroke(NS.border2, lineWidth: 0.5))

                Button(isLoading ? "Adding…" : "Add Channel") { Task { await submit() } }
                    .font(NS.Font.captionMed)
                    .foregroundStyle(isLoading || name.isEmpty || streamURL.isEmpty ? NS.text3 : NS.accent)
                    .buttonStyle(.plain)
                    .padding(.horizontal, NS.Spacing.md)
                    .frame(height: NS.Help.searchHeight)
                    .background(isLoading || name.isEmpty || streamURL.isEmpty ? NS.surface3 : NS.accentGlow)
                    .clipShape(RoundedRectangle(cornerRadius: NS.Radius.md))
                    .overlay(RoundedRectangle(cornerRadius: NS.Radius.md).stroke(
                        isLoading || name.isEmpty || streamURL.isEmpty ? NS.border2 : NS.accentBorder,
                        lineWidth: 0.5))
                    .disabled(name.isEmpty || streamURL.isEmpty || isLoading)
            }
        }
        .padding(NS.Spacing.xl)
        .frame(width: 480)
        .background(NS.surface)
    }

    @ViewBuilder
    private func field(_ label: String, placeholder: String, text: Binding<String>) -> some View {
        VStack(alignment: .leading, spacing: NS.Spacing.xs) {
            Text(label)
                .font(NS.Font.monoSm)
                .foregroundStyle(NS.text3)
            NSTextField(placeholder: placeholder, text: text)
        }
    }

    private func submit() async {
        isLoading = true
        error = nil
        let keywords = [name.lowercased().replacingOccurrences(of: " ", with: "")]
        await channelManager.addChannel(
            name: name,
            groupTitle: groupTitle.isEmpty ? "General" : groupTitle,
            tvgID: tvgID,
            logoURL: "",
            streamURL: streamURL,
            keywords: keywords
        )
        if channelManager.error == nil {
            onDone()
        } else {
            error = channelManager.error
        }
        isLoading = false
    }
}
