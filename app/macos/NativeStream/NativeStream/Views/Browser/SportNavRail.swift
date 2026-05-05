import SwiftUI


// MARK: - Sport Nav Rail (UX-010)

struct SportNavRail: View {
    @Binding var selected: SportCategory

    private let topSports: [SportCategory] = [.favourites, .football, .rugby, .tennis, .basketball, .cricket]

    var body: some View {
        VStack(spacing: 4) {
            ForEach(topSports, id: \.self) { sport in
                NavIcon(icon: sport.rawValue, isActive: selected == sport) {
                    selected = sport
                }
            }
            Divider().frame(width: 32).overlay(NS.border).padding(.vertical, 4)
            NavIcon(icon: SportCategory.regions.rawValue, isActive: selected == .regions) {
                selected = .regions
            }
            Spacer()
        }
        .padding(.vertical, 12)
        .frame(width: 64)
        .background(NS.surface)
    }
}