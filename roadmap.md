# 🗺️ NexAuctionHouse - Geliştirme Yol Haritası

## Aşama 1: Proje Altyapısı, Veritabanı ve Çekirdek Sistemler
- [x] Gradle (Kotlin DSL) proje yapısının oluşturulması
- [x] Paper API 1.21.4, Vault, PlaceholderAPI bağımlılıklarının eklenmesi
- [x] Ana plugin sınıfı (NexAuctionHouse.java) ve plugin.yml oluşturulması
- [x] Konfigürasyon sistemi (config.yml)
- [x] Çoklu dil desteği (lang/en.yml, lang/tr.yml) - MiniMessage tabanlı
- [x] GUI konfigürasyon dosyaları (gui/ klasörü: main-menu, confirm, expired, categories)
- [x] SQLite ve MySQL veritabanı bağlantı katmanı (DatabaseManager)
- [x] Veritabanı tablo yapılarının tasarlanması (auctions, expired_items, transaction_logs)
- [x] Veritabanı CRUD işlemleri (AuctionDAO)
- [x] Vault ekonomi entegrasyonu (EconomyManager)
- [x] ItemStack Base64 Serialize/Deserialize yardımcı sınıfı (ItemSerializer)
- [x] AuctionItem, ExpiredItem veri modelleri ve AuctionStatus enum
- [x] AuctionManager - eşya ekleme, satın alma, iptal etme, süre dolumu işlemleri
- [x] Süresi dolan ihalelerin otomatik temizlenmesi (Scheduler - her 60 saniye)
- [x] Çevrimdışı oyuncu kuyruğu (expired_items tablosu ile iade sistemi)
- [x] Kara liste (blacklist) kontrolü - materyal ve lore bazlı
- [x] Vergi sistemi hesaplamaları
- [x] VIP limit kontrolleri (nexauctions.limit, nexauctions.time, nexauctions.tax)
- [x] /ah komutu (sell, expired, admin, reload) ve tab completion
- [x] Yetki (permission) kontrolleri
- [x] PlayerListener - giriş yapan oyunculara süresi dolmuş eşya bildirimi
- [x] TimeUtil - zaman formatlama yardımcı sınıfı

## Aşama 2: GUI (Arayüz) Sistemi
- [x] Temel GUI altyapısı (AbstractGui, PaginatedGui)
- [x] Ana ihale menüsü (MainMenu)
- [x] Kategori filtreleme sistemi
- [x] Eşya satışa koyma onay menüsü (ConfirmGui)
- [x] Oyuncunun kendi ihaleleri menüsü
- [x] Süresi dolmuş eşyalar menüsü (ExpiredGui)
- [x] Anti-Dupe tıklama korumaları (Shadow GUI mantığı)
- [x] Cursor koruması (sunucu çökmesi/kick durumu)

## Aşama 3: Entegrasyonlar ve Son Dokunuşlar
- [x] Discord Webhook entegrasyonu (satış, alış, iptal logları)
- [x] PlaceholderAPI placeholder'ları
- [x] Admin menüsü (GUI üzerinden ihale silme/iade)
- [x] README.md ve dökümantasyon

## Aşama 4: Özel Obje Entegrasyonları
- [x] ItemsAdder API entegrasyonu (özel eşyaların GUI'de doğru renderlanması)
- [x] Oraxen API entegrasyonu (özel eşya/blok desteği)
- [x] MMOItems API entegrasyonu (özel stat/ability eşyaları)
- [x] MythicMobs drop entegrasyonu (özel mob drop eşyaları)
- [x] ExecutableItems API entegrasyonu
- [x] EcoItems entegrasyonu (Eco eklenti ailesi desteği)
- [x] Slimefun eşya entegrasyonu
- [x] ModelEngine API entegrasyonu (özel model objelerinin desteklenmesi)
- [x] CustomModelData desteği (menüde sorunsuz görüntüleme)
- [x] HeadDatabase kafa dokusu entegrasyonu (kafa eşyalarının doğru listelenmesi)
- [x] CrazyEnchantments / ExcellentEnchants özel büyü desteği
- [x] NexEngine / NexItems entegrasyonu

## Aşama 5: Alternatif Ekonomi Desteği
- [x] PlayerPoints entegrasyonu (puan bazlı alım/satım)
- [x] TokenManager entegrasyonu (token bazlı alım/satım)
- [x] CoinsEngine entegrasyonu (çoklu para birimi desteği)
- [x] GemsEconomy entegrasyonu (çoklu para birimi desteği)
- [x] EcoBits entegrasyonu (çoklu para birimi desteği)
- [x] UltraEconomy entegrasyonu (çoklu para birimi desteği)
- [x] Config üzerinden ekonomi tipi seçimi (7 farklı ekonomi sağlayıcı)
- [x] Aynı anda birden fazla ekonomi sağlayıcı aktif olabilme
- [x] İlan başına para birimi seçimi

## Aşama 6: Çevrimdışı Oyuncu Senkronizasyonu
- [x] Çevrimdışı satış geliri kuyruğu (giriş yapıldığında para teslimi)
- [x] Çevrimdışı eşya iade kuyruğu (iptal/süre dolumu sonrası giriş yapıldığında eşya teslimi)
- [x] Kuyruk durumu bilgilendirme mesajları

## Aşama 7: Gelişmiş Cursor ve Çökme Koruması
- [x] Sunucu çökmesi durumunda Cursor'daki eşyanın veritabanına kaydedilmesi
- [x] Kick/timeout durumunda eşya kurtarma sistemi
- [x] Giriş yapıldığında kurtarılan eşyaların otomatik iadesi

---

## Aşama 8: Arama ve Sıralama Sistemi
- [x] /ah search <kelime> komutu ile eşya arama
- [x] Ana menüde arama butonu (ChatInput entegrasyonu ile)
- [x] Arama sonuçları ayrı PaginatedGui'de gösterilmesi
- [x] Sıralama seçenekleri: Fiyat (artan/azalan), Tarih (yeni/eski), İsim (A-Z/Z-A)
- [x] GUI'de sıralama butonu (toggle olarak çalışır, her tıklamada mod değişir)
- [x] Arama ve sıralama durumunun oturum boyunca korunması
- [x] Kategori filtresi ile aramanın birlikte çalışabilmesi
- [x] Tab completion desteği (popüler aramalar/materyal isimleri)

## Aşama 9: Teklif / Açık Artırma (Bid/Auction) Sistemi
- [x] Auction modeli güncellenmesi (BIN ve AUCTION modları)
- [x] /ah sell <fiyat> --bid komutu ile açık artırma ilanı oluşturma
- [x] Veritabanında bids tablosu (auction_id, bidder_uuid, amount, timestamp)
- [x] Minimum artış miktarı (config: bid-increment yüzdesi)
- [x] Teklif verme GUI'si (mevcut fiyat, minimum teklif, onay)
- [x] Anti-snipe koruması (son 5 dakikada teklif → süre uzatma)
- [x] Süre bitiminde en yüksek teklif veren kazanır, para transfer edilir
- [x] Kaybeden teklifçilerin paralarının otomatik iade edilmesi
- [x] GUI'de açık artırma eşyaları için ayrı lore şablonu
- [x] İlan lore'unda güncel teklif ve teklif veren gösterimi
- [x] Açık artırma bitiminde Discord webhook bildirimi
- [x] Teklif geldiğinde satıcıya anlık bildirim
- [x] Çevrimdışı teklif iade kuyruğu (pending_revenue entegrasyonu)

## Aşama 10: Favori / İzleme Listesi
- [x] Veritabanında favorites tablosu (player_uuid, auction_id)
- [x] GUI'de eşyaya shift+click ile favoriye ekleme/çıkarma
- [x] /ah favorites komutu ile favori listesi GUI'si
- [x] Ana menüde favori butonu
- [x] Favori ilan satıldığında/iptal edildiğinde oyuncuya bildirim
- [x] Config üzerinden favori limiti (max-favorites)
- [x] Favori listesi PaginatedGui desteği

## Aşama 11: Fiyat Geçmişi ve İstatistikler
- [x] /ah history komutu → kişisel işlem geçmişi GUI'si (son 50 alış/satış)
- [x] /ah history <oyuncu> (admin yetkisi) → belirli oyuncunun geçmişi
- [x] Materyal bazlı ortalama fiyat hesaplama (son 7 gün)
- [x] GUI'de item hover'ında "Ortalama Piyasa Fiyatı" bilgisi
- [x] Yeni PlaceholderAPI placeholder'ları:
  - [x] %nexauction_player_total_sales% (toplam satış sayısı)
  - [x] %nexauction_player_total_revenue% (toplam kazanç)
  - [x] %nexauction_player_total_purchases% (toplam alış)
  - [x] %nexauction_avg_price_<MATERIAL>% (materyal bazlı ortalama)
- [x] Admin istatistik GUI'si (en çok satan oyuncular, en pahalı satışlar, günlük hacim)
- [x] İstatistik verileri cache sistemi (performans optimizasyonu)

## Aşama 12: Eşya Önizleme Sistemi
- [x] GUI'de eşyaya sağ tıkla → ayrı Preview GUI açılması
- [x] Tam enchant listesi, attribute'lar, lore detaylı görüntüleme
- [x] Shulker kutusu içerik önizleme (kutunun içindeki tüm eşyalar listelenir)
- [x] Kitap içerik önizleme (yazılmış kitapların sayfaları gösterilir)
- [x] Zırh önizleme sistemi (zırh seti olarak görsel render)
- [x] Custom item detay görüntüleme (MMOItems stat'ları, ItemsAdder özellikleri vb.)
- [x] Önizleme GUI'sinden geri dönüş (önceki sayfaya/menüye)

## Aşama 13: Bildirim Tercihleri
- [x] Veritabanında player_settings tablosu
- [x] /ah notifications komutu veya GUI'de ayarlar butonu
- [x] Oyuncu bazlı bildirim tercihleri:
  - [x] Satış bildirimi (Açık/Kapalı)
  - [x] Teklif bildirimi (Açık/Kapalı)
  - [x] Ses efektleri (Açık/Kapalı)
  - [x] Giriş bildirimi (Açık/Kapalı)
  - [x] Favori bildirimi (Açık/Kapalı)
- [x] Ses efektleri: satış, teklif, eşya kurtarma, favori satış
- [x] Bildirim tercih GUI'si (toggle butonları ile)
- [x] Varsayılan tercihlerin config'den ayarlanabilmesi

## Aşama 14: Otomatik Yenileme (Auto-Relist)
- [x] Config: auto-relist.enabled, max-times, cost ayarları
- [x] Permission: nexauctions.autorelist yetkisi
- [x] İlan oluştururken Auto-Relist toggle seçeneği
- [x] Süre dolduğunda otomatik yenileme (ücretli/ücretsiz)
- [x] Maksimum yenileme sayısı kontrolü
- [x] Yenileme sayacı lore'da gösterimi
- [x] Otomatik yenilendiğinde Discord webhook bildirimi
- [x] Yenileme başarısız olursa (bakiye yetersiz) normal expired akışı

## Aşama 15: Toplu İşlemler (Bulk Operations)
- [x] /ah sell-all <fiyat> [currency] → envanterdeki aynı türden eşyaları grupla satışa koy
- [x] GUI'de "Toplu Sat" butonu → özel satış GUI'si (envanter eşyaları seçme)
- [x] Seçilen eşyaların tek seferde satışa konulması
- [x] Toplu satışta ilan limiti kontrolü
- [x] Admin: /ah admin clear --player=<isim> → belirli oyuncunun ilanlarını temizle
- [x] Admin: /ah admin clear --all → tüm ilanları temizle (onay gerektirir)

## Aşama 16: Gelişmiş Kara Liste (Advanced Blacklist)
- [x] Enchantment bazlı kara liste (belirli büyülü eşyalar engellenebilir)
- [x] NBT tag bazlı kara liste (belirli tag içeren eşyalar engellenir)
- [x] Materyal bazlı fiyat limitleri (min/max fiyat per materyal)
- [x] Dünya bazlı kara liste (belirli dünyalarda AH kullanım engeli)
- [x] Whitelist modu (sadece izin verilen eşyalar satılabilir)
- [x] Admin Blacklist GUI'si (görsel yönetim arayüzü)
- [x] Blacklist config bölümü genişletilmesi

## Aşama 17: Paket Satış (Bundle) Sistemi
- [ ] /ah bundle <fiyat> komutu → paket oluşturma GUI'si
- [ ] Envanterdeki eşyaları seçerek paket oluşturma
- [ ] Birden fazla eşyanın tek ilan olarak satışa konulması
- [ ] Paket önizleme GUI'si (alıcı paketin içeriğini görebilir)
- [ ] Paket satın alındığında tüm eşyaların alıcıya teslimi
- [ ] Config: bundle-limit (oyuncu başı paket ilan limiti)
- [ ] Paket ilanlarının ana menüde özel ikon/etiketle gösterimi
- [ ] Envanter dolu kontrolü (paket eşyaları sığmazsa uyarı)

## Aşama 18: Göç Aracı (Migration Tool)
- [ ] /ah migrate <plugin> komutu (admin yetkisi)
- [ ] AuctionHouse (klip) → NexAuctionHouse göçü
- [ ] CrazyAuctions → NexAuctionHouse göçü
- [ ] zAuctionHouse → NexAuctionHouse göçü
- [ ] AuctionMaster → NexAuctionHouse göçü
- [ ] Kaynak plugin veritabanı yapısını okuma ve eşya transferi
- [ ] İlan geçmişi ve expired eşyaların aktarılması
- [ ] Göç raporu (X ilan, Y expired, Z log taşındı)
- [ ] Göç öncesi yedek uyarısı ve onay mekanizması
- [ ] Hata durumunda rollback desteği

## Aşama 19: GUI Tema Sistemi
- [ ] Çoklu tema dosyası desteği (themes/ klasörü)
- [ ] Varsayılan tema (themes/default.yml)
- [ ] Koyu tema (themes/dark.yml)
- [ ] Nether teması (themes/nether.yml)
- [ ] /ah theme <tema_adı> komutu ile tema seçimi
- [ ] Ayarlar GUI'sinde tema seçim ekranı
- [ ] Her tema: farklı renkler, cam desenleri, buton materyalleri
- [ ] Oyuncu tema tercihinin veritabanında saklanması

## Aşama 20: Geliştirici API (Developer API)
- [ ] NexAuctionHouseAPI public sınıfı (static erişim)
- [ ] Custom Event'ler:
  - [ ] AuctionListEvent (cancellable) → ilan açılmadan önce
  - [ ] AuctionPurchaseEvent (cancellable) → satın almadan önce
  - [ ] AuctionExpireEvent → ilan süresi dolunca
  - [ ] AuctionCancelEvent → ilan iptal edilince
  - [ ] BidPlaceEvent (cancellable) → teklif verilmeden önce
- [ ] API metotları:
  - [ ] getActiveAuctions()
  - [ ] getAuctionsByPlayer(UUID)
  - [ ] forceCreateAuction(player, item, price, currency)
  - [ ] forceRemoveAuction(id)
  - [ ] getPlayerStats(UUID)
- [ ] Javadoc dokümantasyonu
- [ ] Maven/Gradle dependency olarak sunulabilir yapı

## Aşama 21: Redis / BungeeCord Desteği
- [ ] MySQL zorunlu mod ile çoklu sunucu senkronizasyonu
- [ ] Redis pub/sub entegrasyonu (anlık güncelleme)
- [ ] Sunucu A'da ilan açıldığında sunucu B'de anlık yansıma
- [ ] BungeeCord/Velocity plugin messaging desteği
- [ ] Sunucular arası bildirim sistemi
- [ ] Redis bağlantı havuzu ve yeniden bağlanma mekanizması
- [ ] Config: redis bağlantı ayarları (host, port, password, database)
