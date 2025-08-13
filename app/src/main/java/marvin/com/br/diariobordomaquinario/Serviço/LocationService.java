package marvin.com.br.diariobordomaquinario.Serviço;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

import marvin.com.br.diariobordomaquinario.MainActivity;
import marvin.com.br.diariobordomaquinario.R;


public class LocationService extends Service {

    private static final String CHANNEL_ID = "LocationServiceChannel";
    private static final String TAG = "LocationService";

    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;

    @Override
    public void onCreate() {
        super.onCreate();

        Log.d(TAG, "onCreate: Service created");

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult != null) {
                    for (android.location.Location location : locationResult.getLocations()) {
                        Log.d(TAG, "onLocationResult: Location - " + location.toString());
                    }
                }
            }
        };


    }

    @SuppressLint("MissingPermission")
    private void startLocationUpdates() {
        Log.d(TAG, "startLocationUpdates: Starting location updates");

        LocationRequest locationRequest = LocationRequest.create();
        locationRequest.setInterval(5000);
        locationRequest.setFastestInterval(5000);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "startLocationUpdates: Missing location permissions");
            return;
        }

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null);
        Log.d(TAG, "startLocationUpdates: Location updates started");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand: Service started");

        createNotificationChannel();

        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Localização")
                .setContentText("Capturando localização em segundo plano")
                .setSmallIcon(R.drawable.ic_location)
                .setContentIntent(pendingIntent)
                .build();

        startForeground(1, notification);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "onStartCommand: Location permissions not granted");
            stopSelf();
            return START_NOT_STICKY;
        }

        startLocationUpdates();

        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy: Service destroyed");
        fusedLocationClient.removeLocationUpdates(locationCallback);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Location Service Channel",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(serviceChannel);
            Log.d(TAG, "createNotificationChannel: Notification channel created");
        }
    }
}
