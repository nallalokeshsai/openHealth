package app.openhealth.core.seam

/**
 * One camera frame in NV21, the one layout both bundled decoders accept. The frame is
 * produced by the Android camera and handed to the decoder on the analysis thread; no
 * bitmap is kept (`pr.md` §6.3, §6.23).
 */
class BarcodeFrame(val nv21: ByteArray, val width: Int, val height: Int, val rotationDegrees: Int)

/** Reads a barcode from a frame. Implementations are flavour-specific (ML Kit or ZXing). */
interface BarcodeDecoder {
    /** The first plausible barcode, or null when the frame has none. */
    fun decode(frame: BarcodeFrame): String?

    fun close() {}
}
