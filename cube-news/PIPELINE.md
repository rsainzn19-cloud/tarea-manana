# Cube News: plan de producción

Este es el plan acordado para cuando arranque la producción diaria. Cualquier sesión que trabaje en Cube News debe seguirlo.

## Calendario

- **Todos los días:** un Short en inglés con la noticia del día que más retención pueda tener. El tema puede ser cualquiera: mundo, ciencia, tecnología, internet, YouTubers, deportes, etc.
- **Lunes, miércoles y viernes:** un video largo horizontal que recopila las noticias más importantes desde el recopilatorio anterior.
  - El del lunes cubre de viernes a domingo.
  - El del miércoles cubre lunes y martes.
  - El del viernes cubre miércoles y jueves.

## Cómo elegir la noticia del día

- **Gancho en el primer segundo:** una cifra que sorprende, un giro o algo difícil de creer.
- **Se entiende sola en menos de 50 s**, aunque el espectador no tenga contexto.
- **Se puede contar con escenas de bloques:** un lugar, un objeto o una cifra en pantalla.
- **Está confirmada por al menos 2 fuentes confiables.** Los rumores sin confirmar no se usan, aunque retengan.

## Reglas fijas

- **Nunca mostrar personas reales.** A los YouTubers y famosos se les nombra en texto y en la voz; en pantalla aparecen objetos, lugares, cifras o personajes genéricos que no se les parezcan.
- **Nada de terceros:** no se usan clips, miniaturas, logos ni música ajenos. Todo se genera con código.
- **Tragedias y conflictos:** tono serio y sin chistes. Nada de violencia gráfica.
- **Cierre de Mr. News:** siempre termina con "Stay square!" y su guiño.

## Lo que se entrega por cada noticia

1. **Short vertical** de 35–50 s para YouTube, Instagram y Facebook.
2. **Versión para TikTok** de 61–70 s, porque Creator Rewards solo paga videos de más de 1 minuto.
3. **Textos:** título, descripción con las fuentes y hashtags. Se agregan a `videos/DESCRIPCIONES.md` o a un archivo nuevo por semana.

## Recopilatorio de lunes, miércoles y viernes

- **Formato:** horizontal 16:9, con intro y jingle de Cube News.
- **Duración objetivo: 8 min o más,** que es lo que permite anuncios a la mitad del video.
- **Contenido:**
  - Las historias del periodo, re-renderizadas en horizontal y con más contexto.
  - 3–5 noticias extra que no salieron como Short, para llegar a la duración.

## Flujo diario

1. **Buscar las noticias del día** y elegir la de más retención, con sus fuentes.
2. **Escribir el guion en inglés y generar la voz** de Mr. News: Kokoro `am_michael` con el efecto chillón. Después, comprobar con Whisper que se entiende.
3. **Armar la escena** con la biblioteca de escenarios y renderizar los dos formatos.
4. **Revisión:** se manda una hoja de capturas y los videos para aprobar.
5. **Publicación:** con Metricool cuando esté conectado. Mientras tanto, se entregan el MP4 y los textos para subirlos a mano.

## Por decidir antes de arrancar

- Hora de publicación y zona horaria.
- Si cada video se aprueba antes de publicarse o se publica automáticamente.
- Conectar Metricool, y si se quiere vidIQ, en https://claude.ai/customize/connectors.
- Dónde guardar los MP4: en Google Drive o en algún almacenamiento con enlace público. En git el repo crecería mucho.
