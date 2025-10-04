package com.example.meydantestapp.utils

import com.google.firebase.firestore.FirebaseFirestore

/**
 * Abstraction for obtaining a [FirebaseFirestore] instance. This indirection makes
 * JVM unit tests independent from real Firebase initialisation while keeping
 * production code unchanged.
 */
interface FirestoreProvider {
    fun get(): FirebaseFirestore
}

object DefaultFirestoreProvider : FirestoreProvider {
    override fun get(): FirebaseFirestore = FirebaseFirestore.getInstance()
}
