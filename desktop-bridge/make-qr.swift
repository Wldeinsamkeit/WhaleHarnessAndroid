import AppKit
import CoreImage
import Foundation

guard CommandLine.arguments.count == 2,
      let message = CommandLine.arguments[1].data(using: .utf8),
      let filter = CIFilter(name: "CIQRCodeGenerator") else {
    exit(1)
}

filter.setValue(message, forKey: "inputMessage")
filter.setValue("M", forKey: "inputCorrectionLevel")
guard let output = filter.outputImage?.transformed(by: CGAffineTransform(scaleX: 10, y: 10)) else {
    exit(1)
}

let representation = NSBitmapImageRep(ciImage: output)
guard let data = representation.representation(using: .png, properties: [:]) else {
    exit(1)
}

let path = FileManager.default.temporaryDirectory.appendingPathComponent("whale-harness-pairing.png")
try data.write(to: path, options: .atomic)
print(path.path)
