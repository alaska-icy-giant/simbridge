// LoginView.swift
// SimBridgeHost
//
// Login screen matching DESIGN_SYSTEM.md Login Form spec.
// Server URL + username + password + login button + spinner.
// Password field has trailing eye icon toggle.
// Full-width primary button with inline spinner when loading.
// Error text in error color below fields.

import SwiftUI
import GoogleSignIn
import LocalAuthentication

struct LoginView: View {
    @ObservedObject var prefs: Prefs
    let onLoginSuccess: () -> Void

    @Environment(\.simBridgeColors) private var colors

    @State private var serverUrl: String = ""
    @State private var username: String = ""
    @State private var password: String = ""
    @State private var passwordVisible: Bool = false
    @State private var isLoading: Bool = false
    @State private var error: String?
    @State private var showBiometricOffer: Bool = false

    private let secureTokenStore = SecureTokenStore()

    private var canUseBiometric: Bool {
        let context = LAContext()
        return context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: nil)
    }

    var body: some View {
        VStack(spacing: 0) {
            Spacer()

            VStack(spacing: SimBridgeSpacing.itemGap) {
                Text("Connect to Relay Server")
                    .font(.title2.weight(.semibold))
                    .foregroundColor(colors.onSurface)

                Spacer()
                    .frame(height: SimBridgeSpacing.spacerLarge - SimBridgeSpacing.itemGap)

                // Server URL field
                VStack(alignment: .leading, spacing: 4) {
                    Text("Server URL")
                        .font(.footnote)
                        .foregroundColor(colors.onSurfaceVariant)
                    TextField("https://relay.example.com", text: $serverUrl)
                        .keyboardType(.URL)
                        .textContentType(.URL)
                        .autocapitalization(.none)
                        .disableAutocorrection(true)
                        .textFieldStyle(.roundedBorder)
                }

                // Username field
                VStack(alignment: .leading, spacing: 4) {
                    Text("Username")
                        .font(.footnote)
                        .foregroundColor(colors.onSurfaceVariant)
                    TextField("Username", text: $username)
                        .textContentType(.username)
                        .autocapitalization(.none)
                        .disableAutocorrection(true)
                        .textFieldStyle(.roundedBorder)
                }

                // Password field with visibility toggle
                VStack(alignment: .leading, spacing: 4) {
                    Text("Password")
                        .font(.footnote)
                        .foregroundColor(colors.onSurfaceVariant)
                    HStack {
                        if passwordVisible {
                            TextField("Password", text: $password)
                                .textContentType(.password)
                                .autocapitalization(.none)
                                .disableAutocorrection(true)
                        } else {
                            SecureField("Password", text: $password)
                                .textContentType(.password)
                        }
                        Button {
                            passwordVisible.toggle()
                        } label: {
                            Image(systemName: passwordVisible ? "eye.slash.fill" : "eye.fill")
                                .foregroundColor(colors.onSurfaceVariant)
                        }
                    }
                    .padding(.horizontal, 8)
                    .padding(.vertical, 6)
                    .background(
                        RoundedRectangle(cornerRadius: 6)
                            .stroke(Color.gray.opacity(0.4), lineWidth: 1)
                    )
                }

                // Error text
                if let error = error {
                    Text(error)
                        .font(.footnote)
                        .foregroundColor(colors.error)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }

                Spacer()
                    .frame(height: SimBridgeSpacing.spacerSmall)

                // Login button
                Button {
                    performLogin()
                } label: {
                    HStack(spacing: SimBridgeSpacing.spacerSmall) {
                        if isLoading {
                            ProgressView()
                                .progressViewStyle(CircularProgressViewStyle(tint: colors.onPrimary))
                                .scaleEffect(0.8)
                        }
                        Text("Login")
                            .fontWeight(.semibold)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                }
                .buttonStyle(.borderedProminent)
                .tint(colors.primary)
                .disabled(isLoading || serverUrl.isEmpty || username.isEmpty || password.isEmpty)

                // Divider
                HStack {
                    Rectangle()
                        .frame(height: 1)
                        .foregroundColor(colors.onSurfaceVariant.opacity(0.3))
                    Text("or")
                        .font(.footnote)
                        .foregroundColor(colors.onSurfaceVariant)
                    Rectangle()
                        .frame(height: 1)
                        .foregroundColor(colors.onSurfaceVariant.opacity(0.3))
                }

                // Google Sign-In
                Button {
                    performGoogleSignIn()
                } label: {
                    HStack(spacing: SimBridgeSpacing.spacerSmall) {
                        if isLoading {
                            ProgressView()
                                .progressViewStyle(CircularProgressViewStyle(tint: colors.onSurfaceVariant))
                                .scaleEffect(0.8)
                        }
                        Text("Sign in with Google")
                            .fontWeight(.medium)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                }
                .buttonStyle(.bordered)
                .disabled(isLoading || serverUrl.isEmpty)
            }
            .padding(SimBridgeSpacing.loginPadding)

            Spacer()
        }
        .navigationTitle("SimBridge Host")
        .navigationBarTitleDisplayMode(.inline)
        .alert("Enable Biometric Unlock?", isPresented: $showBiometricOffer) {
            Button("Enable") {
                secureTokenStore.saveToken(prefs.token)
                prefs.biometricEnabled = true
                onLoginSuccess()
            }
            Button("Not now", role: .cancel) {
                onLoginSuccess()
            }
        } message: {
            Text("Use fingerprint or face recognition to unlock SimBridge next time.")
        }
        .onAppear {
            let savedUrl = prefs.serverUrl
            serverUrl = savedUrl.isEmpty ? "https://" : savedUrl
        }
    }

    // MARK: - Login Logic

    private func performLogin() {
        isLoading = true
        error = nil

        let apiClient = ApiClient(prefs: prefs)
        let trimmedUrl = serverUrl.trimmingCharacters(in: CharacterSet(charactersIn: "/"))

        Task {
            do {
                let loginResponse = try await apiClient.login(
                    serverUrl: trimmedUrl,
                    username: username,
                    password: password
                )

                await MainActor.run {
                    prefs.serverUrl = trimmedUrl
                    prefs.token = loginResponse.token
                }

                // Register this device
                #if targetEnvironment(simulator)
                let deviceName = "iOS Simulator"
                #else
                let deviceName = UIDevice.current.name
                #endif

                do {
                    let deviceResponse = try await apiClient.registerDevice(name: deviceName)
                    await MainActor.run {
                        prefs.deviceId = deviceResponse.id
                        prefs.deviceName = deviceResponse.name
                    }
                } catch {
                    // Device registration is optional -- continue on failure
                }

                await MainActor.run {
                    isLoading = false
                    if canUseBiometric && !prefs.biometricEnabled {
                        showBiometricOffer = true
                    } else {
                        onLoginSuccess()
                    }
                }
            } catch {
                await MainActor.run {
                    isLoading = false
                    self.error = error.localizedDescription
                }
            }
        }
    }

    private func performGoogleSignIn() {
        isLoading = true
        error = nil

        guard let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
              let rootVC = windowScene.windows.first?.rootViewController else {
            error = "Unable to find root view controller"
            isLoading = false
            return
        }

        GIDSignIn.sharedInstance.signIn(withPresenting: rootVC) { result, signInError in
            if let signInError = signInError {
                Task { @MainActor in
                    isLoading = false
                    error = signInError.localizedDescription
                }
                return
            }

            guard let idToken = result?.user.idToken?.tokenString else {
                Task { @MainActor in
                    isLoading = false
                    error = "Failed to get Google ID token"
                }
                return
            }

            let apiClient = ApiClient(prefs: prefs)
            let trimmedUrl = serverUrl.trimmingCharacters(in: CharacterSet(charactersIn: "/"))

            Task {
                do {
                    let loginResponse = try await apiClient.googleLogin(
                        serverUrl: trimmedUrl, idToken: idToken
                    )

                    await MainActor.run {
                        prefs.serverUrl = trimmedUrl
                        prefs.token = loginResponse.token
                    }

                    #if targetEnvironment(simulator)
                    let deviceName = "iOS Simulator"
                    #else
                    let deviceName = UIDevice.current.name
                    #endif

                    do {
                        let deviceResponse = try await apiClient.registerDevice(name: deviceName)
                        await MainActor.run {
                            prefs.deviceId = deviceResponse.id
                            prefs.deviceName = deviceResponse.name
                        }
                    } catch { /* optional */ }

                    await MainActor.run {
                        isLoading = false
                        if canUseBiometric && !prefs.biometricEnabled {
                            showBiometricOffer = true
                        } else {
                            onLoginSuccess()
                        }
                    }
                } catch {
                    await MainActor.run {
                        isLoading = false
                        self.error = error.localizedDescription
                    }
                }
            }
        }
    }
}

// MARK: - Preview

#if DEBUG
struct LoginView_Previews: PreviewProvider {
    static var previews: some View {
        NavigationStack {
            LoginView(prefs: Prefs(), onLoginSuccess: {})
                .simBridgeTheme()
        }
    }
}
#endif
