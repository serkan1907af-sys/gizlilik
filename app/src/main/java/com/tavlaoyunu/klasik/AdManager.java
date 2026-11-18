package com.tavlaoyunu.klasik;

import android.app.Activity;
import android.content.Context;

import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.OnUserEarnedRewardListener;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.android.gms.ads.rewarded.RewardItem;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;

/**
 * Reklamlar: oyun sonunda geçiş reklamı, ipucu/geri alma hakkı için ödüllü reklam.
 * Pro kullanıcıya reklam gösterilmez.
 *
 * DİKKAT: Aşağıdaki kimlikler Google'ın TEST kimlikleridir. Yayına çıkmadan önce
 * AdMob panelindeki kendi reklam birimi kimliklerinle değiştir (ve AndroidManifest'teki uygulama kimliğini de).
 */
public final class AdManager {
    private static final String INTERSTITIAL_ID = "ca-app-pub-3940256099942544/1033173712";
    private static final String REWARDED_ID = "ca-app-pub-3940256099942544/5224354917";

    private static Context app;
    private static InterstitialAd interstitial;
    private static RewardedAd rewarded;
    private static boolean loadingInterstitial, loadingRewarded;

    private AdManager() {}

    public static void init(Context c) {
        app = c.getApplicationContext();
        MobileAds.initialize(app);
        loadInterstitial();
        loadRewarded();
    }

    private static void loadInterstitial() {
        if (app == null || loadingInterstitial || interstitial != null || Prefs.pro()) return;
        loadingInterstitial = true;
        InterstitialAd.load(app, INTERSTITIAL_ID, new AdRequest.Builder().build(), new InterstitialAdLoadCallback() {
            @Override
            public void onAdLoaded(InterstitialAd ad) {
                interstitial = ad;
                loadingInterstitial = false;
            }

            @Override
            public void onAdFailedToLoad(LoadAdError error) {
                interstitial = null;
                loadingInterstitial = false;
            }
        });
    }

    private static void loadRewarded() {
        if (app == null || loadingRewarded || rewarded != null) return;
        loadingRewarded = true;
        RewardedAd.load(app, REWARDED_ID, new AdRequest.Builder().build(), new RewardedAdLoadCallback() {
            @Override
            public void onAdLoaded(RewardedAd ad) {
                rewarded = ad;
                loadingRewarded = false;
            }

            @Override
            public void onAdFailedToLoad(LoadAdError error) {
                rewarded = null;
                loadingRewarded = false;
            }
        });
    }

    /** Geçiş reklamı göster (Pro değilse ve hazırsa). Bittiğinde (veya hiç gösterilmezse) 'after' çalışır. */
    public static void showInterstitial(Activity a, final Runnable after) {
        if (Prefs.pro() || interstitial == null) {
            loadInterstitial();
            after.run();
            return;
        }
        InterstitialAd ad = interstitial;
        interstitial = null;
        ad.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override
            public void onAdDismissedFullScreenContent() {
                loadInterstitial();
                after.run();
            }

            @Override
            public void onAdFailedToShowFullScreenContent(AdError adError) {
                loadInterstitial();
                after.run();
            }
        });
        ad.show(a);
    }

    public static boolean rewardedReady() {
        return rewarded != null;
    }

    /** Ödüllü reklam: izlenirse onReward, hazır değilse onNotReady çalışır. */
    public static void showRewarded(Activity a, final Runnable onReward, Runnable onNotReady) {
        if (rewarded == null) {
            loadRewarded();
            onNotReady.run();
            return;
        }
        RewardedAd ad = rewarded;
        rewarded = null;
        final boolean[] earned = {false};
        ad.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override
            public void onAdDismissedFullScreenContent() {
                loadRewarded();
                if (earned[0]) onReward.run();
            }

            @Override
            public void onAdFailedToShowFullScreenContent(AdError adError) {
                loadRewarded();
            }
        });
        ad.show(a, new OnUserEarnedRewardListener() {
            @Override
            public void onUserEarnedReward(RewardItem rewardItem) {
                earned[0] = true;
            }
        });
    }
}
