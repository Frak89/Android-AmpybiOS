# Cosa può fare il progetto integrato

Le funzioni qui elencate sono presenti nei sorgenti. I test host non dimostrano
che la ROM sia stata compilata o avviata sul Pixel. L'integrazione di sistema
diventa effettiva dopo la build completa descritta nella guida.

| Componente | Funzioni presenti | Limite attuale |
| --- | --- | --- |
| Base Android-AmpybiOS | Bootstrap GrapheneOS Android 17, target Pixel 7 Pro `cheetah`, vendor files e build | Checkout completo e firmware non prodotti qui |
| AmpySandbox / app AmpybiOS | Core Python modulare con librerie C++, cartelle e moduli editabili, app con alias | È un'app dentro il sistema Android; non sostituisce da sola il kernel Linux |
| Notepad | Note e salvataggio locale | UI e storage del sandbox |
| Calculator | Calcoli scientifici e grafici | Funzionalità dell'app Python |
| Cybercity 3D | Scena GLES 3 con edifici, pioggia, riflessi, bloom, ombre e profili grafici | Prestazioni fisiche non misurate in questa consegna |
| Dummy | Cloni con alias, codice e dati distinti, attivazione/disattivazione | Le risorse del dispositivo restano il limite pratico |
| Visualizzatore 3D | Modello FlightHelmet, rotazione, zoom e vista esplosa/PBR | Risorse modello/texture scaricate e verificate durante la build APK |
| AmpyRaw nel sandbox | Editor fotografico e ponte al decoder nativo LibRaw | Test Canon reali ancora necessari; nessuna promessa di scrittura Canon CR3 |
| AmpyCam | Camera2 con anteprima e acquisizione RAW DNG | Hardware RAW necessario; non converte DNG in CR3 |
| Orologio e batteria del sandbox | Viste decorate, impostazioni, dati reali e animazioni a pressione prolungata | Funzioni nell'interfaccia Ampy; non sostituzione di SystemUI Android |
| Organic SVG Engine e temi | Forme Bézier, import/export SVG/JSON, palette, glow e preset riutilizzabili | Sottoinsiemi/formati documentati, non esecuzione arbitraria di SVG attivi |
| Lab/Tilt nel sandbox (AmpyLab standalone separato) | Esperimenti di tilt/parallax, touch e gesture ottiche locali | Il movimento ottico non è riconoscimento completo delle mani |
| AmpyLauncher Android | Candidato Home, drawer app Android, ricerca, ora e batteria | La Home predefinita si sceglie con il consenso Android |
| AmpyCore | Temi, angoli organici, radar, preview di profili Eco/Balanced/Fusion Extreme e monitor termico/frame | I profili regolano il rendering delle app, non sono overclock del kernel |
| AmpyConnect | Lettura di informazioni Home Assistant via HTTPS | Non invia comandi agli attuatori |
| AmpyRec | Analisi ZIP limitata, staging temi LIVE, promemoria e applicazione confermata | Non è recovery, non crea partizioni e non installa ROM |
| AmpyBoot | Generazione di fotogrammi numerati da SVG e packaging bootanimation | Non modifica il logo proprietario del bootloader; integrazione opzionale |
| Build combinata | Inclusione dell'APK originale conservandone firma e package, preparazione target-files/otatools | Richiede una macchina Linux adeguata e l'APK release firmato |
| Firma e rilascio | Wrapper della procedura upstream per factory ZIP e OTA A/B, audit hash/device/firma | Richiede chiavi persistenti del proprietario e successivo test hardware |

## Richieste che rimangono da realizzare

- Skin globale interna a SystemUI, animazioni organiche applicate a tutta
  Android e a tutte le app; il codice attuale non modifica questi componenti.
- Root predefinito e kernel custom attivato: non inclusi nella build combinata.
  La patch kernel M1 presente nel progetto resta sperimentale e disattivata.
- Server AmpyOTA proprio e aggiornamenti automatici firmati operativi.
- Preinstallazioni Proton/Signal/Bitwarden/Aegis/Organic Maps/VLC/Accrescent:
  la selezione esiste nella configurazione, i relativi APK non sono allegati.
- Emulazione perfetta generica di CPU ARM/AVR/ESP32: il modulo Ampy8 è
  didattico e non fornisce questo tipo di emulazione hardware.
- Verifica sul Pixel di avvio, telefonia, modem, fotocamera, sensori, suspend,
  temperatura, 120 Hz effettivi, Verified Boot, aggiornamento e recupero.

Non sono dichiarate operative funzioni soltanto configurate o pianificate.
