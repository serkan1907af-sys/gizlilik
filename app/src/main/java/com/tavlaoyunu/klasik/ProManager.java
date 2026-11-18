package com.tavlaoyunu.klasik;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.PendingPurchasesParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.ProductDetailsResponseListener;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesResponseListener;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryPurchasesParams;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Pro (tek seferlik satın alma). Play Console'da "pro_surum" kimlikli, yönetilen (tek seferlik)
 * bir uygulama içi ürün oluşturulmalıdır.
 */
public final class ProManager implements PurchasesUpdatedListener {
    public static final String PRODUCT_ID = "pro_surum";

    private static ProManager inst;

    private final BillingClient client;
    private ProductDetails details;
    private final List<Runnable> listeners = new ArrayList<>();
    private final Handler main = new Handler(Looper.getMainLooper());

    public static void init(Context c) {
        if (inst == null) inst = new ProManager(c.getApplicationContext());
    }

    public static ProManager get() {
        return inst;
    }

    private ProManager(Context c) {
        client = BillingClient.newBuilder(c)
                .setListener(this)
                .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
                .build();
        connect();
    }

    private void connect() {
        client.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(BillingResult result) {
                if (result.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    queryDetails();
                    restore();
                }
            }

            @Override
            public void onBillingServiceDisconnected() {
                // Bir sonraki işlemde yeniden bağlanılır.
            }
        });
    }

    private void queryDetails() {
        QueryProductDetailsParams.Product prod = QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_ID)
                .setProductType(BillingClient.ProductType.INAPP)
                .build();
        QueryProductDetailsParams params = QueryProductDetailsParams.newBuilder()
                .setProductList(Collections.singletonList(prod))
                .build();
        client.queryProductDetailsAsync(params, new ProductDetailsResponseListener() {
            @Override
            public void onProductDetailsResponse(BillingResult result, List<ProductDetails> list) {
                if (result.getResponseCode() == BillingClient.BillingResponseCode.OK && list != null && !list.isEmpty()) {
                    details = list.get(0);
                    notifyListeners();
                }
            }
        });
    }

    /** Önceki satın alımı geri yükle. */
    public void restore() {
        if (!client.isReady()) {
            connect();
            return;
        }
        client.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build(),
                new PurchasesResponseListener() {
                    @Override
                    public void onQueryPurchasesResponse(BillingResult result, List<Purchase> purchases) {
                        handle(purchases);
                    }
                });
    }

    @Override
    public void onPurchasesUpdated(BillingResult result, List<Purchase> purchases) {
        if (result.getResponseCode() == BillingClient.BillingResponseCode.OK) {
            handle(purchases);
        }
    }

    private void handle(List<Purchase> purchases) {
        if (purchases == null) return;
        for (Purchase p : purchases) {
            if (!p.getProducts().contains(PRODUCT_ID)) continue;
            if (p.getPurchaseState() != Purchase.PurchaseState.PURCHASED) continue;
            if (!p.isAcknowledged()) {
                client.acknowledgePurchase(
                        AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.getPurchaseToken()).build(),
                        new com.android.billingclient.api.AcknowledgePurchaseResponseListener() {
                            @Override
                            public void onAcknowledgePurchaseResponse(BillingResult r) {
                            }
                        });
            }
            Prefs.setPro(true);
        }
        notifyListeners();
    }

    public boolean canBuy() {
        return details != null && client.isReady();
    }

    public String price() {
        if (details == null) return null;
        ProductDetails.OneTimePurchaseOfferDetails o = details.getOneTimePurchaseOfferDetails();
        return o == null ? null : o.getFormattedPrice();
    }

    /** Satın alma ekranını açar. Başarısızsa false döner. */
    public boolean buy(Activity a) {
        if (details == null || !client.isReady()) {
            connect();
            return false;
        }
        BillingFlowParams.ProductDetailsParams pdp = BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(details)
                .build();
        BillingFlowParams flow = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(Collections.singletonList(pdp))
                .build();
        BillingResult r = client.launchBillingFlow(a, flow);
        return r.getResponseCode() == BillingClient.BillingResponseCode.OK;
    }

    public void addListener(Runnable r) {
        if (!listeners.contains(r)) listeners.add(r);
    }

    public void removeListener(Runnable r) {
        listeners.remove(r);
    }

    private void notifyListeners() {
        main.post(new Runnable() {
            @Override
            public void run() {
                for (Runnable r : new ArrayList<>(listeners)) r.run();
            }
        });
    }
}
