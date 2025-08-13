package marvin.com.br.diariobordomaquinario;



import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.view.SoundEffectConstants;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.journeyapps.barcodescanner.DecoratedBarcodeView;
import com.journeyapps.barcodescanner.DefaultDecoderFactory;
import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.BarcodeResult;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.ResultPoint;

import java.util.Collections;
import java.util.List;
import com.journeyapps.barcodescanner.Size;


public class QrScannerActivity extends AppCompatActivity
        implements com.journeyapps.barcodescanner.BarcodeCallback {

    private DecoratedBarcodeView barcodeView;
    private static final int REQ_CAMERA = 99;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_qr_scanner);

        barcodeView = findViewById(R.id.barcode_scanner);

        // Só QR Code
        List<BarcodeFormat> formats = Collections.singletonList(BarcodeFormat.QR_CODE);
        barcodeView.getBarcodeView().setDecoderFactory(new DefaultDecoderFactory(formats));

        // Deixa o recorte bem evidente (quadrado ~70% da tela)
        barcodeView.getBarcodeView()
                .setFramingRectSize(new Size(900, 900)); // da lib journeyapps

        barcodeView.setStatusText(""); // remove label padrão

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
        } else {
            barcodeView.decodeContinuous(this);
            barcodeView.resume();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        barcodeView.resume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        barcodeView.pause();
    }

    @Override
    public void barcodeResult(BarcodeResult result) {
        if (result == null || result.getText() == null) return;

        // Haptic/som pra feedback
        barcodeView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        barcodeView.playSoundEffect(SoundEffectConstants.CLICK);

        // Retorna pra Activity anterior com o texto lido
        Intent data = new Intent();
        data.putExtra("qr_text", result.getText());
        setResult(Activity.RESULT_OK, data);
        finish();
    }

    @Nullable
    public static Integer extrairIdVeiculoViaUri(String url) {
        if (url == null) return null;
        try {
            Uri uri = Uri.parse(url.trim());
            List<String> segs = uri.getPathSegments(); // ex: ["adm","home","173"]
            int i = segs.indexOf("home");
            if (i >= 0 && i + 1 < segs.size()) {
                String id = segs.get(i + 1);
                if (id.matches("\\d+")) return Integer.valueOf(id);
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }


    @Override
    public void possibleResultPoints(List<ResultPoint> resultPoints) {}

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_CAMERA && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            barcodeView.decodeContinuous(this);
            barcodeView.resume();
        } else {
            Toast.makeText(this, "Permissão da câmera negada.", Toast.LENGTH_SHORT).show();
            finish();
        }
    }
}
