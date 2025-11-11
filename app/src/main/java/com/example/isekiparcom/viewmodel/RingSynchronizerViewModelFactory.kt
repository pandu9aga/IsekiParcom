package com.example.isekiparcom.viewmodel

import android.content.Context
import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class RingSynchronizerViewModelFactory(
    private val context: Context
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(RingSynchronizerViewModel::class.java)) {
            return RingSynchronizerViewModel(context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}