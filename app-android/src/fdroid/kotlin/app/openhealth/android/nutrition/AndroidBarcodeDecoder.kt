package app.openhealth.android.nutrition

import app.openhealth.core.seam.BarcodeDecoder
import app.openhealth.core.seam.BarcodeFrame
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer

/**
 * The F-Droid build's decoder: ZXing, Apache-2.0 and pure Java. The Play build ships the
 * proprietary bundled ML Kit model instead, which F-Droid cannot redistribute; both sit
 * behind the same [BarcodeDecoder] seam and the same CameraX pipeline.
 */
class AndroidBarcodeDecoder : BarcodeDecoder {
    private val reader = MultiFormatReader().apply {
        setHints(
            mapOf(
                DecodeHintType.TRY_HARDER to true,
                DecodeHintType.POSSIBLE_FORMATS to listOf(
                    com.google.zxing.BarcodeFormat.EAN_13,
                    com.google.zxing.BarcodeFormat.EAN_8,
                    com.google.zxing.BarcodeFormat.UPC_A,
                    com.google.zxing.BarcodeFormat.UPC_E,
                    com.google.zxing.BarcodeFormat.CODE_128,
                    com.google.zxing.BarcodeFormat.CODE_39,
                    com.google.zxing.BarcodeFormat.ITF,
                ),
            ),
        )
    }

    override fun decode(frame: BarcodeFrame): String? {
        val source = PlanarYUVLuminanceSource(
            frame.nv21,
            frame.width,
            frame.height,
            0,
            0,
            frame.width,
            frame.height,
            false,
        )
        decode(source)?.let { return it }
        // A phone held upright shows a horizontal barcode rotated in the sensor frame.
        return decode(source.rotateCounterClockwise())
    }

    private fun decode(source: LuminanceSource): String? = runCatching {
        reader.decodeWithState(BinaryBitmap(HybridBinarizer(source)))?.text
    }.getOrNull().also { reader.reset() }

    override fun close() {
        reader.reset()
    }
}
