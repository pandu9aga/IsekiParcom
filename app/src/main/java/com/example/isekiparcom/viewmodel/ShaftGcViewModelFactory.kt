package com.example.isekiparcom.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class ShaftGcViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ShaftGcViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ShaftGcViewModel(context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
