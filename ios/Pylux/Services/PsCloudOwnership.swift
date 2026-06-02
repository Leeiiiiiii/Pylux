// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

import Foundation

/// Raw entitlement fields from Sony internal_entitlements API.
struct PsCloudEntitlement {
    let id: String
    let productId: String
    let activeFlag: Bool
    let packageType: String
    let name: String
}

enum PsCloudOwnership {
    static let pageSize = 300
    static let pageCooldownSeconds: TimeInterval = 0.1

    private struct CatalogIndex {
        var byProductId: [String: Int] = [:]
        var byConceptId: [String: Int] = [:]
    }

    static func filterOwnedPs5Games(_ entitlements: [PsCloudEntitlement]) -> [PsCloudEntitlement] {
        entitlements.filter { ent in
            guard ent.packageType == "PSGD" else { return false }
            guard ent.activeFlag else { return false }
            let pid = ent.productId
            guard !pid.hasPrefix("IP"), !pid.hasPrefix("SUB") else { return false }
            return true
        }
    }

    static func crossReferenceOwnedGames(
        filteredEntitlements: [PsCloudEntitlement],
        publicCatalog: [CloudGame],
        plusLibrarySupplement: [CloudGame] = [],
        productIdAliases: [String: String] = [:],
        componentIdsByProductId: [String: [String]] = [:]
    ) -> [CloudGame] {
        var catalogMap: [String: CloudGame] = [:]
        for game in publicCatalog {
            catalogMap[game.id] = game
        }
        for (alias, canonical) in productIdAliases {
            if catalogMap[alias] != nil { continue }
            if let meta = catalogMap[canonical] {
                catalogMap[alias] = meta
            }
        }
        var supplementMap: [String: CloudGame] = [:]
        for game in plusLibrarySupplement {
            supplementMap[game.id] = game
        }

        let browseStableKey = buildStableKeyIndex(publicCatalog)
        let supplementStableKey = buildStableKeyIndex(plusLibrarySupplement)

        var byKey: [String: CloudGame] = [:]

        func emitMatch(meta: CloudGame, ent: PsCloudEntitlement) {
            let displayName = meta.name.isEmpty ? ent.name : meta.name
            let game = CloudGame(
                productId: meta.id,
                name: displayName,
                imageUrl: meta.imageUrl,
                landscapeImageUrl: meta.landscapeImageUrl,
                platform: meta.platform,
                serviceType: meta.serviceType,
                conceptUrl: meta.conceptUrl,
                conceptId: meta.conceptId,
                isOwned: true,
                entitlementId: ent.id,
                storeProductId: ent.productId
            )

            let key = ownedDedupeKey(meta: meta, ent: ent)
            if let existing = byKey[key] {
                byKey[key] = preferOwnedEntry(existing: existing, candidate: game)
            } else {
                byKey[key] = game
            }
        }

        for ent in filteredEntitlements {
            let skipStableDemo = ent.name.localizedCaseInsensitiveContains("demo")
            var matches: [CloudGame] = []

            if !ent.productId.isEmpty, let g = catalogMap[ent.productId] {
                matches.append(g)
            } else if !ent.id.isEmpty, let g = catalogMap[ent.id] {
                matches.append(g)
            } else if !ent.productId.isEmpty, ent.id == ent.productId,
                      let g = supplementMap[ent.productId] {
                matches.append(g)
            } else {
                let entitlementStableKey = productIdStableKey(ent.id)
                if let entitlementStableKey, !skipStableDemo,
                   let g = browseStableKey[entitlementStableKey] {
                    matches.append(g)
                } else if let entitlementStableKey, !skipStableDemo,
                          let g = supplementStableKey[entitlementStableKey] {
                    matches.append(g)
                }
            }

            if matches.isEmpty {
                var seenProductIds: Set<String> = []
                for siblingId in componentIdsByProductId[ent.productId] ?? [] {
                    let siblingMeta: CloudGame?
                    if let g = catalogMap[siblingId] {
                        siblingMeta = g
                    } else if let g = supplementMap[siblingId] {
                        siblingMeta = g
                    } else if let siblingStableKey = productIdStableKey(siblingId), !skipStableDemo {
                        siblingMeta = browseStableKey[siblingStableKey]
                            ?? supplementStableKey[siblingStableKey]
                    } else {
                        siblingMeta = nil
                    }

                    guard let meta = siblingMeta else { continue }
                    if meta.id.isEmpty || seenProductIds.contains(meta.id) { continue }
                    seenProductIds.insert(meta.id)
                    matches.append(meta)
                }
            }

            if matches.isEmpty { continue }

            for meta in matches {
                emitMatch(meta: meta, ent: ent)
            }
        }

        return Array(byKey.values)
    }

    private static func ownedDedupeKey(meta: CloudGame, ent: PsCloudEntitlement) -> String {
        if !meta.conceptId.isEmpty { return "c:\(meta.conceptId)" }
        if !meta.id.isEmpty { return "p:\(meta.id)" }
        if !ent.id.isEmpty { return "e:\(ent.id)" }
        return "u:\(meta.id):\(ent.id)"
    }

    private static func preferOwnedEntry(existing: CloudGame, candidate: CloudGame) -> CloudGame {
        if existing.entitlementId.isEmpty, !candidate.entitlementId.isEmpty {
            return candidate
        }
        return existing
    }

    /// Tokenize on '-' and '_'; identity is all tokens except the last (store SKU).
    private static func productIdStableKey(_ productId: String) -> String? {
        guard !productId.isEmpty else { return nil }
        var tokens: [String] = []
        for dashPart in productId.split(separator: "-") {
            for token in dashPart.split(separator: "_") where !token.isEmpty {
                tokens.append(String(token))
            }
        }
        guard tokens.count >= 2 else { return nil }
        return tokens.dropLast().joined(separator: "|")
    }

    private static func buildStableKeyIndex(_ games: [CloudGame]) -> [String: CloudGame] {
        var index: [String: CloudGame] = [:]
        for game in games {
            guard let key = productIdStableKey(game.id) else { continue }
            if index[key] == nil {
                index[key] = game
            }
        }
        return index
    }

    static func mergeOwnedIntoBrowseCatalog(
        browseCatalog: [CloudGame],
        ownedCrossRef: [CloudGame]
    ) -> [CloudGame] {
        var games = browseCatalog
        var catalogIndex = buildCatalogIndex(games)

        for owned in ownedCrossRef {
            let catalogMatch = findCatalogIndexForOwned(owned, catalogIndex: catalogIndex)
            if catalogMatch >= 0 {
                var existing = games[catalogMatch]
                existing.isOwned = true
                if !owned.entitlementId.isEmpty { existing.entitlementId = owned.entitlementId }
                if !owned.storeProductId.isEmpty { existing.storeProductId = owned.storeProductId }
                games[catalogMatch] = existing
                continue
            }

            var entry = owned
            entry.isOwned = true
            registerInCatalogIndex(entry, index: games.count, catalogIndex: &catalogIndex)
            games.append(entry)
        }

        return games.sorted {
            if $0.isOwned != $1.isOwned { return $0.isOwned && !$1.isOwned }
            return $0.name.localizedCaseInsensitiveCompare($1.name) == .orderedAscending
        }
    }

    static func parseEntitlement(_ obj: [String: Any]) -> PsCloudEntitlement? {
        guard let id = obj["id"] as? String, !id.isEmpty else { return nil }
        let gameMeta = obj["game_meta"] as? [String: Any] ?? [:]
        let name = (gameMeta["name"] as? String) ?? id
        return PsCloudEntitlement(
            id: id,
            productId: (obj["product_id"] as? String) ?? "",
            activeFlag: (obj["active_flag"] as? Bool) ?? false,
            packageType: (gameMeta["package_type"] as? String) ?? "",
            name: name
        )
    }

    private static func buildCatalogIndex(_ games: [CloudGame]) -> CatalogIndex {
        var catalogIndex = CatalogIndex()
        for i in games.indices {
            registerInCatalogIndex(games[i], index: i, catalogIndex: &catalogIndex)
        }
        return catalogIndex
    }

    private static func registerInCatalogIndex(
        _ game: CloudGame,
        index: Int,
        catalogIndex: inout CatalogIndex
    ) {
        if !game.id.isEmpty { catalogIndex.byProductId[game.id] = index }
        if !game.conceptId.isEmpty { catalogIndex.byConceptId[game.conceptId] = index }
        if !game.entitlementId.isEmpty, game.entitlementId != game.id {
            catalogIndex.byProductId[game.entitlementId] = index
        }
    }

    private static func findCatalogIndexForOwned(_ owned: CloudGame, catalogIndex: CatalogIndex) -> Int {
        if !owned.id.isEmpty, let idx = catalogIndex.byProductId[owned.id] { return idx }
        if !owned.entitlementId.isEmpty, let idx = catalogIndex.byProductId[owned.entitlementId] { return idx }
        if !owned.storeProductId.isEmpty, let idx = catalogIndex.byProductId[owned.storeProductId] { return idx }
        if !owned.conceptId.isEmpty, let idx = catalogIndex.byConceptId[owned.conceptId] { return idx }
        return -1
    }
}
