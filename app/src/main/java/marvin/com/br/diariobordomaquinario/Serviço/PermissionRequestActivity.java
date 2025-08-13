package marvin.com.br.diariobordomaquinario.Serviço;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class PermissionRequestActivity extends Activity {

    private static final int PERMISSIONS_REQUEST_CODE = 100;

    @RequiresApi(api = Build.VERSION_CODES.Q)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Solicitar permissões de localização
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.FOREGROUND_SERVICE,
                        Manifest.permission.ACCESS_BACKGROUND_LOCATION},
                PERMISSIONS_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permissões concedidas, reinicie o serviço para iniciar atualizações de localização
                Intent serviceIntent = new Intent(this, marvin.com.br.diariobordomaquinario.Serviço.LocationService.class);
                ContextCompat.startForegroundService(this, serviceIntent);
            }
        }
        // Fechar a atividade após a solicitação de permissão
        finish();
    }
}