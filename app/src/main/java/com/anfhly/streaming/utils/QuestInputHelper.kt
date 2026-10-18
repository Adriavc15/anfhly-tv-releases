package com.anfhly.streaming.utils

import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent

/**
 * Helper para traducir los eventos de input de los mandos de Meta Quest 3
 * a los eventos estándar de Android TV (D-pad).
 *
 * En Quest 3 cuando se ejecuta una app 2D sideloaded:
 * - El puntero láser envía MotionEvent (hover/click) como un mouse virtual
 * - El thumbstick puede enviar AXIS_X / AXIS_Y como un joystick
 * - El trigger envía ACTION_DOWN/ACTION_UP (click)
 * - Los botones A/B/X/Y pueden enviar KEYCODE_BUTTON_A, etc.
 * - El botón de menú puede enviar KEYCODE_MENU o KEYCODE_BUTTON_START
 *
 * Esta clase traduce esos eventos para que la app de TV los entienda.
 */
object QuestInputHelper {
    
    private const val TAG = "QuestInputHelper"
    
    // Deadzone para el thumbstick (evitar drift)
    private const val JOYSTICK_DEADZONE = 0.25f
    
    // Tiempo mínimo entre eventos de D-pad simulados desde joystick (ms)
    private const val DPAD_REPEAT_DELAY = 200L
    
    private var lastDpadEventTime = 0L
    private var lastDpadDirection = -1
    
    /**
     * Procesa un MotionEvent genérico (joystick/mouse) y devuelve
     * un KeyEvent simulado de D-pad si corresponde, o null.
     */
    fun processGenericMotionEvent(event: MotionEvent): KeyEvent? {
        // Solo procesar eventos de joystick
        if (!isJoystickSource(event)) return null
        
        if (event.action != MotionEvent.ACTION_MOVE) return null
        
        val x = getCenteredAxis(event, MotionEvent.AXIS_X)
        val y = getCenteredAxis(event, MotionEvent.AXIS_Y)
        
        // También intentar con AXIS_HAT (D-pad del mando)
        val hatX = getCenteredAxis(event, MotionEvent.AXIS_HAT_X)
        val hatY = getCenteredAxis(event, MotionEvent.AXIS_HAT_Y)
        
        val effectiveX = if (kotlin.math.abs(hatX) > kotlin.math.abs(x)) hatX else x
        val effectiveY = if (kotlin.math.abs(hatY) > kotlin.math.abs(y)) hatY else y
        
        return translateJoystickToDpad(effectiveX, effectiveY)
    }
    
    /**
     * Traduce un KeyEvent de botones de gamepad a los equivalentes de Android TV.
     * Retorna el KeyEvent traducido, o null si no necesita traducción.
     */
    fun translateButtonEvent(event: KeyEvent): KeyEvent? {
        val translatedKeyCode = when (event.keyCode) {
            // Botón A / trigger → DPAD_CENTER (seleccionar)
            KeyEvent.KEYCODE_BUTTON_A,
            KeyEvent.KEYCODE_BUTTON_1 -> KeyEvent.KEYCODE_DPAD_CENTER
            
            // Botón B → BACK (retroceder)
            KeyEvent.KEYCODE_BUTTON_B,
            KeyEvent.KEYCODE_BUTTON_2 -> KeyEvent.KEYCODE_BACK
            
            // Botón X → MEDIA_PLAY_PAUSE
            KeyEvent.KEYCODE_BUTTON_X,
            KeyEvent.KEYCODE_BUTTON_3 -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            
            // Botón Y → MENU
            KeyEvent.KEYCODE_BUTTON_Y,
            KeyEvent.KEYCODE_BUTTON_4 -> KeyEvent.KEYCODE_MENU
            
            // Shoulder/Bumper → navegación rápida
            KeyEvent.KEYCODE_BUTTON_L1 -> KeyEvent.KEYCODE_MEDIA_REWIND
            KeyEvent.KEYCODE_BUTTON_R1 -> KeyEvent.KEYCODE_MEDIA_FAST_FORWARD
            
            // Thumbstick press → ENTER
            KeyEvent.KEYCODE_BUTTON_THUMBL,
            KeyEvent.KEYCODE_BUTTON_THUMBR -> KeyEvent.KEYCODE_DPAD_CENTER
            
            // Start/Select → MENU
            KeyEvent.KEYCODE_BUTTON_START -> KeyEvent.KEYCODE_MENU
            KeyEvent.KEYCODE_BUTTON_SELECT -> KeyEvent.KEYCODE_SEARCH
            
            // Botón MODE (Oculus/Meta) → HOME
            KeyEvent.KEYCODE_BUTTON_MODE -> KeyEvent.KEYCODE_HOME
            
            else -> return null // No traducir
        }
        
        Log.d(TAG, "Translating button ${event.keyCode} -> $translatedKeyCode")
        
        return KeyEvent(
            event.downTime,
            event.eventTime,
            event.action,
            translatedKeyCode,
            event.repeatCount,
            event.metaState,
            event.deviceId,
            event.scanCode,
            event.flags or KeyEvent.FLAG_FROM_SYSTEM,
            event.source
        )
    }
    
    /**
     * Verifica si un KeyEvent viene de un gamepad/controller.
     */
    fun isGamepadButton(event: KeyEvent): Boolean {
        return when (event.keyCode) {
            KeyEvent.KEYCODE_BUTTON_A,
            KeyEvent.KEYCODE_BUTTON_B,
            KeyEvent.KEYCODE_BUTTON_X,
            KeyEvent.KEYCODE_BUTTON_Y,
            KeyEvent.KEYCODE_BUTTON_L1,
            KeyEvent.KEYCODE_BUTTON_R1,
            KeyEvent.KEYCODE_BUTTON_L2,
            KeyEvent.KEYCODE_BUTTON_R2,
            KeyEvent.KEYCODE_BUTTON_THUMBL,
            KeyEvent.KEYCODE_BUTTON_THUMBR,
            KeyEvent.KEYCODE_BUTTON_START,
            KeyEvent.KEYCODE_BUTTON_SELECT,
            KeyEvent.KEYCODE_BUTTON_MODE,
            KeyEvent.KEYCODE_BUTTON_1,
            KeyEvent.KEYCODE_BUTTON_2,
            KeyEvent.KEYCODE_BUTTON_3,
            KeyEvent.KEYCODE_BUTTON_4 -> true
            else -> false
        }
    }
    
    /**
     * Verifica si un MotionEvent es un click (trigger del mando Quest).
     * Los mandos de Quest envían ACTION_DOWN/UP cuando se presiona el trigger.
     */
    fun isPointerClick(event: MotionEvent): Boolean {
        return event.action == MotionEvent.ACTION_DOWN || 
               event.action == MotionEvent.ACTION_UP
    }
    
    /**
     * Verifica si un MotionEvent es un hover (puntero láser del mando Quest).
     */
    fun isPointerHover(event: MotionEvent): Boolean {
        return event.action == MotionEvent.ACTION_HOVER_MOVE ||
               event.action == MotionEvent.ACTION_HOVER_ENTER ||
               event.action == MotionEvent.ACTION_HOVER_EXIT
    }
    
    /**
     * Obtiene las coordenadas del puntero del MotionEvent.
     */
    fun getPointerPosition(event: MotionEvent): Pair<Float, Float> {
        return Pair(event.x, event.y)
    }
    
    // --- Funciones privadas ---
    
    private fun isJoystickSource(event: MotionEvent): Boolean {
        return event.source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK ||
               event.source and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD
    }
    
    private fun getCenteredAxis(event: MotionEvent, axis: Int): Float {
        val device = event.device ?: return 0f
        val range = device.getMotionRange(axis, event.source) ?: return 0f
        val value = event.getAxisValue(axis)
        
        // Apply deadzone
        val flat = range.flat.coerceAtLeast(JOYSTICK_DEADZONE)
        return if (kotlin.math.abs(value) > flat) value else 0f
    }
    
    private fun translateJoystickToDpad(x: Float, y: Float): KeyEvent? {
        if (x == 0f && y == 0f) {
            lastDpadDirection = -1
            return null
        }
        
        val now = SystemClock.uptimeMillis()
        
        // Determinar dirección dominante
        val keyCode = if (kotlin.math.abs(x) > kotlin.math.abs(y)) {
            if (x > 0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
        } else {
            if (y > 0) KeyEvent.KEYCODE_DPAD_DOWN else KeyEvent.KEYCODE_DPAD_UP
        }
        
        // Rate limiting para evitar navegación demasiado rápida
        if (keyCode == lastDpadDirection && now - lastDpadEventTime < DPAD_REPEAT_DELAY) {
            return null
        }
        
        lastDpadEventTime = now
        lastDpadDirection = keyCode
        
        Log.d(TAG, "Joystick -> D-pad: keyCode=$keyCode (x=$x, y=$y)")
        
        return KeyEvent(
            now, now,
            KeyEvent.ACTION_DOWN,
            keyCode,
            0, 0,
            InputDevice.SOURCE_KEYBOARD,
            0,
            KeyEvent.FLAG_FROM_SYSTEM,
            InputDevice.SOURCE_DPAD
        )
    }
}
