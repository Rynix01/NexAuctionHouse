# 🧪 NexAuctionHouse - Test Checklist
> En basitinden en zoruna doğru sıralanmıştır. Her maddede ✅ veya ❌ işaretle.

---

## 🟢 SEVİYE 1 — Temel İşlevler (Basit)
> Ön koşul: Sunucuya Vault + bir ekonomi eklentisi yükle, OP ol.

### 1.1 Plugin Yükleme & Config
- [X] Sunucu başlatıldığında hata olmadan yükleniyor
- [X] `plugins/NexAuctionHouse/config.yml` oluşuyor
- [X] `plugins/NexAuctionHouse/lang/en.yml` ve `lang/tr.yml` oluşuyor
- [X] `plugins/NexAuctionHouse/gui/` klasörü ve dosyaları oluşuyor
- [X] `/ah reload` komutu çalışıyor, config yeniden yükleniyor

### 1.2 Ana Menü Açma
- [X] `/ah` komutu ile ana menü açılıyor
- [X] Menüde sayfa ilerle/geri butonları görünüyor
- [X] Menüde kategori butonu görünüyor
- [X] Menüde arama butonu görünüyor
- [X] Menüde sıralama butonu görünüyor
- [X] Menüde favori butonu görünüyor
- [X] Menü boşken düzgün gözüküyor (ilan yok mesajı/boş alan)

### 1.3 Eşya Satışa Koyma (BIN — Sabit Fiyat)
- [-] Eline bir eşya al → `/ah sell 100` → onay menüsü açılıyor
- [-] Onay menüsünde eşya ve fiyat görünüyor
- [-] Onay butonuna tıklayınca eşya elinden gidiyor ve "ilan oluşturuldu" mesajı geliyor
- [X] `/ah` ile menüyü açınca ilan görünüyor (fiyat, süre, satıcı adı lore'da)
- [X] İlan süresinin doğru göründüğünü kontrol et (config'deki default-auction-duration)

### 1.4 Satın Alma
- [X] 2. hesapla (veya alttaki `/ah` ile) ilanı tıkla → onay menüsü açılıyor
- [X] Satın al butonuna bas → eşya envantere geliyor, para düşüyor
- [X] Satıcının parasına vergi düşüldükten sonra ekleniyor
- [X] İlan listeden kalkıyor

### 1.5 İlan İptal Etme
- [X] Bir eşya sat → `/ah` aç → kendi ilanını tıkla → iptal menüsü/seçeneği
- [X] İptal edince eşya envantere geri dönüyor
- [X] İlan listeden kalkıyor

### 1.6 Süresi Dolmuş Eşyalar
- [X] Bir eşya sat, config'de `default-auction-duration: 1` yap (1 saat test veya scheduler'ı bekle)
- [X] Alternatif: admin komutu ile ilanı sil → eşya expired'a düşsün
- [X] `/ah expired` komutu ile süresi dolmuş eşyalar menüsü açılıyor
- [X] Eşyayı tıklayarak geri alabiliyorsun

---

## 🟡 SEVİYE 2 — Temel Sistemler (Orta)

### 2.1 Vergi Sistemi
- [X] `config.yml` → `tax.default-rate: 10` ayarla
- [X] 1000₺'ye bir eşya sat, 2. hesapla satın al
- [X] Satıcıya 900₺ geldiğini doğrula (1000 - %10 vergi)
- [X] `nexauctions.tax.5` yetkisi ver → vergi %5'e düşüyor mu?
- [X] `nexauctions.bypass.tax` yetkisi ver → vergi 0 mı?

### 2.2 Limit Sistemi
- [X] `config.yml` → `default-listing-limit: 3` ayarla
- [X] 3 eşya sat → 4. eşyada "limit doldu" hatası
- [X] `nexauctions.limit.10` yetkisi ver → 10'a kadar eşya koyabiliyor mu?

### 2.3 Süre Sistemi
- [X] Default süre config'deki değeri yansıtıyor mu? (lore'da kontrol)
- [X] `nexauctions.time.72` yetkisi ver → 72 saatlik ilan açılabiliyor mu?

### 2.4 Kara Liste (Temel)
- [X] Eline BEDROCK al → `/ah sell 100` → "yasaklı eşya" hatası
- [X] Eline BARRIER al → aynı hata
- [X] Lore'unda "Soulbound" yazan bir eşya yap → satışa konamıyor
- [X] `nexauctions.bypass.blacklist` yetkisi ile yasaklı eşya satılabiliyor mu?

### 2.5 Kategori Filtreleme
- [X] Farklı türde eşyalar sat (kılıç, zırh, blok, yiyecek vb.)
- [X] `/ah` → kategori butonuna tıkla → kategoriler listesi açılıyor
- [X] Bir kategori seç → sadece o türdeki eşyalar görüntüleniyor
- [X] Filtreyi temizleyince tüm eşyalar geri geliyor

### 2.6 Sayfalama
- [X] 45'ten fazla ilan oluştur (limitileri geçici olarak yükselt)
- [X] `/ah` → 2. sayfaya geç → eşyalar doğru sıralı
- [X] Geri butonu ile 1. sayfaya dön → doğru çalışıyor

---

## 🟠 SEVİYE 3 — Gelişmiş Özellikler (Orta-Zor)

### 3.1 Arama Sistemi
- [X] `/ah search diamond` → sadece "diamond" içeren eşyalar listeleniyor
- [X] Ana menüdeki arama butonuna tıkla → chatte arama yazısı iste → sonuçlar gelsin
- [-] Arama + kategori filtresi bir arada çalışıyor mu?
- [-] Tab completion'da materyal isimleri geliyor mu?

### 3.2 Sıralama Sistemi
- [X] Farklı fiyatlarda ilanlar oluştur
- [X] Sıralama butonu → Fiyat (Artan) → düşükten yükseğe sıralanıyor
- [X] Fiyat (Azalan) → yüksekten düşüğe
- [X] Tarih (Yeni Önce) → en son eklenen en üstte
- [X] İsim (A-Z) → alfabetik sıralama
- [-] Sıralama + Kategori + Arama hep birlikte çalışıyor mu?

### 3.3 Favori / İzleme Listesi
- [X] Bir ilana Shift+Click yap → "favorilere eklendi" mesajı
- [X] `/ah favorites` → favori listesi açılıyor, eklediğin ilan var
- [X] Ana menüdeki favori butonuna tıkla → aynı liste
- [X] Aynı ilana tekrar Shift+Click → favorilerden çıkarıldı
- [ ] Favori ilandaki eşya satıldığında bildirim geliyor mu? (çevrimiçiysen)
- [ ] `favorites.max-favorites: 3` yap → 4. favori eklenemez

### 3.4 Bildirim Tercihleri
- [X] `/ah notifications` veya ayarlar butonuyla bildirim menüsü açılıyor
- [X] Satış bildirimi toggle → kapalıyken satış yapıldığında mesaj gelmiyor
- [X] Teklif bildirimi toggle → teklif geldiğinde mesaj yok
- [X] Ses efektleri toggle → kapalıyken ses çalmıyor
- [X] Giriş bildirimi toggle → kapalıyken join'de expired/gelir mesajı yok
- [X] Favori bildirimi toggle → kapalıyken favori bildirim yok

### 3.5 Eşya Önizleme
- [X] Bir eşyaya sağ tıkla → Önizleme GUI açılıyor
- [X] Enchantlı eşyada büyü listesi tam görünüyor
- [X] Lore ve attribute detayları görünüyor
- [X] Shulker kutusu koy → sağ tıkla → kutunun içindeki eşyalar listeleniyor
- [X] Yazılmış kitap koy → sağ tıkla → kitap sayfaları görünüyor
- [X] Önizlemeden geri butonu ile önceki menüye dönebiliyorsun

### 3.6 Çevrimdışı Senkronizasyon
- [X] Oyuncu A eşya satar → Oyuncu A çıkış yapar → Oyuncu B satın alır
- [X] Oyuncu A giriş yapınca "X₺ kazandınız" mesajı ve para alıyor
- [X] Çevrimdışıyken ilan süresi dolan eşya → giriş yapınca expired bildirimi
- [X] Çevrimdışıyken pending revenue (bid kazanımı) → giriş yapınca para geliyor

---

## 🔴 SEVİYE 4 — Karmaşık Sistemler (Zor)

### 4.1 Teklif / Açık Artırma (Bid) Sistemi
- [X] `/ah sell 100 --bid` → açık artırma ilanı oluşuyor
- [X] Menüde ilan "Açık Artırma" etiketi/lore ile gösteriliyor
- [X] 2. oyuncu ilana tıkla → teklif GUI açılıyor (mevcut fiyat, min artış)
- [X] Teklif ver → "teklif kabul edildi" mesajı
- [X] Satıcıya "yeni teklif" bildirimi geliyor
- [ ] 3. oyuncu daha yüksek teklif veriyor → eski teklifçinin parası iade
- [X] Minimum artış kontrolü: %5 artış zorunlu (config'deki min-increment-percent)
- [X] Anti-snipe: Son 5 dk içinde teklif → süre uzatılıyor (config: anti-snipe-seconds)
- [ ] Süre dolunca en yüksek teklifçi eşyayı alıyor, satıcıya para geçiyor
- [ ] Kaybeden teklifçilerin paraları iade ediliyor
- [ ] Çevrimdışı teklifçiler → giriş yapınca para iadesi

### 4.2 Otomatik Yenileme (Auto-Relist)
- [ ] `config.yml` → `auto-relist.enabled: true`, `max-relists: 3`, `cost-percent: 0`
- [ ] `nexauctions.autorelist` yetkisini ver
- [ ] Eşya satışa koyarken onay menüsünde Auto-Relist toggle seçeneği var mı?
- [ ] Auto-Relist açık → süre dolunca otomatik yeniden listeleniyor
- [ ] Lore'da "Yenileme: 1/3" gibi sayaç gösterimi
- [ ] 3 yenileme sonrası artık expired'a düşüyor
- [ ] `cost-percent: 10` yap → yenileme ücreti yeterli bakiye yoksa expired'a düşüyor
- [ ] Discord webhook'ta auto-relist bildirimi

### 4.3 Toplu İşlemler (Bulk Operations)
- [X] Envantere 64 diamond koy → `/ah sell-all 50` → tüm diamond'lar gruplanarak satışa
- [X] "Toplu Sat" butonu → özel GUI'de envanter eşyalarını seçme ekranı
- [X] Seçilen eşyaları onaylayınca hepsi tek seferde satışa
- [X] Toplam ilan limiti kontrolü (seçilen miktar + mevcut ilanlar > limit ise uyarı)
- [X] Admin: `/ah admin clear --player=Rynix` → Rynix'in tüm ilanları temizleniyor
- [X] Admin: `/ah admin clear --all` → onay soruyor → tüm ilanlar temizleniyor

### 4.4 Paket Satış (Bundle) Sistemi
- [X] `/ah bundle 500` → paket oluşturma GUI'si açılıyor
- [X] Envanterden eşyaları GUI'ye taşıyarak pakete ekliyorsun
- [X] Minimum 2, maksimum 9 eşya kuralı çalışıyor mu?
- [X] Onaylayınca paket ilan olarak açılıyor
- [X] Ana menüde paket ilanı özel etiket/ikon ile gösteriliyor
- [X] Paket ilanına sağ tıkla → paket içeriği önizleme GUI'si
- [X] Paket satın alınca tüm eşyalar alıcıya teslim ediliyor
- [X] Alıcının envanteri doluysa uyarı veriyor

### 4.5 Fiyat Geçmişi ve İstatistikler
- [X] Birkaç alışveriş yap (satış + alış)
- [X] `/ah history` → kişisel işlem geçmişi GUI'si açılıyor (son 50 kayıt)
- [-] `/ah history Rynix` (admin yetkisiyle) → başka oyuncunun geçmişi
- [-] Menüde eşya hover → "Ortalama Piyasa Fiyatı: X₺" bilgisi
- [ ] PlaceholderAPI testleri (PAPI yüklü olmalı):
  - [ ] `%nexauction_player_total_sales%` → doğru toplam satış sayısı
  - [ ] `%nexauction_player_total_revenue%` → doğru toplam kazanç
  - [ ] `%nexauction_player_total_purchases%` → doğru toplam alış
  - [ ] `%nexauction_avg_price_DIAMOND%` → diamond ortalama fiyatı
- [X] Admin istatistik GUI'si: en çok satan oyuncular, en pahalı satışlar

---

## 🟣 SEVİYE 5 — Gelişmiş Kara Liste & Korumalar (Zor)

### 5.1 Gelişmiş Kara Liste
- [ ] `blacklist.enchantments` → `["MENDING"]` ekle → Mending'li eşya satılamaz
- [ ] `blacklist.nbt-tags` → `["myplugin:soulbound"]` → o NBT'ye sahip eşya engellenir
- [ ] `blacklist.disabled-worlds` → `["world_nether"]` → nether'da `/ah sell` engellenir
- [ ] `blacklist.mode: whitelist` + `whitelist-materials: ["DIAMOND_SWORD"]` → sadece elmas kılıç satılabilir
- [ ] `material-price-limits.DIAMOND.min: 10` / `max: 500` → DIAMOND fiyatı 10-500 arası zorlanıyor
- [ ] Admin Blacklist GUI'si: `/ah admin` → blacklist yönetim arayüzü çalışıyor

### 5.2 Anti-Dupe & Güvenlik Korumaları
- [ ] Onay menüsü açıkken eşyayı envanterde hareket ettirmeye çalış → engellenmiş
- [ ] Onay menüsü açıkken hızlıca birden fazla tıkla → double-buy yok
- [ ] Satın alma sırasında sunucuyu `stop` ile kapat → eşya kaybı yok (cursor koruması)
- [ ] Eşya satışa konulduğunda envanterde dupelenmemiş
- [ ] Satın alma sırasında oyuncu kicklenirse eşya kurtarılıyor (giriş yapınca iade)

### 5.3 Cursor & Crash Koruması
- [ ] Bu testleri dikkatli yap! Sunucu çökmesini simüle et:
- [ ] GUI açıkken `/stop` → cursor'daki eşya veritabanına kaydediliyor mu?
- [ ] Giriş yapınca "kurtarılan eşyalar" mesajı + eşya iade

---

## ⚫ SEVİYE 6 — Entegrasyonlar & Özel Durumlar (En Zor)

### 6.1 Alternatif Ekonomi Sistemi
- [ ] `economy.providers.vault.enabled: true` → Vault ile çalışıyor
- [ ] PlayerPoints yüklüyse: `playerpoints.enabled: true` → `/ah sell 100 points` çalışıyor
- [ ] TokenManager yüklüyse: `tokenmanager.enabled: true` → token ile satış
- [ ] İki ekonomi aynı anda aktif → ilan açarken para birimi seçimi çalışıyor
- [ ] Farklı para birimiyle açılan ilanlar menüde doğru gösteriliyor

### 6.2 Özel Obje Eklenti Entegrasyonları
> Her birini test etmek için ilgili eklentinin yüklü olması gerekir.

- [ ] **ItemsAdder:** Özel eşyayı satışa koy → menüde doğru görünüyor (model, isim)
- [ ] **Oraxen:** Özel eşya satışa → doğru render
- [ ] **MMOItems:** MMOItems eşyası sat → stat/ability bilgileri korunuyor
- [ ] **MythicMobs:** Mythic drop eşyası sat → NBT korunuyor
- [ ] **ExecutableItems:** Özel eşya sat → fonksiyonellik korunuyor
- [ ] **Slimefun:** Slimefun eşyası sat → satın alan kullanabiliyor
- [ ] **ModelEngine:** Model objesi sat → görsel doğru
- [ ] **HeadDatabase:** HDB kafa sat → menüde doku doğru görünüyor
- [ ] **CrazyEnchantments / ExcellentEnchants:** Özel büyülü eşya → büyüler görünüyor
- [ ] **CustomModelData:** CMD'li eşya → menüde doğru model

### 6.3 Discord Webhook
- [X] `discord.enabled: true`, `webhook-url` ayarla
- [X] Eşya satışa konulduğunda → Discord'a "Yeni İlan" embed geliyor
- [X] Eşya satın alındığında → Discord'a "Satış" embed geliyor
- [X] İlan iptal edildiğinde → Discord'a "İptal" embed geliyor
- [X] Admin sildiğinde → Discord'a "Admin Müdahale" embed geliyor
- [X] Açık artırma bittiğinde → Discord webhook bildirimi
- [X] Auto-relist olduğunda → Discord webhook bildirimi

### 6.4 PlaceholderAPI (Tam Test)
> PAPI yüklü olmalı. Test için placeholder'ları chat/scoreboard/tab'da göster.

- [ ] `%nexauction_player_total_sales%` — doğru değer
- [ ] `%nexauction_player_total_revenue%` — doğru değer
- [ ] `%nexauction_player_total_purchases%` — doğru değer
- [ ] `%nexauction_avg_price_DIAMOND%` — son 7 gün ortalaması doğru

### 6.5 Admin Komutları
- [X] `/ah admin` → admin GUI menüsü açılıyor
- [X] Admin GUI'den herhangi bir ilanı sil → eşya sahibine iade
- [X] `/ah admin clear --player=Rynix` → ilanları temizle
- [X] `/ah admin clear --all` → tüm ilanları temizle (onay ile)
- [X] `/ah reload` → hatasız config yenileme

### 6.6 Migration Tool (Göç Aracı)
> Her birini test için kaynak eklentinin veritabanı dosyasını `plugins/<eklenti>/` altına koy.

- [ ] `/ah admin migrate auctionhouse` → onay soruyor
- [ ] Onayladıktan sonra → veri aktarımı yapılıyor, rapor veriliyor
- [ ] `/ah admin migrate crazyauctions` → YAML'dan veri okuyor
- [ ] `/ah admin migrate zauctionhouse` → SQLite'tan veri okuyor
- [ ] `/ah admin migrate auctionmaster` → SQLite'tan veri okuyor
- [ ] Desteklenmeyen isim: `/ah admin migrate xyzplugin` → "desteklenmiyor" hatası
- [ ] Kaynak dosya yoksa → "dosya bulunamadı" validasyon hatası
- [ ] Tab completion'da plugin isimleri geliyor

---

## 🔁 SEVİYE 7 — Uç Durumlar & Stres Testleri

### 7.1 Eş Zamanlı İşlemler
- [ ] İki oyuncu aynı ilana aynı anda satın alma tıklasın → sadece biri alabilir
- [X] İlan sahibi iptal ederken başkası satın almaya çalışırsa → çakışma yok
- [ ] Bid verirken ilan süresi dolarsa → tutarlı davranış

### 7.2 Envanter Dolu Durumları
- [X] Envanter tamamen dolu → eşya satın alınca uyarı veya eşya düşürme/expired
- [X] Envanter dolu → expired eşya geri alınamıyor, bilgilendirme mesajı
- [X] Bundle satın al + envanter yarı dolu (tüm eşyalar sığmaz) → uyarı

### 7.3 Performans
- [X] 100+ ilan varken menü açma süresi kabul edilebilir
- [X] Sayfa değiştirme akıcı
- [X] Arama sonuçları hızlı geliyor
- [X] İstatistik cache'i çalışıyor (ikinci sorgu daha hızlı)

### 7.4 Veritabanı Tipleri
- [ ] `database.type: sqlite` → tüm temel testler geçiyor
- [ ] `database.type: mysql` → MySQL bağlantısı çalışıyor, tüm temel testler geçiyor

### 7.5 Çoklu Dil
- [X] `general.language: en` → İngilizce mesajlar
- [X] `general.language: tr` → Türkçe mesajlar
- [X] Dil değişip `/ah reload` yapınca anında etkili

---

## ✅ Test Sonuçları Özeti

| Seviye | Toplam | Geçen | Kalan | Durum |
|--------|--------|-------|-------|-------|
| 🟢 Seviye 1 – Temel | 22 | | | |
| 🟡 Seviye 2 – Orta | 20 | | | |
| 🟠 Seviye 3 – Gelişmiş | 32 | | | |
| 🔴 Seviye 4 – Zor | 39 | | | |
| 🟣 Seviye 5 – Kara Liste & Koruma | 14 | | | |
| ⚫ Seviye 6 – Entegrasyonlar | 33 | | | |
| 🔁 Seviye 7 – Stres Testi | 13 | | | |
| **TOPLAM** | **173** | | | |

> **İpucu:** Seviye 1-2'yi sorunsuz geçtikten sonra ilerle. Her seviye bir öncekinin düzgün çalıştığını varsayar.
> **Not:** Özel obje entegrasyonları (6.2) sadece ilgili eklenti yüklüyse test edilebilir — yüklü olmayanları atla.
