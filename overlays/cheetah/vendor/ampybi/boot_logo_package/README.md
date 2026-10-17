# AmpybiOS Boot Logo Package

Questo pacchetto contiene gli script e le risorse per generare un'animazione di boot personalizzata "AmpybiOS" con testo che luccica, un logo organico che cambia forma dinamicamente e un pacchetto zip pronto da inserire in system/media/bootanimation.zip.

Contenuto:
- generate_boot_frames.py: script Python che genera una sequenza numerata di PNG a partire da SVG creati dinamicamente (richiede cairosvg, numpy opzionale).
- make_bootanimation.sh: wrapper shell che crea i frames, impacchetta bootanimation.zip usando scripts/build_bootanimation.py del repository.
- sample_template.svg: esempio di SVG di riferimento (non necessario, il generatore costruisce direttamente l'SVG dinamicamente).

Prerequisiti:
- Python 3.8+
- pip install cairosvg numpy
- In alternativa: Inkscape / rsvg-convert se preferisci rasterizzare SVG esterni.
- Lo script repository scripts/build_bootanimation.py (presente in Android-AmpybiOS) per impacchettare i PNG in bootanimation.zip

Uso rapido (da repository root):

```bash
# creare un virtualenv consigliato
python3 -m venv .venv-boot && source .venv-boot/bin/activate
pip install --upgrade pip
pip install cairosvg numpy

# Generare i frame (es. 120 frame, 30 FPS, 1080x2400)
python3 overlays/cheetah/vendor/ampybi/boot_logo_package/generate_boot_frames.py --out overlays/cheetah/vendor/ampybi/boot_logo_package/frames --count 120 --width 1080 --height 2400 --text "AmpybiOS"

# Creare bootanimation.zip
bash overlays/cheetah/vendor/ampybi/boot_logo_package/make_bootanimation.sh overlays/cheetah/vendor/ampybi/boot_logo_package/frames overlays/cheetah/vendor/ampybi/boot_logo_package/bootanimation.zip

# A questo punto copia bootanimation.zip in device overlay
# cp overlays/cheetah/vendor/ampybi/boot_logo_package/bootanimation.zip ~/android/grapheneos-2026100600/vendor/ampybi/bootanimation.zip
# e aggiungi a PRODUCT_COPY_FILES: vendor/ampybi/bootanimation.zip:system/media/bootanimation.zip
```

Design e dettagli tecnici:
- Il generatore crea per ognuno dei N frame un'immagine PNG di dimensione width x height.
- Il "logo" è composto da un blob organico (path SVG calcolato in coordinate polari) che evolve nel tempo variando la fase e la frequenza delle componenti sinusoidali per simulare morphing.
- Il testo centrale "AmpybiOS" usa un riempimento a gradiente e su di esso viene applicata una striscia di luce (specular highlight) che si muove lungo i frame per dare l'effetto "luccicante".
- I colori e i parametri (ampiezza blob, numero di ondulazioni, palette) sono modificabili via argomenti CLI dello script.

Licenza: MIT
