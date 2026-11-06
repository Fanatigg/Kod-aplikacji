# SumaKamera v2.1 & Webowy Skaner w Przeglądarce

Implementacja inteligentnego rozpoznawania kwot z 1 miejscem po przecinku (np. `750,5 zł`), adaptacyjnego widoku poziomego (Landscape) dla telefonów oraz webowego skanera działającego bezpośrednio w przeglądarce na iPhone i Xiaomi bez instalacji.

---

### Decyzje projektowe i odpowiedź na pytania

> [!IMPORTANT]
> **1. Czy dodanie liczb z 1 miejscem po przecinku to dobry pomysł?**
> **Tak, to bardzo dobry pomysł**, ale wymaga precyzyjnego zabezpieczenia (tzw. guardrails). 
> W księgowości i na fakturach często pojawia się zapis typu `750,5 zł` lub `120,0 zł`. 
> Aby nie zepsuć odczytu i nie zacząć mylić kwot z numerami wersji (`v2.1`), punktami (`1.5. Podstawa`), datami czy procentami (`1,5%`):
> - Liczby z 1 miejscem po przecinku (np. `750,5` -> `750.50 zł`) będą akceptowane:
>   - Zawsze, gdy występuje oznaczenie waluty (`zł`, `zl`, `pln`, `złotych`, `zlotych`).
>   - Lub gdy liczba znajduje się w kolumnie kwotowej w zestawieniu i ma wartość $\ge 10.00$.
> - Wykluczamy z dopasowania pojedyncze cyfry, wersje z literą `v`/`w` przed liczbą oraz wartości z symbolem `%`.

> [!NOTE]
> **2. Adaptacyjny tryb poziomy (Landscape)**
> W pozycji poziomej smartfona dolna karta sumy zasłaniała kamerę. 
> W v2.1 wprowadzamy układ dwukolumnowy dla trybu poziomego:
> - **Lewa strona (65-70% szerokości)**: Pełny, panoramiczny podgląd kamery z interaktywnymi boxami i zaznaczaniem kolumn.
> - **Prawa strona (boczny dock ~300dp)**: Kompaktowy panel z sumą na żywo, wyliczoną składką 9%, przyciskiem kopiowania i przyciskami akcji.

> [!TIP]
> **3. Webowy Skaner w Przeglądarce (dla iPhone i Xiaomi)**
> Bezpośrednio na stronie `https://ais-dev-anfqjh3sg7yj5touozo5ru-826405413163.europe-west2.run.app/` dodajemy zakładkę **"Skaner w przeglądarce (bez instalacji)"**:
> - Działa na Safari (iOS) oraz Chrome/Mi Browser (Android/Xiaomi) za pośrednictwem standardu HTML5 Web Camera (`getUserMedia`).
> - Pozwala na natychmiastowe zamrożenie klatki/zdjęcie dokumentu, automatyczne wyodrębnienie liczb, zaznaczanie kliknięciem oraz natychmiastowe liczenie sumy i 9% ZUS.

---

## 1. Zmiany w Parserze Kwot (`AmountParser.kt`)

```
┌─────────────────────────────────────────────────────────────────┐
│ Tekst w kadrze: "Zaliczka: 750,5 zł" lub "Kwota: 1 200,50 PLN"   │
└────────────────────────────────┬────────────────────────────────┘
                                 │
                                 ▼
┌─────────────────────────────────────────────────────────────────┐
│ 1. Filtr wykluczeń (daty, % procenty, numery kont, wersje v2.1) │
├─────────────────────────────────────────────────────────────────┤
│ 2. Nowy Regex dopasowujący:                                     │
│    - Standardowe 2 miejsca: 1 018,48 zł / 1018.48               │
│    - 1 miejsce z walutą: 750,5 zł / 750,5 złotych               │
│    - 1 miejsce bez waluty (kwoty >= 10.0 w kontekście)          │
├─────────────────────────────────────────────────────────────────┤
│ 3. Normalizacja:                                                │
│    - "750,5" -> traktowane numerycznie jako 750.50 PLN          │
│    - Obliczenie precyzyjnego bounding boxa dla pojedynczego słowa│
└─────────────────────────────────────────────────────────────────┘
```

- Obsługa wariantów waluty: `zł`, `zl`, `pln`, `złotych`, `zlotych`.
- Testy jednostkowe dla formatów: `750,5 zł`, `12,0 zł`, `1 200,5 PLN` oraz zabezpieczeń (`wersja 2.1`, `9%`, `1,5%`).

---

## 2. Adaptacyjny Widok Poziomy w Compose (`ScannerScreen.kt`)

- Sprawdzanie orientacji urządzenia przez `LocalConfiguration.current.orientation`.
- **W orientacji pionowej (Portrait)**:
  - Sprawdzony, wygodny dolny panel pływający z dynamicznym skalowaniem czcionki.
- **W orientacji poziomej (Landscape)**:
  - Podgląd kamery rozciągnięty na cały ekran.
  - Pasek górny (latarka, zamrożenie, dodaj) zredukowany do ergonomicznego pionowego paska bocznego lub zintegrowany z prawym panelem.
  - Prawy panel dokujący (szerokość 280-320dp, wyrównany do `Alignment.CenterEnd` z zachowaniem insets paska nawigacji) zawierający sumę, 9% ZUS z szybkim kopiowaniem oraz listę pozycji.

---

## 3. Webowy Skaner w Przeglądarce (`public/index.html`)

- Dodanie dedykowanej sekcji / przełącznika: **"Uruchom skaner online (iOS / Xiaomi / PC)"**.
- Wykorzystanie natywnego API przeglądarki `navigator.mediaDevices.getUserMedia({ video: { facingMode: 'environment' } })`.
- Funkcje:
  - Przycisk "Włącz aparat" z obsługą uprawnień na iPhone (Safari) i Androidzie.
  - Przycisk "Zrób zdjęcie / Zamroź kadr" lub wybór pliku z galerii.
  - Klientowy OCR (lekki Tesseract.js z CDN) wyodrębniający kwoty.
  - Identyczna kalkulacja: **Suma pozycji** + **9% składka zdrowotna** z 1-tap kopiowaniem do schowka.
  - Zero wysyłania zdjęć na serwer – pełna prywatność i przetwarzanie lokalnie w przeglądarce użytkownika.

---

## 4. Wersjonowanie i Dystrybucja

- Podbicie wersji w `app/build.gradle.kts`:
  - `versionCode = 3`
  - `versionName = "2.1"`
- Kompilacja produkcyjnego APK: `gradle :app:assembleDebug`.
- Umieszczenie `SumaKamera_v2.1.apk` oraz `SumaKamera_v2.1.zip` w folderze `public/download/` obok wersji v2.0 i v1.0.
- Aktualizacja strony pobierania z jasnym opisem nowości w wersji 2.1.
