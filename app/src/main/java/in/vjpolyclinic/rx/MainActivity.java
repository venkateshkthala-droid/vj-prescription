package in.vjpolyclinic.rx;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.core.content.FileProvider;
import androidx.webkit.WebViewAssetLoader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

public class MainActivity extends Activity {
    private static final String HOME = "https://appassets.androidplatform.net/assets/index.html";
    private static final long WINDOW_MS = 15000;

    private WebView web;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private File lastPdf;
    private long lastPdfTime;
    private String pendingWa;
    private long pendingWaTime;

    // Makes <a download href="blob:..."> clicks hand the file to Android.
    private static final String PATCH =
        "(function(){if(window.__vjp)return;window.__vjp=1;var o=HTMLAnchorElement.prototype.click;"
      + "HTMLAnchorElement.prototype.click=function(){var a=this;"
      + "if(a.download&&a.href&&a.href.indexOf('blob:')===0){var fn=a.download;"
      + "fetch(a.href).then(function(r){return r.blob()}).then(function(b){var fr=new FileReader();"
      + "fr.onload=function(){Android.savePdf(fn,String(fr.result).split(',')[1])};fr.readAsDataURL(b)});return}"
      + "return o.apply(a,arguments)}})();";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this)).build();

        web = new WebView(this);
        setContentView(web);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setSupportMultipleWindows(false);
        s.setMediaPlaybackRequiresUserGesture(false);
        web.addJavascriptInterface(new Bridge(), "Android");

        web.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                return loader.shouldInterceptRequest(r.getUrl());
            }
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                String u = r.getUrl().toString();
                if (u.startsWith("https://appassets.androidplatform.net/")) return false;
                if (u.startsWith("https://wa.me/") || u.startsWith("https://api.whatsapp.com/")) {
                    onWhatsApp(u);
                } else {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(u))); } catch (Exception ignored) {}
                }
                return true;
            }
            @Override public void onPageFinished(WebView v, String url) {
                v.evaluateJavascript(PATCH, null);
            }
        });
        web.loadUrl(HOME);
    }

    @Override public void onBackPressed() {
        if (web.canGoBack()) web.goBack(); else super.onBackPressed();
    }

    // ---- WhatsApp: attach the freshly saved PDF and open that patient's chat ----
    private void onWhatsApp(String url) {
        long now = System.currentTimeMillis();
        if (lastPdf != null && now - lastPdfTime < WINDOW_MS) {
            shareToWhatsApp(url, lastPdf);
            return;
        }
        pendingWa = url;
        pendingWaTime = now;
        ui.postDelayed(() -> {
            if (pendingWa != null && pendingWaTime == now) {   // PDF never arrived: plain chat
                String u = pendingWa; pendingWa = null;
                try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(u))); } catch (Exception ignored) {}
            }
        }, 8000);
    }

    private void shareToWhatsApp(String waUrl, File pdf) {
        Uri link = Uri.parse(waUrl);
        String path = link.getPath() == null ? "" : link.getPath().replace("/", "");
        String text = link.getQueryParameter("text");
        Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".files", pdf);
        for (String pkg : new String[]{"com.whatsapp", "com.whatsapp.w4b"}) {
            try {
                Intent i = new Intent(Intent.ACTION_SEND);
                i.setType("application/pdf");
                i.setPackage(pkg);
                i.putExtra(Intent.EXTRA_STREAM, uri);
                if (text != null) i.putExtra(Intent.EXTRA_TEXT, text);
                if (!path.isEmpty()) i.putExtra("jid", path + "@s.whatsapp.net");
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(i);
                return;
            } catch (Exception ignored) {}
        }
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("application/pdf");
        i.putExtra(Intent.EXTRA_STREAM, uri);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(i, "Share prescription"));
    }

    // ---- JS bridge ----
    private class Bridge {
        @JavascriptInterface
        public void savePdf(String name, String b64) {
            try {
                byte[] data = Base64.decode(b64, Base64.DEFAULT);
                String safe = name.replaceAll("[\\\\/:*?\"<>|]", "_");
                File dir = new File(getCacheDir(), "pdf");
                dir.mkdirs();
                File f = new File(dir, safe);
                try (FileOutputStream o = new FileOutputStream(f)) { o.write(data); }
                saveToDownloads(safe, data);
                ui.post(() -> {
                    lastPdf = f;
                    lastPdfTime = System.currentTimeMillis();
                    Toast.makeText(MainActivity.this, "Saved to Downloads: " + safe, Toast.LENGTH_SHORT).show();
                    if (pendingWa != null) {
                        String u = pendingWa; pendingWa = null;
                        shareToWhatsApp(u, f);
                    }
                });
            } catch (Exception e) {
                ui.post(() -> Toast.makeText(MainActivity.this, "Could not save PDF", Toast.LENGTH_LONG).show());
            }
        }
    }

    private void saveToDownloads(String name, byte[] data) throws Exception {
        if (Build.VERSION.SDK_INT >= 29) {
            ContentValues v = new ContentValues();
            v.put(MediaStore.Downloads.DISPLAY_NAME, name);
            v.put(MediaStore.Downloads.MIME_TYPE, "application/pdf");
            v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            Uri u = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
            if (u == null) return;
            try (OutputStream o = getContentResolver().openOutputStream(u)) { o.write(data); }
        } else {
            File d = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            if (d == null) return;
            try (FileOutputStream o = new FileOutputStream(new File(d, name))) { o.write(data); }
        }
    }
}
