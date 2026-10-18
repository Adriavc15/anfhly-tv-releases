# ANFHLYTV - Android TV App

Plataforma de Streaming para Android TV desarrollada en Kotlin con Jetpack Compose.

<img width="1897" height="990" alt="image" src="https://github.com/user-attachments/assets/6f166cf7-6fbc-443c-8d2a-90d21e1c4aa9" />

## Características

- **Autenticación Firebase**: Login/Registro con email y contraseña
- **Gestión de Perfiles**: Soporte multi-perfil con hasta 5 perfiles por usuario
- **Navegación TV-Optimized**: Navegación completa con control remoto (flechas, Enter, Escape)
- **Home Screen**: Carrusel destacado, filas de contenido categorizado
- **Películas y Series**: Interfaz dedicada para cada tipo de contenido
- **Búsqueda**: Búsqueda en tiempo real de películas y series
- **Configuración**: Ajustes de audio, video, subtítulos y privacidad
- **Reproductor**: Reproductor de video con ExoPlayer

## Estructura del Proyecto

```
android-tv/
├── app/
│   ├── src/main/java/com/anfhly/streaming/
│   │   ├── data/
│   │   │   ├── model/           # Modelos de datos (User, Profile, Content, etc.)
│   │   │   ├── repository/      # Repositorios Firebase (Auth, Profile, Content, Watchlist)
│   │   │   └── local/           # DataStore para preferencias locales
│   │   ├── ui/
│   │   │   ├── screens/         # Pantallas (Login, Profiles, Home, Settings, Player)
│   │   │   ├── theme/           # Colores y temas
│   │   │   └── viewmodel/       # ViewModels
│   │   ├── MainActivity.kt      # Actividad principal y navegación
│   │   ├── PlayerActivity.kt    # Reproductor de video
│   │   └── AnfhlyApplication.kt # Configuración de Firebase
│   └── src/main/res/            # Recursos (layouts, strings, colores, drawables)
├── build.gradle                 # Configuración del proyecto
└── settings.gradle
```

## Requisitos

- Android 5.0+ (API 21)
- Android TV con Leanback
- Conexión a Internet
- Firebase Project configurado

## Configuración Firebase

1. Crea un proyecto en [Firebase Console](https://console.firebase.google.com/)
2. Agrega una app Android con el package `com.anfhly.streaming`
3. Descarga `google-services.json` y colócalo en `app/`
4. Habilita Authentication (Email/Password) y Firestore Database

## Compilación

```bash
# Limpiar y compilar
./gradlew clean build

# Instalar en dispositivo/emulador
./gradlew installDebug
```

## Controles del Control Remoto

| Tecla | Acción |
|-------|--------|
| Flechas ↑↓←→ | Navegación entre elementos |
| Enter/OK | Seleccionar elemento enfocado |
| Escape/Back | Volver atrás |
| Home | Ir al inicio |
| Play/Pause | Reproducir/Pausar en reproductor |

## Pantallas Implementadas

### 1. Splash Screen
- Logo animado ANFHLY
- Transición automática a Login o Home

### 2. Login Screen
- Pantalla de bienvenida
- Formulario de email/contraseña
- "Remember email" checkbox
- Navegación completa por control remoto
- Mensajes de error específicos

### 3. Profiles Screen
- Grid de perfiles (máx 5)
- Crear, editar, eliminar perfiles
- Perfiles bloqueados con PIN
- Avatares personalizables
- Selección de perfil

### 4. Home Screen
- Header con menú (Inicio, Películas, Series, Buscar, Perfil, Config)
- Carrusel destacado auto-rotativo
- Filas de contenido por categoría
- Navegación 2D completa

### 5. Movies/Series Screens
- Carrusel destacado
- Grid de contenido
- Información detallada (rating, año, duración)

### 6. Search Screen
- Barra de búsqueda
- Resultados en tiempo real
- Filtros por tipo

### 7. Settings Screen
- Audio (idioma, habilitar/deshabilitar)
- Subtítulos (idioma, habilitar/deshabilitar)
- Video (calidad, auto-calidad)
- General (autoplay, idioma interfaz)
- Privacidad (control parental, notificaciones)
- Cuenta (logout, eliminar cuenta)
- Guardado automático

### 8. Player Screen
- ExoPlayer integrado
- Controles on-screen
- Seek adelante/atrás (10s)
- Indicador de progreso
- Buffering indicator

## Arquitectura

- **MVVM**: Model-View-ViewModel
- **Repository Pattern**: Abstracción de fuentes de datos
- **Dependency Injection**: Manual con factories
- **State Flow**: Para UI reactive
- **Navigation**: State-based composition

## Dependencias Principales

- Jetpack Compose for TV
- Firebase Auth & Firestore
- ExoPlayer
- Coil (carga de imágenes)
- Kotlin Coroutines
- AndroidX TV Leanback

## Notas de Desarrollo

- Usa `androidx.tv:tv-material` para componentes optimizados para TV
- Navegación por control remoto implementada con `onPreviewKeyEvent`
- Focus management manual para TV experience consistente
- DataStore para preferencias locales
- Offline support con Firestore persistence
- ProGuard configurado para Firebase y Compose

## Próximas Mejoras

- [ ] Integración con TMDB API
- [ ] Soporte para trailers
- [ ] Watchlist/My List
- [ ] Historial de reproducción
- [ ] Soporte para subtitles VTT/SRT
- [ ] Picture-in-Picture
- [ ] Chromecast support
- [ ] Search por voz
- [ ] Recomendaciones personalizadas

## Licencia

Proyecto privado - ANFHLY Streaming Platform
>>>>>>> a3dabc8 (feat: complete Android TV streaming app implementation)
