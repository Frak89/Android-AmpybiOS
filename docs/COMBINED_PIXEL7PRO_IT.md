# AmpybiOS + Android-AmpybiOS — integrazione Pixel 7 Pro

Questa integrazione unisce la base ROM della repository pubblica
`Frak89/Android-AmpybiOS` con l'app Python/C++ originale `Frak89/AmpybiOS`.
Il secondo progetto resta privato: codice e credenziali non vengono copiati
nella repository pubblica. Il pacchetto sorgenti personale può contenere
entrambi; non pubblicarlo integralmente senza decidere di rendere pubblico il
codice privato.

**Stato: integrazione sorgenti e test su host. Non è ancora stata compilata
una ROM, né è stato avviato un Pixel con questo firmware. Lo ZIP BuildKit non
va installato da recovery e non va passato a fastboot.**

## Cosa viene integrato

- Base GrapheneOS Android 17 per `cheetah`, kernel/dispositivo Pantah,
  mantenendo il tag già configurato `2026100600`.
- AmpyCore, AmpyCam, AmpyConnect, AmpyLauncher e AmpyRec della repository
  pubblica, come app senza privilegi aggiuntivi.
- L'app originale `io.ampy.bios` come modulo di prodotto **AmpySandbox**:
  core Python, librerie native C++, app modulari, grafica e risorse originali.
  Un APK release viene verificato e conservato byte per byte come prefirmato.
- AmpyLauncher rimane il candidato Home Android; AmpySandbox è accessibile
  dal drawer. Il sistema non sceglie silenziosamente una Home al posto tuo.

Il kernel, i driver e i servizi di sistema sono quelli della base Android:
il core Python dell'app non viene presentato come un kernel Linux nuovo.
Le modifiche globali di SystemUI, il root predefinito, il server AmpyOTA e
le app privacy di terze parti non sono implementati da questa integrazione.
Il lettore RAW standalone di una terza repository non viene aggiunto
implicitamente; l'editor già incluso nel sandbox rimane parte della sua app.

## Host richiesto

Linux x86_64, almeno 32 GiB di RAM e 220 GiB di spazio libero come controllo
minimo di questo progetto. È preferibile più spazio per target-files e release.
Servono i prerequisiti ufficiali GrapheneOS, `repo`, Git, Node/yarn, strumenti
Android SDK, Gradle 8.13, Python 3.11 e fonttools per l'app originale.
Android Studio può compilare le app; la ROM usa il sistema di build Android
su Linux, non il pulsante Build APK di Android Studio.

Riferimento: https://grapheneos.org/build

```bash
python3 scripts/check_build_host.py /srv/ampy/grapheneos-2026100600 --json
bash scripts/build_combined_cheetah.sh /srv/ampy/grapheneos-2026100600 --plan
```

`--plan` non scarica né modifica il telefono. Il comando reale rifiuta host
insufficienti prima di scaricare l'intero Android.

## 1. Sorgente app e firma persistente

Lo ZIP personale contiene uno snapshot integro dell'app con
`AmpySourceSnapshot.json`. Il verificatore ricostruisce il vero albero Git e
lo confronta con l'hash vincolato nella configurazione: non si fida soltanto
del contenuto del manifest. Usa uno snapshot appena estratto per la prima
build. Per sviluppi successivi è preferibile un checkout Git separato:

```bash
bash scripts/prepare_ampysandbox_source.sh /srv/ampy/AmpybiOS
python3 scripts/check_ampysandbox_source.py /srv/ampy/AmpybiOS
```

Per il clone privato usa le tue normali credenziali Git. Nessun token è incluso.
Per compilare lo snapshot allegato, passa invece il percorso `../AmpybiOS`.

Prepara una chiave APK persistente fuori da Git, ad esempio con la procedura
di APK firmato di Android Studio. Non sostituirla a ogni build. Le chiavi APK
del sandbox e quelle della ROM hanno funzioni distinte.

```bash
export AMPY_SANDBOX_KEYSTORE=/srv/keys/AmpybiOS-app.jks
export AMPY_SANDBOX_KEY_ALIAS=AmpybiOS
bash scripts/build_ampysandbox_release.sh /srv/ampy/AmpybiOS /srv/ampy/apk-release
```

L'SDK chiede le password nel terminale. Per un host automatizzato puoi usare
le variabili `AMPY_SANDBOX_KS_PASS` e `AMPY_SANDBOX_KEY_PASS` del tuo ambiente
protetto; non inserirle nei file Git. L'output è `AmpybiOS-release.apk` e
`AmpySandbox.lock.json`. La validazione richiede package `io.ampy.bios`,
versionCode 5, API 37, librerie ARM64, APK non debuggabile e firma valida.
Un hash di build non costituisce da solo un'attestazione crittografica dei sorgenti.

## 2. ROM combinata: compilazione target-files

Usa gli hash prodotti nel file di verifica, senza digitare password in questo
comando. Le credenziali di firma APK non sono richieste per lo staging:

```bash
export AMPY_BUILD_NUMBER=2026102700
export AMPY_SANDBOX_PREBUILT_APK=/srv/ampy/apk-release/AmpybiOS-release.apk
export AMPY_SANDBOX_EXPECTED_APK_SHA256=INCOLLA_SHA256_APK
export AMPY_SANDBOX_EXPECTED_SIGNER_SHA256=INCOLLA_SHA256_CERTIFICATO
bash scripts/build_combined_cheetah.sh /srv/ampy/grapheneos-2026100600
```

La procedura verifica l'app, sincronizza il tag GrapheneOS con controllo della
firma, genera i vendor files con adevtool, integra le app e compila
`vendorbootimage vendorkernelbootimage target-files-package otatools-package`.
Usa `TREE/out` per gli output. Non attiva l'Updater ufficiale GrapheneOS, non
modifica il kernel di serie e non collega un servizio OTA non configurato.

**Il target-files ZIP è un input di rilascio, non un pacchetto da installare.**

## 3. Firma ROM e ZIP installabile

Prepara le chiavi persistenti `keys/cheetah` seguendo la guida ufficiale
GrapheneOS, con la tua identità AmpybiOS. Conserva backup separati e proteggi
le chiavi con passphrase. Non usare chiavi pubbliche di test e non caricarle
in Git. Sono richieste anche AVB e la chiave SSH per autenticare il factory ZIP.

```bash
bash scripts/sign_combined_release.sh /srv/ampy/grapheneos-2026100600 2026102700
```

Questo wrapper usa gli strumenti **del checkout upstream** `finalize.sh` e
`generate-release.sh`. Rifiuta il numero build errato, le chiavi mancanti e
la sovrascrittura di una directory di rilascio esistente. Produce, soltanto
dopo una vera compilazione e firma:

```text
releases/2026102700/release-cheetah-2026102700/
  cheetah-install-2026102700.zip       factory ZIP per prima installazione
  cheetah-install-2026102700.zip.sig   autenticazione factory SSH
  cheetah-ota_update-2026102700.zip    aggiornamento A/B della propria ROM
  cheetah-target_files.zip            archivio firmato di rilascio
  AmpyRom-audit.json                  controlli realmente completati
```

L'audit controlla firma factory con la tua chiave pubblica, device `cheetah`,
set di immagini, hash del payload OTA e presenza dell'esatto APK approvato nei
target-files firmati. Riporta sempre `hardware_boot_validated: false`:
metadati/hash non sostituiscono test reali di avvio, AVB, modem e fotocamera.
Il pacchetto OTA non è destinato a trasformare una ROM stock con firme diverse.

Una prima installazione di un sistema personalizzato e lo sblocco del
bootloader possono cancellare i dati. Non bloccare nuovamente il bootloader
prima di aver verificato immagini, chiavi AVB e avvio con la procedura adatta.
Questo progetto non esegue automaticamente flash o cancellazioni sul Pixel.

## GitHub Actions

La workflow manuale **AmpybiOS combined cheetah target-files** richiede un
runner del proprietario con etichette `self-hosted, linux, x64, ampybios-rom`.
Non viene avviata dai pull request. Compila i target-files lasciandoli sul
tuo host; non pubblica binari contenenti il progetto privato negli artifact
di una repository pubblica. La firma rimane un passaggio locale con le tue chiavi.

## Verifiche e limiti di questa consegna

- Test host della repository di integrazione: eseguiti.
- Core Python, matematica C++ e Organic SVG Engine dell'app originale: eseguiti.
- Le firme APK nei test di staging usano doppi di test; la verifica factory
  usa vere chiavi OpenSSH temporanee su archivi sintetici, non firmware.
- Nessun APK nuovo è stato compilato in questo ambiente.
- Nessuna ROM è stata compilata, firmata o avviata sul Pixel in questa consegna.
- Qui mancano RAM, disco e strumenti per completare il build Android completo.

Vedi `FUNZIONI_INTEGRATE_IT.md` per le capacità e `STATUS.md` per lo storico.
