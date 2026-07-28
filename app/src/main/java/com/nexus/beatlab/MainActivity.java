package com.nexus.beatlab;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.media.midi.MidiDevice;
import android.media.midi.MidiDeviceInfo;
import android.media.midi.MidiManager;
import android.media.midi.MidiOutputPort;
import android.media.midi.MidiReceiver;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
    private MidiDevice midiDev;
    private MidiOutputPort midiOut;

    /** Pont MIDI natif — architecture reprise du MidiEngine de FabKorg. */
    class MidiBridge {
        private MidiManager mgr() {
            return (MidiManager) getSystemService(MIDI_SERVICE);
        }

        @JavascriptInterface
        public String listDevices() {
            try {
                MidiDeviceInfo[] infos = mgr().getDevices();
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < infos.length; i++) {
                    MidiDeviceInfo in = infos[i];
                    String name = in.getProperties()
                            .getString(MidiDeviceInfo.PROPERTY_NAME, "Appareil MIDI");
                    if (i > 0) sb.append(",");
                    sb.append("{\"id\":").append(in.getId())
                      .append(",\"name\":\"").append(name.replace("\"", " ").replace("\\", " "))
                      .append("\",\"outputs\":").append(in.getOutputPortCount()).append("}");
                }
                return sb.append("]").toString();
            } catch (Exception e) { return "[]"; }
        }

        @JavascriptInterface
        public void connect(final int id) {
            try {
                for (MidiDeviceInfo in : mgr().getDevices()) {
                    if (in.getId() != id) continue;
                    mgr().openDevice(in, new MidiManager.OnDeviceOpenedListener() {
                        @Override public void onDeviceOpened(MidiDevice device) {
                            if (device == null) { jsMidiStatus("erreur d'ouverture"); return; }
                            closeMidi();
                            midiDev = device;
                            midiOut = device.openOutputPort(0);
                            if (midiOut != null) {
                                midiOut.connect(new ParserReceiver());
                                jsMidiStatus("connecte");
                            } else jsMidiStatus("pas de port de sortie");
                        }
                    }, new Handler(Looper.getMainLooper()));
                    return;
                }
                jsMidiStatus("appareil introuvable");
            } catch (Exception e) { jsMidiStatus("erreur"); }
        }

        @JavascriptInterface
        public void disconnect() { closeMidi(); jsMidiStatus("deconnecte"); }
    }

    private void closeMidi() {
        try { if (midiOut != null) midiOut.close(); } catch (Exception e) {}
        try { if (midiDev != null) midiDev.close(); } catch (Exception e) {}
        midiOut = null; midiDev = null;
    }

    /** Analyse des flux MIDI avec running status (comme dans FabKorg). */
    class ParserReceiver extends MidiReceiver {
        private int status = 0, d1 = -1;
        @Override
        public void onSend(byte[] msg, int off, int cnt, long ts) {
            for (int i = 0; i < cnt; i++) {
                int b = msg[off + i] & 0xFF;
                if (b >= 0xF8) continue;                 // horloge/temps réel : ignoré ici
                if (b >= 0x80) { status = b; d1 = -1; continue; }
                if (status == 0 || status >= 0xF0) continue;
                int cmd = status & 0xF0;
                if (cmd == 0xC0 || cmd == 0xD0) { sendMidiJs(status, b, 0); }
                else if (d1 < 0) { d1 = b; }
                else { sendMidiJs(status, d1, b); d1 = -1; }
            }
        }
    }

    private void sendMidiJs(final int s, final int a, final int b) {
        runOnUiThread(new Runnable() {
            @Override public void run() {
                if (webView != null) webView.evaluateJavascript(
                    "window.onNativeMidi&&window.onNativeMidi(" + s + "," + a + "," + b + ")", null);
            }
        });
    }

    private void jsMidiStatus(final String st) {
        runOnUiThread(new Runnable() {
            @Override public void run() {
                if (webView != null) webView.evaluateJavascript(
                    "window.onMidiStatus&&window.onMidiStatus('" + st + "')", null);
            }
        });
    }

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
                            expName.endsWith(".wav") ? "audio/wav"
                          : expName.endsWith(".mid") ? "audio/midi"
                          : expName.endsWith(".json") ? "application/json"
                          : "application/zip");
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
        // Pont MIDI natif (claviers et machines Korg en USB)
        webView.addJavascriptInterface(new MidiBridge(), "AndroidMidi");

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
                    i.setType("*/*");
                    i.putExtra(Intent.EXTRA_MIME_TYPES,
                            new String[]{"audio/*","application/zip","application/json","application/octet-stream"});
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
