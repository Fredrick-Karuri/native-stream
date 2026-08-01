// Protocol/Envelope+Proto.swift
//
// Bridges Stream_V1_Envelope's payload_json string to typed proto
// payload messages (Stream_V1_PlayPayload, etc.).

import Foundation
import SwiftProtobuf
import SdkGenSwift

extension Stream_V1_Envelope {
    static func encoding<T: SwiftProtobuf.Message>(
        type: Stream_V1_MessageType, from: String, to: String, payload: T
    ) -> Stream_V1_Envelope? {
        guard let json = try? payload.jsonString() else { return nil }

        var envelope = Stream_V1_Envelope()
        envelope.type = type
        envelope.from = from
        envelope.to = to
        envelope.payloadJson = json
        return envelope
    }

    func decoding<T: SwiftProtobuf.Message>(as type: T.Type) -> T? {
        try? T(jsonString: payloadJson)
    }
}
