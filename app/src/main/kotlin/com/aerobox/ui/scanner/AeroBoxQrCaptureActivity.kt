package com.aerobox.ui.scanner

import com.aerobox.R
import com.journeyapps.barcodescanner.CaptureActivity
import com.journeyapps.barcodescanner.DecoratedBarcodeView

class AeroBoxQrCaptureActivity : CaptureActivity() {
    override fun initializeContent(): DecoratedBarcodeView {
        setContentView(R.layout.aerobox_zxing_capture)
        return findViewById(R.id.zxing_barcode_scanner)
    }
}
