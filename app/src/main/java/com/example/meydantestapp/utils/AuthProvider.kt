package com.example.meydantestapp.utils

import com.google.firebase.auth.FirebaseAuth

/**
 * Abstraction over [FirebaseAuth] to make authentication-dependent components testable.
 */
interface AuthProvider {
    fun currentUserId(): String?
}

class FirebaseAuthProvider(
    private val delegate: FirebaseAuth = FirebaseAuth.getInstance()
) : AuthProvider {
    override fun currentUserId(): String? = delegate.currentUser?.uid
}
