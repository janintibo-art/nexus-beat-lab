package com.nexus.beatlab;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

public class MainActivity extends Activity {

    private static final int FILE_REQUEST = 1;
    private WebView webView;
    private ValueCallback<Uri[]> fileCallback;
    private final StringBuilder expBuf = new StringBuilder();
    private String expName = "export.wav";

    /** Pont JS → Android pour enregistrer les fichiers exportés (WAV, JSON). */
    class ExportBridge {
        @JavascriptInterface
        public void begin(String name) { expBuf.setLength(0); expName = name; }

        @JavascriptInterface
        public void append(String chunk) { expBuf.append(chunk); }

        @JavascriptInterface
        public String end() {
            try {
                byte[] data = Base64.decode(expBuf.toString(), Base64.DEFAULT);
                expBuf.setLength(0);
                if (Build.VERSION.SDK_INT >= 29) {
                    ContentValues cv = new ContentValues();
                    cv.put(MediaStore.Downloads.DISPLAY_NAME, expName);
                    cv.put(MediaStore.Downloads.MIME_TYPE,
                            expName.endsWith(".wav") ? "audio/wav" : "application/json");
                    Uri uri = getContentResolver()
                            .insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                    OutputStream os = getContentResolver().openOutputStream(uri);
                    os.write(data); os.close();
                    return "ok:T\u00e9l\u00e9chargements/" + expName;
                } else {
                    File f = new File(getExternalFilesDir(null), expName);
                    FileOutputStream fo = new FileOutputStream(f);
                    fo.write(data); fo.close();
                    return "ok:" + f.getAbsolutePath();
                }
            } catch (Exception e) {
                expBuf.setLength(0);
                return "err:" + e.getMessage();
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Garder l'écran allumé pendant qu'on joue
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Autorisation micro (module AUDIO)
        if (Build.VERSION.SDK_INT >= 23
                && checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
                   != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO}, 2);
        }

        webView = new WebView(this);
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        // L'audio démarre sans geste obligatoire
        s.setMediaPlaybackRequiresUserGesture(false);
        // Zoom avec les doigts (pincer)
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        // L'interface (conçue en paysage) s'adapte à la largeur de l'écran
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setAllowFileAccess(true);
        // Autoriser la lecture des samples WAV embarqués (XHR sur file://)
        s.setAllowFileAccessFromFileURLs(true);
        s.setAllowUniversalAccessFromFileURLs(true);

        // Pont d'export des fichiers (WAV, projet JSON)
        webView.addJavascriptInterface(new ExportBridge(), "AndroidExport");

        // Sélecteur de fichiers pour charger des samples audio
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final android.webkit.PermissionRequest request) {
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        // Le micro est déjà accordé au niveau Android : on autorise la page
                        request.grant(request.getResources());
                    }
                });
            }

            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                try {
                    Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    i.setType("audio/*");
                    startActivityForResult(Intent.createChooser(i, "Choisir un sample"), FILE_REQUEST);
                } catch (Exception e) {
                    fileCallback = null;
                    return false;
                }
                return true;
            }
        });

        setContentView(webView);
        webView.setBackgroundColor(0xFF0D0D10);
        webView.loadUrl("file:///android_asset/www/index.html");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == FILE_REQUEST && fileCallback != null) {
            fileCallback.onReceiveValue(
                    WebChromeClient.FileChooserParams.parseResult(resultCode, data));
            fileCallback = null;
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemUI();
    }

    private void hideSystemUI() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
              | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
              | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
              | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
              | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
              | View.SYSTEM_UI_FLAG_FULLSCREEN);
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
