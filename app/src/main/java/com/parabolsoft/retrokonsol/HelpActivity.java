package com.parabolsoft.retrokonsol;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.LinearLayout;

/** Kurulum rehberi: hangi dosyalar, nereye, nereden. */
public class HelpActivity extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout c = new LinearLayout(this);
        setContentView(Ui.page(this, c));
        c.addView(Ui.text(this, "Kurulum rehberi", 26, Color.WHITE, true));
        Ui.note(this, c, "Retro Konsol sadece oyun çalıştırıcıdır; içinde oyun yoktur. Oyun dosyalarını sen eklersin. "
                + "BIOS ya da başka ek dosya gerekmez.");

        LinearLayout s1 = Ui.card(this, c, "1) Hangi dosyalar destekleniyor?");
        for (Sys s : Sys.ALL) {
            Ui.note(this, s1, "• " + s.name + ":  " + s.extList());
        }
        Ui.note(this, s1, "• .zip içindeki oyunlar da otomatik çıkarılır. Konsolu dosya uzantısından kendisi anlar; "
                + "klasör ayırman gerekmez.");

        LinearLayout s2 = Ui.card(this, c, "2) Dosyaları nereye koyacaksın?");
        Ui.note(this, s2, "Dosyaları telefonda herhangi bir klasöre koyabilirsin; en kolayı “Download / İndirilenler” "
                + "klasörüdür. Bilgisayardan USB kabloyla ya da bulut sürücüden (Drive vb.) aktarabilirsin.");
        Ui.note(this, s2, "Sonra ana ekranda:\n• “Oyun dosyası seç”: tek tek ya da birkaç tane birden seç.\n"
                + "• “Klasörü tara”: bir klasör seç, içindeki (alt klasörler dahil) tüm oyunlar eklenir.");
        Ui.note(this, s2, "Uygulama dosyaları kendi alanına kopyalar. Ekledikten sonra orijinalleri silebilirsin.");

        LinearLayout s3 = Ui.card(this, c, "3) Oyunları nereden bulabilirsin?");
        Ui.note(this, s3, "Sadece yasal yollarla edin:\n"
                + "• Kendi sahip olduğun oyun kartuşlarından, kendi cihazınla çıkardığın yedekler.\n"
                + "• Geliştiricilerin ücretsiz dağıttığı “homebrew” oyunlar: itch.io (homebrew / retro etiketleri), "
                + "gbdev.io/games (Game Boy), nesdev.org (NES), pdroms.de (kamu malı ve ücretsiz oyunlar).");
        Ui.note(this, s3, "Telif hakkı olan oyunların izinsiz kopyalarını indirmek yasa dışıdır; bu uygulama "
                + "böyle içerik sunmaz ve yönlendirmez.");

        LinearLayout s4 = Ui.card(this, c, "4) Kontroller");
        Ui.note(this, s4, "• Dokunmatik: ekran tuşları. Parmağını kaydırarak tuş değiştirebilirsin, birden çok tuşa aynı anda basılır.\n"
                + "• Bluetooth / USB kumanda ve klavye: bağla, otomatik tanınır (Pro). Tuşları Ayarlar’dan değiştirebilirsin.\n"
                + "• Oyun içinde Geri tuşu ya da MENÜ düğmesi: kaydet, yükle, hile, ekran ve tuş düzeni.\n"
                + "• Tuş düzeni düzenleyicisi (Pro): tuşları sürükle, büyüt, gizle. Dikey ve yatay ekran ayrı kaydedilir.");

        LinearLayout s5 = Ui.card(this, c, "5) Kayıtlar");
        Ui.note(this, s5, "• Oyunun kendi kayıtları (pil kaydı) otomatik saklanır.\n"
                + "• Kayıt noktası: Menü > Kaydet. Ücretsizde 1 yuva, Pro’da 9 yuva.\n"
                + "• Oyundan çıkınca kaldığın yer otomatik saklanır ve bir sonraki açılışta devam eder.");

        c.addView(Ui.button(this, "Tamam", true, v -> finish()));
    }
}
