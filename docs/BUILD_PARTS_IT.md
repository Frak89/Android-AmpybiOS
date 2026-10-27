# AmpyBuild: parti parallele e host ridotto

Le app sono unità compilabili indipendentemente. La pipeline personale nella
repository privata Frak89/AmpybiOS, branch `ampy/build-combined-parts`, compila
l'app Python/C++ e cinque app Android in job paralleli. Gli APK di questa fase
sono release **non firmati**: non sono installabili e non sono una ROM.
Un job successivo li raccoglie con hash SHA256 e riferimenti sorgente.

Il job finale ROM usa un runner del proprietario con etichette
`self-hosted, linux, x64, ampybios-rom`. Scarica l'APK unsigned del sandbox,
lo firma con una chiave persistente già presente sull'host e verifica il
certificato atteso, poi compila il sistema. Le altre cinque app vengono
compilate dal sistema Android dai sorgenti dello stesso commit; i loro APK
standalone sono una verifica indipendente, non sostituiscono i moduli AOSP.

Il runner deve avere queste variabili d'ambiente del servizio (non in Git):
`AMPY_TREE`, `AMPY_SANDBOX_KEYSTORE`, `AMPY_SANDBOX_KEY_ALIAS`,
`AMPY_SANDBOX_EXPECTED_SIGNER_SHA256`, e password protette
`AMPY_SANDBOX_KS_PASS` / `AMPY_SANDBOX_KEY_PASS` se necessarie. Le chiavi ROM
persistenti devono essere già in `AMPY_TREE/keys/cheetah`. Il wrapper upstream
di firma può richiedere una sessione interattiva per chiavi cifrate: in quel
caso eseguire manualmente `sign_combined_release.sh` sull'host dopo la build.
Il job non crea chiavi sostitutive, non carica firmware privato in repository
pubbliche e non esegue flash. Senza runner disponibile rimane **in coda**;
non significa che la compilazione sia iniziata.

## Tentativo su macchina con poche risorse

Per richiesta esplicita del proprietario è possibile tentare comunque:

```bash
export AMPY_ALLOW_CONSTRAINED_HOST=1
export AMPY_BUILD_JOBS=1
bash scripts/build_combined_cheetah.sh "$AMPY_TREE"
```

La prima variabile rende non bloccanti gli avvisi RAM/spazio. Linux x86_64 e
gli strumenti necessari rimangono obbligatori. La seconda passa `-j1` alla
compilazione Android. Non crea spazio aggiuntivo, swap o storage remoto e
non garantisce che Soong o il linker rientrino nella RAM disponibile.
Il valore predefinito mantiene i controlli rigorosi.

Gli output intermedi dello stesso checkout permettono di riprendere una
build interrotta: non cancellare `out`, non cambiare pin sorgente/build number
durante la ripresa e non avviare due processi di compilazione sul medesimo
`out`. Partizionare il file ZIP avviene soltanto dopo una build riuscita;
branch e job separati non combinano i dischi di più macchine per Soong.

Riferimenti: https://grapheneos.org/build e
https://docs.github.com/en/actions/using-jobs/using-a-matrix-for-your-jobs
