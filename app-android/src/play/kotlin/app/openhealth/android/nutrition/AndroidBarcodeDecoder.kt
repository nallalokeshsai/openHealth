package app.openhealth.android.nutrition

import app.openhealth.core.seam.BarcodeDecoder
import app.openhealth.core.seam.BarcodeFrame
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The Play Store build's decoder: the bundled ML Kit barcode model (`Tech-Stack.md` §8).
 * The model ships inside the app, so a scan works offline and needs no Play Services at
 * runtime. The F-Droid build uses [app.openhealth.android.nutrition.AndroidBarcodeDecoder]
 * from its own source set instead.
 */
class AndroidBarcodeDecoder : BarcodeDecoder {
    private val scanner: BarcodeScanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_EAN_13,
                Barcode.FORMAT_EAN_8,
                Barcode.FORMAT_UPC_A,
                Barcode.FORMAT_UPC_E,
                Barcode.FORMAT_CODE_128,
                Barcode.FORMAT_CODE_39,
                Barcode.FORMAT_ITF,
            )
            .build(),
    )

    override fun decode(frame: BarcodeFrame): String? {
        val image = InputImage.fromByteBuffer(
            ByteBuffer.wrap(frame.nv21),
            frame.width,
            frame.height,
            frame.rotationDegrees,
            InputImage.IMAGE_FORMAT_NV21,
        )
        val latch = CountDownLatch(1)
        var value: String? = null
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                value = barcodes.firstOrNull { it.rawValue != null }?.rawValue
                latch.countDown()
            }
            .addOnFailureListener { latch.countDown() }
        latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        return value
    }

    override fun close() {
        scanner.close()
    }

    private companion object {
        const val TIMEOUT_SECONDS = 2L
    }
}
