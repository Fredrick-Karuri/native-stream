// import SwiftUI


// // MARK: - Filter Bar (UX-011)

// struct FilterBar: View {
//     @Binding var searchText: String
//     @Binding var filter: ChannelFilter
//     @Binding var viewMode: BrowserScreen.ViewMode
//     let channelCount: Int

//     var body: some View {
//         HStack(spacing: 10) {
//             // Search
//             HStack(spacing: 6) {
//                 Image(systemName: "magnifyingglass")
//                     .font(.system(size: 12))
//                     .foregroundStyle(NS.text3)
//                 TextField("Search channels…", text: $searchText)
//                     .font(NS.Font.body)
//                     .foregroundStyle(NS.text)
//                     .textFieldStyle(.plain)
//                 if !searchText.isEmpty {
//                     Button { searchText = "" } label: {
//                         Image(systemName: "xmark.circle.fill")
//                             .font(.system(size: 11))
//                             .foregroundStyle(NS.text3)
//                     }
//                     .buttonStyle(.plain)
//                 }
//             }
//             .padding(.horizontal, 10)
//             .frame(width: 240, height: 32)
//             .background(NS.bg)
//             .clipShape(RoundedRectangle(cornerRadius: 8))
//             .overlay(RoundedRectangle(cornerRadius: 8).stroke(NS.border2))

//             // Filter chips
//             HStack(spacing: 6) {
//                 ForEach(ChannelFilter.allCases, id: \.self) { f in
//                     NSChip(label: f.rawValue, isActive: filter == f) {
//                         filter = f
//                     }
//                 }
//             }

//             Spacer()

//             Text("\(channelCount) channels")
//                 .font(NS.Font.caption)
//                 .foregroundStyle(NS.text3)

//             // View toggle
//             HStack(spacing: 0) {
//                 ViewToggleBtn(icon: "square.grid.2x2", isActive: viewMode == .grid) { viewMode = .grid }
//                 ViewToggleBtn(icon: "list.bullet", isActive: viewMode == .list)     { viewMode = .list }
//             }
//             .background(NS.surface2)
//             .clipShape(RoundedRectangle(cornerRadius: 7))
//             .overlay(RoundedRectangle(cornerRadius: 7).stroke(NS.border))
//         }
//         .padding(.horizontal, NS.Spacing.xl)
//         .frame(height: 52)
//         .background(NS.surface)
//     }
// }