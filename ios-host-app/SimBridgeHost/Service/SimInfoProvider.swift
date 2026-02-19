// SimInfoProvider.swift
// SimBridgeHost
//
// Uses CTTelephonyNetworkInfo to retrieve carrier information.
// Matches Android SimInfoProvider.kt functionality with documented iOS limitations.
//
// iOS LIMITATIONS vs Android:
// - Android's SubscriptionManager provides: SIM slot index, carrier name, phone number.
// - iOS's CTTelephonyNetworkInfo provides: carrier name, MCC, MNC.
// - iOS does NOT expose: SIM slot numbers, phone numbers, or subscription IDs.
// - The serviceSubscriberCellularProviders dictionary is keyed by an opaque service
//   identifier (e.g., "0000000100000001"), not by slot number.
// - Phone number retrieval is not possible via any public iOS API.

import Foundation
import CoreTelephony
import os.log

final class SimInfoProvider {

    private static let logger = Logger(subsystem: "com.simbridge.host", category: "SimInfoProvider")

    private let networkInfo = CTTelephonyNetworkInfo()

    /// Returns a list of active SIM cards with carrier info.
    /// iOS does not provide slot numbers or phone numbers, so these are approximated.
    func getActiveSimCards() -> [SimInfo] {
        guard let providers = networkInfo.serviceSubscriberCellularProviders else {
            Self.logger.warning("No cellular providers available")
            return []
        }

        var sims: [SimInfo] = []
        let sortedKeys = providers.keys.sorted()

        for (index, key) in sortedKeys.enumerated() {
            guard let carrier = providers[key] else { continue }
            let carrierName = carrier.carrierName ?? "Unknown"

            // iOS does not expose SIM slot numbers. We use 1-based index as approximation.
            let slot = index + 1

            // iOS does not expose phone numbers via any public API.
            // On Android, SubscriptionManager.getPhoneNumber() provides this.
            let number: String? = nil

            let simInfo = SimInfo(
                slot: slot,
                carrier: carrierName,
                number: number
            )
            sims.append(simInfo)

            Self.logger.info("SIM \(slot): \(carrierName) MCC=\(carrier.mobileCountryCode ?? "?") MNC=\(carrier.mobileNetworkCode ?? "?")")
        }

        if sims.isEmpty {
            Self.logger.info("No active SIM cards detected (may be running on simulator)")
        }

        return sims
    }

    /// Returns carrier details for a given service key.
    /// On iOS, there is no concept of "subscription for slot" like Android.
    /// This method attempts to find the carrier by matching our synthetic slot index.
    func getCarrierForSlot(_ slot: Int) -> CTCarrier? {
        guard let providers = networkInfo.serviceSubscriberCellularProviders else {
            return nil
        }
        let sortedKeys = providers.keys.sorted()
        let index = slot - 1
        guard index >= 0, index < sortedKeys.count else { return nil }
        return providers[sortedKeys[index]]
    }

    /// Returns the current radio access technology for the primary service.
    func currentRadioAccessTechnology() -> String? {
        if let techDict = networkInfo.serviceCurrentRadioAccessTechnology,
           let firstTech = techDict.values.first {
            return radioTechDisplayName(firstTech)
        }
        return nil
    }

    private func radioTechDisplayName(_ tech: String) -> String {
        switch tech {
        case CTRadioAccessTechnologyLTE: return "LTE"
        case CTRadioAccessTechnologyNRNSA, CTRadioAccessTechnologyNR: return "5G"
        case CTRadioAccessTechnologyWCDMA: return "3G (WCDMA)"
        case CTRadioAccessTechnologyHSDPA: return "3G (HSDPA)"
        case CTRadioAccessTechnologyHSUPA: return "3G (HSUPA)"
        case CTRadioAccessTechnologyEdge: return "2G (EDGE)"
        case CTRadioAccessTechnologyGPRS: return "2G (GPRS)"
        default: return tech
        }
    }
}
