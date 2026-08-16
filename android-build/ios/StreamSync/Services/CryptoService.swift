import Foundation
import CryptoKit
import CommonCrypto

// MARK: - Crypto Service

final class CryptoService {
    // MARK: - Key Management

    private var privateKey: P256.KeyAgreement.PrivateKey?
    private var publicKey: P256.KeyAgreement.PublicKey?

    var publicKeyData: Data? {
        publicKey?.derRepresentation
    }

    init() {
        generateKeys()
    }

    private func generateKeys() {
        do {
            privateKey = P256.KeyAgreement.PrivateKey()
            publicKey = privateKey?.publicKey
        } catch {
            print("Failed to generate keys: \(error)")
        }
    }

    // MARK: - Symmetric Encryption (AES-256-GCM)

    func encryptAESGCM(data: Data, key: SymmetricKey) throws -> Data {
        let sealedBox = try AES.GCM.seal(data, using: key)
        return sealedBox.combined ?? Data()
    }

    func decryptAESGCM(encryptedData: Data, key: SymmetricKey) throws -> Data {
        let sealedBox = try AES.GCM.SealedBox(combined: encryptedData)
        return try AES.GCM.open(sealedBox, using: key)
    }

    func generateSymmetricKey() -> SymmetricKey {
        SymmetricKey(size: .bits256)
    }

    // MARK: - ChaCha20-Poly1305

    func encryptChaChaPoly(data: Data, key: SymmetricKey) throws -> Data {
        let sealedBox = try ChaChaPoly.seal(data, using: key)
        return sealedBox.combined ?? Data()
    }

    func decryptChaChaPoly(encryptedData: Data, key: SymmetricKey) throws -> Data {
        let sealedBox = try ChaChaPoly.SealedBox(combined: encryptedData)
        return try ChaChaPoly.open(sealedBox, using: key)
    }

    // MARK: - Hashing

    func sha256(data: Data) -> Data {
        SHA256.hash(data: data).withUnsafeBytes { Data($0) }
    }

    func sha256(string: String) -> Data {
        sha256(data: Data(string.utf8))
    }

    // MARK: - Key Exchange

    func deriveSharedSecret(with publicKeyData: Data) throws -> SymmetricKey {
        guard let privateKey = privateKey else {
            throw CryptoError.keysNotGenerated
        }

        let peerPublicKey = try P256.KeyAgreement.PublicKey(derRepresentation: publicKeyData)
        let sharedSecret = try privateKey.sharedSecretFromKeyAgreement(with: peerPublicKey)

        return sharedSecret.hkdfDerivedSymmetricKey(
            using: SHA256.self,
            salt: "StreamSync".data(using: .utf8)!,
            sharedInfo: Data(),
            outputByteCount: 32
        )
    }

    // MARK: - CRC32C (for chunk verification)

    func crc32c(data: Data) -> UInt32 {
        return data.withUnsafeBytes { ptr in
            var crc: UInt32 = 0xFFFFFFFF
            let table = generateCRC32CTable()
            for byte in ptr.bindMemory(to: UInt8.self) {
                crc = table[Int((crc ^ UInt32(byte)) & 0xFF)] ^ (crc >> 8)
            }
            return crc ^ 0xFFFFFFFF
        }
    }

    private func generateCRC32CTable() -> [UInt32] {
        var table = [UInt32](repeating: 0, count: 256)
        for i in 0..<256 {
            var crc = UInt32(i)
            for _ in 0..<8 {
                if crc & 1 != 0 {
                    crc = 0x82F63B78 ^ (crc >> 1)
                } else {
                    crc >>= 1
                }
            }
            table[i] = crc
        }
        return table
    }
}

// MARK: - Errors

enum CryptoError: Error, LocalizedError {
    case keysNotGenerated
    case encryptionFailed
    case decryptionFailed
    case invalidKey
    case unsupportedScheme

    var errorDescription: String? {
        switch self {
        case .keysNotGenerated:     return "Crypto keys not yet generated"
        case .encryptionFailed:     return "Encryption operation failed"
        case .decryptionFailed:     return "Decryption operation failed"
        case .invalidKey:          return "Invalid key data"
        case .unsupportedScheme:   return "Unsupported encryption scheme"
        }
    }
}
