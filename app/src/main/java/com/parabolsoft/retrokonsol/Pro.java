package com.parabolsoft.retrokonsol;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.PendingPurchasesParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryPurchasesParams;

import java.util.Collections;
import java.util.List;

/**
 * Retro Konsol Pro: Google Play uzerinden bir kerelik satin alma.
 * Pro durumu ayar dosyasinda saklanir (cevrimdisi de calissin diye) ve her acilista Play'den yenilenir.
 * Deneme surumunde (BuildConfig.PRO_UNLOCKED) Pro her zaman acik gelir.
 */
final class Pro {
    static final String PRODUCT_ID = "konsol_pro";
    private static final String KEY = "pro.owned";

    private Pro() {}

    private static BillingClient client;
    private static ProductDetails details;
    private static Runnable onChange;
    private static final Handler ui = new Handler(Looper.getMainLooper());

    static boolean has(Context c) {
        if (BuildConfig.PRO_UNLOCKED) return true;
        return Settings.load(c).getb(KEY, false);
    }

    private static void setOwned(Context c, boolean v) {
        Settings s = Settings.load(c);
        if (s.getb(KEY, false) == v) return;
        s.setb(KEY, v);
        s.save();
        if (onChange != null) ui.post(onChange);
    }

    /** Ekran acilinca cagrilir: Play'e baglanir, onceki satin almalari geri yukler. */
    static void init(Context ctx, Runnable changed) {
        final Context c = ctx.getApplicationContext();
        onChange = changed;
        if (BuildConfig.PRO_UNLOCKED) return;
        if (client != null && client.isReady()) {
            restore(c);
            return;
        }
        client = BillingClient.newBuilder(c)
                .setListener((res, purchases) -> {
                    if (res.getResponseCode() == BillingClient.BillingResponseCode.OK && purchases != null) {
                        for (Purchase p : purchases) handle(c, p, true);
                    } else if (res.getResponseCode() == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
                        restore(c);
                    }
                })
                .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
                .build();
        client.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(BillingResult r) {
                if (r.getResponseCode() != BillingClient.BillingResponseCode.OK) return;
                restore(c);
                loadDetails();
            }

            @Override
            public void onBillingServiceDisconnected() {
            }
        });
    }

    private static void restore(Context c) {
        if (client == null || !client.isReady()) return;
        client.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build(),
                (r, list) -> {
                    if (r.getResponseCode() != BillingClient.BillingResponseCode.OK) return;
                    boolean owned = false;
                    for (Purchase p : list) {
                        if (p.getProducts().contains(PRODUCT_ID)
                                && p.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
                            owned = true;
                            handle(c, p, false);
                        }
                    }
                    setOwned(c, owned);
                });
    }

    private static void loadDetails() {
        QueryProductDetailsParams q = QueryProductDetailsParams.newBuilder()
                .setProductList(Collections.singletonList(QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRODUCT_ID).setProductType(BillingClient.ProductType.INAPP).build()))
                .build();
        client.queryProductDetailsAsync(q, (r, result) -> {
            if (r.getResponseCode() != BillingClient.BillingResponseCode.OK || result == null) return;
            List<ProductDetails> list = result.getProductDetailsList();
            if (list != null && !list.isEmpty()) details = list.get(0);
        });
    }

    private static void handle(Context c, Purchase p, boolean fresh) {
        if (!p.getProducts().contains(PRODUCT_ID)) return;
        if (p.getPurchaseState() == Purchase.PurchaseState.PENDING) {
            if (fresh) ui.post(() -> Toast.makeText(c, "Ödeme bekleniyor. Onaylanınca Pro açılacak.", Toast.LENGTH_LONG).show());
            return;
        }
        if (p.getPurchaseState() != Purchase.PurchaseState.PURCHASED) return;
        setOwned(c, true);
        if (fresh) ui.post(() -> Toast.makeText(c, "Teşekkürler! Retro Konsol Pro açıldı.", Toast.LENGTH_LONG).show());
        if (!p.isAcknowledged()) {
            client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(p.getPurchaseToken()).build(), r -> {
            });
        }
    }

    static String price() {
        if (details == null || details.getOneTimePurchaseOfferDetails() == null) return null;
        return details.getOneTimePurchaseOfferDetails().getFormattedPrice();
    }

    static void buy(Activity a) {
        if (client == null || !client.isReady() || details == null) {
            Toast.makeText(a, "Google Play'e bağlanılamadı. İnternetini kontrol edip tekrar dene.", Toast.LENGTH_LONG).show();
            init(a, onChange);
            return;
        }
        List<BillingFlowParams.ProductDetailsParams> l = Collections.singletonList(
                BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(details).build());
        client.launchBillingFlow(a, BillingFlowParams.newBuilder().setProductDetailsParamsList(l).build());
    }

    /** "Bu ozellik Pro'da" penceresini gosterir; pencere kapaninca onDismiss cagrilir. */
    static void show(Activity a, String what, Runnable onDismiss) {
        String p = price();
        AlertDialog.Builder b = new AlertDialog.Builder(a)
                .setTitle("Retro Konsol Pro")
                .setMessage(what + " Pro sürümde.\n\nPro ile açılanlar:\n"
                        + "• Sınırsız kayıt noktası (9 yuva)\n• Geri sarma ve hızlandırma\n"
                        + "• Hile kodları\n• Ekran filtreleri (CRT, LCD)\n"
                        + "• Kumanda, klavye ve tuş atama\n• Tuş düzeni düzenleyicisi\n\n"
                        + "Bir kez alırsın, hep senin olur.")
                .setPositiveButton(p != null ? "Pro'ya geç (" + p + ")" : "Pro'ya geç", (d, w) -> buy(a))
                .setNeutralButton("Satın alımı geri yükle", (d, w) -> {
                    init(a, onChange);
                    Toast.makeText(a, "Satın alımlar kontrol ediliyor…", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Vazgeç", null);
        if (onDismiss != null) b.setOnDismissListener(d -> onDismiss.run());
        b.show();
    }

    /** Pro ise true doner; degilse Pro penceresini gosterir ve false doner. */
    static boolean check(Activity a, String what) {
        if (has(a)) return true;
        show(a, what, null);
        return false;
    }
}
