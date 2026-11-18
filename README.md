# Retro Oyun (retro dalı)

Doom ve Quake 1'i tek Android uygulamasında oynatan başlatıcı.

- `native/frontend`: küçük libretro ana programı (C). Çekirdeği yükler, kare/ses/girdi alır.
- `native/test`: masaüstünde çekirdeği deneyen duman testi.
- `app`: Android uygulaması (başlatıcı ekran, oyun ekranı, dokunmatik kontroller).
- Motorlar (derleme sırasında indirilir): libretro-prboom (Doom), tyrquake (Quake 1) — GPL.
- Freedoom (BSD) derleme sırasında indirilip uygulamaya gömülür.

Derleme `.github/workflows/retro.yml` ile yapılır; APK `retro-apk` dalına `RetroOyun.apk` olarak konur.
`ci/test-key.p12` sadece deneme imza anahtarıdır; Play Store için kendi anahtarını kullan.
