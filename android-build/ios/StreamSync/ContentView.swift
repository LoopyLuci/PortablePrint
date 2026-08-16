import SwiftUI

// MARK: - Content View (Main Tab View)

struct ContentView: View {
    @EnvironmentObject private var appState: AppState
    @State private var selectedTab: Tab = .dashboard

    enum Tab: String, CaseIterable {
        case dashboard = "Dashboard"
        case devices = "Devices"
        case transfers = "Transfers"
        case streams = "Streams"
        case settings = "Settings"

        var icon: String {
            switch self {
            case .dashboard:  return "square.grid.2x2"
            case .devices:    return "antenna.radiowaves.left.and.right"
            case .transfers:  return "arrow.up.arrow.down"
            case .streams:    return "play.rectangle"
            case .settings:   return "gearshape"
            }
        }

        var selectedIcon: String {
            icon + ".fill"
        }
    }

    var body: some View {
        TabView(selection: $selectedTab) {
            DashboardView()
                .tabItem {
                    Label(Tab.dashboard.rawValue,
                          systemImage: selectedTab == .dashboard ? Tab.dashboard.selectedIcon : Tab.dashboard.icon)
                }
                .tag(Tab.dashboard)

            DevicesView()
                .tabItem {
                    Label(Tab.devices.rawValue,
                          systemImage: selectedTab == .devices ? Tab.devices.selectedIcon : Tab.devices.icon)
                }
                .tag(Tab.devices)

            TransferView()
                .tabItem {
                    Label(Tab.transfers.rawValue,
                          systemImage: selectedTab == .transfers ? Tab.transfers.selectedIcon : Tab.transfers.icon)
                }
                .tag(Tab.transfers)

            StreamView()
                .tabItem {
                    Label(Tab.streams.rawValue,
                          systemImage: selectedTab == .streams ? Tab.streams.selectedIcon : Tab.streams.icon)
                }
                .tag(Tab.streams)

            SettingsView()
                .tabItem {
                    Label(Tab.settings.rawValue,
                          systemImage: selectedTab == .settings ? Tab.settings.selectedIcon : Tab.settings.icon)
                }
                .tag(Tab.settings)
        }
        .accentColor(.blue)
        .onAppear {
            configureTabBarAppearance()
        }
    }

    private func configureTabBarAppearance() {
        let appearance = UITabBarAppearance()
        appearance.configureWithDefaultBackground()
        appearance.backgroundColor = UIColor.systemBackground

        UITabBar.appearance().standardAppearance = appearance
        UITabBar.appearance().scrollEdgeAppearance = appearance
    }
}

// MARK: - Preview

struct ContentView_Previews: PreviewProvider {
    static var previews: some View {
        ContentView()
            .environmentObject(AppState())
            .preferredColorScheme(.dark)
    }
}
