// ChannelsSection.swift

import SwiftUI
import SdkGenSwift

// MARK: - Channels section

struct ChannelsSection: View {

    @State private var channels: [Stream_V1_ChannelResponse] = []
    @State private var isLoading = true
    @State private var searchText = ""
    @State private var loadError: String? = nil

    private var filtered: [Stream_V1_ChannelResponse] {
        let managed = channels.filter(\.hasActiveLink_p)
        guard !searchText.isEmpty else { return managed }
        return managed.filter { $0.name.localizedCaseInsensitiveContains(searchText) }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: NS.Spacing.xl) {
            SectionTitle("Channels")

            NSTextField(placeholder: "Search channels…", text: $searchText)

            if isLoading {
                ProgressView().frame(maxWidth: .infinity, alignment: .center)
            } else if let loadError {
                Text(loadError).font(NS.Font.caption).foregroundStyle(NS.text3)
            } else if filtered.isEmpty {
                Text(searchText.isEmpty
                     ? "No server-managed channels yet."
                     : "No channels match “\(searchText)”.")
                    .font(NS.Font.caption).foregroundStyle(NS.text3)
            } else {
                ForEach(filtered, id: \.id) { channel in
                    ChannelHeaderRow(channel: channel)
                }
            }
        }
        .task { await load() }
    }

    private func load() async {
        isLoading = true
        do {
            channels = try await APIClient.shared.listChannels()
            loadError = nil
        } catch {
            loadError = "Couldn't load channels: \(error.localizedDescription)"
        }
        isLoading = false
    }
}

// MARK: - Channel header row

struct ChannelHeaderRow: View {
    let channel: Stream_V1_ChannelResponse

    @State private var expanded = false
    @State private var isLoadingHeaders = false
    @State private var headers: [(key: String, value: String)] = []
    @State private var newKey = ""
    @State private var newValue = ""
    @State private var saveError: String? = nil
    @State private var isSaving = false

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: NS.Spacing.md) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(channel.name).font(NS.Font.captionMed).foregroundStyle(NS.text)
                    Text(channel.groupTitle).font(NS.Font.monoSm).foregroundStyle(NS.text3)
                }
                Spacer()
                Button(expanded ? "Close" : "Edit Headers") {
                    withAnimation(.easeInOut(duration: 0.2)) { expanded.toggle() }
                    if expanded && headers.isEmpty { Task { await loadHeaders() } }
                }
                .buttonStyle(.plain)
                .font(NS.Font.caption)
                .foregroundStyle(NS.accent)
            }
            .padding(NS.Spacing.md)

            if expanded {
                Divider().overlay(NS.border)
                VStack(alignment: .leading, spacing: NS.Spacing.sm) {
                    if isLoadingHeaders {
                        ProgressView()
                    } else if headers.isEmpty {
                        Text("No custom headers")
                            .font(NS.Font.caption).foregroundStyle(NS.text3)
                    } else {
                        ForEach(headers.indices, id: \.self) { i in
                            HStack(spacing: NS.Spacing.sm) {
                                Text(headers[i].key)
                                    .font(NS.Font.monoSm).foregroundStyle(NS.text2)
                                    .frame(width: 110, alignment: .leading)
                                NSTextField(placeholder: "value", text: Binding(
                                    get: { headers[i].value },
                                    set: { headers[i].value = $0 }
                                ))
                                Button {
                                    headers.remove(at: i)
                                } label: {
                                    Image(systemName: "trash").font(.system(size: 11)).foregroundStyle(NS.text3)
                                }
                                .buttonStyle(.plain)
                            }
                        }
                    }

                    HStack(spacing: NS.Spacing.sm) {
                        NSTextField(placeholder: "key", text: $newKey)
                        NSTextField(placeholder: "value", text: $newValue)
                        Button("+ Add header") {
                            guard !newKey.isEmpty else { return }
                            headers.append((key: newKey, value: newValue))
                            newKey = ""; newValue = ""
                        }
                        .buttonStyle(.bordered)
                    }

                    if let saveError {
                        Text(saveError).font(NS.Font.caption).foregroundStyle(NS.red)
                    }

                    HStack {
                        Spacer()
                        Button(isSaving ? "Saving…" : "Save") { Task { await save() } }
                            .buttonStyle(.borderedProminent)
                            .disabled(isSaving)
                    }
                }
                .padding(NS.Spacing.md)
            }
        }
        .background(NS.surface2)
        .clipShape(RoundedRectangle(cornerRadius: NS.Radius.lg))
        .overlay(RoundedRectangle(cornerRadius: NS.Radius.lg).stroke(NS.border, lineWidth: 0.5))
    }

    private func loadHeaders() async {
        isLoadingHeaders = true
        if let detail = try? await APIClient.shared.getChannel(id: channel.id),
           detail.hasActiveLink {
            headers = detail.activeLink.headers.map { (key: $0.key, value: $0.value) }
        }
        isLoadingHeaders = false
    }

    private func save() async {
        isSaving = true
        saveError = nil
        let dict = Dictionary(uniqueKeysWithValues: headers.map { ($0.key, $0.value) })
        do {
            var req = Stream_V1_UpdateChannelRequest()
            req.streamHeaders = dict
            try await APIClient.shared.updateChannel(id: channel.id, req)
        } catch {
            saveError = "Save failed: \(error.localizedDescription)"
        }
        isSaving = false
    }
}
