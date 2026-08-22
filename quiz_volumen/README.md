# quiz_volumen — prueba de concepto

Lee la pregunta de opción múltiple que esté en pantalla, se la manda a Claude,
y traduce la respuesta al **volumen del sistema**: A = 1, B = 2, C = 3, D = 4…

```
captura de pantalla  →  Claude (visión)  →  letra  →  volumen del sistema
```

## Instalación

```bash
pip install -r requirements.txt
export ANTHROPIC_API_KEY=sk-ant-...        # en Windows: setx ANTHROPIC_API_KEY sk-ant-...
```

En **Windows** hace falta además `pip install pycaw comtypes` para poder mover
el volumen. En **macOS** y **Linux** no hace falta nada extra.

La primera vez, macOS va a pedir permiso de *Grabación de pantalla* para tu
terminal (Ajustes → Privacidad y seguridad → Grabación de pantalla).

## Uso

```bash
# Una sola vez, con lo que haya en pantalla ahora
python3 quiz_volumen.py

# Revisando cada 5 segundos (Ctrl-C para salir)
python3 quiz_volumen.py --watch 5

# A=10%, B=20%, C=30%, D=40% — se nota mucho más que 1%, 2%, 3%
python3 quiz_volumen.py --step 10

# Probar el control de volumen sin gastar API
python3 quiz_volumen.py --fake-answer C --step 10

# Ver qué contestaría sin tocar el volumen
python3 quiz_volumen.py --dry-run
```

### Opciones

| Opción | Para qué |
|---|---|
| `--watch SEG` | Repetir cada SEG segundos. Si la pantalla no cambió, no vuelve a preguntar (no gasta API). |
| `--step N` | Volumen por letra. Default `1` (A=1%, B=2%…). Con `10` queda A=10%, B=20%… |
| `--hold SEG` | Después de SEG segundos regresa el volumen a como estaba. |
| `--min-confianza 0-1` | No mueve el volumen si Claude no está lo bastante seguro. |
| `--region X,Y,W,H` | Capturar sólo un rectángulo en vez de toda la pantalla. |
| `--monitor N` | Qué pantalla (1 = principal, 0 = todas juntas). |
| `--image RUTA` | Usar un PNG en vez de capturar (útil para probar). |
| `--save-shot RUTA` | Guardar la captura para revisar qué vio Claude. |
| `--dry-run` | Hace todo menos cambiar el volumen. |
| `--fake-answer LETRA` | Se salta la API y finge esa respuesta. |
| `--model` | Default `claude-opus-5`. |

## Cómo funciona

1. **Captura** — `mss` toma el PNG (rápido y multiplataforma). Si no está
   instalado, cae a `screencapture` en macOS o `gnome-screenshot`/`scrot`/`grim`
   en Linux.
2. **Claude** — el PNG va como imagen base64 a la Messages API con
   `output_config.format` (salida estructurada), así que la respuesta siempre
   llega como JSON válido con la letra, la confianza y una razón corta. No hay
   que parsear texto libre.
3. **Volumen** — `osascript` en macOS, `wpctl`/`pactl`/`amixer` en Linux,
   `pycaw` (o `nircmd`) en Windows.

Si no encuentra ninguna pregunta en pantalla, no toca el volumen.

## Límites conocidos

- Un solo dígito por ronda: el volumen sólo codifica una respuesta a la vez.
- Con `--step 1` la diferencia entre 1% y 2% no se oye; se ve en el indicador
  de volumen. Para distinguir de oído usa `--step 10` o más.
- En `--watch`, la detección de "la pantalla no cambió" es un hash exacto del
  PNG: un cursor parpadeando o un reloj cuentan como cambio.
- El indicador de volumen aparece en pantalla al cambiarlo — el canal no es
  discreto.

Es un prototipo para probar la idea; no lo uses en exámenes vigilados.
