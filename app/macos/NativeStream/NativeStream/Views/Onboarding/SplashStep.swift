//
//  SplashStep.swift
//
import SwiftUI


struct SplashStep: View {
    let onComplete: () -> Void
    @State private var opacity = 0.0

    var body: some View {
        VStack(spacing: NS.Spacing.xl) {
            Image(systemName: "play.tv.fill")
                .font(.system(size: 56))
                .foregroundStyle(NS.accent)
            Text("NativeStream")
                .font(NS.Font.display)
                .foregroundStyle(NS.text)
            Text("Your live TV. On every screen.")
                .font(NS.Font.body)
                .foregroundStyle(NS.text3)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(NS.bg)
        .opacity(opacity)
        .task {
            withAnimation(.easeIn(duration: 0.4)) { opacity = 1.0 }
            try? await Task.sleep(for: .seconds(2))
            onComplete()
        }
    }
}
