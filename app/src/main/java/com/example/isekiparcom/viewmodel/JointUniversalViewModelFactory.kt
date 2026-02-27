package com.example.isekiparcom.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class JointUniversalViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(JointUniversalViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return JointUniversalViewModel(context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
