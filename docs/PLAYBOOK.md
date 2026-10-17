# Playbook per costruire AmpybiOS (Google Pixel 7 Pro - cheetah)

Questo playbook descrive tutti i passi necessari per sincronizzare le sorgenti GrapheneOS, applicare le overlay AmpybiOS, generare la animazione di boot personalizzata "AmpybiOS", costruire le immagini e flasharle su un Google Pixel 7 Pro (cheetah).

Nota: il file è fornito in formato Markdown. Per generare un file `playbook.docx` eseguibile con Microsoft Word, usa lo script `scripts/generate_playbook_docx.sh` incluso in questo repository che richiede `pandoc`.

Indice
- Requisiti
- Preparazione dell'ambiente
- Sincronizzare GrapheneOS
- Applicare overlay AmpybiOS
- Generare bootanimation.zip personalizzato
- Integrare AmpyCore (APK o modulo nativo)
- Integrazione kernel Pantah (prebuilt)
- Eseguire la build
- Firmare e preparare le immagini per rilascio
- Flash su dispositivo
- Testing e verifica
- Automazione e CI (self-hosted)
- Note di sicurezza


REQUISITI MINIMI
----------------
- Sistema operativo: Linux x86_64 (Ubuntu/Debian consigliato)
- Storage libero: 200–300+ GiB (dipende da overlay e vendor blobs)
- RAM: minimo 32 GiB (consigliato 64 GiB)
- CPU multicore

Software richiesto (installare i pacchetti appropriati per la distro):
- repo (Android repo tool)
- git, curl, ssh-keygen
- python3 (3.8+)
- pip, virtualenv (opzionale)
- nodejs 24 LTS e yarn
- OpenJDK 17
- Android SDK (API 37) se compili APK o moduli
- yarn (per adevtool)
- adb, fastboot
- opcionalmente: pandoc (per generare playbook.docx dal Markdown)


PREPARAZIONE DELL'AMBIENTE
--------------------------
1) Crea una directory di lavoro:

```bash
mkdir -p ~/android
cd ~/android
```

2) Clona o posiziona il repository Android-AmpybiOS (se non è già presente):

```bash
git clone https://github.com/Frak89/Android-AmpybiOS.git
cd Android-AmpybiOS
```

3) Assicurati che gli script creati siano eseguibili:

```bash
chmod +x scripts/*.sh overlays/cheetah/vendor/ampybi/boot_logo_package/*.sh
```


SINCRONIZZARE LE SORGENTI GRAPHENEOS
-----------------------------------
Usa lo script di preparazione incluso nel repo per inizializzare il manifest e sincronizzare il tree GrapheneOS (tag target: 2026100600 per questa guida).

```bash
# Esempio: sincronizza in ~/android/grapheneos-2026100600
python3 scripts/prepare_source.sh ~/android/grapheneos-2026100600
```

Lo script verificherà il tag e scaricherà il manifest. Se preferisci usare opzioni avanzate (mirror kernel), esporta la variabile `AMPY_USE_KERNEL_MIRROR=1` prima della chiamata.


APPLICARE LE OVERLAY AMPYBIOS
----------------------------
Dopo la sincronizzazione, copia le overlay dal repo AmpybiOS nel tree GrapheneOS.

```bash
# Dalla root di Android-AmpybiOS
python3 scripts/copy_overlays.py ~/android/grapheneos-2026100600
```

Questo copierà il contenuto di `overlays/cheetah/` nella corrispondente posizione del tree GrapheneOS. I file principali sono:
- device/ampybi_product.mk (frammento product)
- vendor/ampybi/bootanimation.zip (opzionale, generato localmente)
- vendor/ampybi/prebuilts/AmpyCore.apk (opzionale)

Verifica che i file siano stati copiati correttamente.


GENERARE L'ANIMAZIONE DI BOOT (AmpyBoot)
----------------------------------------
Nel repository AmpybiOS è presente uno script per generare una boot animation personalizzata: `overlays/cheetah/vendor/ampybi/boot_logo_package/generate_boot_frames.py` e il wrapper `make_bootanimation.sh`.

1) Preparare l'ambiente Python:

```bash
cd overlays/cheetah/vendor/ampybi/boot_logo_package
python3 -m venv .venv-boot && source .venv-boot/bin/activate
pip install --upgrade pip
pip install cairosvg numpy
```

2) Generare i frames PNG numerati (esempio: 120 frame, 1080×2400):

```bash
python3 generate_boot_frames.py --out ./frames --count 120 --width 1080 --height 2400 --text "AmpybiOS"
```

Per test rapidi puoi ridurre a `--count 24 --width 540 --height 1200`.

3) Creare il file `bootanimation.zip` usando lo script di packaging (incluso nel repo):

```bash
bash make_bootanimation.sh ./frames ./bootanimation.zip 1080 2400 30
```

4) Copia `bootanimation.zip` nella posizione vendor usata dall'overlay (il comando `copy_overlays.py` l'avrà creato come placeholder ma è possibile sovrascriverlo):

```bash
cp ./bootanimation.zip ~/android/grapheneos-2026100600/vendor/ampybi/bootanimation.zip
```

5) L'overlay `ampybi_product.mk` include la riga:

```
PRODUCT_COPY_FILES += vendor/ampybi/bootanimation.zip:system/media/bootanimation.zip
```

che copierà lo zip all'interno dell'immagine di sistema durante la build.


INTEGRARE AMPYCORE (APK o modulo nativo)
---------------------------------------
Hai due opzioni per includere AmpyCore nella ROM:

Opzione A — Modulo nativo (meglio per integrazione piena):
- Converti `apps/AmpyCore` in un modulo Android build (`Android.bp`) se non già presente.
- Aggiungi `AmpyCore` a `PRODUCT_PACKAGES` nel product fragment (già presente come esempio in `ampybi_product.mk`).

Opzione B — Prebuilt APK (più rapido per test):
- Compila localmente l'APK con Gradle o Android Studio:
  `cd apps/AmpyCore && ./gradlew :app:assembleRelease`
- Copia l'APK in `overlays/cheetah/vendor/ampybi/prebuilts/AmpyCore.apk` e riesegui `copy_overlays.py` o copia manualmente in `~/android/grapheneos-2026100600/vendor/ampybi/prebuilts/AmpyCore.apk`.
- L'overlay contiene la riga `PRODUCT_COPY_FILES += vendor/ampybi/prebuilts/AmpyCore.apk:system/priv-app/AmpyCore/AmpyCore.apk` per installarlo come app privilegiata.


INTEGRAZIONE KERNEL PANTAH (PREBUILTS)
--------------------------------------
Se vuoi usare i prebuilt kernel Pantah dal repo `Frak89/device_google_pantah-kernels_6.1`:

```bash
git clone https://github.com/Frak89/device_google_pantah-kernels_6.1.git ~/src/device_google_pantah-kernels_6.1
mkdir -p ~/android/grapheneos-2026100600/vendor/pantah-kernels
cp -r ~/src/device_google_pantah-kernels_6.1/* ~/android/grapheneos-2026100600/vendor/pantah-kernels/
```

Poi modifica il `BoardConfigVendor.mk` o il makefile device specifico in `device/google/cheetah` per puntare ai prebuilts, o aggiungi `PRODUCT_COPY_FILES` per il `boot.img`/artifacts necessari.

In alternativa, se vuoi compilare il kernel sorgente, usa lo script:

```bash
bash scripts/prepare_kernel_sources.sh ~/src/kernel_pixel_6.1
```


ESEGUIRE LA BUILD
-----------------
1) Impostare l'ambiente di build e selezionare il target (lunch):

```bash
cd ~/android/grapheneos-2026100600
source build/envsetup.sh
# Nota: il nome del lunch può variare; uno script incluso usa `lunch cheetah-cur-user`
lunch cheetah-cur-user
```

2) Costruire i target di sviluppo (vendor images e target-files):

```bash
m vendorbootimage vendorkernelbootimage target-files-package -j$(nproc)
```

Questo genera i pacchetti in `out/target/product/cheetah/`.


FIRMA E PREPARAZIONE PER RILASCIO
---------------------------------
- Per test puoi utilizzare chiavi di debug, ma per una release ufficiale devi generare e proteggere nuove chiavi di rilascio (platform, release, boot, etc.).
- NON commettere le chiavi nel repository.
- Per OTA sicura devi gestire un server AmpyOTA e firmare i payload con le chiavi appropriate.


FLASH SU DISPOSITIVO (ATTENZIONE)
----------------------------------
- Sbloccare il bootloader cancellerà tutti i dati del dispositivo. Fai backup.
- Esempio di flash:

```bash
fastboot reboot bootloader
# Assicurati bootloader unlocked
fastboot -w update out/target/product/cheetah/images-cheetah.zip
```

Verifica lo stato del dispositivo e i log via `adb logcat` dopo l'avvio.


TESTING E VERIFICA
------------------
- Controlla la presenza di AmpyCore in /system/priv-app.
- Verifica che `system/media/bootanimation.zip` sia il nuovo file e che l'animazione venga riprodotta.
- Raccogli log: `adb logcat -d > logcat.txt`.
- Per problemi kernel: controlla `dmesg` o `adb shell cat /proc/kmsg` (se accessibile).


AUTOMAZIONE E CI (SELF-HOSTED)
------------------------------
- GitHub-hosted runners non hanno risorse sufficienti per build complete (spazio e RAM). Usa un self-hosted runner con:
  - 256+ GiB disco
  - 64+ GiB RAM
  - CPU multicore
- Workflow consigliato:
  - job: sync sources (repo sync)
  - job: apply overlays
  - job: build (on self-hosted)
  - job: collect artifacts (target-files.zip, images)

Posso preparare un esempio di workflow GitHub Actions che invoca un self-hosted runner per la build e archivia artefatti.


NOTE DI SICUREZZA
------------------
- Non caricare mai chiavi private, token o certificati nel repo.
- Per distribuzioni pubbliche: imposta un processo di firma e verifica delle immagini, e mantenere i server OTA e chiavi protetti.
- Test su dispositivi fisici con attenzione: lo sblocco del bootloader e il flashing possono compromettere dispositivi di produzione.


ALLEGATI E FILE UTILI
--------------------
- scripts/prepare_source.sh
- scripts/copy_overlays.py
- overlays/cheetah/device/ampybi_product.mk
- overlays/cheetah/vendor/ampybi/boot_logo_package/*


CONTATTI E SUPPORTO
-------------------
Se durante l'esecuzione incontri errori, copia qui gli output di `repo sync`, `m`, o `adb logcat` e ti aiuterò a risolverli.
