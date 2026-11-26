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
import com.android.billingclient.api.ConsumeParams;
import com.android.billingclient.api.ConsumeResponseListener;
import com.android.billingclient.api.PendingPurchasesParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.ProductDetailsResponseListener;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesResponseListener;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryProductDetailsResult;
import com.android.billingclient.api.QueryPurchasesParams;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

/**
 * Pro (tek seferlik satın alma). Play Console'da "pro_surum" kimlikli, yönetilen (tek seferlik)
 * bir uygulama içi ürün oluşturulmalıdır.
 */
public final class ProManager implements PurchasesUpdatedListener {
    public static final String PRODUCT_ID = "pro_surum";
    /** Tüketilebilir jeton paketleri (Play Console'da bu kimliklerle oluşturulmalı). */
    public static final String[] COIN_IDS = {"jeton_500", "jeton_1500", "jeton_5000"};
    public static final int[] COIN_AMOUNTS = {500, 1500, 5000};

    private static ProManager inst;

    private final BillingClient client;
    private ProductDetails details;
    private final Map<String, ProductDetails> all = new HashMap<>();
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
        List<QueryProductDetailsParams.Product> prods = new ArrayList<>();
        prods.add(QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_ID).setProductType(BillingClient.ProductType.INAPP).build());
        for (String id : COIN_IDS) {
            prods.add(QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(id).setProductType(BillingClient.ProductType.INAPP).build());
        }
        QueryProductDetailsParams params = QueryProductDetailsParams.newBuilder().setProductList(prods).build();
        client.queryProductDetailsAsync(params, new ProductDetailsResponseListener() {
            @Override
            public void onProductDetailsResponse(BillingResult result, QueryProductDetailsResult qr) {
                List<ProductDetails> list = qr == null ? null : qr.getProductDetailsList();
                if (result.getResponseCode() == BillingClient.BillingResponseCode.OK && list != null) {
                    for (ProductDetails d : list) {
                        all.put(d.getProductId(), d);
                        if (PRODUCT_ID.equals(d.getProductId())) details = d;
                    }
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
            if (p.getPurchaseState() != Purchase.PurchaseState.PURCHASED) continue;
            if (p.getProducts().contains(PRODUCT_ID)) {
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
                continue;
            }
            int amount = 0;
            for (int i = 0; i < COIN_IDS.length; i++) {
                if (p.getProducts().contains(COIN_IDS[i])) amount = COIN_AMOUNTS[i];
            }
            if (amount > 0) {
                final int give = amount;
                client.consumeAsync(ConsumeParams.newBuilder().setPurchaseToken(p.getPurchaseToken()).build(),
                        new ConsumeResponseListener() {
                            @Override
                            public void onConsumeResponse(BillingResult r, String token) {
                                if (r.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                                    Economy.addCoins(give);
                                    notifyListeners();
                                }
                            }
                        });
            }
        }
        notifyListeners();
    }

    public String priceOf(String productId) {
        ProductDetails d = all.get(productId);
        if (d == null) return null;
        ProductDetails.OneTimePurchaseOfferDetails o = d.getOneTimePurchaseOfferDetails();
        return o == null ? null : o.getFormattedPrice();
    }

    public boolean buy(Activity a, String productId) {
        ProductDetails d = all.get(productId);
        if (d == null || !client.isReady()) {
            connect();
            return false;
        }
        BillingFlowParams.ProductDetailsParams pdp = BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(d).build();
        BillingFlowParams flow = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(Collections.singletonList(pdp)).build();
        return client.launchBillingFlow(a, flow).getResponseCode() == BillingClient.BillingResponseCode.OK;
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
