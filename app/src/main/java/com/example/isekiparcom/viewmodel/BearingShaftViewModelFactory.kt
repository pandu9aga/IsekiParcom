package com.example.isekiparcom.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class BearingShaftViewModelFactory(
    private val context: Context
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BearingShaftViewModel::class.java)) {
            return BearingShaftViewModel(context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}