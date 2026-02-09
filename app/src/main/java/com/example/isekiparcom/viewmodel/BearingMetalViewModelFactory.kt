package com.example.isekiparcom.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class BearingMetalViewModelFactory(
    private val context: Context
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BearingMetalViewModel::class.java)) {
            return BearingMetalViewModel(context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}