# Scanner V2: reconocimiento visual de cartas — plan definitivo de ejecución

Fecha de decisión: 2026-10-04. Precisión de alcance: 2026-10-07. Estado: ejecución de inventario y herramientas iniciada; motor visual y pruebas de reconocimiento nuevo pendientes. Seguimiento: `docs/plans/scanner-v2-progress.md`.

Este documento sustituye `C:\Users\Miguel\Desktop\prompt-scanner-v2-art-recognition.md` como especificación de ejecución. Es una referencia duradera, conservada en `docs/adr/`, no un plan temporal. La implementación se hará en una tarea posterior. Las cifras de aceptación son objetivos, no resultados obtenidos.

## 1. Decisión y resultado esperado

Construir un scanner local capaz de localizar una carta física completa, corregir su perspectiva y reconocer su identidad visual sin leer el nombre. El OCR actual seguirá disponible y será el modo predeterminado hasta superar las puertas de calidad. El nuevo motor debe funcionar con OCR desactivado; el modo híbrido será una mejora adicional, nunca una forma de ocultar un reconocedor visual insuficiente.

La arquitectura elegida separa **localización → normalización → recuperación de candidatos → verificación → decisión temporal → selección de impresión → cola existente**. El motor recupera identidades de un catálogo actualizable, no clasifica entre una lista fija de nombres. Se recomienda búsqueda mediante embeddings compactos y verificación de características locales; pHash es el baseline obligatorio y una posible señal auxiliar. El algoritmo concreto se congela tras una comparación reproducible y una prueba temprana en Android. No se prohíbe ML por el fracaso anterior ni se da por hecho que cualquier embedding funcionará.

Un paquete descargable de cientos de MB es aceptable. La prioridad es precisión, cobertura y comportamiento estable; el límite práctico está en la RAM, el tiempo por captura, la temperatura y la instalación fiable. El catálogo y los modelos se distribuyen desde Cloudflare R2; las imágenes de cámara permanecen en el dispositivo. Miguel precisó el 2026-10-07 que, tras descargar el catálogo pertinente, la identificación, la comprobación de candidatos y la resolución de impresión deben ejecutarse en el dispositivo sin consultas al backend. El catálogo debe incluir el payload versionado necesario para resolver localmente un `Card` válido de sus impresiones elegibles; no se exige red para comprobar una carta durante el escaneo visual.

La primera entrega cubre una carta objetivo por vez, completamente visible, con libertad razonable de posición y rotación dentro del visor. La guía ayuda a encuadrar, pero no exige alinear la ilustración a un rectángulo exacto. Reconocer varias cartas simultáneamente, oclusiones fuertes, cartas dentro de slabs y autenticar falsificaciones quedan fuera de la primera entrega. Mostrar una carta en otra pantalla o un proxy visualmente idéntico puede producir la misma identidad: esto no es autenticación.

## 2. Qué se conserva y qué se corrige del borrador

| Propuesta original | Resolución y motivo |
|---|---|
| Mantener OCR y desarrollar por fases | Se conserva, con comparación y rollback explícitos. |
| Rectificar la geometría antes de comparar | Se conserva. Detectar un rectángulo orientado no demuestra haber recuperado las cuatro esquinas proyectivas. |
| pHash como solución definitiva, sin ML | Se reemplaza por comparación de alternativas. Su dependencia del recorte puede perjudicar precisamente las cartas difíciles. |
| Un `art_crop` y un representante por ilustración | Insuficiente: hacen falta caras, distintas presentaciones visuales, correspondencias a todas las impresiones elegibles y conflictos de identidad. |
| Recorte fijo x=7,5–92,5%, y=11–56% | Solo baseline para el layout comprobado. No es válido universalmente ni necesariamente equivalente al `art_crop` de Scryfall. |
| Fondo difícil → usar la guía como si fuera una carta | La guía solo genera una hipótesis. No habilita aceptación sin evidencia visual independiente. |
| Girar 180° el recorte superior | Incorrecto para una carta invertida: primero se orienta la carta completa y después se extrae su arte. |
| `1 - distance/255` como confianza y umbrales 50/25 | Se guardan como experimentos del baseline; no son probabilidades ni umbrales certificados. |
| Arte y OCR coinciden → aceptar instantáneamente | Solo pueden combinarse si pertenecen al mismo objeto, generación y ventana temporal. La coincidencia de nombre no identifica la impresión. |
| Desacuerdo → gana arte si similitud ≥0,9 | Se elimina. El desacuerdo invalida la aceptación automática y pide más evidencia o selección. |
| 10 fotos, tres sets y 20 cartas de tuning | Sirven para depurar, no para aceptar el producto. Se exige catálogo completo y conjunto físico independiente. |
| Muestra de 100.000 pares para calibrar colisiones | No descubre los vecinos difíciles de cada referencia. Se necesita evaluación de vecinos cercanos y falsos positivos por presentación. |
| Archivo total de 4–8 MB | Ni el propio formato cumple necesariamente: 130.000 × 56 = 7,28 MB, antes de tabla de strings, nombres y cabecera. Los hashes solos ocupan 4,16 MB. |
| Motor siempre resuelve `Card` por Internet | Identidad, candidatos e impresión se resuelven localmente; el pack contiene el payload validado de las impresiones elegibles. No se fabrican campos vacíos ni se consulta el backend para completar la comprobación del scanner visual. |
| Borrar todo el antiguo pipeline al final | Revalidar primero: gran parte ya fue eliminada en agosto. No borrar por una lista histórica. |
| Añadir Quick Mode / Lookup Only a un sheet antiguo | El código actual cambió. Se define la nueva conducta sin resucitar ajustes antiguos como arreglo oportunista. |
| Kill switch y diseños especiales en backlog | Kill switch entra antes de beta; borderless/showcase y full-art forman parte de la evaluación principal. |

Los resultados del anexo fechado 2026-07-03 —295 referencias, distancias y cuatro fotos— se consideran **evidencia histórica declarada por el borrador**, no reproducida en esta revisión. Faltan los originales, scripts, configuración y particiones para verificarlos. No deben convertirse en golden tests ni en garantías de precisión.

ManaBox documenta reconocimiento por ilustración y recomienda contraste, buena luz y evitar reflejos y solapamientos. Es una referencia funcional útil. Su documentación consultada no identifica el modelo, formato interno ni explica cuánto ocupa su índice; no se deduce su implementación del tamaño observado de la aplicación. [Guía oficial de ManaBox](https://www.manabox.app/guides/scanner/getting-started/).

## 3. Integración: estado real comprobado en ManaHub

La revisión se hizo sobre el árbol de trabajo del 2026-10-04, con cambios locales ajenos ya presentes. Antes de implementar se debe registrar el commit, diff aplicable y versiones efectivas; los números de línea siguientes sirven para encontrar el contrato, no para aplicar parches ciegos.

| Archivo / ancla actual | Observación verificada | Consecuencia |
|---|---|---|
| `feature/scanner/data/CardRecognizer.kt:229`, `analyze` | OCR con intervalo de 800 ms, límite de llamadas, cachés, estabilidad previa de dos lecturas, generación y descarte por antigüedad. | Mantener esta conducta en modo OCR; el visual tendrá su propio ritmo y no duplicará solicitudes. |
| `feature/scanner/presentation/ScannerScreen.kt:519`, `CameraPreview` | CameraX, pausa quitando analyzer, parada real para overlays, restauración de torch y `KEEP_ONLY_LATEST`. | Preservar ciclo de vida y ahorro de cámara. |
| `ScannerScreen.kt:889`, `FrameMetadataAnalyzer` | Ya no captura una lambda `isPaused`; cierre de emergencia en `catch`. | El supuesto cierre obsoleto del borrador no describe este código. Auditar propiedad del frame con pruebas, no dar por vigente el doble cierre histórico. |
| `ScannerViewModel.kt:275`, `processRecognitionResult` | Usa siempre `HIGH_CONFIDENCE_FRAMES = 1`; el comentario de estabilidad adaptativa no implica que se aplique. | No inyectar distancias visuales en el flujo actual esperando tres frames. Añadir una política visual explícita. |
| `domain/model/RecognitionResult.kt` | `Identified` lleva un `Card`, un escalar `similarity` y un booleano `ambiguous`. | Insuficiente para varios candidatos, estado offline y certeza de impresión. Crear contratos internos específicos y un adaptador final. |
| `ScannerScreen.kt:1453`, `AmbiguityDropdown` | Confirma o descarta un único nombre, no compara varios candidatos. | Reutilizar componentes base; se necesita un selector real con candidatos. |
| `ScannerViewModel.kt:1144`, `onOpenVariantSelector` | Consulta `getCardArtVariants`. | No tratar su nombre como evidencia de que retorna solo ilustraciones. |
| `shared/core-data/.../ScryfallRemoteDataSource.kt:362` | Actualmente consulta `game:paper` con `unique=prints`; toma `.data` de una respuesta. | Comprobar paginación y cobertura por idioma antes de reutilizarlo como selector exhaustivo. Preferir catálogo local para las relaciones del scanner. |
| `ScannerViewModel.kt:184`, `observeSharedQueue` | Cola compartida de colección y cola de deck específica; acciones de commit ya separadas. | Entregar solo selecciones resueltas a estas rutas. No reemplazar persistencia, wishlist, ownership ni gamificación. |
| `feature/scanner/di/ScannerModule.kt` | Hilt y singletons OCR/sonido; documenta eliminación de embeddings en agosto. | No cerrar singletons desde una pantalla ni reinstalar código antiguo sin revisión. |
| `settings.gradle.kts`, `app/build.gradle.kts:90`, `gradle/libs.versions.toml:21` | Proyecto ya multimódulo, minSdk 29 y CameraX declarado 1.6.2. | El supuesto de proyecto single-module ya no es correcto. Verificar dependencias resueltas antes del spike. |

Todas las rutas `feature/scanner/` anteriores parten de `app/src/main/java/com/mmg/manahub/`. Los tests actuales relevantes son `ScannerViewModelTest`, `CardRecognizerTest`, `CardOcrAnalyzerTest` y `CardOcrAnalyzerExtractionTest`.

El scanner sigue excluido de la migración KMP: su integración permanece Android/Hilt/Compose actual. La implementación nueva tendrá capas y contratos puros aislados, pero no migrará la feature ni editará `wasmJsMain` o `:webApp`. Un módulo JVM pequeño para contratos/algoritmos compartidos con herramientas es admisible; no convertirlo en migración KMP. Cualquier modificación Kotlin o Gradle se delega a `android-kotlin-architect`. El rol `compose-design-reviewer` del borrador ya no existe: la auditoría corresponde a `android-edge-case-tester`.

## 4. Contratos que deben quedar claros antes de programar

### 4.1 Tres identidades distintas

1. **Identidad de juego:** normalmente `oracle_id`, más identidad de cara cuando corresponda. Para objetos sin Oracle ID se conserva una clave tipada propia; no se inventa un Oracle ID.
2. **Apariencia visual:** referencia de imagen/cara/layout, con `illustration_id` opcional y relación muchos-a-muchos con identidades. Una ilustración no demuestra una edición ni necesariamente una única identidad de juego.
3. **Impresión:** UUID Scryfall, set, número de colección, idioma y acabados disponibles. La elección de foil/condición/cantidad del usuario no se infiere del embedding.

Una cara trasera de DFC remite a la carta padre; una imagen meld puede relacionarse con varias cartas y requiere selección. Split/adventure/flip no deben procesarse suponiendo que cada `card_faces` tiene imagen separada. Dorso genérico de Magic, publicidad y objetos no soportados son negativos explícitos. Tokens, emblemas y art-series tendrán cobertura y destino declarados; no se excluirán silenciosamente ni se guardarán como cartas jugables por similitud.

El resultado visual interno incluye candidatos, referencias/caras, identidad, evidencia geométrica, versión de paquete/modelo, `trackId`, instante monotónico de captura y generación. Estados mínimos: `NotReady`, `Searching`, `PoorCapture`, `Unknown`, `AmbiguousIdentity`, `RecognizedIdentity`, `NeedsPrinting`, `ReadyForQueue`, `RecoverableError`.

`ReadyForQueue` es el único estado que puede pasar al flujo de adición. Lleva un `Card` válido y una impresión elegida. No fabricar un `Card` parcial rellenando campos desconocidos como datos ciertos ni insertar el catálogo entero en Room al arrancar.

### 4.2 No confundir estabilidad con exactitud

La decisión utiliza calidad de captura, score absoluto calibrado, separación entre **identidades distintas**, verificación geométrica y consistencia temporal. Varias referencias del mismo arte no son competidores independientes. Al agrupar para decidir identidad se retiene aparte la ambigüedad de impresión; no se transforma un empate de ediciones en certeza.

Un score de Hamming o coseno no se muestra como «99% seguro» sin calibración independiente. Repetir tres veces el mismo error no lo corrige. Los umbrales se ajustan en calibración y se congelan antes de evaluación final, por familia de pipeline/layout cuando haya suficientes datos. Familias sin muestra suficiente usan selección manual.

## 5. Motor visual propuesto

### 5.1 Localizar la carta completa

Comparar dos localizadores con las mismas escenas: contornos/líneas OpenCV y un detector compacto de una clase con cuatro esquinas o segmentación. El segundo se entrena para encontrar **cartas**, no una clase por carta. La ruta recomendada, si el detector clásico falla con fundas y fondos poco contrastados, es detector aprendido más refinamiento de esquinas y tracking.

Las cajas delimitadoras y `minAreaRect` son propuestas, no rectificaciones fiables. Validar convexidad, área mínima, orden de esquinas, solapamiento, bordes visibles, distancia al borde de imagen y plausibilidad de la homografía. La proporción aparente cambia con la perspectiva: un filtro rígido 1,15–1,65 puede eliminar cartas válidas. Refinar el cuadrilátero sobre bordes reales, distinguiendo funda de carta. Si no se puede obtener geometría fiable, pedir recolocar; nunca convertir una guía fija en detección confirmada.

Buscar prioritariamente cerca de la guía y ampliar al visor con menor frecuencia si no hay candidato. Entre varias cartas, seleccionar una por proximidad al objetivo y continuidad de tracking; no alternar resultados ni contar las cartas secundarias. Registrar esta selección en `trackId`.

### 5.2 Normalización y calidad

Conservar la transformación completa entre sensor, `ImageProxy.cropRect`, rotación, vista previa, guía y coordenadas del overlay. Usar los contratos de transformación de CameraX y un viewport compartido donde proceda; coincidir en relación de aspecto no basta. Las transformaciones no disponibles durante el bind son estado de espera. Validar esquinas dibujadas sobre cartas reales en 0/90/180/270°, diferentes resoluciones, zoom y ventanas. [CameraX: transformaciones](https://developer.android.com/media/camera/camerax/transform-output).

Rectificar la carta completa a una proporción canónica documentada —por ejemplo 504×704 para 63:88—, con padding y convención de esquinas explícitos. La resolución definitiva depende de los ensayos. No confundir una imagen `border_crop` con la extensión física completa. Probar la orientación antes de extraer regiones; los layouts horizontales necesitan hipótesis específicas. Nunca aplicar reflexión especular como alternativa de lectura.

Medir desenfoque, tamaño útil de carta y reflejos/zonas saturadas. Una zona blanca impresa no equivale a reflejo: calibrar con cartas reales y evidencia temporal. Rechazar frames demasiado degradados, conservar los mejores pocos frames del track y ofrecer una instrucción estable. No fusionar píxeles de frames sin registrarlos geométricamente.

### 5.3 Recuperar candidatos: comparación obligatoria

| Candidato | Implementación de prueba | Qué decide su futuro |
|---|---|---|
| A: pHash rectificado | DCT 255 bits del borrador; referencias de tarjeta completa y regiones coherentes por layout, sin catálogo reducido en la evaluación final. | Baseline de tamaño y latencia; puede bastar en una familia, no se presume cobertura universal. |
| B: recuperación clásica + verificación | ORB con índice visual/invertido para shortlist; matching local y homografía. No comparar todos los descriptores de todas las cartas por frame. | Alternativa sin entrenamiento si conserva recall y precisión con un coste móvil aceptable. |
| C: embeddings compactos + verificación | Encoder móvil de recuperación, shortlist inicial top-20 y ORB/homografía sobre candidatos; comparar top-10/20/50. | Ruta recomendada si obtiene mejor cobertura de layouts/fundas y tolera la ejecución móvil. |

No usar pHash como filtro obligatorio antes de C: un candidato eliminado por error geométrico nunca llegará a la verificación. Se puede unir el shortlist de varias señales cuando la ablación demuestre beneficio y el coste esté acotado.

Baseline pHash reproducible: RGB a luminancia BT.601 documentada, resize bilinear a 64×64, DCT-II ortonormal, bloque superior izquierdo 16×16 sin DC, mediana de sus 255 coeficientes y 255 bits empaquetados en 32 bytes con el último bit a cero. No añadir normalización global de media/desviación como supuesta mejora de robustez: en aritmética ideal no cambia esta comparación AC/mediana; clipping y redondeo sí requieren pruebas. Mantener 50/25 solo como primera configuración de experimento, nunca como constantes de release heredadas.

Para embeddings, comenzar con búsqueda exacta de referencia; comparar ejecución móvil exacta vectorizada contra ANN. Si se usa ANN, medir pérdida de Recall@K respecto a exacta, tamaño real y latencia fría/caliente; su aceptación depende de conservar la puerta de recall final. No prometer unos pocos milisegundos extrapolando el coste de Hamming a cientos de dimensiones. Los filtros por set/idioma no deben ocultar alternativas globales necesarias para detectar una identificación errónea.

Para C, comenzar con backbone móvil pequeño y embedding L2 normalizado de 256 dimensiones; evaluar 128/256/512 y resolución 224/320, no fijarlas por costumbre. Un encoder DINOv2 puede servir de referencia offline o profesor, sin asumir que debe ejecutarse en el teléfono ni que sus features bastan para MTG. Comparar pretrained contra ajuste contrastivo/triplet con positivos de misma identidad visual y negativos difíciles. Aplicar augmentations de perspectiva, márgenes, fundas, reflejos localizados, blur, balance de color, exposición y oclusiones leves. Textos/nombres enmascarados y variantes de idioma impiden aprender solo tipografía. El fracaso previo no demuestra que todos los embeddings genéricos sean incapaces: geometría, representación y paridad deben medirse por separado. [DINOv2, trabajo original](https://arxiv.org/abs/2304.07193).

No entrenar un clasificador cerrado con 100.000 salidas: añadir una carta al catálogo no debería requerir otra clase y otro entrenamiento. Nuevas referencias se codifican con el modelo instalado. Un cambio de modelo invalida el espacio vectorial anterior y requiere regenerar el índice completo.

### 5.4 Verificación de candidatos

Precomputar un número acotado de descriptores locales y sus posiciones normalizadas por referencia. Cargar solo los de los candidatos. Usar matching consistente, ratio test/cross-check según ensayo y homografía robusta, con mínimos calibrados de inliers, distribución espacial, cobertura y error de reproyección. Unos pocos puntos colineales o coincidencias solo en el marco/texto repetido no verifican el arte. [OpenCV: ORB](https://docs.opencv.org/4.x/d1/d89/tutorial_py_orb.html), [matching y homografía](https://docs.opencv.org/4.x/d1/de0/tutorial_py_feature_homography.html).

ORB puede fallar con ilustraciones lisas, brillo o poca resolución. Conservar un verificador alternativo de regiones/embedding calibrado para esas familias, o exigir selección manual; no bajar globalmente el umbral. Las regiones se derivan del layout real, con máscaras de texto y correspondencias referencia/captura, no de un solo rectángulo superior para todo MTG. Full-art, extended-art, showcase, retro y DFC entran en esta fase, no al final del backlog.

### 5.5 Paridad reproducible

Versionar: decodificador y color space, orientación EXIF, RGB/BGR, rango completo/limitado, conversión YUV y strides, alpha, resize y centros de píxel, interpolación, warp, máscaras, normalización, modelo, salida y métrica. La luminancia Y del sensor no es automáticamente el mismo dato que BT.601 calculado sobre RGB decodificado.

Para pHash mantener una implementación de cálculo compartida JVM/Android, con endianness, recorrido de bits, comparación `>` frente a mediana, tratamiento de empate y bit de padding definidos. Probar entradas constantes, gradientes y coeficientes casi empatados. No «congelar» cualquier salida de la primera implementación: contrastar con una referencia independiente. Compartir código no garantiza por sí solo identidad numérica entre decodificadores y plataformas.

Para embeddings, generar referencias con el mismo modelo **exportado** y preprocessing que Android. Comparar FP32/FP16/INT8 y delegados contra referencia CPU; fijar tolerancias numéricas y equivalencia de ranking/decisión. La cuantización exige calibración representativa. Si afecta calidad, mantener FP16 antes que aceptar errores por ahorrar espacio. La exportación y ejecución móvil se prueban antes de invertir en el catálogo completo.

## 6. Catálogo y herramienta offline

### 6.1 Fuente, inventario y cobertura

Ingerir un snapshot bulk reproducible: `default_cards` como punto de partida de impresiones y `all_cards` para cobertura de idiomas cuando sea necesaria; `unique_artwork` es útil para baseline/deduplicación, pero no basta como inventario de impresiones. Antes del generador completo verificar la semántica vigente de estos exports y objetos de cara con documentación oficial y muestras reales. Las páginas oficiales de Scryfall devolvieron HTTP 403 al lector web de esta revisión; por ello no se certificó aquí su esquema actual ni un recuento vivo. [Bulk data](https://scryfall.com/docs/api/bulk-data), [objetos de carta](https://scryfall.com/docs/api/cards), [imágenes](https://scryfall.com/docs/api/images).

Guardar el hash del bulk, fecha, versión del generador y reglas de selección. Contar cartas, caras, ilustraciones, referencias visuales e impresiones por separado. No presupuestar 130.000 como dato real: se usa más abajo únicamente para estimaciones. Identificar referencias faltantes, imágenes placeholder/lowres, layouts excluidos, IDs ausentes, duplicados y mappings conflictivos. Cada exclusión tiene motivo y conteo; cero truncamiento silencioso.

Mantener imágenes completas de referencia offline para conocer su sistema de coordenadas; generar regiones coherentes desde esas imágenes. `art_crop` puede añadirse como referencia secundaria, con tratamiento explícito de su geometría. Deduplicar por apariencia suficiente para identidad sin perder diferentes encuadres, frames o caras. El catálogo de impresiones conserva las relaciones aun cuando varias compartan un vector.

Descargas reanudables en caché externa al repo, claves por ID/cara y hash de contenido, validación de bytes y decodificación, timeouts y backoff. Verificar encabezados y política vigente de Scryfall antes de la descarga completa; inicialmente usar un único descargador con separación mínima de 100 ms, `User-Agent` descriptivo y `Accept` adecuado. No lanzar búsquedas API por cada nombre. Respetar `Retry-After` y separar presupuesto de API de descarga de imágenes sin asumir que un CDN permite paralelismo ilimitado. [FAQ oficial de acceso](https://scryfall.com/docs/faqs/i-m-having-trouble-accessing-the-scryfall-api-or-i-m-blocked-17).

No copiar imágenes ajenas al repositorio de tests sin permiso. El corpus físico será privado y consentido; los fixtures públicos serán sintéticos o con licencia compatible. Registrar licencias de código, pesos y fuentes de datos por separado; acceso a una imagen no demuestra permiso de entrenamiento o redistribución. Confirmar las condiciones aplicables antes de publicar el pack; la documentación consultada no constituye autorización de esos usos.

### 6.2 Herramientas y artefactos

Directorio propuesto `tools/scanner-catalog/`. Python/OpenCV para dataset, experimentos y entrenamiento; Kotlin/JVM para el núcleo determinista cuando convenga. No obligar al entrenamiento a ser JVM. Dependencias bloqueadas, semillas y entorno reproducible. Interfaz de CLI a implementar:

```text
ingest       --snapshot <bulk> --cache <external-dir>
build        --recipe <recipe.json> --out <external-dir>
verify       --pack <path> --strict
match        --image <photo> --pack <path> --ocr off --report <path>
evaluate     --dataset <manifest> --split <name> --pack <path>
export       --channel <beta|stable> --out <external-dir>
```

`--limit` se admite solo en desarrollo y marca el paquete como incompleto; el publicador stable lo rechaza. Exportar reportes de cobertura, vecinos difíciles, retrieval recall, decisión final, coste por etapa, hashes y exclusiones. Muestrear pares aleatorios solo sirve para describir la distribución global. Para estudiar colisiones, calcular vecinos por referencia con búsqueda exacta por bloques o índice aproximado validado contra exacta en una muestra estratificada; no llamar «mínimo global» al mínimo de una muestra. Listar conflictos entre identidades y clusters de referencias casi idénticas.

## 7. Pack, tamaño y distribución por R2

### 7.1 Presupuesto explicado

Ejemplo aritmético para N=130.000 referencias, sin afirmar que ese sea el catálogo:

| Componente | Fórmula | Tamaño decimal sin comprimir |
|---|---|---:|
| pHash 255 bits | N × 32 | 4,16 MB |
| Embedding 256 FP16 | N × 256 × 2 | 66,56 MB |
| Embedding 256 INT8 | N × 256 | 33,28 MB + escalas |
| 64 features locales de 40 bytes | N × 64 × 40 | 332,80 MB |
| 128 features locales de 40 bytes | N × 128 × 40 | 665,60 MB |

Los 40 bytes son un presupuesto de serialización propuesto: descriptor ORB de 32 bytes más posición/atributos empaquetados, no el tamaño en memoria de un objeto OpenCV. Metadatos, encoder, detector, índice y mapas de impresiones se añaden. La compresión debe medirse; no prometer grandes ratios para vectores o descriptores.

Presupuesto inicial de ingeniería: pack de aproximadamente 150–500 MB, ampliable si la mejora está demostrada. No es veto de producto. Ajustar número de referencias y features antes de recortar cobertura. Separar bytes de descarga, bytes instalados, APK/AAB entregado por ABI, RSS/PSS, heap Java, memoria nativa y page faults. `mmap` no hace gratuita la RAM ni garantiza búsquedas rápidas en frío.

### 7.2 Formato y compatibilidad

Reemplazar el archivo monolítico `MHAH` como contrato global por un **pack versionado**, con archivos inmutables y manifest firmado. El baseline puede usar `MHAH` internamente, pero no limita al motor final.

Manifest mínimo: `schemaVersion`, `packId`, `releaseSequence`, `minAppVersion`, `pipelineId`, `preprocessId`, `modelId`, `embeddingDimension`, `dtype`, `metric`, `catalogSnapshotHash`, `coverage`, `calibrationId`, `files[]`, `signingKeyId`. Cada archivo declara ruta relativa controlada, longitud comprimida/instalada y SHA-256. Añadir versiones de detector/verificador y límite máximo de working set previsto. Firma Ed25519 sobre bytes de manifest definidos de forma inequívoca, con verificación compatible con minSdk 29 mediante biblioteca revisada si hace falta; ninguna clave privada en cliente/repo.

Separar hashes/vectores contiguos de metadatos y descriptores por bloques para leerlos con pocos objetos y offsets. Especificar en `FORMAT.md` endianness, alineación, límites, tablas, UTF-8 y aritmética de offsets con Long. Validar overflow, duplicados, índices fuera de rango, NaN/Inf, dimensiones, relaciones de caras y conteos antes de activar. Un hash SHA publicado junto al archivo detecta corrupción, pero no autentica un manifest sustituido.

Modelo, índice, calibración y mapas forman una unidad compatible. No mezclar versiones ni convertir un pack de otro modelo porque coincida la dimensión. Copiar/activar solo datos/modelos, nunca código ejecutable descargado. Los límites de parser/descompresión permanecen finitos aunque el producto acepte packs grandes.

### 7.3 Instalación y actualización

1. Activación del usuario del modo visual: mostrar tamaño y ofrecer descarga; OCR continúa disponible. Wi-Fi por defecto; datos móviles solo por acción expresa. Actualizaciones posteriores según preferencia persistida.
2. Worker único para un pack, progreso persistido, cancelación y reanudación por chunks o Range con ETag/If-Range. Si cambia el objeto, reiniciar el fragmento; no concatenar versiones. Recuperar tras process death y respetar las restricciones Android de trabajo largo/visible vigentes al implementarlo.
3. HTTPS con hosts y redirects permitidos; ruta generada internamente; límites de bytes/tiempo y comprobación de espacio antes de descargar/descomprimir. Reservar espacio para paquete activo + staging + expansión + margen, sin asumir «solo dos veces el zip».
4. Guardar en almacenamiento privado excluido de backup. Verificar firma, hashes, formato y smoke queries; fsync de archivos/metadatos y activación por puntero atómico. Si el proceso muere, recuperar una generación completa.
5. Conservar último pack válido. No borrarlo porque la nueva descarga esté corrupta. Sesiones y queries toman un lease de una generación; retirar mappings solo cuando no haya lectores. Liberación/GC de mappings nativos debe medirse, no asumirse inmediata.
6. Confirmar `Ready` después de cargar y verificar el pack compatible, no después de HTTP 200. Cambiar de pack entre sesiones o invalidar generación, tracks y candidatos pendientes si es necesario cambiar en vivo.
7. Si no hay pack válido, explicar «Visual scanner unavailable» y ofrecer OCR/reintento; si falla una actualización, mantener el visual anterior. No llamar «fallo de reconocimiento» a un catálogo todavía descargándose.

Publicación: `scanner/v2/packs/<packId>/...` inmutable; subir todos los blobs, verificar, subir manifest firmado y actualizar al final un puntero de canal beta/stable. Despliegue con dominio público propio/cache configurada, no `r2.dev` de producción. [Cloudflare R2: acceso público](https://developers.cloudflare.com/r2/buckets/public-buckets/).

La pausa de descarga persiste y no se deshace al relanzar la app o por un worker programado. Eliminar el paquete detiene el visual, resuelve/invalida selecciones dependientes, cancela staging y espera lectores antes de borrar; vuelve a OCR sin modificar la cola. Un token de operación impide que un worker tardío reactive un paquete eliminado. Distinguir «pausar descarga», «pausar cámara» y «desactivar visual» en estado y pruebas.

Canal firmado con secuencia monotónica; rollback autorizado es una nueva secuencia que selecciona un pack previo, no aceptar cualquier replay. Kill switch firmado desactiva el visual/una generación sin borrar la cola y sin exigir red para cada sesión. Definir recuperación, caché y caducidad por separado: no poder refrescar configuración ordinaria no debe inutilizar automáticamente un pack offline válido.

Regenerar incrementalmente ante nuevas referencias/correcciones, con revisión de cobertura; reevaluar cuando cambie el modelo o la distribución. Comprobar novedades al menos semanalmente en operación, no solo una regeneración mensual. El cliente conserva reconocimiento de referencias antiguas si no hay actualización y ofrece OCR para cartas nuevas desconocidas.

## 8. Flujo Android, OCR y selección de impresión

### 8.1 Concurrencia y vida del frame

Una sesión controla el analyzer y el scheduler. `KEEP_ONLY_LATEST`, un trabajo visual activo y como máximo una captura pendiente sustituible. El frame y sus buffers tienen propietario explícito; copiar solo lo necesario a buffers reutilizables o mantener la referencia hasta que termine el consumidor. No compartir un `ImageProxy` cuyo OCR todavía lo lee y cerrarlo al terminar el hash.

El timeout de una coroutine no demuestra que una tarea nativa o ML Kit haya dejado de leer memoria. Probar cancelación antes del inicio de la coroutine, durante copia/inferencia, timeout, exception, bind/unbind y rotación. Cierre exactamente una vez en toda ruta y sin reutilizar buffers antes de liberar lectores. El watchdog no debe permitir que dos propietarios antiguos y nuevos publiquen sobre el mismo estado.

Usar tiempo monotónico y tokens capturados: generación de cámara, modo, catálogo, target/track y preferencias relevantes. Verificar al publicar y antes de añadir; una cancelación no basta si el trabajo nativo no coopera. La hidratación no mantiene abierto el frame, no bloquea la inferencia y se deduplica por candidato. Empezar visual a 4–6 análisis/s adaptativos; el valor final depende de perf/temperatura, no de los FPS de preview.

### 8.2 Temporalidad, duplicados y modo híbrido

Baseline de estabilidad visual: mismo track/identidad verificada durante al menos 250 ms y dos capturas distintas de calidad; ajustar solo con datos. Una captura excepcional puede servir para mostrar resultado, pero no elimina por defecto el gate de auto-adición. Un track retirado o reemplazado reinicia evidencia. La cola conserva sus reglas de duplicados y «añadir otra copia»; la estabilidad visual no cambia cantidades ni reinterpreta dos observaciones como dos cartas.

OCR-only conserva throttle, presupuesto y resultados existentes. En híbrido, OCR opcional sobre la **misma carta rectificada** y zonas conocidas, no otra carta del fondo dentro de la banda antigua. Se conserva el OCR legacy como ruta independiente; cualquier adaptación de extracción se prueba sin cambiar su salida legacy. OCR y arte coincidentes solo aceleran después de calidad y asociación temporal; resultados antiguos se descartan. OCR puede ayudar con set/número/idioma cuando sean legibles, pero no es requisito para acertar la identidad visual.

Arte y OCR en conflicto: limpiar confirmación automática, esperar nueva captura o presentar candidatos. Arte no concluyente y OCR fiable: fallback etiquetado con el contrato actual. No contar un fallback como acierto del motor visual. El score de otra identidad no se ignora porque el set esté bloqueado: filtrar un catálogo hasta dejar un solo candidato puede producir certeza artificial.

### 8.3 Resolver la impresión sin inventarla

Reconocer el arte ofrece el conjunto de impresiones compatibles. Aplicar set bloqueado, idioma y disponibilidad de acabado a ese conjunto; distinguir filtros de usuario de evidencia visual. Si el set bloqueado no tiene una impresión compatible, mostrarlo y no añadir otra automáticamente. Nunca poner `lang=es` a un UUID inglés.

Cuando hay varias impresiones visualmente indistinguibles, mostrar selección. Un modo rápido visual puede proponer una impresión por preferencia explícita y visible, pero debe marcarla como elección predeterminada, no impresión detectada. En beta la política inicial es estricta. El selector usa mappings locales de arte/cara, paginación de impresión/idioma y el componente existente cuando sus contratos encajen; no utiliza el nombre como identidad única.

Paquete mínimo obligatorio: identidad, nombre, mappings de cara/padre e impresión, metadatos para mostrar candidatos offline y payload completo versionado suficiente para construir un `Card` válido de cada impresión elegible. El mapeador y su compatibilidad se validan antes de activar el pack. Identificación, verificación y selección de impresión no usan `CardRepository` remoto ni backend como comprobación o fallback obligatorio. `NeedsPrinting` puede requerir una elección local del usuario; un payload ausente/corrupto produce `NeedsDetails` o indisponibilidad explícita y no entrega una carta incompleta a la cola. Una impresión fuera de la cobertura instalada no se certifica por consulta de red. La cola conserva sus comprobaciones de propietario y su persistencia; la sincronización normal de la colección después de una adición confirmada mantiene su contrato.

Toda llamada de catálogo en la app pasa por repositorios/colas existentes; no llamar a Scryfall por frame ni reintroducir tormentas. Preservar owner/generation y badges acotados del scanner, cola colección/wishlist/deck, commits transaccionales y emisión de XP una sola vez por acción real. Ningún ensayo escribe automáticamente en la colección del usuario.

Al presentar un selector, congelar un snapshot con ID de selección, candidatos, evidencia, versión de paquete, target y contexto de propietario/preferencias. La parada de cámara deliberada al abrirlo invalida frames en vuelo, pero no ese snapshot; nuevos frames no pueden reemplazarlo. Conservarlo o restaurarlo con contrato explícito al rotar/background, sin depender de buffers de cámara. Confirmar contra el snapshot y revalidar destino/propietario; cancelar, cambiar modo/target o eliminar/incompatibilizar el pack lo resuelve explícitamente. Un update puede mantener un lease del pack previo hasta cerrar la selección; nunca reinterpretar sus índices sobre el nuevo pack.

## 9. Experiencia de usuario y observabilidad

La UI muestra «Text scanner» y «Visual scanner»; los nombres de modelos, hashes y formatos se reservan a diagnósticos. OCR asistido es una opción comprensible, no tres pantallas de configuración técnica. El paquete muestra tamaño, versión/fecha de cobertura, descarga, pausa, reintento y eliminación sin tocar la cola. La selección de modo persiste en un store cuya existencia se verifica; no asumir un `scanner_prefs` heredado.

Estados de cámara: buscando carta → contorno detectado → mejorando captura → identidad encontrada → elegir impresión → añadido. La guía conserva proporción física sin un ancho fijo que se salga de pantalla en landscape; el overlay sigue el quad real. Mensajes cortos, estables y accionables: «Move closer», «Reduce glare», «Hold steady», «Choose printing». Sonido/haptic solo al evento correspondiente, nunca cada frame. Ambigüedad presenta varios candidatos o varias impresiones; «Confirm» sobre un solo nombre no la resuelve.

Los overlays mantienen prioridad única y parada de cámara existente. Reutilizar `MagicToast`, `MagicAlertDialog`, `VariantSelectorSheet` y componentes compartidos donde proceda. Tokens `magicColors`, `magicTypography`, spacing y shapes; sin colores/tamaños arbitrarios. Acciones ≥48dp, texto escalado, insets, TalkBack sin anunciar cada frame; no depender solo del color sobre fondos de cámara impredecibles. Verificar las 12 paletas, especialmente NeonVoid y HallowedPrint. Son requisitos de las skills Compose del proyecto, no una migración de su diseño.

Telemetría propuesta, revisada por `crashlytics-ux-auditor` antes de implementar: contadores agregados de `capture_rejected`, `visual_unknown`, `visual_ambiguous`, `visual_accepted`, `ocr_fallback`, `printing_required`, latencia por buckets, fase de instalación y fallo de pack por categoría cerrada. Flush al acabar sesión o ventana acotada; no log por frame. Hasta 3–4 claves de contexto por operación, sin imágenes, embeddings de cámara, nombres/IDs de cartas, texto OCR, rutas privadas, datos de cuenta ni colecciones. Errores externos se sanean sin causas/suprimidos privados. Corrección manual/abandono son señales de fricción, no ground truth automático de precisión.

La revisión de diseño de telemetría está realizada; el contrato final se reaudita al implementar. Crashlytics sirve para diagnóstico, no como denominador de precisión. Los benchmarks registran intentos completos; las tasas de beta requieren resúmenes agregados con denominadores definidos. `visual_accepted` cuenta identidad aceptada una vez por decisión, separado de impresión resuelta y adición confirmada por la cola. Añadir conflicto híbrido e hidratación pendiente/fallida; los rechazos de captura tienen denominador de capturas, no de intentos.

Reservar non-fatals deduplicados para fallos inesperados de carga/inferencia, corrupción o invariantes incumplidas. Mala captura, ambigüedad, ausencia de red, cancelación y descartes obsoletos son estados esperados. Construir errores nuevos con mensaje constante, sin cause/suppressed externos. Contexto máximo propuesto: `scanner_mode`, `scanner_pack_release`, `scanner_phase`, `scanner_failure_category`; enums cerrados y release validada, reiniciados por operación. No transmitir `trackId`, generación de cuenta ni ID global de sesión. La emisión nunca bloquea el analyzer ni retiene buffers; cerrar sesión no garantiza entrega, de ahí las ventanas acotadas.

## 10. Validación: lo que debe demostrar calidad

### 10.1 Dataset y particiones

Dataset v1 mínimo para desarrollar: 500 cartas físicas distintas y ≥3.000 presentaciones independientes, más ≥3.000 presentaciones negativas/desconocidas. Incluir ≥100 presentaciones por familia crítica donde sea posible y reportar denominadores reales. Para promoción se amplía hasta cumplir los intervalos estadísticos de abajo; el mínimo de desarrollo por sí solo no certifica release.

Cubrir marcos modernos/retro, borderless/showcase/full-art/extended, DFC ambas caras, split/adventure, orientaciones, idiomas latinos/no latinos, tierras de arte similar, reprints del mismo arte, ilustraciones lisas, foils, fundas transparentes/mates, fondos claros/oscuros/texturizados, mano, sombras y luz interior. Negativos: cartas MTG deliberadamente fuera del índice, dorso genérico, otros juegos, teléfonos/documentos/rectángulos y arte similar no perteneciente a la identidad. Escenas de solapamiento y carta parcial deben rechazar con claridad si están fuera de alcance.

Separar entrenamiento, calibración y evaluación por sesiones de captura, objetos físicos y familias de ilustración cuando se mida generalización. Ninguna foto/augmentación hermana entra en dos particiones. El índice de evaluación sí contiene referencias limpias de cartas conocidas: eso es el problema real de recuperación; lo que no puede filtrarse son fotos de evaluación o ajustes hechos mirándolas. Mantener un subconjunto de artes no vistas en entrenamiento, un subconjunto de referencias nuevas y otro open-set deliberadamente ausente del índice.

Definir open-set por identidad/apariencia, no solo por UUID: una impresión retirada que comparte identidad y arte con otra presente sigue siendo conocida para reconocimiento de identidad. Separar identidades desconocidas, nuevas impresiones conocidas y conflictos visualmente indistinguibles. Los conflictos conocidos exigen ambigüedad; declarar el límite inevitable de un conflicto que el catálogo todavía desconoce.

Anotar quad físico, identidad/cara, impresión real cuando se sepa, visibilidad de pistas de edición, condiciones y dispositivo. Evaluar siempre contra el catálogo completo y la versión que se publicará. Los diez ejemplos históricos son smoke tests si se recuperan, nunca el test final.

### 10.2 Métricas y puertas de promoción

Una presentación es un intento independiente de colocar una carta; sus frames no multiplican el tamaño estadístico. Predefinir criterio de fin/timeout de 3 s con carta visible y agrupar intervalos por carta/sesión cuando haya repetición. Reportar todo rechazo/timeout, no solo aciertos rápidos.

Un acierto visual automático requiere `RecognizedIdentity` correcto sin OCR ni intervención humana. Que la identidad correcta esté en una lista ambigua, o que el usuario la seleccione, no cuenta como acierto automático. Sí cuenta una identidad resuelta que después necesite elegir impresión; medir la elección de impresión y resolución asistida en columnas separadas.

| Métrica | Objetivo inicial de release |
|---|---|
| Detección útil de carta completa | ≥98% en condiciones normales; esquinas anotadas y error normalizado reportado; evaluar también éxito downstream, no solo IoU de caja. |
| Retrieval Recall@20, quad manual / automático | ≥99,5% normal con quad manual; publicar pérdida causada por localizador y por ANN por separado. |
| Reconocimiento visual correcto en ≤3 s | ≥97% normal y ≥90% en cada familia difícil declarada soportada, OCR apagado. Rechazos cuentan como fallo de cobertura. |
| Identidad incorrecta aceptada automáticamente | Límite superior unilateral 95% ≤0,1% de aceptaciones. Con cero errores se necesitan aproximadamente 3.000 aceptaciones independientes; correlación obliga a más datos/agrupación. |
| Falso accept open-set | Límite superior unilateral 95% ≤0,1% de presentaciones negativas; medir por separado del anterior. |
| Impresión ambigua | 100% de los casos de prueba indistinguibles conservan selección/elección explícita; no se presentan como edición detectada. |
| Tiempo hasta decisión correcta | Objetivo p50 ≤700 ms, p95 ≤1.500 ms normal; documentar desde primera captura utilizable y también desde entrada al visor. |
| Análisis visual móvil | Objetivo p95 ≤200 ms en gama media para mantener 4–6 análisis/s; desglosar etapas y cola. |
| Memoria | Objetivo incremento PSS ≤250 MiB en el dispositivo medio, peak registrado; cero OOM en la matriz mínima. Revisar presupuesto con evidencia si se incumple. |
| Sesión sostenida | 15 minutos por dispositivo, sin bloqueo de cámara ni crecimiento sostenido; latencia p95 final ≤2× inicial; registrar estado térmico/batería. |

Estas son puertas de producto propuestas, no prestaciones comprobadas. Un algoritmo que rechaza todo consigue pocos falsos positivos y aun así falla cobertura. Un promedio global no compensa fallos de una familia. Si una familia no pasa, queda explícitamente manual/no soportada; no reducir el objetivo silenciosamente.

La prueba «no necesita leer el nombre» exige cumplir las mismas puertas de cobertura y errores en un subconjunto independiente, predefinido, con OCR apagado y regiones identificativas de texto ocultas, conservando suficiente arte visible. Dimensionar sus denominadores para los intervalos exigidos; no reutilizar el total del corpus como si todo estuviera enmascarado. Incluir idiomas distintos y publicar también la diferencia frente a capturas sin máscara. No basta apagar ML Kit si el embedding aprendió a leer tipografía. La edición exacta se mide aparte y solo donde haya pistas visibles suficientes.

### 10.3 Matriz Android y fallos inducidos

Al menos tres teléfonos físicos: Android 10/API29 de capacidad baja, gama media reciente y gama alta; incluir fabricantes/cámaras/SoC distintos, dispositivo con páginas de 16 KB cuando haya bibliotecas nativas y delegados CPU/GPU realmente utilizados. Emulador sirve para UI/contratos, no para certificar reconocimiento físico, calentamiento ni autofocus. Verificar binarios OpenCV/LiteRT empaquetados y ABIs; usar `imgproc` no implica que el AAR contenga solo ese módulo. [Android: páginas de 16 KB](https://developer.android.com/guide/practices/page-sizes), [LiteRT: medición](https://ai.google.dev/edge/litert/models/measurement).

Pruebas unitarias/contrato: resolución de caras traseras a su padre, payload local completo con caché ordinaria vacía y spies remotos que demuestren cero peticiones durante identificación/verificación/selección/adaptación a cola; writer-reader independiente, parsing malicioso/corrupto, paridad, transformaciones, score/gap entre identidades, orientación antes de crop, mappings de caras/meld, catálogo incompleto, impresión/idioma, engine switch, estado stale, presupuesto de red, cancelación nativa, propiedad de buffers y no duplicación de cola.

Instrumentación: descarga cortada/reanudada, proceso muerto antes/después de activación, disco lleno, checksum/firma inválidos, versión incompatible, rollback, consulta concurrente con update, restauración de preferencias/cola, ausencia de red, foto cacheada inexistente, 20 entradas/salidas, pausa/reanudación, rotación, overlays, permisos revocados, cambio de cuenta/target y resultados tardíos. Verificar torch después de rebind y cámara detenida tras sheet. No cerrar ni recrear incorrectamente los singletons OCR/sonido.

Regresiones de commits: colección, wishlist y deck con cantidades/atributos, duplicados, selección manual y propietarios; un evento de escaneo no duplica XP ni sustituye guardas existentes. Fuera de la cola no debe producirse ninguna mutación por reconocer una carta.

Añadir selector → detener cámara → rotar/background → confirmar, selector → cambio de pack y eliminación → worker tardío. Con un sink de telemetría falso verificar saneamiento, ausencia de nombres/IDs/OCR/URLs/rutas/causas, deduplicación, cancelación sin non-fatal y volumen acotado durante 15 minutos. Los contadores agregados pueden acumular capturas, pero no emitir un evento por frame.

## 11. Ejecución por bloques con salidas verificables

No comenzar la construcción completa de UI/distribución antes de demostrar recuperación visual y exportación móvil. Se permite un harness Android temprano: exigir cero código Android hasta el final del spike ocultaría fallos de runtime/paridad demasiado tarde.

| Bloque | Trabajo y artefactos | Puerta para continuar |
|---|---|---|
| 0. Baseline y recursos | Inventario actual; evidencia del intento anterior si existe; corpus privado con permisos; manifiesto de splits; modelos/licencias; dispositivos/GPU disponibles; baseline OCR; especificación de identidad/impresión. | Comandos baseline registrados y ejecución de tests pertinentes; fuentes y recursos identificados. Si faltan fotos/hardware, marcar pruebas pendientes, no sustituirlas por datos sintéticos. |
| 1. Catálogo y benchmark | Ingesta reanudable, layout/face inventory, baseline pHash, localizadores y alternativas B/C; pipeline `match/evaluate`; catálogo completo para evaluación. | Reporte reproducible de cobertura, errores y shortlist. Pruebas de nombre enmascarado. Corregir aquí antes de expansión de producto. |
| 2. Viabilidad móvil y selección | Exportar modelo candidato; harness de CameraX/paridad/CPU, comparación ANN-exacta, memoria y latencia en teléfonos; congelar pipeline y recipe. | Un candidato cumple objetivos técnicos iniciales y calidad de calibración. Si ninguno pasa, iterar detector/dataset/representación; no rebajar el plan a pHash por defecto. |
| 3. Pack y operación | `FORMAT.md`, manifiesto firmado, generador reproducible, instalador privado, resume/atomicidad/rollback, estado de pack y kill switch. | Corrupción, process death, compatibilidad y actualización concurrente probados; pack de beta verificable. |
| 4. Integración scanner | Scheduler, track/evidencia, estados visuales, selección de impresión y adaptador a cola; OCR legacy aislado y paridad de conducta; UI accesible. | Tests afectados verdes y aceptación OCR sin regresión; identidad/impresión/Card válidos resueltos localmente y cero peticiones de comprobación al backend. |
| 5. Calidad y beta | Evaluación congelada contra catálogo completo, matriz física, híbrido y ablaciones, prueba térmica, revisión de UI/telemetría/seguridad. | Puertas de §10 con intervalos, conteos y artefactos; beta opt-in sin convertir visual en default. |
| 6. Promoción | Comparar beta, incidencias y correcciones manuales; rollback ensayado; runbook y memorias actualizados. | Activar visual por defecto solo al aprobar todas las puertas; OCR accesible. Sin evidencia suficiente permanece opt-in. |

Cada bloque termina en un checkpoint revisable. Agrupar cambios relacionados y ejecutar pruebas afectadas al final del bloque, no una compilación por archivo. Usar wrapper y cachés externos según AGENTS.md. Ejemplo orientativo de tests existentes:

```powershell
$env:GRADLE_USER_HOME = 'E:\Projects\ManaHub-build\gradle-user-home'
.\gradlew.bat --project-cache-dir 'E:\Projects\ManaHub-build\scanner-project-cache' :app:testDebugUnitTest --tests 'com.mmg.manahub.feature.scanner.*'
```

El ejecutor deberá reutilizar la configuración externa vigente; no crear caches por agente ni outputs en el checkout. Añadir suites de consumidores si se modifica contrato público y `assembleDebug` por bloque de implementación. `testDebugUnitTest` compila todo `src/test`; una falla ajena debe documentarse, no contarse como scanner PASS. Full suite solo cuando lo exijan cambios transversales o el gate final de PR del proyecto. Web sigue pausada.

Responsables de ejecución: `android-kotlin-architect` para Kotlin/Gradle Android y núcleo JVM; responsable de herramientas para Python/dataset/entrenamiento; `android-unit-test-writer` para estrategia/especificaciones; `android-edge-case-tester` para QA y Compose; `crashlytics-ux-auditor` para telemetría; `android-security-auditor` para parser/distribución/modelos y gate de staged diff antes de commit/push/PR. Las revisiones entregan hallazgos al propietario de implementación, no editan Kotlin por su cuenta.

## 12. Entregables y definición de terminado

- Código integrado con OCR legacy conservado, visual independiente y adaptación a la cola existente. Tras instalar el catálogo, resolver localmente identidad, caras traseras, impresión y payload `Card` válido sin comprobaciones de backend; verificar con red desactivada y contadores de peticiones remotas.
- Generador, receta fijada, contrato de formato, corpus/splits privados y reportes reproducibles; resultados rastreables a commit, pack y hardware.
- Pack beta/stable publicado solo después de sus verificaciones; descarga recuperable, firma, fallback al último válido y kill switch operativo.
- Dataset/metadata de cobertura por cara/layout/idioma; catálogo no descrito como «todas las cartas» si tiene huecos.
- Reconocimiento sin nombre demostrado y reporte de exactitud de identidad separado de exactitud de impresión; ninguna promesa universal para cartas indistinguibles.
- Evidencia de latencia/memoria/térmica/disco por dispositivo y de UI en paletas/accesibilidad; no sustituirla por tiempos de desktop.
- Regresiones de OCR, cola, wishlist/deck, autenticación/ownership y gamificación comprobadas.
- Runbook de generación, actualización, recuperación y retirada; licencias/provenance; memoria e índice actualizados y restricciones nuevas en AGENTS.md.

No se considera terminado por compilar, reconocer una demo o lograr cero errores en diez fotos. Tampoco por descargar un índice grande: su tamaño es un recurso permitido, no evidencia de calidad.

## 13. Evidencia de esta revisión y primer encargo de implementación

Realizado: lectura íntegra del borrador, contraste dirigido con código/tests/DI/CameraX/variantes actuales, memorias de fiabilidad y consulta de fuentes primarias enlazadas. Graphify se utilizó para orientación; algunas páginas wiki etiquetadas como scanner apuntan a otros archivos, por lo que las conclusiones de integración se basan en código actual y manifest, no en sus títulos.

No realizado: descarga del corpus, entrenamiento, benchmark visual, evaluación física, compilación Android, publicación R2 ni modificación del scanner. Los resultados históricos no se reprodujeron. Las páginas de esquema de Scryfall no pudieron verificarse por HTTP 403; se exige comprobarlas en bloque 0 antes de ingesta masiva. Ninguna de estas limitaciones impide especificar el trabajo, pero sí impide declarar viable un algoritmo concreto con las tasas aquí exigidas.

**Primer encargo recomendado:** ejecutar bloques 0–2 y entregar un informe comparativo con fotos reales, catálogo completo, arte sin texto, errores por familia y mediciones Android. Congelar en ese checkpoint el localizador, encoder/verificador, formato numérico y presupuesto final. Los bloques restantes ya tienen contratos y puertas de aceptación definidos; no deben comenzar asumiendo que pHash o un embedding sin medir alcanzarán la calidad deseada.
