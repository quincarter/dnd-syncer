import Foundation
import AppKit

// MARK: - DND Syncer Native Focus Mode Types

enum DndFocusProfile: String, CaseIterable {
    case dnd = "dnd"
    case work = "work"
    case personal = "personal"
    case sleep = "sleep"
    case presentation = "presentation"
    case gaming = "gaming"
    case driving = "driving"
    case fitness = "fitness"
    case mindfulness = "mindfulness"
    case reading = "reading"
    case writing = "writing"
    case research = "research"
    case creative = "creative"
    case social = "social"
    case family = "family"
    case friends = "friends"

    var title: String {
        switch self {
        case .dnd: return "Do Not Disturb"
        case .work: return "Work"
        case .personal: return "Personal"
        case .sleep: return "Sleep"
        case .presentation: return "Presentation"
        case .gaming: return "Gaming"
        case .driving: return "Driving"
        case .fitness: return "Fitness"
        case .mindfulness: return "Mindfulness"
        case .reading: return "Reading"
        case .writing: return "Writing"
        case .research: return "Research"
        case .creative: return "Creative"
        case .social: return "Social"
        case .family: return "Family"
        case .friends: return "Friends"
        }
    }

    var domainIdentifier: String {
        switch self {
        case .dnd: return "com.apple.focus.donotdisturb"
        case .work: return "com.apple.focus.work"
        case .personal: return "com.apple.focus.personal"
        case .sleep: return "com.apple.focus.sleep"
        case .presentation: return "com.apple.focus.presentation"
        case .gaming: return "com.apple.focus.gaming"
        case .driving: return "com.apple.focus.driving"
        case .fitness: return "com.apple.focus.fitness"
        case .mindfulness: return "com.apple.focus.mindfulness"
        case .reading: return "com.apple.focus.reading"
        case .writing: return "com.apple.focus.writing"
        case .research: return "com.apple.focus.research"
        case .creative: return "com.apple.focus.creative"
        case .social: return "com.apple.focus.social"
        case .family: return "com.apple.focus.family"
        case .friends: return "com.apple.focus.friends"
        }
    }
}

// MARK: - Native Focus Engine

final class NativeFocusEngine {
    private let preferences = UserDefaults.standard

    func updateFocusProfile(_ profile: DndFocusProfile, state: Bool) {
        let ident = profile.domainIdentifier
        if state {
            preferences.set(true, forKey: "focusMode.\(ident).enabled")
            preferences.set(Date(), forKey: "focusMode.\(ident).activatedAt")
            preferences.set(true, forKey: "com.apple.focus.enabled")
            preferences.set(ident, forKey: "com.apple.focus.activeMode")
        } else {
            preferences.removeObject(forKey: "focusMode.\(ident).enabled")
            preferences.removeObject(forKey: "focusMode.\(ident).activatedAt")
            if preferences.string(forKey: "com.apple.focus.activeMode") == ident {
                preferences.set(false, forKey: "com.apple.focus.enabled")
                preferences.removeObject(forKey: "com.apple.focus.activeMode")
            }
        }
        preferences.synchronize()

        NotificationCenter.default.post(
            name: NSNotification.Name("FocusModeChanged"),
            object: nil,
            userInfo: ["identifier": ident, "enabled": state]
        )

        NotificationCenter.default.post(
            name: NSNotification.Name("com.apple.controlcenter.refresh"),
            object: nil
        )
    }

    func isProfileActive(_ profile: DndFocusProfile) -> Bool {
        let ident = profile.domainIdentifier
        let isDirect = preferences.bool(forKey: "focusMode.\(ident).enabled")
        let isGlobal = preferences.bool(forKey: "com.apple.focus.enabled") &&
                       preferences.string(forKey: "com.apple.focus.activeMode") == ident
        return isDirect || isGlobal
    }

    func getCurrentActiveProfile() -> (isActive: Bool, profile: DndFocusProfile?, title: String) {
        for profile in DndFocusProfile.allCases {
            if isProfileActive(profile) {
                return (true, profile, profile.title)
            }
        }
        return (false, nil, "Normal")
    }
}

// MARK: - CLI Dispatcher

let engine = NativeFocusEngine()
let cliArgs = CommandLine.arguments

guard cliArgs.count > 1 else {
    // Return JSON status
    let current = engine.getCurrentActiveProfile()
    let statusPayload: [String: Any] = [
        "isActive": current.isActive,
        "mode": current.profile?.rawValue ?? "off",
        "title": current.title
    ]
    if let jsonData = try? JSONSerialization.data(withJSONObject: statusPayload),
       let jsonStr = String(data: jsonData, encoding: .utf8) {
        print(jsonStr)
    }
    exit(0)
}

let targetCommand = cliArgs[1].lowercased()

if targetCommand == "status" || targetCommand == "json" {
    let current = engine.getCurrentActiveProfile()
    let statusPayload: [String: Any] = [
        "isActive": current.isActive,
        "mode": current.profile?.rawValue ?? "off",
        "title": current.title
    ]
    if let jsonData = try? JSONSerialization.data(withJSONObject: statusPayload),
       let jsonStr = String(data: jsonData, encoding: .utf8) {
        print(jsonStr)
    }
    exit(0)
}

let actionArg = cliArgs.count > 2 ? cliArgs[2].lowercased() : "toggle"

// Resolve mode profile
let matchedProfile: DndFocusProfile
switch targetCommand {
case "work": matchedProfile = .work
case "personal", "relax": matchedProfile = .personal
case "sleep", "bedtime": matchedProfile = .sleep
case "driving": matchedProfile = .driving
case "gaming": matchedProfile = .gaming
case "fitness": matchedProfile = .fitness
case "mindfulness": matchedProfile = .mindfulness
case "reading": matchedProfile = .reading
case "writing": matchedProfile = .writing
case "research": matchedProfile = .research
case "creative": matchedProfile = .creative
case "social": matchedProfile = .social
case "family": matchedProfile = .family
case "friends": matchedProfile = .friends
default: matchedProfile = .dnd
}

switch actionArg {
case "on", "enable", "true", "1":
    engine.updateFocusProfile(matchedProfile, state: true)
    print("ENABLED:\(matchedProfile.rawValue)")
case "off", "disable", "false", "0":
    // Disable active profiles
    for profile in DndFocusProfile.allCases {
        engine.updateFocusProfile(profile, state: false)
    }
    print("DISABLED")
case "toggle":
    let current = engine.isProfileActive(matchedProfile)
    engine.updateFocusProfile(matchedProfile, state: !current)
    print("TOGGLED:\(matchedProfile.rawValue):\( !current)")
default:
    print("UNKNOWN_ACTION")
}
