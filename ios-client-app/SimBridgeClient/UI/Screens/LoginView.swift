// LoginView.swift
// Login screen: Server URL + username + password + login button + "Create Account" link.

import SwiftUI
import GoogleSignIn
import LocalAuthentication

struct LoginView: View {
    @EnvironmentObject private var appState: AppState
    @Environment(\.colorScheme) private var colorScheme

    @State private var serverUrl: String = Prefs.serverUrl.isEmpty ? "http://localhost:8100" : Prefs.serverUrl
    @State private var username: String = ""
    @State private var password: String = ""
    @State private var showPassword: Bool = false
    @State private var isLoading: Bool = false
    @State private var errorMessage: String = ""
    @State private var showBiometricOffer: Bool = false

    private let secureTokenStore = SecureTokenStore()

    private var canUseBiometric: Bool {
        let context = LAContext()
        return context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: nil)
    }

    private var colors: AdaptiveColors {
        AdaptiveColors(colorScheme: colorScheme)
    }

    var body: some View {
        ScrollView {
            VStack(spacing: SimBridgeTheme.itemGap) {
                Spacer().frame(height: SimBridgeTheme.spacerLarge)

                // App icon placeholder
                Image(systemName: "antenna.radiowaves.left.and.right")
                    .font(.system(size: 48))
                    .foregroundColor(colors.primary)

                Text("SimBridge Client")
                    .font(.title)
                    .fontWeight(.bold)
                    .foregroundColor(colors.onSurface)

                Text("Connect to Relay Server")
                    .font(.headline)
                    .foregroundColor(colors.onSurface.opacity(0.7))

                Spacer().frame(height: SimBridgeTheme.spacerMedium)

                // Server URL
                VStack(alignment: .leading, spacing: 6) {
                    Text("Server URL")
                        .font(.footnote)
                        .foregroundColor(colors.onSurface.opacity(0.6))
                    TextField("http://localhost:8100", text: $serverUrl)
                        .textFieldStyle(.roundedBorder)
                        .autocapitalization(.none)
                        .disableAutocorrection(true)
                        .keyboardType(.URL)
                }

                // Username
                VStack(alignment: .leading, spacing: 6) {
                    Text("Username")
                        .font(.footnote)
                        .foregroundColor(colors.onSurface.opacity(0.6))
                    TextField("Username", text: $username)
                        .textFieldStyle(.roundedBorder)
                        .autocapitalization(.none)
                        .disableAutocorrection(true)
                }

                // Password
                VStack(alignment: .leading, spacing: 6) {
                    Text("Password")
                        .font(.footnote)
                        .foregroundColor(colors.onSurface.opacity(0.6))
                    HStack {
                        if showPassword {
                            TextField("Password", text: $password)
                                .autocapitalization(.none)
                                .disableAutocorrection(true)
                        } else {
                            SecureField("Password", text: $password)
                        }
                        Button {
                            showPassword.toggle()
                        } label: {
                            Image(systemName: showPassword ? "eye.fill" : "eye.slash.fill")
                                .foregroundColor(colors.onSurface.opacity(0.5))
                        }
                    }
                    .padding(10)
                    .background(
                        RoundedRectangle(cornerRadius: 6)
                            .stroke(Color.gray.opacity(0.3), lineWidth: 1)
                    )
                }

                // Error message
                if !errorMessage.isEmpty {
                    Text(errorMessage)
                        .font(.footnote)
                        .foregroundColor(colors.error)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }

                Spacer().frame(height: SimBridgeTheme.spacerSmall)

                // Login button
                Button {
                    performLogin()
                } label: {
                    HStack(spacing: 8) {
                        if isLoading {
                            ProgressView()
                                .progressViewStyle(CircularProgressViewStyle(tint: colors.onPrimary))
                        }
                        Text("Login")
                    }
                    .primaryButton(colors: colors)
                }
                .disabled(isLoading || username.isEmpty || password.isEmpty || serverUrl.isEmpty)
                .opacity((isLoading || username.isEmpty || password.isEmpty || serverUrl.isEmpty) ? 0.6 : 1.0)

                // Create Account link
                Button {
                    performRegister()
                } label: {
                    Text("Create Account")
                        .font(.body)
                        .foregroundColor(colors.primary)
                }
                .disabled(isLoading)

                // Divider
                HStack {
                    Rectangle()
                        .frame(height: 1)
                        .foregroundColor(colors.onSurface.opacity(0.2))
                    Text("or")
                        .font(.footnote)
                        .foregroundColor(colors.onSurface.opacity(0.5))
                    Rectangle()
                        .frame(height: 1)
                        .foregroundColor(colors.onSurface.opacity(0.2))
                }

                // Google Sign-In
                Button {
                    performGoogleSignIn()
                } label: {
                    HStack(spacing: 8) {
                        if isLoading {
                            ProgressView()
                                .progressViewStyle(CircularProgressViewStyle(tint: colors.onSurface))
                        }
                        Text("Sign in with Google")
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                    .background(
                        RoundedRectangle(cornerRadius: 8)
                            .stroke(colors.primary, lineWidth: 1)
                    )
                    .foregroundColor(colors.primary)
                }
                .disabled(isLoading || serverUrl.isEmpty)
                .opacity((isLoading || serverUrl.isEmpty) ? 0.6 : 1.0)

                Spacer()
            }
            .padding(SimBridgeTheme.loginPadding)
        }
        .background(colors.surface.ignoresSafeArea())
        .alert("Enable Biometric Unlock?", isPresented: $showBiometricOffer) {
            Button("Enable") {
                secureTokenStore.saveToken(Prefs.token)
                Prefs.biometricEnabled = true
            }
            Button("Not now", role: .cancel) {}
        } message: {
            Text("Use fingerprint or face recognition to unlock SimBridge next time.")
        }
    }

    // MARK: - Actions

    private func performLogin() {
        errorMessage = ""
        isLoading = true

        let trimmedUrl = serverUrl.trimmingCharacters(in: .whitespacesAndNewlines)

        Task {
            do {
                let response = try await ApiClient.shared.login(
                    serverUrl: trimmedUrl,
                    username: username,
                    password: password
                )
                appState.completeLogin(token: response.token, serverUrl: trimmedUrl)
                await ensureDeviceRegistered()
                offerBiometricIfAvailable()
            } catch {
                errorMessage = error.localizedDescription
            }
            isLoading = false
        }
    }

    private func performRegister() {
        errorMessage = ""
        isLoading = true

        let trimmedUrl = serverUrl.trimmingCharacters(in: .whitespacesAndNewlines)

        Task {
            do {
                _ = try await ApiClient.shared.register(
                    serverUrl: trimmedUrl,
                    username: username,
                    password: password
                )
                let loginResp = try await ApiClient.shared.login(
                    serverUrl: trimmedUrl,
                    username: username,
                    password: password
                )
                appState.completeLogin(token: loginResp.token, serverUrl: trimmedUrl)
                await ensureDeviceRegistered()
                offerBiometricIfAvailable()
            } catch {
                errorMessage = error.localizedDescription
            }
            isLoading = false
        }
    }

    private func performGoogleSignIn() {
        errorMessage = ""
        isLoading = true

        guard let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
              let rootVC = windowScene.windows.first?.rootViewController else {
            errorMessage = "Unable to find root view controller"
            isLoading = false
            return
        }

        let trimmedUrl = serverUrl.trimmingCharacters(in: .whitespacesAndNewlines)

        GIDSignIn.sharedInstance.signIn(withPresenting: rootVC) { result, error in
            if let error = error {
                Task { @MainActor in
                    self.errorMessage = error.localizedDescription
                    self.isLoading = false
                }
                return
            }

            guard let idToken = result?.user.idToken?.tokenString else {
                Task { @MainActor in
                    self.errorMessage = "Failed to get Google ID token"
                    self.isLoading = false
                }
                return
            }

            Task {
                do {
                    let response = try await ApiClient.shared.googleLogin(
                        serverUrl: trimmedUrl, idToken: idToken
                    )
                    appState.completeLogin(token: response.token, serverUrl: trimmedUrl)
                    await ensureDeviceRegistered()
                    offerBiometricIfAvailable()
                } catch {
                    errorMessage = error.localizedDescription
                }
                isLoading = false
            }
        }
    }

    private func offerBiometricIfAvailable() {
        if canUseBiometric && !Prefs.biometricEnabled {
            showBiometricOffer = true
        }
    }

    private func ensureDeviceRegistered() async {
        // Check if we already have a client device registered
        do {
            let devices = try await ApiClient.shared.listDevices()
            if let existingClient = devices.first(where: { $0.type == "client" }) {
                appState.setDevice(id: existingClient.id, name: existingClient.name)
            } else {
                // Register a new client device
                let deviceName = UIDevice.current.name
                let device = try await ApiClient.shared.registerDevice(name: deviceName)
                appState.setDevice(id: device.id, name: device.name)
            }
        } catch {
            errorMessage = "Device registration failed: \(error.localizedDescription)"
        }
    }
}

#if DEBUG
struct LoginView_Previews: PreviewProvider {
    static var previews: some View {
        LoginView()
            .environmentObject(AppState())
    }
}
#endif
