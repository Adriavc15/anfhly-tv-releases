package com.anfhly.streaming

import android.app.Application
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.firestore.persistentCacheSettings
import com.anfhly.streaming.utils.ImageLoaderFactory

class AnfhlyApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        
        // Initialize Firebase
        FirebaseApp.initializeApp(this)
        
        // Configure Firestore offline persistence
        val db = FirebaseFirestore.getInstance()
        val settings = firestoreSettings {
            setLocalCacheSettings(persistentCacheSettings {
                // Enable single-tab multi-document sync
                setSizeBytes(100 * 1024 * 1024) // 100 MB cache
            })
        }
        db.firestoreSettings = settings
        
        // Enable offline persistence
        db.enableNetwork()
        
        // Configure Coil ImageLoader with external domain support
        coil.Coil.setImageLoader(ImageLoaderFactory.createImageLoader(this))
    }
}
